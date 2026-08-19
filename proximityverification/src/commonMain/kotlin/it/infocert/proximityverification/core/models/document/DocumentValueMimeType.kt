package it.infocert.proximityverification.core.models.document

enum class DocumentValueMimeType(val value: String) {
    TEXT("text"),
    IMAGE("image");

    companion object {
        fun fromValue(value: String): DocumentValueMimeType =
            entries.firstOrNull { it.value == value } ?: TEXT
    }
}