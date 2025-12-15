package com.dshatz.openapi2ktor.generators.clients

import com.dshatz.openapi2ktor.GeneratorConfig
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

class KtorClientGenerator(override val typeStore: TypeStore, val packages: Packages, private val config: GeneratorConfig): IClientGenerator {

    private val baseApiClientType = ClassName(packages.client, "BaseClient")
    private val addOptionalParamHelper = MemberName(packages.client, "addOptionalParam")
    private val addRequiredParamHelper = MemberName(packages.client, "addRequiredParam")
    private val addOptionalHeaderParamHelper = MemberName(packages.client, "addOptionalHeaderParam")
    private val addRequiredHeaderParamHelper = MemberName(packages.client, "addRequiredHeaderParam")
    private val addPathParamHelper = MemberName(packages.client, "replacePathParams")
    private val unknownSuccessException = ClassName(packages.client, "UnknownSuccessCodeError")
    private val unknownErrorException = ClassName(packages.client, "OpenAPIResponseError")
    private val ktorBodyMethod = MemberName("io.ktor.client.call", "body")
    private val additionalPropsSerializer = ClassName(packages.client, "PropsSerializer")
    private val libResultClass = ClassName(packages.client, "HttpResult")
    private val bodyAsChanel = MemberName("io.ktor.client.statement", "bodyAsChannel")
    private val readBuffer = MemberName("io.ktor.utils.io", "readBuffer")
    private val decodeFromSource = MemberName("kotlinx.serialization.json.io", "decodeFromSource")

    private lateinit var securitySchemes: Map<String, SecurityScheme>
    private var globalSecurityRequirements: List<String> = emptyList()

    private val ignoreUnknownJson = Json {
        ignoreUnknownKeys = true
    }

    override fun generate(schema: OpenApi3): List<FileSpec> {
        processSecurity(schema)
        val serversEnum = generateServersEnum(schema)


        fun TreeNode.generateClients(parentPath: String, results: MutableList<FileSpec>) {
            val currentPath = parentPath + "/" + toString()
            if (currentPath.isNotEmpty() && children.any { it.pathObj != null } || pathObj != null) {
                // This node has some api paths that need to be generated.
                // So we generate all descending paths in one client.
                results += generateClientForPackagePrefix(schema, currentPath, getAllPaths())
            } else {
                children.forEach {
                    it.generateClients(currentPath, results)
                }
            }
        }

        val pathTree = buildPathTree(schema.paths)
        val clientSpecs = mutableListOf<FileSpec>()
        pathTree.generateClients("", clientSpecs)
        return clientSpecs + serversEnum
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
            securitySchemes = ignoreUnknownJson.decodeFromString<Map<String, SecurityScheme>>(Overlay.of(schema.securitySchemes).toJson().toString())
                .mapValues { (_, scheme) ->
                    if (scheme is ApiKey && scheme.bearerFormat == "bearer") {
                        // Some strange syntax for defining bearer.
                        // For example in TMDB and https://github.com/nulltea/kicksware-api/blob/master/openapi.yaml
                        Http.Bearer("")
                    } else scheme
                }
        } else securitySchemes = emptyMap()
        globalSecurityRequirements = schema.securityRequirements.flatMap { it.requirements.keys }
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
        val configLambdaType = LambdaTypeName.get(
            receiver = HttpClientConfig::class.asTypeName().parameterizedBy(STAR),
            returnType = UNIT
        )
        val params = listOf(
            ParameterSpec.builder("engine", HttpClientEngine::class).build(),
            ParameterSpec.builder("baseUrl", String::class.asTypeName())
                .run { if (api.hasServers()) defaultValue("%S", api.servers.first().url) else this }
                .build(),
            ParameterSpec.builder("json", Json::class.asTypeName()).defaultValue("Json { ignoreUnknownKeys = true }").build(),
            ParameterSpec.builder("config", configLambdaType).defaultValue(CodeBlock.of("{}")).build()
        )
        val constructor = FunSpec.constructorBuilder().addParameters(params).build()
        val props = params.filter { it.name != "json" }.map { PropertySpec.builder(it.name, it.type, KModifier.PRIVATE).initializer(it.name).build() }

        val authSchemesProp = if (securitySchemes.isNotEmpty()) PropertySpec.builder("authSchemes", MUTABLE_MAP.parameterizedBy(String::class.asTypeName(), authSchemeType(packages)), KModifier.PRIVATE)
            .initializer(
                CodeBlock.builder()
                    .beginControlFlow("%M", MemberName("kotlin.collections", "buildMap"))
                    .apply {
                        securitySchemes.forEach { (name, scheme) ->
                            addStatement("put(%S, %T())", name, scheme.generatedContainer(packages))
                        }
                    }
                    .endControlFlow() // end buildMap
                    .add(".toMutableMap()")
                    .build()
            ).build() else null

