package it.infocert.proximityverification.crl

/**
 * Test fixture for [CrlPrefetcher]. Returns the scripted [CrlFetchResult]
 * for known URIs and [CrlFetchResult.Unreachable] for everything else.
 */
internal class FakeCrlHttpFetcher(
    private val scripted: Map<String, CrlFetchResult>
) : CrlHttpFetcher {
    val calls: MutableList<String> = mutableListOf()

    override suspend fun fetch(uri: String): CrlFetchResult {
        calls.add(uri)
        return scripted[uri] ?: CrlFetchResult.Unreachable
    }
}
