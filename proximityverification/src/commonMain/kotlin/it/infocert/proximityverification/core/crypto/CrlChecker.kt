package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.crl.CrlFetchResult
import it.infocert.proximityverification.crl.CrlHttpFetcher
import it.infocert.proximityverification.crl.CrlMetaExtractor
import it.infocert.proximityverification.crl.CrlStore
import it.infocert.proximityverification.crl.DefaultCrlHttpFetcher
import it.infocert.proximityverification.crl.model.CachedCrl
import it.infocert.proximityverification.crl.model.CrlMeta
import it.infocert.proximityverification.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509Crl
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Per-cert outcome produced by [CrlChecker.checkCert]. Aggregated
 * across a chain by `verifyNoneOfCertificateIsRevoked(chain, checker)`.
 */
internal data class CertCrlOutcome(
    val isRevoked: Boolean,
    val status: CrlCheckStatus,
    val error: Throwable? = null
)

/**
 * Cache- and offline-aware orchestrator for CRL revocation checks.
 *
 * Mirrors the `MsoRevocationChecker` injection pattern: connectivity
 * and offline-mode flags are passed in as suspend lambdas so the
 * decision matrix is easy to exercise from tests.
 *
 * Decision matrix (see unit-03 spec):
 *
 * | online | offline_enabled | cache | freshness | refresh   | outcome                       |
 * |--------|-----------------|-------|-----------|-----------|-------------------------------|
 * | true   | (any)           | -     | -         | success   | VERIFIED + check(fresh)       |
 * | true   | (any)           | -     | -         | fail/none | FETCH_FAILED + non-revoked    |
 * | true   | (any)           | yes   | fresh     | -         | VERIFIED + check(cached)      |
 * | true   | (any)           | yes   | stale     | success   | REFRESHED + check(fresh)      |
 * | true   | (any)           | yes   | stale     | fail      | STALE_CACHE + check(cached)   |
 * | false  | true            | yes   | fresh     | -         | VERIFIED + check(cached)      |
 * | false  | true            | yes   | stale     | -         | STALE_CACHE + check(cached)   |
 * | false  | true            | -     | -         | -         | OFFLINE_NO_DATA + non-revoked |
 * | false  | false           | -     | -         | -         | FETCH_FAILED + non-revoked    |
 *
 * `CancellationException` is propagated verbatim throughout.
 */
