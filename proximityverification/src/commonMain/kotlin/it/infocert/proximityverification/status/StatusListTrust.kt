package it.infocert.proximityverification.status

import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.status.model.CachedFormat
import it.infocert.proximityverification.status.model.StatusListPayload
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.multipaz.cbor.Cbor
import org.multipaz.cbor.DataItem
import org.multipaz.cbor.Tagged
import org.multipaz.cose.Cose
import org.multipaz.cose.CoseSign1
import org.multipaz.cose.toCoseLabel
import org.multipaz.crypto.EcPublicKey
import org.multipaz.crypto.X509CertChain
import org.multipaz.trustmanagement.TrustManagerInterface
import org.multipaz.util.fromBase64Url
import org.multipaz.util.toHex
import kotlin.time.Instant

/**
 * Resolves the issuer signing key for a Token Status List token by validating
 * the certificate chain advertised in the token header against the SDK's
 * [TrustManagerInterface] (populated at init from
 * `RelyingPartyProviderInfo.trustAnchors`).
 *
 * Per RFC 7515 §4.1.6 (`x5c`) and RFC 9360 §2 (`x5chain`), the certificate
 * carrying the public key used to sign the token is **always** the first
 * element of the chain. Subsequent elements form the path toward a trust
 * anchor.
 *
 * This object only enforces trust on the chain; it does NOT verify the JWS /
 * COSE_Sign1 signature itself — that step is delegated to multipaz's
 * `StatusList.fromJwt` / `StatusList.fromCwt`, which expects the resolved
 * [EcPublicKey] as `publicKey`.
 */
internal object StatusListTrust {
    private const val TAG = "StatusListTrust"
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Validates the chain and, if trusted, returns the leaf certificate's
     * public key. Returns `null` when the token has no chain header, the chain
     * cannot be parsed, or it does not lead to a known trust anchor.
     */
    suspend fun resolveSigningKey(
        payload: StatusListPayload,
        format: CachedFormat,
        trustManager: TrustManagerInterface,
        atTime: Instant
    ): EcPublicKey? {
        val chain: X509CertChain = extractChain(payload, format) ?: return null
        val result = trustManager.verify(chain.certificates, atTime)
        if (!result.isTrusted) {
            Logger.warn(TAG) {
                val chainSummary: String = chain.certificates.joinToString(separator = " <- ") { c ->
                    "subj='${c.subject.name}' SKI=${c.subjectKeyIdentifier?.toHex() ?: "<none>"} " +
                        "AKI=${c.authorityKeyIdentifier?.toHex() ?: "<none>"}"
                }
                "Status list signing chain is not anchored at any known trust point" +
                    (result.error?.let { " (${it::class.simpleName}: ${it.message})" } ?: "") +
                    "; offered chain (leaf -> root): [$chainSummary]. " +
                    "Compare these AKIs against the SKIs of the trust anchors registered " +
                    "via RelyingPartyProviderInfo.trustAnchors."
            }
            return null
        }
        return chain.certificates.first().ecPublicKey
    }

    private fun extractChain(payload: StatusListPayload, format: CachedFormat): X509CertChain? {
        return try {
            when (format) {
                CachedFormat.JWT -> extractFromJwt(payload.bytes)
                CachedFormat.CWT -> extractFromCwt(payload.bytes)
            }
        } catch (e: Throwable) {
            Logger.warn(TAG) { "Failed to extract certificate chain (${format}): ${e::class.simpleName}: ${e.message}" }
            null
        }
    }

    private fun extractFromJwt(bytes: ByteArray): X509CertChain? {
        val parts: List<String> = bytes.decodeToString().trim().split(".")
        if (parts.size != 3) return null
        val header: JsonObject = json.decodeFromString(
            JsonObject.serializer(),
            parts[0].fromBase64Url().decodeToString()
        )
        val x5c: JsonElement = header["x5c"] ?: return null
        return X509CertChain.fromX5c(x5c)
    }

    private fun extractFromCwt(bytes: ByteArray): X509CertChain? {
        val outer: DataItem = Cbor.decode(bytes)
        val sign1Item: DataItem = if (outer is Tagged) outer.taggedItem else outer
        val sign1: CoseSign1 = CoseSign1.fromDataItem(sign1Item)
        val x5chain: DataItem = sign1.protectedHeaders[Cose.COSE_LABEL_X5CHAIN.toCoseLabel]
            ?: sign1.unprotectedHeaders[Cose.COSE_LABEL_X5CHAIN.toCoseLabel]
            ?: return null
        return X509CertChain.fromDataItem(x5chain)
    }
}
