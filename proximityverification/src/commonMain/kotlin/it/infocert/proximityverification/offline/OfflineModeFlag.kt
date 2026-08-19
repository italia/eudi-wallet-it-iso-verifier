package it.infocert.proximityverification.offline

/**
 * Shared constant for the offline-mode flag persisted in [LocalStorage].
 *
 * The flag is governed by `StatusListStore.setEnabled(...)` only. Other
 * components (e.g. `CrlStore`) reference this constant to read the same
 * stored value WITHOUT exposing their own setter — keeping a single
 * source of truth and avoiding state divergence.
 *
 * The string value is stable across releases: changing it would orphan
 * already-persisted flags on user devices.
 */
internal object OfflineModeFlag {
    const val STORAGE_KEY: String = "PROX_VER_OFFLINE_ENABLED"
}
