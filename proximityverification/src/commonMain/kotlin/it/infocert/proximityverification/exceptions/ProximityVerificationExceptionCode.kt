package it.infocert.proximityverification.exceptions

sealed class ProximityVerificationExceptionCode(open val details: String) {

    abstract val code: String

    override fun toString(): String = "Exception code $code. Details: $details"

    // GENERIC EXCEPTION
    data class PVE00(override val details: String = "GENERIC EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE00"

        companion object {
            fun default() = PVE00()
        }
    }

    // GENERIC INITIALIZATION EXCEPTION
    data class PVE01(override val details: String = "GENERIC INITIALIZATION EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE01"

        companion object {
            fun default() = PVE01()
        }
    }

    // MISSING PERMISSION EXCEPTION
    data class PVE02(override val details: String = "MISSING PERMISSION EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE02"

        companion object {
            fun default() = PVE02()
        }
    }

    // CRYPTO OPERATION EXCEPTION ON INIT
    data class PVE03(override val details: String = "CRYPTO OPERATION EXCEPTION ON INIT") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE03"

        companion object {
            fun default() = PVE03()
        }
    }

    // DEVICE RESPONSE INTEGRITY VERIFICATION FAILED
    data class PVE04(override val details: String = "DEVICE RESPONSE INTEGRITY VERIFICATION FAILED") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE04"

        companion object {
            fun default() = PVE04()
        }
    }

    // INVALID PARAMETERS EXCEPTION
    data class PVE05(override val details: String = "INVALID PARAMETERS EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE05"

        companion object {
            fun default() = PVE05()
        }
    }

    // STORAGE EXCEPTION
    data class PVE06(override val details: String = "STORAGE EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE06"

        companion object {
            fun default() = PVE06()
        }
    }

    // GENERIC VERIFICATION EXCEPTION
    data class PVE07(override val details: String = "GENERIC VERIFICATION EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE07"

        companion object {
            fun default() = PVE07()
        }
    }

    // REST CLIENT RESPONSE GENERIC EXCEPTION
    data class PVE08(override val details: String = "REST CLIENT RESPONSE GENERIC EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE08"

        companion object {
            fun default() = PVE08()
        }
    }

    // BLUETOOTH UNAVAILABLE EXCEPTION
    data class PVE09(override val details: String = "BLUETOOTH UNAVAILABLE EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE09"

        companion object {
            fun default() = PVE09()
        }
    }

    // DEVICE ATTESTATION NOT SUPPORTED ON THIS DEVICE
    data class PVE10(override val details: String = "ATTEST DEVICE NOT SUPPORTED ON THIS DEVICE") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE10"

        companion object {
            fun default() = PVE10()
        }
    }

    // ATTEST DEVICE DATA LEN IS NOT 32 BYTE
    data class PVE11(override val details: String = "ATTEST DEVICE DATA LEN IS NOT 32 BYTES") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE11"

        companion object {
            fun default() = PVE11()
        }
    }

    // DEVICE ATTESTATION GENERIC EXCEPTION
    data class PVE12(override val details: String = "DEVICE ATTESTATION GENERIC EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE12"

        companion object {
            fun default() = PVE12()
        }
    }

    // DEVICE ATTESTATION NOT EXECUTED
    data class PVE13(override val details: String = "DEVICE ATTESTATION NOT EXECUTED") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE13"

        companion object {
            fun default() = PVE13()
        }
    }

    // DEVICE ATTESTATION NOT EXECUTED
    data class PVE14(override val details: String = "DEVICE OFFLINE WITHOUT CERTIFICATE") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE14"

        companion object {
            fun default() = PVE14()
        }
    }

    // GENERIC NETWORK ERROR
    data class PVE15(override val details: String = "GENERIC NETWORK ERROR") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE15"

        companion object {
            fun default() = PVE15()
        }
    }

    // NETWORK REQUEST TIMEOUT ERROR
    data class PVE16(override val details: String = "NETWORK REQUEST TIMEOUT ERROR") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE16"

        companion object {
            fun default() = PVE16()
        }
    }

    // 400 - REST CLIENT RESPONSE: ERROR BAD REQUEST
    data class PVE17(override val details: String = "REST CLIENT RESPONSE: ERROR BAD REQUEST") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE17"
        companion object { fun default() = PVE17() }
    }

    // 403 - REST CLIENT RESPONSE: ERROR FORBIDDEN
    data class PVE18(override val details: String = "REST CLIENT RESPONSE: ERROR FORBIDDEN") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE18"
        companion object { fun default() = PVE18() }
    }

    // 404 - REST CLIENT RESPONSE: ERROR NOT FOUND
    data class PVE19(override val details: String = "REST CLIENT RESPONSE: ERROR NOT FOUND") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE19"
        companion object { fun default() = PVE19() }
    }

    // 422 - REST CLIENT RESPONSE: ERROR UNPROCESSABLE ENTITY
    data class PVE20(override val details: String = "REST CLIENT RESPONSE: ERROR UNPROCESSABLE ENTITY") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE20"
        companion object { fun default() = PVE20() }
    }

    // 500 - REST CLIENT RESPONSE: ERROR INTERNAL SERVER ERROR
    data class PVE21(override val details: String = "REST CLIENT RESPONSE: ERROR INTERNAL SERVER ERROR") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE21"
        companion object { fun default() = PVE21() }
    }

    // 503 - REST CLIENT RESPONSE: ERROR SERVICE UNAVAILABLE
    data class PVE22(override val details: String = "REST CLIENT RESPONSE: ERROR SERVICE UNAVAILABLE") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE22"
        companion object { fun default() = PVE22() }
    }

    // 401 - REST CLIENT RESPONSE: ERROR UNAUTHORIZED
    data class PVE23(override val details: String = "REST CLIENT RESPONSE: ERROR UNAUTHORIZED") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE23"
        companion object { fun default() = PVE23() }
    }

    // NFC NOT AVAILABLE EXCEPTION
    data class PVE24(override val details: String = "NFC NOT AVAILABLE EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE24"
        companion object { fun default() = PVE24() }
    }

    // NFC DISABLED EXCEPTION
    data class PVE25(override val details: String = "NFC DISABLED EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE25"
        companion object { fun default() = PVE25() }
    }

    // NFC ENGAGEMENT FAILED EXCEPTION
    data class PVE26(override val details: String = "NFC ENGAGEMENT FAILED EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE26"
        companion object { fun default() = PVE26() }
    }

    // NFC TAG LOST EXCEPTION
    data class PVE27(override val details: String = "NFC TAG LOST EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE27"
        companion object { fun default() = PVE27() }
    }

    // INVALID ENGAGEMENT DATA EXCEPTION
    data class PVE28(override val details: String = "INVALID ENGAGEMENT DATA EXCEPTION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE28"
        companion object { fun default() = PVE28() }
    }

    data class PVE29(override val details: String = "NFC SCAN CANCELED BY THE USER") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE29"
        companion object { fun default() = PVE29() }
    }

    data class PVE30(override val details: String = "TRANSPORT OPERATION TIMEOUT") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE30"
        companion object { fun default() = PVE30() }
    }

    data class PVE31(override val details: String = "CATALOG INVALID JSON") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE31"
        companion object { fun default() = PVE31() }
    }

    data class PVE32(override val details: String = "CATALOG NO MSO_MDOC CONFIGURATION") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE32"
        companion object { fun default() = PVE32() }
    }

    data class PVE33(override val details: String = "CATALOG ISSUER METADATA FETCH FAILED") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE33"
        companion object { fun default() = PVE33() }
    }

    data class PVE34(override val details: String = "REQUEST BUILDER UNKNOWN CLAIM") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE34"
        companion object { fun default() = PVE34() }
    }

    data class PVE35(override val details: String = "REQUEST BUILDER UNKNOWN DOCTYPE") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE35"
        companion object { fun default() = PVE35() }
    }

    /** mDL signalled session termination before any usable DeviceResponse was received. */
    data class PVE36(override val details: String = "MDL SESSION ENDED WITHOUT DEVICE RESPONSE") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE36"
        companion object { fun default() = PVE36() }
    }

    // STATUS LIST PREFETCH: NO AGGREGATION ENDPOINT AVAILABLE FOR REQUESTED DOCTYPES
    data class PVE37(override val details: String = "NO STATUS LIST AGGREGATION ENDPOINT AVAILABLE FOR REQUESTED DOCTYPES") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE37"
        companion object { fun default() = PVE37() }
    }

    // STATUS LIST PREFETCH: UNKNOWN DOCTYPE
    data class PVE38(override val details: String = "STATUS LIST PREFETCH UNKNOWN DOCTYPE") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE38"
        companion object { fun default() = PVE38() }
    }

    // STATUS LIST CACHE I/O ERROR
    data class PVE39(override val details: String = "STATUS LIST CACHE I/O ERROR") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE39"
        companion object { fun default() = PVE39() }
    }

    // OFFLINE ENABLE REQUIRES NETWORK CONNECTIVITY
    data class PVE40(override val details: String = "OFFLINE ENABLE REQUIRES NETWORK CONNECTIVITY") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE40"
        companion object { fun default() = PVE40() }
    }

    // NO COMPATIBLE CONNECTION METHOD BETWEEN WALLET AND READER
    data class PVE41(override val details: String = "NO COMPATIBLE CONNECTION METHOD BETWEEN WALLET AND READER") :
        ProximityVerificationExceptionCode(details) {
        override val code: String = "PVE41"
        companion object { fun default() = PVE41() }
    }
}
