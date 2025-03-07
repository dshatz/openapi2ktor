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


inline fun <K, V, reified R> Sequence<Map.Entry<K, V>>.filterValuesIsInstance(): Sequence<Map.Entry<K, R>> {
    return filter { it.value is R } as Sequence<Map.Entry<K, R>>
}

fun <T, K> Sequence<T>.findDuplicates(by: (T) -> K): MutableMap<K, HashSet<T>> {
    val seen = mutableSetOf<K>()
    val duplicates = mutableMapOf<K, HashSet<T>>()

    for (item in this) {
        val key = by(item)
        if (!seen.add(key)) { // If `add` returns false, it's a duplicate
            val listForKey = duplicates.getOrElse(key) { hashSetOf() }
            listForKey.add(item)
            duplicates[key] = listForKey
        }
    }

    return duplicates
}
