package it.ipzs.scan.detail.data

import it.ipzs.architecture.viewmodel.BaseState
import it.ipzs.scan.detail.data.model.DetailDriveLicenseUI

data class DetailState(
    val driveLicense: DetailDriveLicenseUI
): BaseState