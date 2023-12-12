package it.ipzs.scan.validation.data

import it.ipzs.architecture.viewmodel.BaseEvent

sealed interface ValidationEvent: BaseEvent{

    data object OnValidationEnded: ValidationEvent
    data object OnError: ValidationEvent

}