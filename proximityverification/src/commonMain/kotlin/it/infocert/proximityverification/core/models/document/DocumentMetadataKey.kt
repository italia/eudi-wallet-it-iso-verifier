package it.infocert.proximityverification.core.models.document

enum class DocumentMetadataKey(val label: String) {
    Type("type"),
    DocType("doc_type"),
    ValidFrom("valid_from"),
    ValidUntil("valid_until"),
    SignedAt("signed_at"),
    ExpectedUpdate("expected_update"),
    Namespace("namespace");

    companion object {
        fun fromLabel(label: String): DocumentMetadataKey? =
            entries.find { it.label == label }
    }
}