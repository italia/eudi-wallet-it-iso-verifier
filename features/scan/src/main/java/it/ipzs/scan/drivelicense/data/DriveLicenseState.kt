package it.ipzs.scan.drivelicense.data

import it.ipzs.architecture.viewmodel.BaseState
import it.ipzs.scan.drivelicense.data.model.DriveLicenseUI
import it.ipzs.scan.drivelicense.data.model.VerificationSpecs

data class DriveLicenseState(
    val driveLicense: DriveLicenseUI,
    val verificationSpecs: VerificationSpecs
): BaseState