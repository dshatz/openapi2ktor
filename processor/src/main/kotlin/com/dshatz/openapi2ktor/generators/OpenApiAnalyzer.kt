package com.dshatz.openapi2ktor.generators

import com.dshatz.openapi2ktor.generators.clients.KtorClientGenerator
import com.dshatz.openapi2ktor.generators.models.KotlinxCodeGenerator
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import com.reprezen.kaizen.oasparser.model3.Operation
import com.reprezen.kaizen.oasparser.model3.Schema
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.dshatz.openapi2ktor.generators.Type.Companion.simpleType
import com.dshatz.openapi2ktor.utils.*

class OpenApiAnalyzer(
    private val typeStore: TypeStore,
    private val packages: Packages,
) {

    private val modelGenerator = KotlinxCodeGenerator()
    private val clientGenerator = KtorClientGenerator()

    fun generate(api: OpenApi3): List<FileSpec> {
        api.gatherRefModels()
        api.gatherPathModels()
        typeStore.printTypes()
        val fileSpecs = modelGenerator.generate(typeStore)
        return fileSpecs
    }

    private fun OpenApi3.gatherRefModels() {
        schemas.map { (schemaName, schema) ->
            schema.makeType(schemaName)
        }
    }

    private fun OpenApi3.gatherPathModels() {
        gatherPathResponseModels()
        gatherPathRequestBodyModels()
    }

    private fun OpenApi3.gatherPathResponseModels() {
        paths.map { (pathString, path) ->
            path.operations.map { (verb, operation) ->
                operation.responses.map { (statusCode, response) ->
                    val schema = response.contentMediaTypes.values.first().schema
                    val modelName = makeResponseModelName(
                        verb = verb,
                        path = pathString,
                        response = statusCode,
                        includeStatus = !statusCode.isSuccessCode() || operation.hasMultipleSuccessfulResponseCodes()
                    )
                    schema.makeType(modelName)
                }
            }
        }
    }

    private fun OpenApi3.gatherPathRequestBodyModels() {
        paths.map { (pathString, path) ->
            path.operations.filter { it.value.requestBody.contentMediaTypes.isNotEmpty()  }.map { (verb, operation) ->
                val schema = operation.requestBody.contentMediaTypes.values.first().schema
                val modelName = makeRequestBodyModelName(verb, pathString)
                schema.makeType(modelName)
            }
        }
    }

    private fun Schema.makeProps(): Map<String, Type> {
        return properties.entries.associate { (name, schema) ->
            val type = schema.makeType("", schema.isNullable)
            name to type
        }
    }

    private fun Schema.makeType(nameForObject: String, nullable: Boolean = false): Type {
        fun Type.register(): Type = apply {
            if (isReference()) {
                typeStore.registerReference(this@makeType.getReferenceId()!!, this)
            } else {
                typeStore.registerType(this@makeType, this)
            }
        }


        /*fun Type.registerUnique(): Type = apply {
            if (!typeStore.getTypes().values.contains(this)) typeStore.registerType(this@makeType, this)
        }*/

        return when (type) {
            "array" -> {
                List::class.asTypeName().parameterizedBy(itemsSchema.makeType(nameForObject).typeName).simpleType(nullable)
            }
            "string" -> String::class.asClassName().simpleType(nullable, default)
            "boolean" -> Boolean::class.asClassName().simpleType(nullable, default)
            "number" -> Double::class.asClassName().simpleType(nullable, default)
            "integer" -> Int::class.asClassName().simpleType(nullable, default)
            "object" -> {
                Type.WithProps.Object(
                    ClassName(packages.models, nameForObject),
                    props = makeProps()
                ).register()
            }
            null -> {
                if (hasOneOfSchemas()) {
                    // One of
                    Type.OneOf(
                        typeName = ClassName(packages.models, "I$nameForObject"),
                        childrenMapping = oneOfSchemas.associate {
                            val discriminatorValue = discriminator
                                .mappings.entries
                                .find { pair -> pair.value == it.getReferenceId() }?.key

                            it.makeType(nameForObject) to (discriminatorValue ?: it.name)
                        },
                        discriminator = discriminator.propertyName
                    ).register()
                } else if (hasAllOfSchemas()) {
                    val allProps = allOfSchemas.flatMap { it.makeProps().entries }.associate { it.key to it.value }
                    Type.WithProps.Object(ClassName(packages.models, nameForObject), allProps).register()
                } else {
                    // It is a reference!
                    Type.WithProps.Reference(ClassName(packages.models, name), makeProps()).register()
                }
            }
            else -> {
                error("Unknown type: $type")
            }
        }
    }

    private fun Operation.hasMultipleSuccessfulResponseCodes(): Boolean {
        return responses.keys.count { it.isSuccessCode() } > 1
    }

    private fun String.isSuccessCode(): Boolean = toInt() in 200..299

}