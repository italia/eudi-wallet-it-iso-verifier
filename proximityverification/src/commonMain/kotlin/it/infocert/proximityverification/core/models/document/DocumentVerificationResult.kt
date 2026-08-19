package it.infocert.proximityverification.core.models.document

import it.infocert.proximityverification.core.models.VerificationCheck

data class DocumentVerificationResult(
    val docType: String,
    val checks: List<VerificationCheck>,
    val validityInfo: DocumentValidityInfo,
    val issuerCertInfo: IssuerCertificateInfo?,
    val data: List<DocumentKeyValuePair>
)