internal class CrlChecker(
    private val store: CrlStore? = null,
    private val isOfflineEnabled: suspend () -> Boolean = { false },
    private val isOnline: suspend () -> Boolean = { true },
    private val httpFetcher: CrlHttpFetcher = DefaultCrlHttpFetcher(),
    private val crlUriLookup: (X509Cert) -> List<String> = TrustUtils::extractCrlDistributionPoint,
    private val clock: () -> Long = { defaultEpochMillis() }
) {
    private val tag: String = "CrlChecker"

    suspend fun checkCert(cert: X509Cert): CertCrlOutcome {
        val online: Boolean = isOnline()
        val offlineOn: Boolean = isOfflineEnabled()
        val cached: CachedCrl? = store?.get(cert)
        val nowSec: Long = clock() / 1000L

        // Branch 1 — offline-mode enabled (user is in airplane / no-network mode).
        if (offlineOn && !online) {
            if (cached == null) {
                Logger.warn(tag) {
                    "offline + no cache for cert ${cert.subject.name}: returning OFFLINE_NO_DATA (non-revoked)"
                }
                return CertCrlOutcome(isRevoked = false, status = CrlCheckStatus.OFFLINE_NO_DATA)
            }
            val isStale: Boolean = store!!.isStale(cached, nowSec)
            return decideFromCached(
                cert = cert,
                cached = cached,
                status = if (isStale) CrlCheckStatus.STALE_CACHE else CrlCheckStatus.VERIFIED
            )
        }

        // Branch 2 — online (regardless of offline flag): use cache when fresh, else refresh.
        if (online) {
            // Fresh-cached → use it without HTTP.
            // (cached != null implies store != null since `store?.get()` produced it.)
            if (cached != null && !store!!.isStale(cached, nowSec)) {
                return decideFromCached(
                    cert = cert,
                    cached = cached,
                    status = CrlCheckStatus.VERIFIED
                )
            }
            // No cache OR stale-cache → attempt refresh.
            val uris: List<String> = safeUriLookup(cert)
            if (uris.isEmpty()) {
                // No distribution points — surface CACHE_MISS rather than FETCH_FAILED
                // because no fetch was even possible.
                Logger.warn(tag) {
                    "online + no distribution point for cert ${cert.subject.name}: CACHE_MISS (non-revoked)"
                }
                return CertCrlOutcome(isRevoked = false, status = CrlCheckStatus.CACHE_MISS)
            }
            val refresh: RefreshOutcome = refresh(cert, uris)
            return when (refresh) {
                is RefreshOutcome.Refreshed -> {
                    val refreshedStatus: CrlCheckStatus = if (cached == null) {
                        CrlCheckStatus.VERIFIED
                    } else {
                        CrlCheckStatus.REFRESHED
                    }
                    decideFromBytes(cert, refresh.bytes, refreshedStatus)
                }
                is RefreshOutcome.Failed -> {
                    if (cached != null) {
                        Logger.warn(tag) {
                            "online refresh failed for cert ${cert.subject.name}, falling back to STALE_CACHE: ${refresh.reason}"
                        }
                        decideFromCached(cert, cached, CrlCheckStatus.STALE_CACHE)
                    } else {
                        Logger.warn(tag) {
                            "online refresh failed for cert ${cert.subject.name}, no cache: FETCH_FAILED (non-revoked)"
                        }
                        CertCrlOutcome(
                            isRevoked = false,
                            status = CrlCheckStatus.FETCH_FAILED,
                            error = refresh.error
                        )
                    }
                }
            }
        }

        // Branch 3 — offline AND offline_enabled = false (legacy / status-quo).
        // Cannot fetch, no offline coverage opted-in. Behave like the legacy path:
        // FETCH_FAILED + non-revoked.
        Logger.warn(tag) {
            "offline + offline-mode-disabled for cert ${cert.subject.name}: FETCH_FAILED (non-revoked)"
        }
        return CertCrlOutcome(isRevoked = false, status = CrlCheckStatus.FETCH_FAILED)
    }

    private fun decideFromCached(
        cert: X509Cert,
        cached: CachedCrl,
        status: CrlCheckStatus
    ): CertCrlOutcome {
        val store = this.store
            ?: return CertCrlOutcome(isRevoked = false, status = status)
        val payload: ByteArray = try {
            store.decodePayload(cached)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "decodePayload failed for ${cert.subject.name}: ${e.message}" }
            return CertCrlOutcome(isRevoked = false, status = CrlCheckStatus.CACHE_MISS, error = e)
        }
        return decideFromBytes(cert, payload, status)
    }

    private fun decideFromBytes(
        cert: X509Cert,
        crlBytes: ByteArray,
        status: CrlCheckStatus
    ): CertCrlOutcome {
        val isRevoked: Boolean = try {
            isRevokedByCrl(cert, X509Crl(ByteString(crlBytes)))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "CRL parse/check failed for ${cert.subject.name}: ${e.message}" }
            return CertCrlOutcome(isRevoked = false, status = CrlCheckStatus.CACHE_MISS, error = e)
        }
        return CertCrlOutcome(isRevoked = isRevoked, status = status)
    }

    /** Mirrors the legacy `TrustUtils.isCertRevoked` core check: serial match. */
    private fun isRevokedByCrl(cert: X509Cert, crl: X509Crl): Boolean {
        // Touch `.version` to force the lazy ASN.1 decode and surface
        // any malformed payloads as a thrown exception (caught upstream).
        crl.version
        return crl.revokedSerials.any { it == cert.serialNumber }
    }

    private suspend fun refresh(cert: X509Cert, uris: List<String>): RefreshOutcome {
        var lastError: Throwable? = null
        var lastReason: String = "no-uri"
        for (uri in uris) {
            val result: CrlFetchResult = httpFetcher.fetch(uri)
            when (result) {
                is CrlFetchResult.Success -> {
                    val parsedOk: Boolean = try {
                        val crl = X509Crl(ByteString(result.bytes))
                        crl.version // force lazy decode
                        true
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        lastError = e
                        lastReason = "parse-failed"
                        false
                    }
                    if (!parsedOk) continue
                    val meta: CrlMeta = CrlMetaExtractor.extract(result.bytes)
                    if (store != null) {
                        try {
                            store.put(cert, uri, result.bytes, meta)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            // Best-effort persistence — the live check still succeeds.
                            Logger.warn(tag) {
                                "store.put failed during refresh for ${cert.subject.name} (continuing with REFRESHED): ${e.message}"
                            }
                        }
                    }
                    return RefreshOutcome.Refreshed(result.bytes)
                }
                CrlFetchResult.Unreachable -> { lastReason = "unreachable" }
                is CrlFetchResult.HttpError -> { lastReason = "http-${result.statusCode}" }
                CrlFetchResult.Malformed -> { lastReason = "malformed" }
            }
        }
        return RefreshOutcome.Failed(reason = lastReason, error = lastError)
    }

    private fun safeUriLookup(cert: X509Cert): List<String> {
        return try {
            crlUriLookup(cert)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "crlUriLookup failed for ${cert.subject.name}: ${e.message}" }
            emptyList()
        }
    }

    private sealed class RefreshOutcome {
        data class Refreshed(val bytes: ByteArray) : RefreshOutcome() {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other !is Refreshed) return false
                return bytes.contentEquals(other.bytes)
            }
            override fun hashCode(): Int = bytes.contentHashCode()
        }
        data class Failed(val reason: String, val error: Throwable?) : RefreshOutcome()
    }

    companion object {
        @OptIn(ExperimentalTime::class)
        private fun defaultEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
    }
}
