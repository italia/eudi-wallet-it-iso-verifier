package it.infocert.proximityverification.status.model

/**
 * Reasons for per-list / per-issuer failures surfaced in
 * [StatusListPrefetchSummary].
 *
 * The transport-related reasons split "the issuer endpoint could not be
 * contacted at all" (`*_UNREACHABLE`) from "the endpoint was reached but
 * answered with a non-success HTTP status" (`*_HTTP_ERROR`). The exact status
 * code and any OAuth-style `error` / `error_description` envelope returned by
 * the issuer are emitted in the SDK log at WARN level (see
 * `it.infocert.proximityverification.network.logHttpErrorResponse`).
 */
enum class FailureReason {
    /** Aggregation endpoint did not respond (DNS failure, connection refused, timeout, transport-level I/O). */
    AGGREGATION_UNREACHABLE,
    /** Aggregation endpoint responded with a non-success HTTP status (4xx / 5xx). */
    AGGREGATION_HTTP_ERROR,
    /** Aggregation response body was not parseable as a `status_lists` JSON document. */
    AGGREGATION_MALFORMED,
    /** Status list URI did not respond (DNS failure, connection refused, timeout, transport-level I/O). */
    FETCH_UNREACHABLE,
    /** Status list URI responded with a non-success HTTP status (4xx / 5xx). */
    FETCH_HTTP_ERROR,
    UNSUPPORTED_FORMAT,
    MALFORMED_PAYLOAD,
    EXPIRED_AT_DOWNLOAD,
    /**
     * The status list signing certificate chain (`x5c` for JWT, `x5chain` for
     * CWT) could not be tied back to a trust anchor known to the SDK, or the
     * status list token did not advertise a chain at all. The body itself may
     * be well-formed; only its signature trust is missing.
     */
    UNTRUSTED_SIGNATURE,
    STORAGE_ERROR
}
