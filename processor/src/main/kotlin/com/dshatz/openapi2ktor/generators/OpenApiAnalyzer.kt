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
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.ovl3.SchemaImpl
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.xml.transform.Source
import javax.xml.validation.SchemaFactory

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
            schema.makeType(schemaName, schema.jsonReference, components = true, isReference = false)
        }
    }

    private fun OpenApi3.gatherPathModels() {
        gatherPathResponseModels()
        gatherPathRequestBodyModels()
    }

    private fun OpenApi3.gatherPathResponseModels() {
        paths.forEach { (pathString, path) ->
            path.operations.forEach { (verb, operation) ->
                operation.responses.forEach { (statusCode, response) ->
                    val schema = response.contentMediaTypes.values.firstOrNull()?.schema
                    val modelName = makeResponseModelName(
                        verb = verb,
                        path = pathString,
                        response = statusCode,
                        includeStatus = !statusCode.isSuccessCode() || operation.hasMultipleSuccessfulResponseCodes()
                    )
                    val jsonReference = schema?.let { Overlay.of(it).jsonReference } ?: Overlay.of(response).jsonReference
                    schema.makeType(modelName, jsonReference, isReference = false)
                }
            }
        }
    }

    private fun OpenApi3.gatherPathRequestBodyModels() {
        paths.forEach { (pathString, path) ->
            path.operations.filter { it.value.requestBody.contentMediaTypes.isNotEmpty()  }.forEach { (verb, operation) ->
                val schema = operation.requestBody.contentMediaTypes.values.first().schema
                val modelName = makeRequestBodyModelName(verb, pathString)
                schema.makeType(modelName, schema.jsonReference, isReference = false)
            }
        }
    }


    private fun Schema.makeProps(): Map<String, Type> {
        return properties.entries.associate { (name, schema) ->
            val type = schema.makeType(name, schema.jsonReference, schema.isNullable, isReference = isPropAReference(name))
            name to type
        }
    }

    private val simpleTypes = listOf("string", "integer", "number", "boolean")

    private fun Schema?.makeType(nameForObject: String, jsonReference: String, nullable: Boolean = false, components: Boolean = false, isReference: Boolean): Type {
        println("Entering ${"component".takeIf { components } ?: ""} ${jsonReference.substringAfter("#")}")
        fun Type.register(): Type = apply {
            if (this@makeType != null) {
                if (isComponentSchema()) {
                    if (components) {
                        typeStore.registerComponentSchema(this@makeType.getComponentRef()!!, this)
                    }
                } else {
                    typeStore.registerType(jsonReference, this)
                }
            }
        }

        val packageName = makePackageName(jsonReference, packages.models)

        val schemaType = if (isReference) null else this?.type

        if (this != null) {
            return when (schemaType) {
                "array" -> {
                    List::class.asTypeName().parameterizedBy(
                        this.itemsSchema.makeType(nameForObject + "Item", itemsSchema.jsonReference, isReference = isArrayItemAReference()).typeName
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
                    if (isReference) {
                        // Reference to something
                        val packageName = makePackageName(getComponentRef()!!, packages.models)
                        println("Reference found! ${getComponentRef()} $packageName")
                        Type.SimpleType(
                            ClassName(packageName, packageName.substringAfterLast(".").capitalize())
                        )
                    } else if (hasOneOfSchemas()) {
                        // oneOf
                        if (oneOfSchemas.all { it.type in simpleTypes }) {
                            JsonPrimitive::class.asClassName().simpleType(nullable, default?.makeDefaultPrimitive())
                        } else if (oneOfSchemas.any { it.type in simpleTypes } || discriminator.propertyName == null) {
                            // Some of oneOf are primitives so we can't make a truly polymorphic supertype?
                            JsonElement::class.asClassName().simpleType(nullable, default)
                        } else {
                            Type.OneOf(
                                typeName = ClassName(packageName, if (components) nameForObject else "I$nameForObject"),
                                childrenMapping = oneOfSchemas.mapIndexed { index, it ->
                                    val isReference = Overlay.of(oneOfSchemas).isReference(index)
                                    val discriminatorValue = discriminator
                                        .mappings.entries
                                        .find { pair -> pair.value == it.getComponentRef() }?.key

                                    it.makeType(nameForObject, it.jsonReference, isReference = isReference) to (discriminatorValue ?: it.name)
                                }.toMap(),
                                discriminator = discriminator.propertyName
                            ).register()
                        }
                    } else if (hasAllOfSchemas()) {
                        val allProps = allOfSchemas.flatMap { it.makeProps().entries }.associate { it.key to it.value }
                        Type.WithProps.Object(ClassName(packageName, nameForObject), allProps).register()
                    } else if (hasAnyOfSchemas()) {
                        JsonElement::class.asClassName().simpleType(nullable, default)
                    } else {
                        // It is either a component definition or a reference!
                        if (isReference) {
                            error("Reference in the wrong place!")
                        } else if (components) {
                            // Component definition
                            println("Component def found! ${getComponentRef()}")
                            val className = makePackageName(getComponentRef()!!, packages.models)
                            makeObject(className.substringAfterLast(".")).register()
                        } else {
                            // Something without schema at all!
                            Type.Alias(
                                ClassName(packageName, nameForObject.capitalize()),
                                JsonObject::class.asClassName().simpleType(nullable, default)
                            ).register()
                        }
                    }
                }
                else -> {
                    error("Unknown type: $schemaType")
                }
            }
        } else {
            // No schema. For example, this:
            /**
             * responses:
             *   "403":
             *     description: Empty response
             */
            return Type.Alias(
                ClassName(packageName, nameForObject.capitalize()),
                JsonObject::class.asClassName().simpleType(nullable, null)
            ).apply {
                typeStore.registerType(jsonReference, this)
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