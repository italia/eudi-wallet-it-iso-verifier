package it.ipzs.debug.list.data

import it.ipzs.architecture.viewmodel.BaseState
import it.ipzs.debug.list.data.models.LogUI

data class DebugState(
    val data: List<LogUI>
): BaseState