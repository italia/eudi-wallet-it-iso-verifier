package it.infocert.proximityverification.status

import io.ktor.http.Url
import io.ktor.http.URLProtocol
import io.ktor.http.decodeURLPart

/**
 * Outcome of [StatusListUriFilter.retain]: partitioning of input URIs into
 * the ones to download (`kept`) and the ones identified as irrelevant for
 * the requested credential types (`droppedAsIrrelevant`). Non-match-pattern
 * URIs always land in `kept` (lenient fallback — see RATIONALE
 * [2026-05-08] *URI-Based credential_type Filter Best-Effort*).
 */
internal data class FilterOutcome(
    val kept: List<String>,
    val droppedAsIrrelevant: List<String>
)

/**
 * Best-effort path-based filter for Token Status List URIs.
 *
 * Recognises the IPZS-style convention
 * `{scheme}://{host}[:port]/statuslists/{credential_type}/{*}` — `statuslists`
 * MUST be the first path component. URIs that don't match the pattern are
 * always kept (lenient).
 */
internal object StatusListUriFilter {

    /**
     * Returns the URL-decoded `credential_type` segment when [uri] matches the
     * pattern, `null` otherwise.
     */
    fun extractCredentialType(uri: String): String? {
        if (!uri.startsWith("http://", ignoreCase = true) &&
            !uri.startsWith("https://", ignoreCase = true)) return null
        val parsed: Url = try {
            Url(uri)
        } catch (_: Throwable) {
            return null
        }
        if (parsed.protocol != URLProtocol.HTTP && parsed.protocol != URLProtocol.HTTPS) return null
        if (parsed.host.isBlank()) return null
        val segments: List<String> = parsed.encodedPath
            .removePrefix("/")
            .split("/")
        if (segments.size < 2) return null
        if (segments[0] != "statuslists") return null
        val credentialType: String = segments[1]
        if (credentialType.isBlank()) return null
        return credentialType.decodeURLPart()
    }

    /**
     * Partitions [uris] into [FilterOutcome.kept] and [FilterOutcome.droppedAsIrrelevant]:
     *  - `extractCredentialType(uri) == null` → kept (non-match-pattern)
     *  - `extractCredentialType(uri) ∈ expected` → kept
     *  - `extractCredentialType(uri) ∉ expected` → droppedAsIrrelevant
     *
     * Returns `kept = uris` when [expected] is empty (no-op safe).
     */
    fun retain(uris: List<String>, expected: Set<String>): FilterOutcome {
        if (expected.isEmpty()) {
            return FilterOutcome(kept = uris, droppedAsIrrelevant = emptyList())
        }
        val kept = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        for (uri in uris) {
            val credentialType: String? = extractCredentialType(uri)
            when {
                credentialType == null -> kept += uri
                credentialType in expected -> kept += uri
                else -> dropped += uri
            }
        }
        return FilterOutcome(kept = kept.toList(), droppedAsIrrelevant = dropped.toList())
    }
}
