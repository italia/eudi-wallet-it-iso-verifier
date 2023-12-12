package it.ipzs.utils

fun atLeastOneNull(
    vararg values: Any?
) = values.any {
    it == null
}