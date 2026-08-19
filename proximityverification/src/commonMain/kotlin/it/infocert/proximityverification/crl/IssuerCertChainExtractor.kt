package it.infocert.proximityverification.crl

import it.infocert.proximityverification.crl.model.ChainExtractionResult
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.registry.model.IssuerResolvedMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.multipaz.crypto.X509Cert
import org.multipaz.util.fromBase64

/**
 * Extracts every certificate published by an issuer in
 * `metadata.openid_credential_issuer.jwks.keys[0].x5c[0..n]`, in original
 * order — leaf included.
 *
 * Errors are absorbed into [ChainExtractionResult]:
 * - missing `jwks`, `keys`, `x5c`, or out-of-range index → empty list,
 *   `parseFailureCount = 0`, WARN logged for diagnostics;
 * - per-cert Base64 / DER parse failure → skip-and-warn, increment
 *   `parseFailureCount`.
 *
 * The presence (or absence) of a `cRLDistributionPoints` extension on each
 * cert is not the extractor's concern; it is decided downstream by
 * [CrlPrefetcher].
 *
 * `CancellationException` is propagated verbatim per the project-wide
 * cancellation contract.
 */
internal object IssuerCertChainExtractor {
    private const val TAG: String = "IssuerCertChainExtractor"

    fun extract(issuerMetadata: IssuerResolvedMetadata): ChainExtractionResult {
        val jwks: JsonObject = issuerMetadata.jwks ?: run {
            Logger.warn(TAG) {
                "extract(${issuerMetadata.entityId}): jwks missing → empty chain"
            }
            return ChainExtractionResult(emptyList(), 0)
        }
        val keys: JsonArray = jwks["keys"]?.jsonArray ?: run {
            Logger.warn(TAG) {
                "extract(${issuerMetadata.entityId}): jwks.keys missing → empty chain"
            }
            return ChainExtractionResult(emptyList(), 0)
        }
        val key0: JsonObject = keys.getOrNull(0)?.jsonObject ?: run {
            Logger.warn(TAG) {
                "extract(${issuerMetadata.entityId}): jwks.keys empty → empty chain"
            }
            return ChainExtractionResult(emptyList(), 0)
        }
        val x5c: JsonArray = key0["x5c"]?.jsonArray ?: run {
            Logger.warn(TAG) {
                "extract(${issuerMetadata.entityId}): jwks.keys[0].x5c missing → empty chain"
            }
            return ChainExtractionResult(emptyList(), 0)
        }
        val b64Strings: List<String> = x5c.mapNotNull { entry ->
            try {
                entry.jsonPrimitive.contentOrNull
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.warn(TAG) {
                    "extract(${issuerMetadata.entityId}): non-string entry in x5c → skip: ${e.message}"
                }
                null
            }
        }
        var parseFailures: Int = 0
        val parsed: MutableList<X509Cert> = mutableListOf()
        // Track entries where the JsonElement was non-string (mapNotNull above
        // already incremented the warn-log, but we need to count them too).
        val nonStringSkips: Int = x5c.size - b64Strings.size
        parseFailures += nonStringSkips
        for ((index, b64) in b64Strings.withIndex()) {
            val cert: X509Cert? = try {
                val der: ByteArray = b64.fromBase64()
                X509Cert(ByteString(der))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.warn(TAG) {
                    "extract(${issuerMetadata.entityId}): x5c[$index] parse failed → skip: " +
                        "${e::class.simpleName}: ${e.message}"
                }
                null
            }
            if (cert != null) {
                parsed.add(cert)
            } else {
                parseFailures += 1
            }
        }
        return ChainExtractionResult(
            certificates = parsed.toList(),
            parseFailureCount = parseFailures
        )
    }
}
