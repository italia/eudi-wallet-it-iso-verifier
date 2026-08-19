package it.infocert.proximityverification.core.models.document

data class DocumentKeyValuePair(
    val key: String,
    val textValue: String,
    val mimeType: DocumentValueMimeType = DocumentValueMimeType.TEXT,
    val imageData: ByteArray? = null
)
