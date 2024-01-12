package it.ipzs.scan_data.utils

import androidx.annotation.Keep

@Keep
enum class SupportedCurves {
    SECP256R1,
    SECP384R1,
    SECP521R1,
    BRAINPOOLP256R1,
    BRAINPOOLP384R1,
    BRAINPOOLP512R1,
    ED25519,
    ED448
}