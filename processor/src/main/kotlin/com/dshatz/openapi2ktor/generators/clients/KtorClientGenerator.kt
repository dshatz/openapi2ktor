package com.dshatz.openapi2ktor.generators.clients

import com.dshatz.openapi2ktor.generators.Type
import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.generators.TypeStore.OperationParam.ParamLocation
import com.dshatz.openapi2ktor.kdoc.DocTemplate
import com.dshatz.openapi2ktor.utils.*
import com.reprezen.jsonoverlay.Overlay
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import com.reprezen.kaizen.oasparser.model3.Path
import com.reprezen.kaizen.oasparser.model3.Response
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import net.pwall.mustache.Template

class KtorClientGenerator(override val typeStore: TypeStore, val packages: Packages): IClientGenerator {

    private val baseApiClientType = ClassName(packages.client, "BaseClient")
    private val addOptionalParamHelper = MemberName(packages.client, "addOptionalParam")
    private val addRequiredParamHelper = MemberName(packages.client, "addRequiredParam")
    private val addPathParamHelper = MemberName(packages.client, "replacePathParams")
    private lateinit var securitySchemes: Map<String, SecurityScheme>

    override fun generate(schema: OpenApi3): List<FileSpec> {
        processSecurity(schema)
        val serversEnum = generateServersEnum(schema)

        val clients = schema.paths.entries.groupBy { it.key.drop(1).substringBefore("/") }
            .mapValues { (firstSegment, paths) ->
                generateClientForPackagePrefix(schema, firstSegment, paths.associate { it.key to it.value})
            }
        return clients.values.toList() + serversEnum
    }

    override fun generateTemplates(): List<IClientGenerator.Template> {
        val template = this.javaClass.classLoader.getResourceAsStream("apiclient.kt")!!.use {
            Template.parse(it)
        }

        val content = template.processToString(mapOf("client" to packages.client))
        return listOf(IClientGenerator.Template(packages.client, baseApiClientType.simpleName, content))
    }

    private fun processSecurity(schema: OpenApi3) {
        if (schema.securitySchemes.isNotEmpty()) {
            securitySchemes = Json {
                ignoreUnknownKeys = true
            }.decodeFromString<Map<String, SecurityScheme>>(Overlay.of(schema.securitySchemes).toJson().toString())
        } else securitySchemes = emptyMap()
    }

    private fun generateServersEnum(schema: OpenApi3): FileSpec {
        val enumNames = schema.servers.associateWith {
            it.url.substringAfter("//").substringBefore("/")
                .split(".", "-").joinToString("_") { it.uppercase() }
        }
        val enum = TypeSpec.enumBuilder(ClassName(packages.client, "Servers"))
            .primaryConstructor(FunSpec.constructorBuilder().addParameter("url", String::class).build())
            .apply {
                enumNames.forEach { (server, name) ->
                    addEnumConstant(name,
                        TypeSpec.anonymousClassBuilder()
                            .addSuperclassConstructorParameter("%S", server.url)
                            .build()
                    )
                }
            }
            .addProperty(PropertySpec.builder("url", String::class).initializer("url").build())
            .build()
        return FileSpec.builder(ClassName(packages.client, "Servers")).addType(enum).build()
    }

