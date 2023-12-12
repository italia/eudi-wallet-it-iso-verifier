package it.ipzs.scan.detail.data

import dagger.hilt.android.lifecycle.HiltViewModel
import it.ipzs.architecture.viewmodel.BaseViewModel
import it.ipzs.architecture.viewmodel.NoEvents
import it.ipzs.scan.detail.data.model.DetailDriveLicenseUI
import it.ipzs.scan_data.ScanRepository
import javax.inject.Inject

@HiltViewModel
class DetailViewModel @Inject constructor(
    scanRepository: ScanRepository
): BaseViewModel<DetailState, NoEvents>(
    initialState = DetailState(
        driveLicense = DetailDriveLicenseUI.from(
            scanRepository.lastDriveLicenseScanned ?: throw IllegalStateException("Drive license can't be null")
        )
    )
) {



}