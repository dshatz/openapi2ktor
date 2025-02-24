package com.dshatz.openapi2ktor.utils

fun <T, K, V> Iterable<T>.associateByNotNull(
    keySelector: (T) -> K?,
    valueTransform: (T) -> V?,
): Map<K, V> = buildMap {
    for (item in this@associateByNotNull) {
        val key = keySelector(item) ?: continue
        val value = valueTransform(item) ?: continue
        this[key] = value
    }
}


fun <T, R> Iterable<T>.associateWithNotNull(block: (T) -> R?): Map<T, R> {
    return associateByNotNull(keySelector = {it}, valueTransform = block)
}
