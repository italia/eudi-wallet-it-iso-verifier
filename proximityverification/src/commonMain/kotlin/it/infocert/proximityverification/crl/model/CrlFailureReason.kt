package it.infocert.proximityverification.crl.model

/**
 * Granular failure classification for the CRL prefetch / lookup pipeline.
 * Defined in unit-01 alongside the persistence layer because the enum
 * is part of the model contract; consumed by unit-02 (prefetcher) and
 * surfaced in summaries.
 */
enum class CrlFailureReason {
    /** CRL endpoint not reachable (DNS, timeout, transport I/O). */
    CRL_UNREACHABLE,

    /** CRL endpoint responded with a non-2xx HTTP status. */
    CRL_HTTP_ERROR,

    /** Downloaded bytes could not be parsed as a `X509Crl`. */
    CRL_MALFORMED_PAYLOAD,

    /** A certificate in the `x5c` chain failed Base64 / DER parsing. */
    X5C_PARSE_ERROR,

    /** `LocalStorage` write failed. */
    STORAGE_ERROR
}
