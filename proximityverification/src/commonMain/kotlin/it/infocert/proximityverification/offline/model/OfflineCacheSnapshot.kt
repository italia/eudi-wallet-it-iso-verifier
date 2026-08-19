package it.infocert.proximityverification.offline.model

import kotlinx.serialization.Serializable

/**
 * Consumer-facing point-in-time projection of the SDK's persistent offline
 * caches.
 *
 * Aggregates the Token Status List cache (`StatusListStore`) and the X.509
 * CRL cache (`CrlStore`) into a single snapshot that consumers (Android/iOS
 * apps, Flutter plugin, internal diagnostics screens) can render without
 * touching internal SDK types.
 *
 * The snapshot reflects what was persisted at the moment of the call. There
 * is no atomicity guarantee against concurrent writers (e.g. a parallel
 * `enableStatusListsForOfflineUse(...)`); the underlying `LocalStorage`
 * implementation serializes individual reads and writes in practice.
 *
 * @property offlineEnabled mirrors the persisted offline flag
 *   (`PROX_VER_OFFLINE_ENABLED`). `true` after a successful
 *   `enableStatusListsForOfflineUse(...)`, `false` on a fresh install or after
 *   `disableStatusListsForOfflineUse()` / `clearLibraryLocalStorage()`.
 * @property generatedAtEpochMillis wall-clock timestamp at which the snapshot
 *   was assembled. Always strictly positive.
 * @property statusLists projection of every persisted Status List entry.
 * @property crls projection of every persisted CRL entry.
 */
@Serializable
data class OfflineCacheSnapshot(
    val offlineEnabled: Boolean,
    val generatedAtEpochMillis: Long,
    val statusLists: List<CachedStatusListInfo>,
    val crls: List<CachedCrlInfo>
)
