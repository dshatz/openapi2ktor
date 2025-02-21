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
import com.reprezen.jsonoverlay.Overlay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class OpenApiAnalyzer(
    private val typeStore: TypeStore,
    private val packages: Packages,
) {

    private val modelGenerator = KotlinxCodeGenerator()
    private val clientGenerator = KtorClientGenerator()

    fun generate(api: OpenApi3): List<FileSpec> {
        api.gatherComponentModels()
        api.gatherPathModels()
        typeStore.printTypes()
        val fileSpecs = modelGenerator.generate(typeStore)
        return fileSpecs
    }

    private fun OpenApi3.gatherComponentModels() {
        schemas.map { (schemaName, schema) ->
            schema.makeType(schemaName, components = true)
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
            val type = schema.makeType(name, schema.isNullable)
            name to type
        }
    }

    private val simpleTypes = listOf("string", "integer", "number", "boolean")

    private fun Schema.makeType(nameForObject: String, nullable: Boolean = false, components: Boolean = false, isReference: Boolean = false): Type {
        fun Type.register(): Type = apply {
            if (isComponentSchema()) {
                typeStore.registerComponentSchema(this@makeType.getComponentRef()!!, this)
            } else {
                typeStore.registerType(this@makeType, this)
            }
        }

        fun Schema.isArrayItemAReference(): Boolean {
            return Overlay.of(this).toJson()?.get("items")?.get("\$ref") != null
        }

        // component if createDefinitions and isComponentSchema
        // reference if isComponentSchema and !createDefinitions
        // type if

       /* if (!createDefinitions && isComponentSchema()) {
            // reference
            val className = makePackageName(getComponentRef()!!, packages.models)
            return Type.SimpleType(ClassName(className, className.substringAfterLast(".")))
        }*/

        val schemaType = if (isReference) null else type


        return when (schemaType) {
            "array" -> {
                List::class.asTypeName().parameterizedBy(
                    itemsSchema.makeType(nameForObject + "Item", isReference = isArrayItemAReference()).typeName
                ).simpleType(nullable).also {
                    // typealias XXResponse = List<XXResponseItem>
                    Type.Alias(
                        typeName = ClassName(modelPackageName(packages), nameForObject),
                        aliasTarget = it
                    ).register()
                }
            }
            "string" -> String::class.asClassName().simpleType(nullable, default)
            "boolean" -> Boolean::class.asClassName().simpleType(nullable, default)
            "number" -> Double::class.asClassName().simpleType(nullable, default)
            "integer" -> Int::class.asClassName().simpleType(nullable, default)
            "object" -> makeObject(nameForObject).register()
            null -> {
                // *Of or component definition
                if (hasOneOfSchemas()) {
                    // oneOf
                    if (oneOfSchemas.all { it.type in simpleTypes }) {
                        JsonPrimitive::class.asClassName().simpleType(nullable, default?.makeDefaultPrimitive())
                    } else if (oneOfSchemas.any { it.type in simpleTypes } || discriminator.propertyName == null) {
                        // Some of oneOf are primitives so we can't make a truly polymorphic supertype?
                        JsonElement::class.asClassName().simpleType(nullable, default)
                    } else {
                        Type.OneOf(
                            typeName = ClassName(modelPackageName(packages), "I$nameForObject"),
                            childrenMapping = oneOfSchemas.associate {
                                val discriminatorValue = discriminator
                                    .mappings.entries
                                    .find { pair -> pair.value == it.getComponentRef() }?.key

                                it.makeType(nameForObject) to (discriminatorValue ?: it.name)
                            },
                            discriminator = discriminator.propertyName
                        ).register()
                    }
                } else if (hasAllOfSchemas()) {
                    val allProps = allOfSchemas.flatMap { it.makeProps().entries }.associate { it.key to it.value }
                    Type.WithProps.Object(ClassName(modelPackageName(packages), nameForObject), allProps).register()
                } else if (hasAnyOfSchemas()) {
                    JsonElement::class.asClassName().simpleType(nullable, default)
                } else {
                    // It is either a component definition or a reference!
//                    error("Definition or reference ${getComponentRef()}")
                    val className = makePackageName(getComponentRef()!!, packages.models)
                    makeObject(className.substringAfterLast(".")).register()
//                    Type.SimpleType(ClassName(className, className.substringAfterLast(".")))
                }
            }
            else -> {
                error("Unknown type: $schemaType")
            }
        }
    }

    private fun Schema.makeObject(nameForObject: String): Type {
        val props = makeProps()
        return if (props.isNotEmpty()) {
            Type.WithProps.Object(
                ClassName(modelPackageName(packages), nameForObject.capitalize()),
                props = makeProps()
            )
        } else {
            Type.Alias(
                ClassName(modelPackageName(packages), nameForObject.capitalize()),
                JsonObject::class.asClassName().simpleType(nullable ?: false, default)
            )
        }
    }

    private fun Operation.hasMultipleSuccessfulResponseCodes(): Boolean {
        return responses.keys.count { it.isSuccessCode() } > 1
    }

    private fun String.isSuccessCode(): Boolean = toInt() in 200..299

}