        val authSchemeSetters = securitySchemes.map { (name, scheme) ->
           scheme.generateSetter(name)
        }
        val httpClientFactory = HttpClientEngineFactory::class.asTypeName().parameterizedBy(STAR)

        val pkg = packages.client + "." + prefix.split('/').joinToString(".") { it.safePropName() }
        val clientName = prefix.substringAfterLast('/').safePropName().capitalize() + "Client"
        val prefixClientName = ClassName(pkg, clientName)
        val clientType = TypeSpec.classBuilder(prefixClientName)
            .primaryConstructor(constructor)
            .superclass(baseApiClientType)
            .addSuperclassConstructorParameter(CodeBlock.of("engine"))
            .addSuperclassConstructorParameter("json")
            .addSuperclassConstructorParameter(
                CodeBlock.builder()
                    .beginControlFlow("")
                    .beginControlFlow("%M", MemberName("io.ktor.client.plugins", "defaultRequest"))
                    .addStatement("url(%N)", "baseUrl")
                    .endControlFlow() // end defaultRequest
                    .addStatement("config()")
                    .endControlFlow()
                    .build()
            )
            .addFunction(
                FunSpec.constructorBuilder()
                    .addParameter("engine", httpClientFactory)
                    .addParameters(params.drop(1)) // remaining
                    .callThisConstructor("engine.create()", "baseUrl", "json", "config")
                    .build()
            )
            .addProperties(props)
            .addAnnotation(AnnotationSpec.builder(Suppress::class).addMember("%S", "unused").build())
            .apply {
                generateFunctions(paths, prefix.replace("/", "")).forEach { (function) ->
                    addFunction(function)
//                    addType(exception)
                }
            }
            .addFunctions(authSchemeSetters)
            .apply {
                authSchemesProp?.let { addProperty(it) }
            }
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

    data class EndpointTools(
        val func: FunSpec
    )

