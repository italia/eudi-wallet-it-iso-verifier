package it.infocert.proximityverification.core.crypto

import it.infocert.proximityverification.core.handlers.NativeUtils
import it.infocert.proximityverification.core.models.CheckMetadata
import it.infocert.proximityverification.core.models.CheckStatus
import it.infocert.proximityverification.core.models.RevocationState
import it.infocert.proximityverification.logging.Logger
import it.infocert.proximityverification.status.model.CachedFormat
import it.infocert.proximityverification.status.model.CachedStatusList
import it.infocert.proximityverification.status.model.StatusListPayload
import it.infocert.proximityverification.status.model.Usability
import kotlinx.coroutines.CancellationException
import org.multipaz.crypto.EcPublicKey
import org.multipaz.revocation.IdentifierList
import org.multipaz.revocation.RevocationStatus
import org.multipaz.revocation.StatusList
import org.multipaz.util.fromBase64

internal object MsoRevocationChecker {
    private const val TAG = "MsoRevocationChecker"

    data class MsoRevocationOutcome(
        val status: CheckStatus,
        val metadata: CheckMetadata.StatusListRevocation? = null,
    )

    /**
     * Validates the MSO revocation status against (in order):
     * 1. live fetch when online (existing behaviour),
     * 2. on online success, write-through to the cache,
     * 3. on online failure, cache fallback (Fresh / StaleByTtl),
     * 4. when offline, cache lookup only.
     *
     * `cacheLookup`, `cachePut` and `cacheUsability` default to no-op so consumers
     * and tests that do not wire the [it.infocert.proximityverification.status.StatusListStore]
     * keep the legacy semantics. `cacheUsability` defaults to `Fresh` (treat absent
     * stale signal as fresh).
     *
     * Both `RevocationStatus.StatusList` payload formats supported by
     * Token Status List draft-13 §6 are dispatched (CWT and JWT).
     * `RevocationStatus.IdentifierList` keeps the online-only behaviour (cache
     * out of scope for IdentifierList per design §3).
     */
    suspend fun check(
        revocationStatus: RevocationStatus?,
        isNetworkAvailable: () -> Boolean = { NativeUtils.isNetworkAvailable() },
        fetchPayload: suspend (String) -> ByteArray = { TrustUtils.fetchRevocationPayload(it) },
        isOfflineEnabled: suspend () -> Boolean = { false },
        cacheLookup: suspend (String) -> CachedStatusList? = { null },
        cachePut: suspend (String, StatusListPayload) -> Unit = { _, _ -> },
        cacheUsability: (CachedStatusList, Long) -> Usability = { _, _ -> Usability.Fresh },
        resolveSigningKey: suspend (StatusListPayload, CachedFormat) -> EcPublicKey? = { _, _ -> null }
    ): MsoRevocationOutcome {
        if (revocationStatus == null) {
            Logger.debug(TAG) { "No revocation status in MSO, returning NOT_DETERMINED" }
            return MsoRevocationOutcome(CheckStatus.NOT_DETERMINED, null)
        }
        return try {
            when (revocationStatus) {
                is RevocationStatus.StatusList -> checkStatusList(
                    status = revocationStatus,
                    isNetworkAvailable = isNetworkAvailable,
                    fetchPayload = fetchPayload,
                    isOfflineEnabled = isOfflineEnabled,
                    cacheLookup = cacheLookup,
                    cachePut = cachePut,
                    cacheUsability = cacheUsability,
                    resolveSigningKey = resolveSigningKey
                )
                is RevocationStatus.IdentifierList -> checkIdentifierList(
                    status = revocationStatus,
                    isNetworkAvailable = isNetworkAvailable,
                    fetchPayload = fetchPayload
                )
                else -> {
                    Logger.warn(TAG) { "Unknown revocation status format, returning NOT_DETERMINED" }
                    MsoRevocationOutcome(CheckStatus.NOT_DETERMINED, null)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.warn(TAG) { "MSO revocation check failed: ${e.message}" }
            MsoRevocationOutcome(CheckStatus.NOT_DETERMINED, null)
        }
    }

    private suspend fun checkStatusList(
        status: RevocationStatus.StatusList,
        isNetworkAvailable: () -> Boolean,
        fetchPayload: suspend (String) -> ByteArray,
        isOfflineEnabled: suspend () -> Boolean,
        cacheLookup: suspend (String) -> CachedStatusList?,
        cachePut: suspend (String, StatusListPayload) -> Unit,
        cacheUsability: (CachedStatusList, Long) -> Usability,
        resolveSigningKey: suspend (StatusListPayload, CachedFormat) -> EcPublicKey?
    ): MsoRevocationOutcome {
        val offlineOn: Boolean = isOfflineEnabled()
        if (isNetworkAvailable()) {
            val live: ByteArray? = try {
                fetchPayload(status.uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Logger.warn(TAG) { "Live status list fetch failed: ${e.message}" }
                null
            }
            if (live != null) {
                val payload = StatusListPayload(bytes = live, contentType = null)
                if (offlineOn) {
                    try {
                        cachePut(status.uri, payload)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Throwable) {
                        /* best-effort: cache write must never affect verification outcome */
                    }
                }
                return decideStatusList(
                    bytes = live,
                    format = detectFormatHeuristic(live),
                    status = status,
                    mode = ValidationMode.ONLINE_LIVE,
                    usability = Usability.Fresh,
                    resolveSigningKey = resolveSigningKey
                )
            }
            if (!offlineOn) {
                Logger.info(TAG) {
                    "Offline disabled and live fetch failed for ${status.uri} → NOT_DETERMINED " +
                        "(mode=${ValidationMode.OFFLINE_DISABLED_NOT_DETERMINED.name})"
                }
                return MsoRevocationOutcome(CheckStatus.NOT_DETERMINED, null)
            }
            return cachePathOrNotDetermined(
                status = status,
                cacheLookup = cacheLookup,
                cacheUsability = cacheUsability,
                modeOnFresh = ValidationMode.ONLINE_FALLBACK_CACHE_FRESH,
                modeOnStale = ValidationMode.ONLINE_FALLBACK_CACHE_STALE,
                resolveSigningKey = resolveSigningKey
            )
        }
        if (!offlineOn) {
            Logger.info(TAG) {
                "Offline disabled and device offline for ${status.uri} → NOT_DETERMINED " +
                    "(mode=${ValidationMode.OFFLINE_DISABLED_NOT_DETERMINED.name})"
            }
            return MsoRevocationOutcome(CheckStatus.NOT_DETERMINED, null)
        }
        return cachePathOrNotDetermined(
            status = status,
            cacheLookup = cacheLookup,
            cacheUsability = cacheUsability,
            modeOnFresh = ValidationMode.OFFLINE_CACHE_FRESH,
            modeOnStale = ValidationMode.OFFLINE_CACHE_STALE,
            resolveSigningKey = resolveSigningKey
        )
    }

    private suspend fun cachePathOrNotDetermined(
        status: RevocationStatus.StatusList,
        cacheLookup: suspend (String) -> CachedStatusList?,
        cacheUsability: (CachedStatusList, Long) -> Usability,
        modeOnFresh: ValidationMode,
        modeOnStale: ValidationMode,
        resolveSigningKey: suspend (StatusListPayload, CachedFormat) -> EcPublicKey?
    ): MsoRevocationOutcome {
        val cached: CachedStatusList? = try {
            cacheLookup(status.uri)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(TAG) { "Cache lookup failed: ${e.message}" }
            null
        }
        if (cached == null) {
            Logger.info(TAG) { "Cache miss for ${status.uri} → NOT_DETERMINED" }
            return MsoRevocationOutcome(CheckStatus.NOT_DETERMINED, null)
        }
        val payloadBytes: ByteArray = decodeBase64(cached.payloadBase64)
        val now: Long = currentEpochSeconds()
        val usability: Usability = cacheUsability(cached, now)
        val isStale: Boolean = usability == Usability.StaleByTtl
        val mode: ValidationMode = if (isStale) modeOnStale else modeOnFresh
        if (mode.isStale) {
            Logger.warn(TAG) { "Cached status list past iat+ttl for ${status.uri} (mode=${mode.name})" }
        } else {
            Logger.info(TAG) { "Using cached status list for ${status.uri} (mode=${mode.name})" }
        }
        return decideStatusList(
            bytes = payloadBytes,
            format = cached.format,
            status = status,
            mode = mode,
            usability = usability,
            resolveSigningKey = resolveSigningKey
        )
    }

    private suspend fun decideStatusList(
        bytes: ByteArray,
        format: CachedFormat,
        status: RevocationStatus.StatusList,
        mode: ValidationMode,
        usability: Usability,
        resolveSigningKey: suspend (StatusListPayload, CachedFormat) -> EcPublicKey?,
    ): MsoRevocationOutcome {
        // multipaz's StatusList.fromJwt/fromCwt require a non-null signing key, otherwise
        // validateJwt's `require(publicKey != null || caValidated)` throws (basicCertificate
        // chain validation never establishes trust on its own). The key is taken from the
        // optional certificate embedded in the MSO status reference and, failing that, resolved
        // by anchoring the token's own x5c / x5chain to a known trust point (Token Status List
        // draft §11.3: Issuer == Status Issuer, same signing key conveyed via the token header).
        val payload = StatusListPayload(bytes = bytes, contentType = null)
        val signingKey: EcPublicKey? = status.certificate?.ecPublicKey ?: resolveSigningKey(payload, format)
        if (signingKey == null) {
            Logger.warn(TAG) {
                "Status list signing key could not be resolved/anchored for ${status.uri} " +
                    "(no embedded certificate and token chain not trusted) → NOT_DETERMINED"
            }
            return MsoRevocationOutcome(CheckStatus.NOT_DETERMINED, null)
        }
        val statusList = when (format) {
            CachedFormat.CWT -> StatusList.fromCwt(cwt = bytes, publicKey = signingKey)
            CachedFormat.JWT -> StatusList.fromJwt(jwt = bytes.decodeToString(), publicKey = signingKey)
        }
        val bits = statusList[status.idx]
        val state = when (bits) {
            0 -> RevocationState.VALID
            1 -> RevocationState.REVOKED
            2 -> RevocationState.SUSPENDED
            else -> RevocationState.UNKNOWN
        }
        val checkStatus = if (state == RevocationState.VALID) CheckStatus.PASSED else CheckStatus.FAILED
        Logger.debug(TAG) { "Status list decision for ${status.uri} idx=${status.idx}: bits=$bits state=$state (mode=${mode.name})" }
        return MsoRevocationOutcome(
            status = checkStatus,
            metadata = CheckMetadata.StatusListRevocation(mode = mode, usability = usability, state = state),
        )
    }

    private suspend fun checkIdentifierList(
        status: RevocationStatus.IdentifierList,
        isNetworkAvailable: () -> Boolean,
        fetchPayload: suspend (String) -> ByteArray
    ): MsoRevocationOutcome {
        if (!isNetworkAvailable()) {
            Logger.warn(TAG) { "Device is offline, cannot check IdentifierList" }
            return MsoRevocationOutcome(CheckStatus.NOT_DETERMINED, null)
        }
        val payload = fetchPayload(status.uri)
        val identifierList = IdentifierList.fromCwt(
            cwt = payload,
            publicKey = status.certificate?.ecPublicKey
        )
        val checkStatus = if (identifierList.contains(status.id)) CheckStatus.FAILED else CheckStatus.PASSED
        return MsoRevocationOutcome(checkStatus, null)
    }

    private fun detectFormatHeuristic(bytes: ByteArray): CachedFormat {
        val asString: String = try { bytes.decodeToString() } catch (_: Throwable) { "" }
        return if (asString.count { it == '.' } == 2 &&
            asString.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' || it == '=' }
        ) CachedFormat.JWT else CachedFormat.CWT
    }

    private fun decodeBase64(value: String): ByteArray = value.fromBase64()

    @OptIn(kotlin.time.ExperimentalTime::class)
    private fun currentEpochSeconds(): Long = kotlin.time.Clock.System.now().epochSeconds
}