    private fun generateClientForPackagePrefix(api: OpenApi3, prefix: String, paths: Map<String, Path>): FileSpec {
        val params = listOf(
            ParameterSpec.builder("client", baseApiClientType).build(),
            ParameterSpec.builder("baseUrl", String::class.asTypeName())
                .run { if (api.hasServers()) defaultValue("%S", api.servers.first().url) else this }
                .build()
        )
        val constructor = FunSpec.constructorBuilder().addParameters(params).build()
        val props = params.map { PropertySpec.builder(it.name, it.type, KModifier.PRIVATE).initializer(it.name).build() }

        val authSchemesProp = PropertySpec.builder("authSchemes", MUTABLE_MAP.parameterizedBy(String::class.asTypeName(), authSchemeType(packages)), KModifier.PRIVATE)
            .initializer(
                CodeBlock.builder()
                    .beginControlFlow("%M", MemberName("kotlin.collections", "buildMap"))
                    .apply {
                        securitySchemes.forEach { (name, scheme) ->
                            add("put(%S, %T())", name, scheme.generatedContainer(packages))
                        }
                    }
                    .endControlFlow() // end buildMap
                    .add(".toMutableMap()")
                    .build()
            ).build()
        val authSchemeSetters = securitySchemes.map { (name, scheme) ->
           scheme.generateSetter(name)
        }

        val prefixClientName = ClassName(packages.client + "." + prefix.safePropName(), "${prefix.capitalize()}Client")
        val clientType = TypeSpec.classBuilder(prefixClientName)
            .primaryConstructor(constructor)
            .addProperties(props)
            .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "unused").build())
            .addFunctions(generateFunctions(api, paths, prefix))
            .addFunctions(authSchemeSetters)
            .addProperty(authSchemesProp)
            .build()
        return FileSpec.builder(prefixClientName)
            .addType(clientType)
            .build()
    }

    private fun Response.resolveReference(): Type {
        return typeStore.resolveReference(Overlay.of(this).jsonReference.cleanJsonReference())
    }

    private fun Type.resolveClassName(): TypeName {
        return when (this) {
            is Type.List -> List::class.asTypeName().parameterizedBy(itemsType.resolveClassName())
            is Type.Reference -> typeStore.resolveReference(jsonReference).resolveClassName()
            is Type.SimpleType -> kotlinType
            is Type.WithTypeName -> typeName
        }
    }

    private fun generateFunctions(api: OpenApi3, paths: Map<String, Path>, prefix: String): List<FunSpec> {
        val funcSpecs: List<FunSpec> = paths.flatMap { (pathString, path) ->
            path.operations.map { (verb, operation) ->
                val requiredSecuritySchemes = operation.securityRequirements.flatMap { it.requirements.keys }
                val statusToResponseType = operation.responses.mapValues { (statusCode, response) ->
                    val resposneRef = Overlay.of(response).jsonReference
                    val responseModel = typeStore.getResponseMapping(TypeStore.PathId(pathString, verb))[statusCode.toInt()]?.type
                        ?: typeStore.resolveReference(resposneRef.cleanJsonReference())
                    responseModel
                }
                val pathID = TypeStore.PathId(pathString, verb)
                val iResponseClass = typeStore.getResponseSuccessInterface(pathID)
                val iErrorClass = typeStore.getResponseErrorInterface(pathID)

                val successResponseClass = if (iResponseClass != null)
                    iResponseClass
                else {
                    val successResponse = operation.responses.entries.singleOrNull { it.key.toInt().isSuccessCode() }
                    successResponse?.value?.resolveReference()?.resolveClassName() ?: JsonObject::class.asTypeName()
                }

                val errorResponseClass = if (iErrorClass != null)
                    iErrorClass
                else {
                    val errorResponse = operation.responses.entries.singleOrNull { !it.key.toInt().isSuccessCode() }
                    errorResponse?.value?.resolveReference()?.resolveClassName() ?: JsonObject::class.asTypeName()
                }

                val params = typeStore.getParamsForOperation(pathID)
                val paramSpecs = params.associateWith {
                    val finalType = it.type.addNullabilityIfOptional(it.isRequired)
                    // Default value in parameters are just examples that the server will assume if parameter is not received.
                    val defaultCode = it.type.makeDefaultValueCodeBlock(it.isRequired, null)

                    ParameterSpec.builder(it.name.safePropName(), finalType)
                        .defaultValue(defaultCode)
                        .build()
                }

                val description = operation.description
                val libResultClass = ClassName(packages.client, "HttpResult")
                val ktorBodyMethod = MemberName("io.ktor.client.call", "body")
                val funName = pathID.makeRequestFunName(dropPrefix = prefix)
                val requestFun = FunSpec.builder(funName)
                    .returns(libResultClass.parameterizedBy(successResponseClass, errorResponseClass))
                    .addModifiers(KModifier.SUSPEND)
                    .addParameters(paramSpecs.values)
                    .apply {
                        description?.let { addKdoc(DocTemplate.Builder().add(it).build().toCodeBlock(::findConcreteType)) }
                    }
                    .addCode(
                        CodeBlock.builder()
                            .beginControlFlow("try")
                            .beginControlFlow("val response = client.httpClient.%M(%L)",
                                MemberName("io.ktor.client.request", verb, isExtension = true),
                                CodeBlock.of("%S%L", pathString, CodeBlock.builder().apply {
                                    // Replace path params in url of client.<verb>(url)
                                    paramSpecs
                                        .filter { it.key.where == ParamLocation.PATH }
                                        .forEach { (param, spec) ->
                                            add(".%M(%S, %N, %L)", addPathParamHelper, param.name, spec.name, spec.type.isNullable)
                                        }
                                }.build()))
                            .apply {
                                // Add query params
                                paramSpecs.filter { it.key.where == ParamLocation.QUERY }.forEach { (paramInfo, paramSpec) ->
                                    if (paramInfo.isRequired) {
                                        addStatement("%M(%S, %N)", addRequiredParamHelper, paramInfo.name, paramSpec.name)
                                    } else {
                                        addStatement("%M(%S, %N, %L)", addOptionalParamHelper, paramInfo.name, paramSpec.name, paramInfo.type.resolveClassName().isNullable)
                                    }
                                }
                            }.apply {
                                // Add security
                                requiredSecuritySchemes.forEach {
                                    securitySchemes[it]?.generateApplicator(it)?.apply(::add)
                                }
                            }
                            .endControlFlow() // request config
                            .beginControlFlow("val result = when (response.status.value)") // begin when
                            .apply {
                                statusToResponseType.filterKeys { it.toInt().isSuccessCode() }.forEach { (code, type) ->
                                    addStatement("%L -> response.%M<%T>()", code.toInt(), ktorBodyMethod, type.resolveClassName())
                                }
                                if (statusToResponseType.isEmpty()) {
                                    addStatement("else -> response.%M<%T>()", ktorBodyMethod, successResponseClass)
                                } else {
                                    addStatement("else -> error(\"Unknown success status code \${response.status.value}.\")")
                                }
                            }
                            .endControlFlow() // end when
                            .addStatement("return %T.Ok(result, response)", libResultClass)
                            .endControlFlow() //try
                            .beginControlFlow("catch (e: %T)", ClientRequestException::class)
                            .beginControlFlow("val responseData: %T? = when(e.response.status.value)", errorResponseClass)  // begin when
                            .apply {
                                statusToResponseType.filterKeys { !it.toInt().isSuccessCode() }.forEach { (status, type) ->
                                    addStatement("%L -> e.response.%M<%T>()", status.toInt(), ktorBodyMethod, type.resolveClassName())
                                }
                                addStatement("else -> null")
                            }
                            .endControlFlow() // end when
                            .addStatement("return %T.Failure(responseData, e.response, e)", libResultClass)
                            .endControlFlow() // catch
                            .build()
                    ).build()
                requestFun
            }
        }
        return funcSpecs
    }


    @Deprecated("Using template instead")
    private fun generateApiClient(pkg: String): FileSpec {
        val configLambdaType = LambdaTypeName.get(
            receiver = HttpClientConfig::class.asTypeName().parameterizedBy(STAR),
            returnType = UNIT
        )
        val httpClientFactory = HttpClientEngineFactory::class.asTypeName().parameterizedBy(STAR)

        return FileSpec.builder(baseApiClientType)
            .addType(
                TypeSpec.classBuilder(baseApiClientType.simpleName)
                    // class ApiClient(engine: HttpClientEngine, config: HttpClientConfig<*>.() -> Unit = {})
                    .primaryConstructor(
                        FunSpec.constructorBuilder()
                            .addParameter("engine", HttpClientEngine::class)
                            .addParameter(
                                ParameterSpec
                                    .builder("config", configLambdaType)
                                    .defaultValue(CodeBlock.of("{}")).build()
                            ).build()
                    )
                    .addProperty(
                        PropertySpec
                            .builder("httpClient", HttpClient::class)
                            .addModifiers(KModifier.INTERNAL)
                            .initializer(
                                CodeBlock.builder()
                                    .beginControlFlow("%T(engine)", HttpClient::class)
                                    .addStatement("expectSuccess = true")
                                    .beginControlFlow(
                                        "install(%M)",
                                        MemberName("io.ktor.client.plugins.contentnegotiation", "ContentNegotiation")
                                    )
                                    .addStatement("%M()", MemberName("io.ktor.serialization.kotlinx.json", "json"))
                                    .endControlFlow()
                                    .endControlFlow()
                                    .build()
                            ).build()
                    )
                    .addFunction(
                        FunSpec.constructorBuilder()
                            .addParameter("engine", httpClientFactory)
                            .addParameter(
                                ParameterSpec.builder("config", configLambdaType)
                                    .defaultValue("{}").build()
                            )
                            .callThisConstructor("engine.create()", "config")
                            .build()
                    )
                    .build()
            )
            .build()
    }
}