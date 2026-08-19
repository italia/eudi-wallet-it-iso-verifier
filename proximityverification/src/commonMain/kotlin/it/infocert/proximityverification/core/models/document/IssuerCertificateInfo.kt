package it.infocert.proximityverification.core.models.document

data class IssuerCertificateInfo(
    val trustPointName: String?,
    val certChain: List<ByteArray>
)
