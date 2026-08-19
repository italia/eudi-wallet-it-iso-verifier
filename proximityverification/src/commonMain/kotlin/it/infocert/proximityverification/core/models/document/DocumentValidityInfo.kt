package it.infocert.proximityverification.core.models.document

import kotlin.time.Instant

data class DocumentValidityInfo(
    val validFrom: Instant,
    val validUntil: Instant,
    val signedAt: Instant,
    val expectedUpdate: Instant?
)
