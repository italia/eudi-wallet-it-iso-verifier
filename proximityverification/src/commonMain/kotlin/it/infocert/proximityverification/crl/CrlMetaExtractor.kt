package it.infocert.proximityverification.crl

import it.infocert.proximityverification.crl.model.CrlMeta
import it.infocert.proximityverification.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.X509Crl
import kotlin.time.ExperimentalTime

/**
 * Extracts validity metadata (`thisUpdate`, `nextUpdate`) from a raw
 * CRL DER payload by delegating to multipaz `X509Crl`.
 *
 * Verified against multipaz 0.98.0: `X509Crl.thisUpdate` is a non-null
 * `Instant` and `nextUpdate` is `Instant?`. No ASN.1 fallback is
 * required.
 *
 * Errors are absorbed: any parse failure (or unexpected `Throwable`) is
 * logged at WARN and produces an empty [CrlMeta] with both fields
 * `null`. `CancellationException` is propagated verbatim per the
 * project-wide cancellation contract.
 */
internal object CrlMetaExtractor {
    private const val TAG = "CrlMetaExtractor"

    @OptIn(ExperimentalTime::class)
    fun extract(payloadBytes: ByteArray): CrlMeta {
        return try {
            val crl = X509Crl(ByteString(payloadBytes))
            CrlMeta(
                thisUpdateEpochSeconds = crl.thisUpdate.epochSeconds,
                nextUpdateEpochSeconds = crl.nextUpdate?.epochSeconds
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(TAG) { "extract failed (returning empty meta): ${e.message}" }
            CrlMeta()
        }
    }
}
