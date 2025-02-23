package com.dshatz.openapi2ktor.generators.models

import com.dshatz.openapi2ktor.generators.TypeStore
import com.squareup.kotlinpoet.FileSpec

interface IModelGenerator {

    fun generate(): List<FileSpec>
}