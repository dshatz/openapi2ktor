package com.dshatz.openapi2ktor.generators.clients

import com.dshatz.openapi2ktor.generators.TypeStore
import com.squareup.kotlinpoet.FileSpec

interface IClientGenerator {

    fun generate(typeStore: TypeStore): List<FileSpec>

}