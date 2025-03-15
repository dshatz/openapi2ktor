package com.dshatz.openapi2ktor.generators.analyze

import com.dshatz.openapi2ktor.GeneratorConfig
import com.dshatz.openapi2ktor.generators.Type
import com.dshatz.openapi2ktor.generators.clients.KtorClientGenerator
import com.dshatz.openapi2ktor.generators.models.KotlinxCodeGenerator
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import com.reprezen.kaizen.oasparser.model3.Operation
import com.reprezen.kaizen.oasparser.model3.Schema
import com.squareup.kotlinpoet.*
import com.dshatz.openapi2ktor.generators.Type.Companion.simpleType
import com.dshatz.openapi2ktor.generators.Type.WithTypeName.Object.PropInfo
import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.generators.TypeStore.OperationParam.ParamLocation
import com.dshatz.openapi2ktor.generators.clients.IClientGenerator
import com.dshatz.openapi2ktor.kdoc.DocTemplate
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
    private val config: GeneratorConfig
) {

    private val modelGenerator = KotlinxCodeGenerator(typeStore, packages, config)
    private val clientGenerator = KtorClientGenerator(typeStore, packages, config)

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

        println(typeStore.printableSummary())

        typeStore.disambiguateTypeNames()

        val clientTemplates = clientGenerator.generateTemplates()
        val fileSpecs = modelGenerator.generate() + clientGenerator.generate(api)
        return@runBlocking fileSpecs to clientTemplates
    }

    private suspend fun OpenApi3.gatherComponentModels() = withContext(coroutineContext) {
        schemas.map { (schemaName, schema) ->
            launch { processComponent(schemaName, schema) }
        }
        processResponseComponents(responses)
    }

    internal suspend fun processResponseComponents(responses: Map<String, Response>) = withContext(coroutineContext) {
        return@withContext responses.map { (schemaName, response) ->
            launch {
                response.contentMediaTypes.values.firstOrNull()?.schema.run {
                    makeType(
                        schemaName.safePropName().capitalize(),
                        Overlay.of(response).jsonReference,
                        true,
                        response.getResponseComponentRefInfo(),
                        WrapMode.None
                    )
                }
            }
        }
    }

    internal suspend fun processComponent(schemaName: String, schema: Schema) {
        schema.makeType(
            schemaName.safePropName().capitalize(),
            schema.jsonReference,
            components = true,
            referenceData = null,
            wrapMode = WrapMode.None
        )
    }

    private suspend fun OpenApi3.gatherPathModels(): List<Job> {
        return gatherPathResponseModels() + gatherPathRequestBodyModels() + calculateOperationParameters()
    }

    private suspend fun OpenApi3.calculateOperationParameters() = withContext(coroutineContext) {
        mapPaths { pathID, operation ->
            launch {
                calculateParameters(pathID, operation)
            }
        }
    }

    internal suspend fun calculateParameters(pathID: TypeStore.PathId, operation: Operation) {
        val params = operation.parameters.map { param ->
            param.schema.makeType(
                param.jsonReference.split("/").last().safePropName(),
                param.jsonReference,
                false,
                operation.isParameterAReference(param.name),
                WrapMode.None
            ).let { paramType ->
                val where = when (param.`in`) {
                    "query" -> ParamLocation.QUERY
                    "path" -> ParamLocation.PATH
                    "header" -> ParamLocation.HEADER
                    else -> error("What is this param location? ${param.`in`}")
                }
                TypeStore.OperationParam(param.name, paramType, param.isRequired, where)
            }
        }
        typeStore.registerOperationParams(pathID, params)
    }

    internal suspend fun processPath(pathId: TypeStore.PathId, operation: Operation): List<Job> {
        val multipleSuccess = operation.responses.count { it.key.toInt().isSuccessCode() } > 1
        val multipleErrors = operation.responses.count { !it.key.toInt().isSuccessCode() } > 1

        return if (operation.responses.isNotEmpty()) {
            val packageName = makePackageName(Overlay.of(operation.responses).jsonReference, packages.models)
            val iResponseClass = ClassName(packageName, "I${makeResponseModelName(pathId, 0, false)}")
            val iErrorClass = ClassName(packageName, "I${makeResponseModelName(pathId, 0, false)}Error")
            typeStore.registerResponseInterface(
                path = pathId,
                successInterface = iResponseClass.takeIf { multipleSuccess },
                errorInterface = iErrorClass.takeIf { multipleErrors }
            )
            operation.responses.map { (statusCode, response) ->
                val wrapPrimitives = if (statusCode.toInt().isSuccessCode()) multipleSuccess else true
                processPathResponse(operation, response, pathId.pathString, statusCode.toInt(), pathId.verb, wrapPrimitives)
            }
        } else emptyList()
    }

    internal suspend fun OpenApi3.gatherPathResponseModels() = withContext(coroutineContext) {
        return@withContext mapPaths { pathId, operation ->
            processPath(pathId, operation)
        }.flatten()
    }

    internal open suspend fun processPathResponse(
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
        val ref = operation.getReferenceForResponse(statusCode)
        val refData = operation.isResponseAReference(statusCode)
        val jsonReference = Overlay.of(operation.responses).jsonReference + "/$statusCode"
        launch {
            val mode = if (wrapPrimitives) {
                if (statusCode.isSuccessCode()) WrapMode.Simple
                else WrapMode.Exception
            } else WrapMode.None
            schema.makeType(modelName, jsonReference, referenceData = refData, wrapMode = mode).also {
                typeStore.registerResponseMapping(TypeStore.PathId(pathString, verb), statusCode, ref?.target ?: jsonReference, it)
            }
        }
    }

    private suspend fun OpenApi3.gatherPathRequestBodyModels() = withContext(coroutineContext) {
        return@withContext paths.flatMap { (pathString, path) ->
            path.operations.filter { it.value.requestBody.contentMediaTypes.isNotEmpty()  }.map { (verb, operation) ->
                val schema = operation.requestBody.contentMediaTypes.values.first().schema
                val modelName = makeRequestBodyModelName(verb, pathString)
                launch {
                    schema.makeType(modelName, schema.jsonReference, referenceData = null, wrapMode = WrapMode.None)
                }
            }
        }
    }


    private suspend fun Schema.makeProps(components: Boolean): List<Deferred<Pair<String, PropInfo>>> = withContext(coroutineContext) {
        properties.entries.map { (name, schema) ->
            async {
                val type = schema.makeType(
                    nameForObject = name,
                    jsonReference = schema.jsonReference,
                    referenceData = propRefData(name),
                    components = components,
                    wrapMode = WrapMode.None
                )
                name to PropInfo(type, DocTemplate.Builder().add(schema.description).add(schema.example?.let { "\nExample: $it" }).build())
            }
        }
    }

    private val simpleTypes = listOf("string", "integer", "number", "boolean")

    private suspend fun List<Deferred<Pair<String, PropInfo>>>.awaitProps(): Map<String, PropInfo> {
        return awaitAll().toMap()
    }

    enum class WrapMode {
        Simple,
        Exception,
        None
    }

    private suspend fun Schema?.makeType(
        nameForObject: String,
        jsonReference: String,
        components: Boolean = false,
        referenceData: ReferenceMetadata?,
        wrapMode: WrapMode
    ): Type {
        println("Entering ${"component".takeIf { components } ?: ""} ${jsonReference.cleanJsonReference()}")

        val packageName = makePackageName(jsonReference, packages.models)

        fun Type.wrapPrimitive(): Type {
            if (wrapMode != WrapMode.None) {
                return Type.WithTypeName.PrimitiveWrapper(
                    ClassName(packageName, nameForObject),
                    wrappedType = this
                ).also {
                    if (wrapMode == WrapMode.Exception) {
                        typeStore.extendException(it)
                    }
                }
            } else {
                // Exception wrap mode
//                typeStore.extendException(this)
                return this
                /*return Type.WithTypeName.ExceptionWrapper(
                    wrapped = this,
                    ClassName(packageName, nameForObject + "Exception")
                )*/
            }
        }

        fun Type.register(): Type = run {
            val typeToRegister = if (wrapMode == WrapMode.Simple && this !is Type.WithTypeName)
                wrapPrimitive()
            else if (wrapMode == WrapMode.Exception) {
                if (this is Type.WithTypeName) {
                    typeStore.extendException(this)
                    this
                } else {
                    wrapPrimitive().also {
                        typeStore.extendException(it)
                    }
                }
            }
            else this

            if (this@makeType?.isPartOfComponentSchema() == true && components && this !is Type.Reference) {
                typeStore.registerComponentSchema(jsonReference, typeToRegister)
            }
            else {
                // TODO: Check why this check is needed. It shouldn't be!
                // Check if added reference is not pointing to itself.
                if (jsonReference.cleanJsonReference() != (typeToRegister as? Type.Reference)?.jsonReference) {
                    typeStore.registerType(jsonReference, typeToRegister)
                }
            }
            /*if (wrapPrimitives) {
                // Is reference inside a response.
                this.wrapPrimitive().register()
            }*/
            typeToRegister
        }

        val schemaType = if (referenceData.isReference) null else this?.type

        if (this != null) {
            return when (schemaType) {
                "array" -> {
                    Type.List(
                        if (arrayItemRefData().isReference) {
                            Type.Reference(itemsSchema.getComponentRef()!!)
                        } else {
                            itemsSchema.makeType(
                                nameForObject + "Item",
                                itemsSchema.jsonReference,
                                referenceData = arrayItemRefData(),
                                components = components,
                                wrapMode = WrapMode.None
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
                            typeName = ClassName(
                                packageName,
                                nameForObject.capitalize()
                            ).copy(nullable = canBeNull),
                            elements = enums.filterNotNull().associateWith { it.toString().safeEnumEntryName() },
                            description = DocTemplate.of(description))
                            .register()
                    } else {
                        String::class.asClassName().simpleType(isNullable).register()
                    }
                }
                "boolean" -> Boolean::class.asClassName().simpleType(isNullable).register()
                "number" -> {
                    if (format == "double") Double::class.asClassName().simpleType(isNullable).register()
                    else Float::class.asClassName().simpleType(isNullable).register()
                }
                "integer" -> {
                    if (this.format == "int64") Long::class.asClassName().simpleType(isNullable).register()
                    else Int::class.asClassName().simpleType(isNullable).register()
                }
                "object" -> makeObject(nameForObject, components).register()
                null -> {
                    if (referenceData != null) {
                        // Reference to something
                        Type.Reference(
                            jsonReference = referenceData.target
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
                            val childrenMapping = oneOfSchemas.mapIndexed { index, it ->
                                val isReference = oneOfRefData(index)
                                val discriminatorValue = discriminator
                                    .mappings.entries
                                    .find { pair -> pair.value == it.getComponentRef() }?.key

                                it.makeType(
                                    nameForObject,
                                    it.jsonReference,
                                    referenceData = isReference,
                                    wrapMode = WrapMode.None
                                ) to (discriminatorValue ?: it.name)
                            }.toMap()

                            val isNullable = oneOfSchemas.any {
                                it.isNullable
                            }

                            Type.WithTypeName.OneOf(
                                typeName = ClassName(packageName, if (components) nameForObject else "I$nameForObject").copy(nullable = isNullable),
                                childrenMapping = childrenMapping,
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
                            defaultValues = defaultValues,
                            description = DocTemplate.of(description)
                        ).register()
                    } else if (hasAnyOfSchemas()) {
                        // TODO: Generate an object with superset of fields but all fields optional?
                        JsonElement::class.asClassName().simpleType(isNullable).register()
                    } else {
                        // It is either a component definition or a reference!
                        if (referenceData.isReference) {
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
            return if (wrapMode != WrapMode.None) {
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
                ClassName(modelPackageName(packages), nameForObject.capitalize()).copy(nullable = nullable ?: false),
                props = props,
                requiredProps = this.requiredFields,
                defaultValues = properties.mapValues { it.value.default },
                description = DocTemplate.Builder()
                    .add(description)
                    .newLine()
                    .addMany(props.entries) { idx, (name, info) ->
                        if (info.doc != null) {
                            add("@param [$name] ").addDoc(info.doc)
                            newLine()
                            add("@property [$name] ").addDoc(info.doc)
                            newLine()
                        }
                    }
                    .build()
            )
        } else {
            JsonObject::class.asClassName().simpleType(nullable ?: false)
        }
    }

    private fun Operation.hasMultipleSuccessfulResponseCodes(): Boolean {
        return responses.keys.count { it.toInt().isSuccessCode() } > 1
    }


}