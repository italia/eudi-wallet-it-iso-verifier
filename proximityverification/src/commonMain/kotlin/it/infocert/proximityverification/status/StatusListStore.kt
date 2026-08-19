package it.infocert.proximityverification.status

import it.infocert.proximityverification.core.crypto.LocalStorage
import it.infocert.proximityverification.core.crypto.provideLocalStorage
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.offline.OfflineModeFlag
import it.infocert.proximityverification.status.model.CachedStatusList
import it.infocert.proximityverification.status.model.StatusListMeta
import it.infocert.proximityverification.status.model.StatusListPayload
import it.infocert.proximityverification.status.model.Usability
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.multipaz.util.fromBase64
import org.multipaz.util.toBase64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Persistent cache of Token Status Lists, stored as a single JSON envelope
 * under [STORAGE_KEY] in [LocalStorage]. Single-tenant by design (mirrors
 * the `RegistryResolver` pattern) — a single SDK install holds one envelope
 * shared across all issuers.
 *
 * Cleared together with the rest of the SDK state by
 * [it.infocert.proximityverification.ProximityVerification.clearLibraryLocalStorage].
 */
internal class StatusListStore(
    private val storage: LocalStorage = provideLocalStorage(),
    private val clock: () -> Long = { defaultEpochMillis() }
) {
    private val tag: String = "StatusListStore"
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Envelope(val entries: Map<String, CachedStatusList> = emptyMap())

    /**
     * Persists a new entry for [uri], overwriting any existing one.
     *
     * Non-cancellation errors propagate to the caller. Use-time write-through
     * (in `MsoRevocationChecker.checkStatusList`) wraps this call in a
     * best-effort try/catch so verification outcome is never affected. The
     * prefetch path (in `StatusListPrefetcher`) wraps it to surface
     * `FailureReason.STORAGE_ERROR` in the summary.
     *
     * `CancellationException` is re-thrown verbatim per RATIONALE [2026-02-23].
     *
     * The optional [issuerEntityId], [credentialTypes] and [docTypes] parameters
     * carry provenance context surfaced through `CachedStatusListInfo` in the
     * consumer-facing offline-cache snapshot. They default to safe empty values
     * so legacy call sites keep compiling unchanged.
     */
    suspend fun put(
        uri: String,
        payload: StatusListPayload,
        meta: StatusListMeta,
        issuerEntityId: String? = null,
        credentialTypes: List<String> = emptyList(),
        docTypes: List<String> = emptyList()
    ) {
        val envelope: Envelope = readEnvelope()
        val entry = CachedStatusList(
            uri = uri,
            format = meta.format,
            payloadBase64 = payload.bytes.toBase64(),
            iat = meta.iat,
            ttlSeconds = meta.ttlSeconds,
            fetchedAtEpochMillis = clock(),
            issuerEntityId = issuerEntityId,
            credentialTypes = credentialTypes,
            docTypes = docTypes
        )
        val updated = Envelope(entries = envelope.entries + (uri to entry))
        writeEnvelope(updated)
    }

    /** Returns the cached entry for [uri], or `null` when missing or unreadable. */
    suspend fun get(uri: String): CachedStatusList? {
        return try {
            readEnvelope().entries[uri]
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "get($uri) failed: ${e.message}" }
            null
        }
    }

    /** Returns a snapshot of all cached entries. Empty list on read failure. */
    suspend fun listAll(): List<CachedStatusList> {
        return try {
            readEnvelope().entries.values.toList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "listAll failed (returning empty list): ${e.message}" }
            emptyList()
        }
    }

    /** Decodes the cached payload bytes for use-time parsing. */
    fun decodePayload(entry: CachedStatusList): ByteArray = entry.payloadBase64.fromBase64()

    /**
     * Computes [Usability] for a cached entry. `Expired` is **not** decided
     * here — it is decided at use time when multipaz `validateXxx` rejects the
     * payload on `exp`. This method only distinguishes `Fresh` vs `StaleByTtl`
     * based on the spec-defined soft-cache window `iat + ttl`.
     */
    fun usability(entry: CachedStatusList, nowEpochSeconds: Long): Usability {
        val iat: Long = entry.iat ?: return Usability.Fresh
        val ttl: Long = entry.ttlSeconds ?: return Usability.Fresh
        return if (nowEpochSeconds <= iat + ttl) Usability.Fresh else Usability.StaleByTtl
    }

    suspend fun isEnabled(): Boolean {
        return try {
            val raw: String? = storage.getElement(STORAGE_KEY_ENABLED)
            raw == "true"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "isEnabled read failed (treating as false): ${e.message}" }
            false
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        storage.saveElement(STORAGE_KEY_ENABLED, if (enabled) "true" else "false")
    }

    suspend fun clearEntries() {
        try {
            storage.clearElement(STORAGE_KEY)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "clearEntries failed: ${e.message}" }
        }
    }

    private suspend fun readEnvelope(): Envelope {
        val raw: String? = storage.getElement(STORAGE_KEY)
        if (raw.isNullOrBlank()) return Envelope()
        return try {
            json.decodeFromString(Envelope.serializer(), raw)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "Envelope decode failed (treating as empty): ${e.message}" }
            Envelope()
        }
    }

    private suspend fun writeEnvelope(envelope: Envelope) {
        val raw: String = json.encodeToString(Envelope.serializer(), envelope)
        storage.saveElement(STORAGE_KEY, raw)
    }

    companion object {
        const val STORAGE_KEY: String = "PROX_VER_STATUS_LIST_CACHE"
        const val STORAGE_KEY_ENABLED: String = OfflineModeFlag.STORAGE_KEY

        @OptIn(ExperimentalTime::class)
        private fun defaultEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
    }
}
