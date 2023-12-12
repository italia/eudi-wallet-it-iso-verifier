package it.ipzs.scan.picture.data

import dagger.hilt.android.lifecycle.HiltViewModel
import it.ipzs.architecture.viewmodel.BaseViewModel
import it.ipzs.architecture.viewmodel.NoEvents
import it.ipzs.scan_data.ScanRepository
import javax.inject.Inject

@HiltViewModel
class PictureViewModel @Inject constructor(
    private val scanRepository: ScanRepository
): BaseViewModel<PictureState, NoEvents>(
    PictureState(
        fullName = "${scanRepository.lastDriveLicenseScanned?.name ?: ""} ${scanRepository.lastDriveLicenseScanned?.surname ?: ""}",
        picture = scanRepository.lastDriveLicenseScanned?.photo ?: throw IllegalStateException("Image must be available")
    )
)