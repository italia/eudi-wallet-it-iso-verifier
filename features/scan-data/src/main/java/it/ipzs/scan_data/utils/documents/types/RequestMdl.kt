package it.ipzs.scan_data.utils.documents.types

import it.ipzs.scan_data.R
import it.ipzs.scan_data.utils.documents.RequestDocument

object RequestMdl : RequestDocument() {

    override val docType = "org.iso.18013.5.1.mDL"

    override val nameSpaces: List<String> = listOf(
        "org.iso.18013.5.1",
        "org.iso.18013.5.1.it"
    )

    override val dataItems = DataItems.entries

    enum class DataItems(
        override val identifier: String
    ) : RequestDataItem {
        ISSUING_AUTHORITY("issuing_authority"),
        FAMILY_NAME("family_name"),
        BIRTH_DATE("birthdate"),
        DRIVING_PRIVILEGES("driving_privileges"),
        ISSUING_COUNTRY("issuing_country"),
        ISSUE_DATE("issue_date"),
        UN_DISTINGUISHING_SIGN("un_distinguishing_sign"),
        GIVEN_NAMES("given_name"),
        EXPIRY_DATE("expiry_date"),
        DOCUMENT_NUMBER("document_number"),
        PORTRAIT("portrait"),
        VERIFICATION_TRUST_FRAMEWORK("verification.trust_framework"),
        VERIFICATION_EVIDENCE("verification.evidence"),
        VERIFICATION_ASSURANCE_LEVEL("verification.assurance_level"),
        BIRTH_PLACE("birth_place"),
        ADDRESS("address"),
        LOCALITY("locality"),
        VEHICLE_CATEGORY_CODE("vehicle_category_code")
    }

    override fun getItemsToRequest(): Map<String, Map<String, Boolean>> {

        val namespaceMap = dataItems.filter {
            it != DataItems.VERIFICATION_TRUST_FRAMEWORK &&
            it != DataItems.VERIFICATION_EVIDENCE &&
            it != DataItems.VERIFICATION_ASSURANCE_LEVEL
        }.associate {
            it.identifier to true
        }

        val namespace2Map = dataItems.filter {
            it == DataItems.VERIFICATION_TRUST_FRAMEWORK ||
                    it == DataItems.VERIFICATION_EVIDENCE ||
                    it == DataItems.VERIFICATION_ASSURANCE_LEVEL
        }.associate {
            it.identifier to true
        }

        return mapOf(
            nameSpaces[0] to namespaceMap,
            nameSpaces[1] to namespace2Map
        )
    }
}