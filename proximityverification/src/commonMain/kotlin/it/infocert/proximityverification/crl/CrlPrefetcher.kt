package it.infocert.proximityverification.crl

import it.infocert.proximityverification.core.crypto.TrustUtils
import it.infocert.proximityverification.crl.model.CrlFailureReason
import it.infocert.proximityverification.crl.model.CrlMeta
import it.infocert.proximityverification.crl.model.CrlPrefetchSummary
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.registry.model.ResolvedRegistryData
import kotlinx.coroutines.CancellationException
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.X509Cert
import org.multipaz.crypto.X509Crl
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Orchestrates the offline-mode CRL prefetch pipeline.
 *
 * Inputs:
 * - the relying party's PEM-encoded `trustAnchors` list (from
 *   `RelyingPartyProviderInfo.trustAnchors`);
 * - the issuer chain certificates from
 *   `ResolvedRegistryData.issuerMetadata[].jwks.keys[0].x5c[0..n]`
 *   (full chain including the leaf — see [IssuerCertChainExtractor]).
 *
 * Pipeline:
 * 1. Collect candidate certs from both sources.
 * 2. Deduplicate by [computeCertKey] (`issuerDN | serialHex`), first-wins.
 * 3. For each unique cert, look up CRL Distribution Points via
 *    [TrustUtils.extractCrlDistributionPoint] and try each URI in turn
 *    (first-success-wins). Successful payloads are persisted via
 *    [CrlStore.put].
 * 4. Produce a granular [CrlPrefetchSummary] with per-cert outcomes.
 *
 * The loop is **sequential** (per the unit-01 / status-list concurrency
 * note). Refresh-on-demand and consumer-facing snapshot APIs are
 * deliberately out of scope here (units 03 and 04 respectively).
 *
 * `CancellationException` is propagated verbatim throughout.
 */
internal class CrlPrefetcher(
    private val store: CrlStore,
    private val httpFetcher: CrlHttpFetcher = DefaultCrlHttpFetcher(),
    private val crlUriLookup: (X509Cert) -> List<String> = TrustUtils::extractCrlDistributionPoint,
    private val clock: () -> Long = { defaultEpochMillis() }
) {
    private val tag: String = "CrlPrefetcher"

    suspend fun prefetch(
        trustAnchors: List<String>,
        registryData: ResolvedRegistryData
    ): CrlPrefetchSummary {
        val candidates: MutableList<X509Cert> = mutableListOf()
        var parseFailureCount: Int = 0

        for (pem: String in trustAnchors) {
            val cert: X509Cert? = try {
                TrustUtils.loadPemCert(pem)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.warn(tag) {
                    "trust anchor PEM parse failed → skip: ${e::class.simpleName}: ${e.message}"
                }
                null
            }
            if (cert != null) candidates.add(cert)
        }

        for (issuerMetadata in registryData.issuerMetadata) {
            val result = IssuerCertChainExtractor.extract(issuerMetadata)
            candidates.addAll(result.certificates)
            parseFailureCount += result.parseFailureCount
        }

        val uniqueCerts: LinkedHashMap<String, X509Cert> = LinkedHashMap()
        for (cert in candidates) {
            val key: String = computeCertKey(cert)
            if (!uniqueCerts.containsKey(key)) {
                uniqueCerts[key] = cert
            }
        }

        val crlsFetched: MutableList<String> = mutableListOf()
        val crlsFailed: LinkedHashMap<String, CrlFailureReason> = LinkedHashMap()

        for ((certKey: String, cert: X509Cert) in uniqueCerts) {
            val uris: List<String> = try {
                crlUriLookup(cert)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.warn(tag) {
                    "crlUriLookup failed for $certKey → treating as none: " +
                        "${e::class.simpleName}: ${e.message}"
                }
                emptyList()
            }
            if (uris.isEmpty()) {
                Logger.warn(tag) { "no CRL distribution point for $certKey → skip" }
                continue
            }

            var lastReason: CrlFailureReason? = null
            var fetchedUri: String? = null
            for (uri: String in uris) {
                when (val outcome: CrlFetchResult = httpFetcher.fetch(uri)) {
                    is CrlFetchResult.Success -> {
                        val parsedOk: Boolean = try {
                            // X509Crl is a data class that stores bytes —
                            // its decode is lazy. Force the ASN.1 walk so
                            // we positively reject malformed payloads
                            // before persisting them.
                            val crl = X509Crl(ByteString(outcome.bytes))
                            // Touching `version` triggers `parsed` (lazy)
                            // which calls ASN1.decode and throws on
                            // malformed bytes.
                            crl.version
                            true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Logger.warn(tag) {
                                "CRL parse failed for $uri ($certKey) → CRL_MALFORMED_PAYLOAD: " +
                                    "${e::class.simpleName}: ${e.message}"
                            }
                            false
                        }
                        if (!parsedOk) {
                            lastReason = CrlFailureReason.CRL_MALFORMED_PAYLOAD
                            continue
                        }
                        val meta: CrlMeta = CrlMetaExtractor.extract(outcome.bytes)
                        try {
                            store.put(
                                cert = cert,
                                crlUri = uri,
                                payloadBytes = outcome.bytes,
                                meta = meta
                            )
                            fetchedUri = uri
                            break
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            Logger.warn(tag) {
                                "store.put failed for $uri ($certKey) → STORAGE_ERROR: " +
                                    "${e::class.simpleName}: ${e.message}"
                            }
                            // Storage errors are not transient per-uri — do
                            // not fall back to the next URI.
                            lastReason = CrlFailureReason.STORAGE_ERROR
                            break
                        }
                    }
                    CrlFetchResult.Unreachable -> {
                        lastReason = CrlFailureReason.CRL_UNREACHABLE
                    }
                    is CrlFetchResult.HttpError -> {
                        lastReason = CrlFailureReason.CRL_HTTP_ERROR
                    }
                    CrlFetchResult.Malformed -> {
                        lastReason = CrlFailureReason.CRL_MALFORMED_PAYLOAD
                    }
                }
            }
            if (fetchedUri != null) {
                crlsFetched.add(fetchedUri)
            } else {
                // lastReason cannot be null here: uris was non-empty so the
                // for-loop body ran at least once and set it (or broke out
                // with a fetchedUri).
                crlsFailed[certKey] = lastReason ?: CrlFailureReason.CRL_UNREACHABLE
            }
        }

        val summary = CrlPrefetchSummary(
            certsConsidered = uniqueCerts.size,
            crlsFetched = crlsFetched.toList(),
            crlsFailed = crlsFailed.toMap(),
            parseFailureCount = parseFailureCount,
            persistedAtEpochMillis = clock()
        )
        Logger.info(tag) {
            "prefetch done: certsConsidered=${summary.certsConsidered}, " +
                "crlsFetched=${summary.crlsFetched.size}, " +
                "crlsFailed=${summary.crlsFailed.size}, " +
                "parseFailures=${summary.parseFailureCount}"
        }
        return summary
    }

    companion object {
        @OptIn(ExperimentalTime::class)
        private fun defaultEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
    }
}
