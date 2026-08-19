package it.infocert.proximityverification.crl

import it.infocert.proximityverification.core.crypto.LocalStorage
import it.infocert.proximityverification.core.crypto.provideLocalStorage
import it.infocert.proximityverification.crl.model.CachedCrl
import it.infocert.proximityverification.crl.model.CrlMeta
import it.infocert.proximityverification.logging.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.multipaz.crypto.X509Cert
import org.multipaz.util.fromBase64
import org.multipaz.util.toBase64
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Persistent cache of X.509 CRLs, stored as a single JSON envelope
 * under [STORAGE_KEY] in [LocalStorage]. Mirrors the design of
 * `StatusListStore`: single-tenant, single-envelope, soft-cache by
 * `nextUpdate`.
 *
 * The offline-mode toggle lives exclusively on `StatusListStore` —
 * `CrlStore` does NOT expose its own setter / getter. It only owns the
 * cache entries and is cleared together with the rest of the SDK state
 * by `ProximityVerification.clearLibraryLocalStorage` (wired up by
 * unit-04).
 */
internal class CrlStore(
    private val storage: LocalStorage = provideLocalStorage(),
    private val clock: () -> Long = { defaultEpochMillis() }
) {
    private val tag: String = "CrlStore"
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable
    private data class Envelope(val entries: Map<String, CachedCrl> = emptyMap())

    /**
     * Persists a CRL entry keyed by [computeCertKey] of [cert],
     * overwriting any existing one for the same certificate.
     *
     * Non-cancellation errors propagate to the caller (caller decides
     * whether to surface as `STORAGE_ERROR` in a summary, swallow as
     * best-effort, etc.). `CancellationException` is re-thrown verbatim.
     */
    suspend fun put(
        cert: X509Cert,
        crlUri: String,
        payloadBytes: ByteArray,
        meta: CrlMeta
    ) {
        val certKey: String = computeCertKey(cert)
        val serialHex: String = cert.serialNumber.value.stripLeadingSignByte().toHexLower()
        val envelope: Envelope = readEnvelope()
        val entry = CachedCrl(
            certKey = certKey,
            subjectDn = cert.subject.name,
            issuerDn = cert.issuer.name,
            serialHex = serialHex,
            crlUri = crlUri,
            payloadBase64 = payloadBytes.toBase64(),
            thisUpdateEpochSeconds = meta.thisUpdateEpochSeconds,
            nextUpdateEpochSeconds = meta.nextUpdateEpochSeconds,
            fetchedAtEpochMillis = clock()
        )
        val updated = Envelope(entries = envelope.entries + (certKey to entry))
        writeEnvelope(updated)
    }

    /** Returns the cached entry for [cert]'s `certKey`, or `null` when missing or unreadable. */
    suspend fun get(cert: X509Cert): CachedCrl? {
        return try {
            val key: String = computeCertKey(cert)
            readEnvelope().entries[key]
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Logger.warn(tag) { "get failed: ${e.message}" }
            null
        }
    }

    /** Returns a snapshot of all cached entries. Empty list on read failure. */
    suspend fun listAll(): List<CachedCrl> {
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
    fun decodePayload(entry: CachedCrl): ByteArray = entry.payloadBase64.fromBase64()

    /**
     * `true` iff the entry has a `nextUpdate` and the supplied wall-clock
     * is past it. Entries without `nextUpdate` are never considered stale
     * by this method (caller decides separately whether to refresh).
     */
    fun isStale(entry: CachedCrl, nowEpochSeconds: Long): Boolean {
        val nextUpdate: Long = entry.nextUpdateEpochSeconds ?: return false
        return nowEpochSeconds > nextUpdate
    }

    /**
     * Clears all CRL cache entries. Idempotent on absent state, best-effort
     * on storage failures (errors are logged at WARN, never propagated).
     * `CancellationException` is re-thrown.
     */
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
        const val STORAGE_KEY: String = "PROX_VER_CRL_CACHE"

        @OptIn(ExperimentalTime::class)
        private fun defaultEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
    }
}

/**
 * Deterministic cache key for a certificate: `${issuerDN}|${serialHex}`.
 *
 * - `issuerDN` is the multipaz `X500Name.name` (RFC 2253 / RFC 4514).
 * - `serialHex` is the lowercase hex of `ASN1Integer.value` with the
 *   leading sign-padding `0x00` byte stripped (matches DER's exact
 *   sign-padding rule), keeping the key compact and stable across
 *   parse → encode round-trips.
 */
internal fun computeCertKey(cert: X509Cert): String {
    val issuerDn: String = cert.issuer.name
    val serialHex: String = cert.serialNumber.value.stripLeadingSignByte().toHexLower()
    return "$issuerDn|$serialHex"
}

private fun ByteArray.stripLeadingSignByte(): ByteArray =
    if (size > 1 && this[0].toInt() == 0x00) copyOfRange(1, size) else this

private fun ByteArray.toHexLower(): String =
    joinToString(separator = "") { byte ->
        (byte.toInt() and 0xff).toString(16).padStart(2, '0')
    }
