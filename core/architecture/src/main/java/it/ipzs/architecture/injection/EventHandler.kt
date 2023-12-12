package it.ipzs.architecture.injection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import it.ipzs.architecture.viewmodel.BaseEvent
import kotlinx.coroutines.flow.Flow

@Composable
fun<E: BaseEvent> EventHandler(
    onEventDispatched: ((E) -> Unit)?,
    eventFlow: Flow<E>
){

    val dispatchLambda by rememberUpdatedState(newValue = onEventDispatched)

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(key1 = true){
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
            eventFlow.collect{
                dispatchLambda?.invoke(it)
            }
        }
    }

}