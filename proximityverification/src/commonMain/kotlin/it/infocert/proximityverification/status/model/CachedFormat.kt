package it.infocert.proximityverification.status.model

import kotlinx.serialization.Serializable

/**
 * Wire format of a Token Status List per draft-ietf-oauth-status-list-13 §6.
 */
@Serializable
enum class CachedFormat { CWT, JWT }
