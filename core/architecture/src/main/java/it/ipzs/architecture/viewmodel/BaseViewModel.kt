package it.ipzs.architecture.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

abstract class BaseViewModel<S: BaseState, E: BaseEvent>(
    initialState: S
): ViewModel() {

    private val _events = Channel<E>()
    @PublishedApi
    internal val events = _events.receiveAsFlow()

    private val state = MutableStateFlow(initialState)
    @PublishedApi
    internal val currentState: StateFlow<S> = state.asStateFlow()

    protected fun dispatchEvent(event: E){
        viewModelScope.launch {
            _events.send(event)
        }
    }

    protected fun changeState(changer: S.() -> S){
        state.value = state.value.changer()
    }

}