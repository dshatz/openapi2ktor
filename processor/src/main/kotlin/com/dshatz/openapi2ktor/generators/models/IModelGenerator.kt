package com.dshatz.openapi2ktor.generators.models

import com.dshatz.openapi2ktor.generators.TypeStore
import com.squareup.kotlinpoet.FileSpec

interface IModelGenerator {

    fun generate(typeStore: TypeStore): List<FileSpec>

    fun String.safePropName(): String {
        val parts = split("_")
        return parts.first() + parts.drop(1).joinToString("") { it.replaceFirstChar { char -> char.uppercase() } }
    }
}