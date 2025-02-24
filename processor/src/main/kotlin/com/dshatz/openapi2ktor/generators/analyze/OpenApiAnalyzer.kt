package com.dshatz.openapi2ktor.generators.analyze

import com.dshatz.openapi2ktor.generators.Type
import com.dshatz.openapi2ktor.generators.clients.KtorClientGenerator
import com.dshatz.openapi2ktor.generators.models.KotlinxCodeGenerator
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import com.reprezen.kaizen.oasparser.model3.Operation
import com.reprezen.kaizen.oasparser.model3.Schema
import com.squareup.kotlinpoet.*
import com.dshatz.openapi2ktor.generators.Type.Companion.simpleType
import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.generators.clients.IClientGenerator
import com.dshatz.openapi2ktor.utils.*
import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.model3.Response
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.coroutineContext
import kotlin.time.measureTime

open class OpenApiAnalyzer(
    private val typeStore: TypeStore,
    private val packages: Packages,
) {

    private val modelGenerator = KotlinxCodeGenerator(typeStore, packages)
    private val clientGenerator = KtorClientGenerator(typeStore, packages)

    fun generate(api: OpenApi3): Pair<List<FileSpec>, List<IClientGenerator.Template>> = runBlocking {
        val modelsTime = measureTime {
            withContext(Dispatchers.Default) {
                api.gatherComponentModels().joinAll()
            }
        }

        val pathTime = measureTime {
            withContext(Dispatchers.Default) {
                api.gatherPathModels().joinAll()
            }
        }

        println("Models: $modelsTime")
        println("Paths: $pathTime")
        typeStore.printTypes()
        val clientTemplates = clientGenerator.generateTemplates()
        val fileSpecs = modelGenerator.generate() + clientGenerator.generate(api)
        return@runBlocking fileSpecs to clientTemplates
    }

    private suspend fun OpenApi3.gatherComponentModels() = withContext(coroutineContext) {
        schemas.map { (schemaName, schema) ->
            launch { processComponent(schemaName, schema) }
        }
    }

    internal suspend fun processComponent(schemaName: String, schema: Schema) {
        schema.makeType(
            schemaName.safePropName().capitalize(),
            schema.jsonReference,
            components = true,
            isReference = false,
            wrapPrimitives = false
        )
    }

    private suspend fun OpenApi3.gatherPathModels(): List<Job> {
        return gatherPathResponseModels() + gatherPathRequestBodyModels()
    }

    internal suspend fun OpenApi3.gatherPathResponseModels() = withContext(coroutineContext) {
        return@withContext paths.flatMap { (pathString, path) ->
            path.operations.flatMap { (verb, operation) ->
                val multipleSuccess = operation.responses.count { it.key.toInt().isSuccessCode() } > 1
                val multipleErrors = operation.responses.count { !it.key.toInt().isSuccessCode() } > 1

                val packageName = makePackageName(Overlay.of(operation.responses.values.first()).jsonReference, packages.models)
                val pathId = TypeStore.PathId(pathString, verb)
                val iResponseClass = ClassName(packageName, "I${pathId.pathId}Response")
                val iErrorClass = ClassName(packageName, "I${pathId.pathId}Error")
                typeStore.registerResponseInterface(
                    path = pathId,
                    successInterface = iResponseClass.takeIf { multipleSuccess },
                    errorInterface = iErrorClass.takeIf { multipleErrors }
                )

                operation.responses.map { (statusCode, response) ->
                    val wrapPrimitives = if (statusCode.toInt().isSuccessCode()) multipleSuccess else multipleErrors
                    processPathResponse(operation, response, pathString, statusCode.toInt(), verb, wrapPrimitives)
                }
            }
        }
    }

    open internal suspend fun processPathResponse(
        operation: Operation,
        response: Response,
        pathString: String,
        statusCode: Int = 200,
        verb: String = "get",
        wrapPrimitives: Boolean = false
    ) = withContext(coroutineContext) {
        val mediaType = response.contentMediaTypes.values.firstOrNull()
        val schema = mediaType?.schema
        val modelName = makeResponseModelName(
            verb = verb,
            path = pathString,
            statusCode = statusCode,
            includeStatus = !statusCode.isSuccessCode() || operation.hasMultipleSuccessfulResponseCodes()
        )
        val jsonReference = Overlay.of(response).jsonReference
        launch {
            val isReference = Overlay.of(response.contentMediaTypes.values.firstOrNull()).isReference("schema")

            schema.makeType(modelName, jsonReference, isReference = isReference, wrapPrimitives = wrapPrimitives).also {
                typeStore.registerResponseMapping(TypeStore.PathId(pathString, verb), statusCode, Overlay.of(response).jsonReference, it)
            }
        }
    }

    private suspend fun OpenApi3.gatherPathRequestBodyModels() = withContext(coroutineContext) {
        return@withContext paths.flatMap { (pathString, path) ->
            path.operations.filter { it.value.requestBody.contentMediaTypes.isNotEmpty()  }.map { (verb, operation) ->
                val schema = operation.requestBody.contentMediaTypes.values.first().schema
                val modelName = makeRequestBodyModelName(verb, pathString)
                launch {
                    schema.makeType(modelName, schema.jsonReference, isReference = false, wrapPrimitives = false)
                }
            }
        }
    }


    private suspend fun Schema.makeProps(components: Boolean): List<Deferred<Pair<String, Type>>> = withContext(coroutineContext) {
        properties.entries.map { (name, schema) ->
            async {
                val type = schema.makeType(
                    nameForObject = name,
                    jsonReference = schema.jsonReference,
                    isReference = isPropAReference(name),
                    components = components,
                    wrapPrimitives = false
                )
                name to type
            }
        }
    }

    private val simpleTypes = listOf("string", "integer", "number", "boolean")

    private suspend fun List<Deferred<Pair<String, Type>>>.awaitProps(): Map<String, Type> {
        return awaitAll().toMap()
    }

    private suspend fun Schema?.makeType(
        nameForObject: String,
        jsonReference: String,
        components: Boolean = false,
        isReference: Boolean,
        wrapPrimitives: Boolean
    ): Type {
        println("Entering ${"component".takeIf { components } ?: ""} ${jsonReference.stripFilePathFromRef()}")

        val packageName = makePackageName(jsonReference, packages.models)

        fun Type.wrapPrimitive(): Type.WithTypeName.PrimitiveWrapper {
            if (this is Type.WithTypeName) error("Trying to wrap type: $this")
            return Type.WithTypeName.PrimitiveWrapper(
                ClassName(packageName, nameForObject),
                wrappedType = this
            )
        }

        fun Type.register(): Type = run {
            val typeToRegister = if (wrapPrimitives && this !is Type.WithTypeName) wrapPrimitive() else this

            if (this@makeType?.isPartOfComponentSchema() == true && components && this !is Type.Reference) {
                typeStore.registerComponentSchema(jsonReference, typeToRegister)
            }
            else {
                // TODO: Check why this check is needed. It shouldn't be!
                if (jsonReference.stripFilePathFromRef() != (typeToRegister as? Type.Reference)?.jsonReference) {
                    typeStore.registerType(jsonReference, typeToRegister)
                }
            }
            /*if (wrapPrimitives) {
                // Is reference inside a response.
                this.wrapPrimitive().register()
            }*/
            typeToRegister
        }

        val schemaType = if (isReference) null else this?.type

        if (this != null) {
            return when (schemaType) {
                "array" -> {
                    Type.List(
                        if (isArrayItemAReference()) {
                            Type.Reference(itemsSchema.getComponentRef()!!)
                        } else {
                            itemsSchema.makeType(
                                nameForObject + "Item",
                                itemsSchema.jsonReference,
                                isReference = isArrayItemAReference(),
                                components = components,
                                wrapPrimitives = false
                            )
                        }
                    ).let {
                        // typealias XXResponse = List<XXResponseItem>
                        if (components && isComponentSchemaRoot()) {
                            Type.WithTypeName.Alias(
                                typeName = ClassName(modelPackageName(packages), nameForObject),
                                aliasTarget = it
                            ).register()
                        }
                        else it.register()
                    }
                }
                "string" -> {
                    if (hasEnums()) {
                        val enumValues = enums
                        val canBeNull = isNullable && null in enumValues
                        Type.WithTypeName.Enum(
                            ClassName(
                                packageName,
                                nameForObject.capitalize()
                            ).copy(nullable = canBeNull), enumValues.filterNotNull().map { it.toString() }).register()
                    } else {
                        String::class.asClassName().simpleType(isNullable).register()
                    }
                }
                "boolean" -> Boolean::class.asClassName().simpleType(isNullable).register()
                "number" -> Double::class.asClassName().simpleType(isNullable).register()
                "integer" -> Int::class.asClassName().simpleType(isNullable).register()
                "object" -> makeObject(nameForObject, components).register()
                null -> {
                    // *Of or component definition
                    if (isReference) {
                        // Reference to something
                        val packageName = makePackageName(getComponentRef()!!, packages.models)
//                        println("Reference found! ${getComponentRef()} $packageName")
                        Type.Reference(
                            jsonReference = getComponentRef()!!
                        ).register()
                    } else if (hasOneOfSchemas()) {
                        // oneOf
                        if (oneOfSchemas.all { it.type in simpleTypes }) {
                            JsonPrimitive::class.asClassName().simpleType(isNullable).register()
                        } else if (oneOfSchemas.any { it.type in simpleTypes } || discriminator.propertyName == null) {
                            // Some of oneOf are primitives so we can't make a truly polymorphic supertype?
                            // TODO: Make a sealed class with Int(), String(), etc subclasses.
                            JsonElement::class.asClassName().simpleType(isNullable).register()
                        } else {
                            Type.WithTypeName.OneOf(
                                typeName = ClassName(packageName, if (components) nameForObject else "I$nameForObject"),
                                childrenMapping = oneOfSchemas.mapIndexed { index, it ->
                                    val isReference = Overlay.of(oneOfSchemas).isReference(index)
                                    val discriminatorValue = discriminator
                                        .mappings.entries
                                        .find { pair -> pair.value == it.getComponentRef() }?.key

                                    it.makeType(
                                        nameForObject,
                                        it.jsonReference,
                                        isReference = isReference,
                                        wrapPrimitives = false
                                    ) to (discriminatorValue ?: it.name)
                                }.toMap(),
                                discriminator = discriminator.propertyName
                            ).register()
                        }
                    } else if (hasAllOfSchemas()) {
                        val allProps = allOfSchemas.flatMap { it.makeProps(components).awaitProps().entries }.associate { it.key to it.value }
                        val allRequired = allOfSchemas.flatMap { it.requiredFields } + this.requiredFields
                        val defaultValues = allOfSchemas.flatMap { allOf -> allOf.properties.mapValues { it.value.default }.entries }.associate { it.key to it.value }
                        Type.WithTypeName.Object(
                            typeName = ClassName(packageName, nameForObject),
                            props = allProps,
                            requiredProps = allRequired,
                            defaultValues = defaultValues
                        ).register()
                    } else if (hasAnyOfSchemas()) {
                        // TODO: Generate an object with superset of fields but all fields optional?
                        JsonElement::class.asClassName().simpleType(isNullable).register()
                    } else {
                        // It is either a component definition or a reference!
                        if (isReference) {
                            error("Reference in the wrong place!")
                        } else if (components) {
                            // Component definition
//                            println("Component def found! ${getComponentRef()}")
                            val className = makePackageName(getComponentRef()!!, packages.models)
                            makeObject(className.substringAfterLast("."), components).register()
                        } else {
                            // Something without schema at all!
                            JsonObject::class.asClassName().simpleType(isNullable).register()
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
            return if (wrapPrimitives) {
                JsonObject::class.asClassName().simpleType(false).register()
            } else {
                Type.WithTypeName.Alias(
                    ClassName(packageName, nameForObject.capitalize()),
                    JsonObject::class.asClassName().simpleType(false)
                ).register()
            }

        }
    }

    private suspend fun Schema.makeObject(nameForObject: String, components: Boolean): Type {
        val props = makeProps(components).awaitProps()
        return if (props.isNotEmpty()) {
            Type.WithTypeName.Object(
                ClassName(modelPackageName(packages), nameForObject.capitalize()),
                props = props,
                requiredProps = this.requiredFields,
                defaultValues = properties.mapValues { it.value.default }
            )
        } else {
            JsonObject::class.asClassName().simpleType(nullable ?: false)
        }
    }

    private fun Operation.hasMultipleSuccessfulResponseCodes(): Boolean {
        return responses.keys.count { it.toInt().isSuccessCode() } > 1
    }


}