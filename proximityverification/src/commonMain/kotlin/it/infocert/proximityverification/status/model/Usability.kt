package it.infocert.proximityverification.status.model

/**
 * Usability tri-state of a cached Status List relative to a wall-clock time.
 *
 * - [Fresh] — within the spec-defined `ttl` window (or no `ttl`/`iat` published).
 * - [StaleByTtl] — past `iat + ttl` but the underlying CWT/JWT is still parseable
 *   (multipaz `validateXxx` did not reject it on `exp`). Usable as fallback.
 * - [Expired] — multipaz parsing rejects the entry (e.g. past `exp`).
 */
enum class Usability { Fresh, StaleByTtl, Expired }
