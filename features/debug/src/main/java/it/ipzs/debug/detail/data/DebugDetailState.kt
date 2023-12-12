package it.ipzs.debug.detail.data

import it.ipzs.architecture.viewmodel.BaseState
import it.ipzs.debug.detail.data.models.LogDetailUI

data class DebugDetailState(
    val data: LogDetailUI = LogDetailUI.empty()
): BaseState