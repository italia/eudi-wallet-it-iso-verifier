package it.ipzs.architecture.injection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.dialog
import it.ipzs.architecture.viewmodel.BaseEvent
import it.ipzs.architecture.viewmodel.BaseState
import it.ipzs.architecture.viewmodel.BaseViewModel

inline fun <S: BaseState, E: BaseEvent, reified V: BaseViewModel<S, E>> NavGraphBuilder.dialogVM(
    route: String,
    noinline onEventDispatched: ((E) -> Unit)? = null,
    crossinline content: @Composable V.(S) -> Unit
){

    dialog(
        route = route
    ){
        val viewModel = hiltViewModel<V>(it)

        EventHandler(onEventDispatched, viewModel.events)

        val state by viewModel.currentState.collectAsStateWithLifecycle()
        viewModel.content(state)
    }

}