    /**
     * Generate endpoint methods and a error exception class.
     */
    private fun generateFunctions(paths: Map<String, Path>, prefix: String): List<EndpointTools> {
        val funcSpecs: List<EndpointTools> = paths.flatMap { (pathString, path) ->
            path.operations.map { (verb, operation) ->
                val pathLevelSecurity = operation.securityRequirements.flatMap { it.requirements.keys }
                val security = pathLevelSecurity.ifEmpty { globalSecurityRequirements }
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
                    val successResponse = statusToResponseType.entries.singleOrNull { it.key.toInt().isSuccessCode() }?.value
                    successResponse?.resolveClassName() ?: JsonObject::class.asTypeName()
                }

                val errorResponseClass = if (iErrorClass != null)
                    iErrorClass
                else {
                    val errorResponse = statusToResponseType.entries.singleOrNull { !it.key.toInt().isSuccessCode() }?.value
                    errorResponse?.resolveClassName() ?: unknownErrorException
                }

                val params = typeStore.getParamsForOperation(pathID)
                val paramSpecs = params.associateWith {
                    val defaultCode = it.type.makeDefaultValueCodeBlock(it.isRequired, null, useKotlinDefaults = false)
                    // Default value in parameters are just examples that the server will assume if parameter is not received.
                    val finalType = it.type.nullableIfNoDefault(it.isRequired, null)

                    ParameterSpec.builder(it.name.safePropName(), finalType)
                        .defaultValue(defaultCode)
                        .build()
                }

                val description = operation.description
                val funName = pathID.makeRequestFunName(dropPrefix = prefix)

                val exceptionTypeName = funName.capitalize() + "Exception"
                val exceptionType = buildResponseException(exceptionTypeName, errorResponseClass)

                val enableAdditionalProps = config.additionalPropsConfig.matches(pathID)

                val requestFun = FunSpec.builder(funName)
                    .returns(libResultClass.parameterizedBy(successResponseClass, errorResponseClass))
                    .addModifiers(KModifier.SUSPEND)
                    .addParameters(paramSpecs.values)
                    .apply {
                        description?.let { addKdoc(DocTemplate.Builder().add(it).build().toCodeBlock(::findConcreteType)) }
                    }
                    .addCode(
                        CodeBlock.builder()
                            .apply {
                                if (enableAdditionalProps) {
                                    statusToResponseType.entries.mapIndexed { index, (status, type) ->
                                        addStatement(
                                            "val s$status = object: %T(%T.serializer()) {}",
                                            additionalPropsSerializer.parameterizedBy(type.resolveClassName().copy(nullable = false)),
                                            type.resolveClassName().copy(nullable = false)
                                        )
                                    }
                                }
                            }
                            .beginControlFlow("try")
                            .beginControlFlow("val response = httpClient.%M(%L)",
                                MemberName("io.ktor.client.request", verb, isExtension = true),
                                CodeBlock.of("%S%L", pathString.removeLeadingSlash(), CodeBlock.builder().apply {
                                    // Replace path params in url of client.<verb>(url)
                                    paramSpecs
                                        .filter { it.key.where == ParamLocation.PATH }
                                        .forEach { (param, spec) ->
                                            add(".%M(%S, %N, %L)", addPathParamHelper, param.name, spec.name, spec.type.isNullable)
                                        }
                                }.build()))
                            .apply {
                                // Add query params
                                paramSpecs.filter { it.key.where == ParamLocation.QUERY || it.key.where == ParamLocation.HEADER }.forEach { (paramInfo, paramSpec) ->
                                    if (paramInfo.isRequired) {
                                        val helper = if (paramInfo.where == ParamLocation.QUERY) addRequiredParamHelper else addRequiredHeaderParamHelper
                                        addStatement("%M(%S, %N)", helper, paramInfo.name, paramSpec.name)
                                    } else {
                                        val helper = if (paramInfo.where == ParamLocation.QUERY) addOptionalParamHelper else addOptionalHeaderParamHelper
                                        addStatement("%M(%S, %N, %L)", addOptionalParamHelper, paramInfo.name, paramSpec.name, paramInfo.type.resolveClassName().isNullable)
                                    }
                                }
                            }.apply {
                                // Add security
                                security.forEach {
                                    securitySchemes[it]?.generateApplicator(it)?.apply(::add)
                                }
                            }
                            .endControlFlow() // request config
                            .beginControlFlow("val result = when (response.status.value)") // begin when
                            .apply {
                                // Success status code mapping
                                statusToResponseType.filterKeys { it.toInt().isSuccessCode() }.forEach { (code, type) ->
                                    if (enableAdditionalProps) {
                                        addStatement("%L -> json.%M<%T>(s%L, response.%M().%M())", code.toInt(), decodeFromSource, type.resolveClassName(), code.toInt(), bodyAsChanel, readBuffer)
                                    } else {
                                        addStatement("%L -> response.%M<%T>()", code.toInt(), ktorBodyMethod, type.resolveClassName())
                                    }
                                }
                                if (statusToResponseType.isEmpty()) {
                                    addStatement("else -> response.%M<%T>()", ktorBodyMethod, successResponseClass)
                                } else {
                                    addStatement("else -> throw %T(response.%M(), response.status.value, response)", unknownSuccessException, ktorBodyMethod)
                                }
                            }
                            .endControlFlow() // end when
                            .addStatement("return %T.Ok(result, response)", libResultClass)
                            .endControlFlow() //try
                            .beginControlFlow("catch (e: %T)", ClientRequestException::class)
                            .beginControlFlow("val responseData: %T = when(e.response.status.value)", errorResponseClass)  // begin when
                            .apply {
                                // Error status code mapping
                                statusToResponseType.filterKeys { !it.toInt().isSuccessCode() }.forEach { (code, type) ->
                                    if (enableAdditionalProps) {
                                        addStatement("%L -> json.%M<%T>(s%L, e.response.%M().%M())", code.toInt(), decodeFromSource, type.resolveClassName(), code.toInt(), bodyAsChanel, readBuffer)
                                    } else {
                                        addStatement("%L -> e.response.%M<%T>()", code.toInt(), ktorBodyMethod, type.resolveClassName())
                                    }
                                }
                                if (statusToResponseType.isEmpty()) {
                                    addStatement("else -> response.%M<%T>()", ktorBodyMethod, errorResponseClass)
                                } else {
                                    addStatement("else -> throw e")
                                }
                            }
                            .endControlFlow() // end when
                            .addStatement("return %T.Failure(responseData, e.response, e)", libResultClass)
                            .endControlFlow() // catch
                            .build()
                    ).build()
                EndpointTools(requestFun)
            }
        }
        return funcSpecs
    }

    private fun buildResponseException(exceptionTypeName: String, errorResponseClass: TypeName): TypeSpec {
        return TypeSpec.classBuilder(exceptionTypeName)
            .addModifiers(KModifier.DATA)
            .superclass(Exception::class)
            .addProperties(listOf(
                PropertySpec.builder("body", errorResponseClass)
                    .initializer("body")
                    .build(),
                PropertySpec.builder("reason", ClassName(libResultClass.packageName, "HttpResult.Failure").parameterizedBy(STAR, errorResponseClass))
                    .initializer("reason")
                    .build()
            ))
            .apply {
                primaryConstructor(
                    FunSpec.constructorBuilder()
                        .addParameters(propertySpecs.map {
                            ParameterSpec.builder(it.name, it.type).build()
                        }).build()
                )
            }
            .addSuperclassConstructorParameter("reason.cause.message, reason.cause")
            .build()
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