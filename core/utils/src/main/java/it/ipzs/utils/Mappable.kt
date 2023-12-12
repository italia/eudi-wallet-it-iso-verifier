package it.ipzs.utils

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.reflect.full.companionObjectInstance

interface Mappable <in O, out R>{
    fun from(origin: O): R
}

inline fun <O: Any, reified R> Collection<O>.mapToModel(): List<R> = mapNotNull {
    (R::class.companionObjectInstance as Mappable<O, R>).from(it)
}

inline fun <O: Any, reified R> Flow<List<O>>.mapToModel() = map {
    it.mapToModel<O, R>()
}