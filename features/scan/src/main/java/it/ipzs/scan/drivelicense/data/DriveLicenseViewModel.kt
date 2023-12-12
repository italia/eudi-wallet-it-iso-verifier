package it.ipzs.scan.drivelicense.data

import dagger.hilt.android.lifecycle.HiltViewModel
import it.ipzs.architecture.viewmodel.BaseViewModel
import it.ipzs.architecture.viewmodel.NoEvents
import it.ipzs.scan.drivelicense.data.model.DriveLicenseUI
import it.ipzs.scan.drivelicense.data.model.VerificationSpecs
import it.ipzs.scan_data.ScanRepository
import javax.inject.Inject

@HiltViewModel
class DriveLicenseViewModel @Inject constructor(
    scanRepository: ScanRepository
): BaseViewModel<DriveLicenseState, NoEvents>(
    DriveLicenseState(
        driveLicense = DriveLicenseUI.from(
            scanRepository.lastDriveLicenseScanned
        ),
        verificationSpecs = VerificationSpecs.from(scanRepository.lastDriveLicenseScanned?.status ?: throw IllegalStateException("Missing status"))
    )
)