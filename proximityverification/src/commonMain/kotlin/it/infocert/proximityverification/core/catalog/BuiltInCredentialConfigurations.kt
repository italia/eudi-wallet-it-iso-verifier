package it.infocert.proximityverification.core.catalog

/**
 * Embedded JSON for built-in credential configurations.
 * Loaded into the [DocumentTypeCatalog] during initialization.
 *
 * Structure follows OID4VCI 1.0 Appendix A.2.2:
 * - `doctype` at the credential config level (sibling of `credential_metadata`)
 * - `display` and `claims` inside `credential_metadata`
 *
 * Italian mDL attributes are aligned to the official IT mDL CDDL specification.
 * Claims use OID4VCI Appendix B.2 array format with `path` arrays.
 */
internal object BuiltInCredentialConfigurations {

    /**
     * Italian Driving License (mDL) — aligned to IT mDL CDDL.
     *
     * Namespace `org.iso.18013.5.1`: 12 mandatory data elements.
     * Namespace `org.iso.18013.5.1.IT`: 3 mandatory + 1 optional (`verification`).
     */
    val ITALIAN_DRIVING_LICENSE_JSON: String = """
{
  "doctype": "org.iso.18013.5.1.mDL",
  "credential_metadata": {
    "display": [
      { "name": "Driving License", "locale": "en" },
      { "name": "Patente di Guida", "locale": "it" }
    ],
    "claims": [
      { "path": ["org.iso.18013.5.1", "given_name"], "mandatory": true, "display": [{ "name": "Given Names", "locale": "en" }, { "name": "Nomi", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1", "family_name"], "mandatory": true, "display": [{ "name": "Family Name", "locale": "en" }, { "name": "Cognome", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1", "birth_date"], "mandatory": true, "value_type": "date", "display": [{ "name": "Date of Birth", "locale": "en" }, { "name": "Data di Nascita", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1", "birth_place"], "mandatory": true, "display": [{ "name": "Place of Birth", "locale": "en" }, { "name": "Luogo di Nascita", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1", "issue_date"], "mandatory": true, "value_type": "date", "display": [{ "name": "Date of Issue", "locale": "en" }, { "name": "Data di Rilascio", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1", "issuing_country"], "mandatory": true, "display": [{ "name": "Issuing Country", "locale": "en" }, { "name": "Paese di Rilascio", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1", "issuing_authority"], "mandatory": true, "display": [{ "name": "Issuing Authority", "locale": "en" }, { "name": "Autorità di Rilascio", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1", "expiry_date"], "mandatory": true, "value_type": "date", "display": [{ "name": "Date of Expiry", "locale": "en" }, { "name": "Data di Scadenza", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1", "document_number"], "mandatory": true, "display": [{ "name": "License Number", "locale": "en" }, { "name": "Numero Patente", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1", "portrait"], "mandatory": true, "value_type": "picture", "display": [{ "name": "Photo of Holder", "locale": "en" }, { "name": "Foto del Titolare", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1", "driving_privileges"], "mandatory": true, "value_type": "complex", "display": [{ "name": "Driving Privileges", "locale": "en" }, { "name": "Categorie di Guida", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1", "un_distinguishing_sign"], "mandatory": true, "display": [{ "name": "UN Distinguishing Sign", "locale": "en" }, { "name": "Sigla Distintiva UN", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1.IT", "sub"], "mandatory": true, "display": [{ "name": "Subject Identifier", "locale": "en" }, { "name": "Identificativo Soggetto", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1.IT", "verification"], "mandatory": false, "value_type": "complex", "display": [{ "name": "Verification Info", "locale": "en" }, { "name": "Informazioni di Verifica", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1.IT", "document_iss_country"], "mandatory": true, "display": [{ "name": "Document Issuing Country", "locale": "en" }, { "name": "Paese Emittente Documento", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1.IT", "document_iss_authority"], "mandatory": true, "display": [{ "name": "Document Issuing Authority", "locale": "en" }, { "name": "Autorità Emittente Documento", "locale": "it" }] }
    ]
  }
}
""".trimIndent()

    val IT_WALLET_1_0_WALLET_ATTESTATION_JSON: String = """
{
  "doctype": "org.iso.18013.5.1.IT.WalletAttestation",
  "credential_metadata": {
    "display": [
      { "name": "Wallet Attestation", "locale": "en" },
      { "name": "Attestazione Wallet", "locale": "it" }
    ],
    "claims": [
      { "path": ["org.iso.18013.5.1.IT", "sub"], "mandatory": true, "display": [{ "name": "Wallet Instance Identifier", "locale": "en" }, { "name": "Identificativo Istanza Wallet", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1.IT", "aal"], "mandatory": true, "display": [{ "name": "Authentication Assurance Level", "locale": "en" }, { "name": "Livello di Garanzia Autenticazione", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1.IT", "wallet_link"], "mandatory": true, "display": [{ "name": "Wallet Link", "locale": "en" }, { "name": "Link Wallet", "locale": "it" }] },
      { "path": ["org.iso.18013.5.1.IT", "wallet_name"], "mandatory": true, "display": [{ "name": "Wallet Provider Name", "locale": "en" }, { "name": "Nome Fornitore Wallet", "locale": "it" }] }
    ]
  }
}
""".trimIndent()
}
