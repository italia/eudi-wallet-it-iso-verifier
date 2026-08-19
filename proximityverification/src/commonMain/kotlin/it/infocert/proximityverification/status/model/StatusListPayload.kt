package it.infocert.proximityverification.status.model

/**
 * In-memory wrapper for a freshly-fetched Status List payload, preserving the
 * `Content-Type` header so the consumer can dispatch CWT vs JWT.
 *
 * Not serialized — see [CachedStatusList] for the persisted form.
 */
data class StatusListPayload(
    val bytes: ByteArray,
    val contentType: String?
)
