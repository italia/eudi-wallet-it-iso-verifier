package it.ipzs.debug.detail.data

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.simonecascino.destination.DebugGraph
import it.ipzs.architecture.viewmodel.BaseViewModel
import it.ipzs.architecture.viewmodel.NoEvents
import it.ipzs.debug.detail.data.models.LogDetailUI
import it.ipzs.debug_data.DebugRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val debugRepository: DebugRepository
): BaseViewModel<DebugDetailState, NoEvents>(
    DebugDetailState()
) {

    init {
        viewModelScope.launch {

            val logId = savedStateHandle.get<String>(DebugGraph.DebugDetailScreen.KEY_ITEM_ID)?.toLongOrNull() ?: throw IllegalArgumentException("No id present or casting not succeeded")

            debugRepository.getLogById(logId).let {
                changeState {
                    copy(
                        data = LogDetailUI.from(it)
                    )
                }
            }

        }
    }

    fun getItemPrettyPrint(id: Long, result: (String) -> Unit) {
        viewModelScope.launch {
            result(debugRepository.prettyPrint(id))
        }
    }

}