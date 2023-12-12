package it.ipzs.debug.list.data

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.ipzs.architecture.viewmodel.BaseViewModel
import it.ipzs.architecture.viewmodel.NoEvents
import it.ipzs.debug.list.data.models.LogUI
import it.ipzs.debug_data.DebugRepository
import it.ipzs.debug_data.model.LogRepo
import it.ipzs.utils.mapToModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DebugViewModel @Inject constructor(
    private val debugRepository: DebugRepository
): BaseViewModel<DebugState, NoEvents>(
    DebugState(listOf())
) {

    init {
        viewModelScope.launch {
            debugRepository.getLogs().mapToModel<LogRepo, LogUI>().collect{
                changeState {
                    copy(data = it)
                }
            }
        }
    }

    fun deleteItem(id: Long){
        viewModelScope.launch {
            debugRepository.deleteItem(id)
        }
    }

    fun getItemPrettyPrint(id: Long, result: (String) -> Unit) {
        viewModelScope.launch {
            result(debugRepository.prettyPrint(id))
        }
    }

}