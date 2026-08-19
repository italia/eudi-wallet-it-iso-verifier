package it.infocert.proximityverification.status

import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.status.model.CachedFormat
import it.infocert.proximityverification.status.model.StatusListMeta
import it.infocert.proximityverification.status.model.StatusListPayload
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tagged
import org.multipaz.cbor.toDataItem
import org.multipaz.util.fromBase64Url

/**
 * Extracts freshness metadata (`iat`, `ttl`) from a Status List envelope without
 * verifying its signature. Used at prefetch time to enrich
 * [it.infocert.proximityverification.status.model.CachedStatusList].
 *
 * Format detection priority:
 * 1. `Content-Type` of the response (`application/statuslist+jwt` vs
 *    `application/statuslist+cwt`).
 * 2. Heuristic: payload starts with three Base64URL segments separated by `.` ⇒
 *    JWT; otherwise CWT.
 *
 * Missing claims are surfaced as `null` (the cache treats them as "Fresh
 * indefinitely").
 */
internal object FreshnessExtractor {
    private const val TAG = "FreshnessExtractor"
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun extract(payload: StatusListPayload): StatusListMeta {
        val format: CachedFormat = detectFormat(payload)
        return try {
            when (format) {
                CachedFormat.JWT -> extractFromJwt(payload.bytes)
                CachedFormat.CWT -> extractFromCwt(payload.bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(TAG) { "Freshness extraction failed (${format}): ${e.message}" }
            StatusListMeta(format = format)
        }
    }

    private fun detectFormat(payload: StatusListPayload): CachedFormat {
        val ct: String? = payload.contentType?.lowercase()
        if (ct != null) {
            if (ct.contains("statuslist+jwt") || ct.contains("application/jwt")) return CachedFormat.JWT
            if (ct.contains("statuslist+cwt") || ct.contains("application/cwt")) return CachedFormat.CWT
        }
        val asString: String = try { payload.bytes.decodeToString() } catch (_: Throwable) { "" }
        return if (asString.count { it == '.' } == 2 &&
            asString.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' || it == '=' }
        ) CachedFormat.JWT else CachedFormat.CWT
    }

    private fun extractFromJwt(bytes: ByteArray): StatusListMeta {
        val jwt: String = bytes.decodeToString().trim()
        val parts: List<String> = jwt.split(".")
        require(parts.size == 3) { "JWT must have 3 segments, got ${parts.size}" }
        val claimsBytes: ByteArray = parts[1].fromBase64Url()
        val obj: JsonObject = json.decodeFromString(JsonObject.serializer(), claimsBytes.decodeToString())
        return StatusListMeta(
            format = CachedFormat.JWT,
            iat = (obj["iat"] as? JsonPrimitive)?.longOrNull,
            ttlSeconds = (obj["ttl"] as? JsonPrimitive)?.longOrNull
        )
    }

    private fun extractFromCwt(bytes: ByteArray): StatusListMeta {
        val outer: DataItem = Cbor.decode(bytes)
        val sign1: DataItem = if (outer is Tagged) outer.taggedItem else outer
        val payloadBytes: ByteArray = sign1.asArray[2].asBstr
        val claims: DataItem = Cbor.decode(payloadBytes)
        return StatusListMeta(
            format = CachedFormat.CWT,
            iat = readIntClaim(claims, key = 6L),
            ttlSeconds = readIntClaim(claims, key = 65534L)
        )
    }

    private fun readIntClaim(map: DataItem, key: Long): Long? {
        return try {
            val keyItem: DataItem = key.toDataItem()
            map.asMap[keyItem]?.asNumber
        } catch (_: Throwable) {
            null
        }
    }
}
