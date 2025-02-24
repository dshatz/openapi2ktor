package com.dshatz.openapi2ktor.generators.models

import com.dshatz.openapi2ktor.generators.Type
import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.utils.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import java.io.Serial

class KotlinxCodeGenerator(private val typeStore: TypeStore, private val packages: Packages): IModelGenerator {

    private lateinit var responseMappings: ResponseInterfaceResult

    override fun generate(): List<FileSpec> {

        responseMappings = generateResponseInterfaces()

        val objectSpecs = generateObjects()
        val superInterfaceSpecs = generateSuperInterfacesForOneOf(typeStore)
        val aliases = generateTypeAliases(typeStore)
        val enums = generateEnums(typeStore)
        return objectSpecs + superInterfaceSpecs + aliases + enums + responseMappings.files
    }

    private fun generateObjects(): List<FileSpec> {
        val typeToSuperInterfaces = interfaceMappingForOneOf(typeStore)
            .mapKeys { it.key.makeTypeName() }
        return typeStore.getTypes().values.filterIsInstance<Type.WithTypeName.Object>().map { type ->
            val className = type.typeName as ClassName
            val fileSpec = FileSpec.builder(className)

            val interfacesForOneOf = typeToSuperInterfaces[type.typeName]
            val typeSpecBuilder = TypeSpec.classBuilder(className)

            val propsParams = type.props.entries.map { type.makeDataClassProps(typeStore, it.key, it.value) }

            val uniqueProps = propsParams
                .groupBy { it.propertySpec.name.lowercase() }
                .entries
                .asSequence()
                .map { (lowerName, params) ->
                    if (params.size > 1) {
                        params.mapIndexed { index, propparam ->
                            val newName = lowerName + index
                            propparam.updateName(newName)
                        }
                    } else params
                }.flatten().map {
                    it.updateName(it.propertySpec.name.safePropName())
                }

            if (!interfacesForOneOf.isNullOrEmpty()) {
                typeSpecBuilder.addSuperinterfaces(interfacesForOneOf.map { it.typeName })
            }

            responseMappings.responseSuperclasses[type]?.let {
                typeSpecBuilder.addSuperinterface(it)
            }

            val constructorBuilder = FunSpec.constructorBuilder()
            uniqueProps.forEach { (prop, param) ->
                constructorBuilder.addParameter(param)
                typeSpecBuilder.addProperty(prop)
            }

            typeSpecBuilder
                .primaryConstructor(constructorBuilder.build())
                .addModifiers(KModifier.DATA)
                .addAnnotation(Serializable::class)
            fileSpec.addType(typeSpecBuilder.build())
            fileSpec.build()
        }
    }


    private data class DataClassProp(
        val propertySpec: PropertySpec,
        val parameterSpec: ParameterSpec,
        val serialName: String
    )

    private fun DataClassProp.updateName(newName: String): DataClassProp {
        return DataClassProp(
            propertySpec
                .toBuilder(name = newName)
                .initializer(newName)
                .apply {
                    annotations.removeIf { it.typeName == SerialName::class.asClassName() }
                    if (serialName != newName)
                        addAnnotation(AnnotationSpec.builder(SerialName::class).addMember("%S", serialName).build())
                }
                .build(),
            parameterSpec
                .toBuilder(name = newName)
                .build(),
            serialName
        )
    }

    private fun generateSuperInterfacesForOneOf(typeStore: TypeStore): List<FileSpec> {
        val polymorphicSerializers = typeStore.getTypes().values
            .filterIsInstance<Type.WithTypeName.OneOf>()
            .associate { oneOf ->
                oneOf.typeName to customPolymorphicSerializer(oneOf)
            }


        return polymorphicSerializers.map { (superType, serializer) ->
            val serializerClass = serializer.first
            val serializerSpec = serializer.second
            val className = superType as ClassName
            val fileSpec = FileSpec.builder(className)
            val typeSpec = TypeSpec.interfaceBuilder(className)
                .addAnnotation(AnnotationSpec.builder(Serializable::class).addMember("%T::class", serializerClass).build())
            fileSpec.addType(typeSpec.build())
            fileSpec.addType(serializerSpec)
            fileSpec.build()
        }
    }

    private fun generatePrimitiveResponseWrappers(types: Collection<TypeStore.ResponseTypeInfo>, superInterface: TypeName): Map<Type, FileSpec> {
        val wrappers = types.associateWithNotNull { (type, jsonReference) ->
            val wrapper = (typeStore.getTypes()[jsonReference] as? Type.WithTypeName.PrimitiveWrapper)
            wrapper
        }.mapKeys { it.key.type }
        return wrappers.mapValues { (type, wrapper) ->
            val className = wrapper.typeName as ClassName
            val fileSpec = FileSpec.builder(className)
            val wrappedType = wrapper.wrappedType.makeTypeName()

            val serializerClassname = ClassName(className.packageName, className.simpleName + "Serializer")

            val typeSpec = TypeSpec.classBuilder(className)
                .addSuperinterface(ClassName(packages.client, "Wrapper").parameterizedBy(wrappedType))
                .addModifiers(KModifier.DATA)
                .addAnnotation(AnnotationSpec.builder(Serializable::class)
                    .addMember("%T::class", serializerClassname).build())
                .addProperty(
                    PropertySpec.builder("d", wrappedType)
                        .addModifiers(KModifier.OVERRIDE)
                        .initializer("d").build()
                )
                .primaryConstructor(
                    FunSpec.constructorBuilder().addParameter(
                        ParameterSpec.builder("d", wrappedType).build()
                    ).build()
                )
                .addSuperinterface(superInterface)
                .build()

            /**
             * class GetUsersListResponse205Serializer: KSerializer<GetUsersListResponse205> {
             *         override val descriptor: SerialDescriptor = serializer<List<String>>().descriptor
             *         override fun deserialize(decoder: Decoder): GetUsersListResponse205 {
             *             return GetUsersListResponse205(decoder.decodeSerializableValue(serializer()))
             *         }
             *         override fun serialize(encoder: Encoder, value: GetUsersListResponse205) {
             *             encoder.encodeSerializableValue(serializer(), value.data)
             *         }
             *     }
             */
            val serializerMember = MemberName("kotlinx.serialization", "serializer")
            val serializer = TypeSpec.classBuilder(serializerClassname)
                .addSuperinterface(KSerializer::class.asTypeName().parameterizedBy(className))
                .addProperty(
                    PropertySpec.builder("descriptor", SerialDescriptor::class, KModifier.OVERRIDE)
                        .initializer("serializer<%T>().descriptor", wrappedType).build()
                )
                .addFunction(
                    FunSpec.builder("deserialize")
                        .addModifiers(KModifier.OVERRIDE)
                        .returns(className)
                        .addParameter(ParameterSpec("decoder", Decoder::class.asTypeName()))
                        .addCode("return %T(decoder.decodeSerializableValue(%M()))", className, serializerMember)
                        .build()
                )
                .addFunction(
                    FunSpec.builder("serialize")
                        .addModifiers(KModifier.OVERRIDE)
                        .addParameters(listOf(
                            ParameterSpec("encoder", Encoder::class.asTypeName()),
                            ParameterSpec("value", className)
                        ))
                        .addCode("encoder.encodeSerializableValue(%M(), value.d)", serializerMember)
                        .build()
                ).build()
            fileSpec.addType(typeSpec).addType(serializer).build()
        }
    }

    private fun generateResponseInterfaces(): ResponseInterfaceResult {
        val results = typeStore.getAllResponseTypes().map { pathId -> // e.g.GetUsers
            val responseTypeInfos = typeStore.getResponseMapping(pathId)
            val successTypes = responseTypeInfos.filterKeys { it.isSuccessCode() }
            val otherTypes = responseTypeInfos.filterKeys { !it.isSuccessCode() }

            val iResponseClass = typeStore.getResponseSuccessInterface(pathId)
            val iErrorClass = typeStore.getResponseErrorInterface(pathId)

            /*val packages = responseTypeInfos.values.map { makePackageName(it.jsonReference, packages.models) }
            assert(packages.toSet().size == 1, lazyMessage = { "packages differ! $packages" })*/

            val iResponse = iResponseClass?.let {
                TypeSpec.interfaceBuilder(iResponseClass).addModifiers(KModifier.SEALED).build()
            }
            val iError = iErrorClass?.let {
                TypeSpec.interfaceBuilder(iErrorClass).addModifiers(KModifier.SEALED).build()
            }
            val successWrappedTypes = iResponseClass?.let {
                generatePrimitiveResponseWrappers(successTypes.values, iResponseClass)
            }
            val errorWrappedTypes = iErrorClass?.let {
                generatePrimitiveResponseWrappers(otherTypes.values, iErrorClass)
            }

            val successFile = if (iResponse != null) {
                FileSpec.builder(iResponseClass).addType(iResponse).build()
            } else null

            val errorFile = if (iError != null) {
                FileSpec.builder(iErrorClass).addType(iError).build()
            } else null

            Pair(
                first = successWrappedTypes?.values.orEmpty()
                        + errorWrappedTypes?.values.orEmpty()
                        + listOfNotNull(successFile, errorFile),
                second =
                    successTypes.mapKeys { it.value.type }.mapValues { iResponseClass }
                            + otherTypes.mapKeys { it.value.type }.mapValues { iErrorClass }
            )
        }
        return ResponseInterfaceResult(
            results.flatMap { it.first },
            results.flatMap { it.second.entries }.associate { it.key to it.value }
        )
    }

    private data class ResponseInterfaceResult(val files: List<FileSpec>, val responseSuperclasses: Map<Type, ClassName?>)

    private fun generateTypeAliases(typeStore: TypeStore): List<FileSpec> {
        return typeStore.getTypes()
            .values
            .filterIsInstance<Type.WithTypeName.Alias>()
            .map { (typeAliasName, target) ->
                val className = typeAliasName as ClassName
                val aliasSpec = TypeAliasSpec.builder(className.simpleName, target.makeTypeName()).build()
                FileSpec.builder(typeAliasName)
                    .addTypeAlias(aliasSpec)
                    .build()
            }
    }

    private fun generateEnums(typeStore: TypeStore): List<FileSpec> {
        return typeStore.getTypes()
            .values
            .filterIsInstance<Type.WithTypeName.Enum<*>>()
            .map {
                val classname = it.typeName as ClassName
                val enumSpec = TypeSpec.enumBuilder(classname)
                it.elements.forEach {
                    enumSpec.addEnumConstant(
                        it.toString().safePropName(),
                        TypeSpec.anonymousClassBuilder()
                            .addAnnotation(
                                AnnotationSpec.builder(SerialName::class)
                                    .addMember("%S", it.toString()).build()
                            ).build()
                    )
                }
                FileSpec.builder(classname)
                    .addType(enumSpec.build())
                    .build()
            }
    }

    private fun Type.WithTypeName.Object.makeDataClassProps(typeStore: TypeStore, name: String, type: Type): DataClassProp {
        val isRequired = name in requiredProps
        val defaultValue = defaultValues[name].run {
            if (type is Type.WithTypeName.Enum<*> && this is String) "${(type.typeName as ClassName).simpleName}.${safePropName()}"
            else this
        }

        val actualType = if (type is Type.Reference) typeStore.resolveReference(type.jsonReference) else type
        val isNullable = actualType.makeTypeName().isNullable

        val finalType =
            if (isRequired) {
                actualType.makeTypeName()
            } else {
                if (!isNullable && defaultValue == null) {
                    actualType.makeTypeName().copy(nullable = true)
                } else actualType.makeTypeName()
            }

        val default = if (!isRequired) {
            if (defaultValue != null) {
                // Optional and non-null default is provided. Set that default.
                actualType.defaultValue(defaultValue)
            } else {
                // Optional and no default value provided, or default of null.
                CodeBlock.of("null")
            }
        } else defaultValue?.let { actualType.defaultValue(it) }

        val prop = PropertySpec.builder(name, finalType).initializer(name)
        val param = ParameterSpec.builder(name, finalType).defaultValue(default)

        return DataClassProp(prop.build(), param.build(), name)
    }

    internal fun Type.defaultValue(default: Any): CodeBlock {
        return when (this.makeTypeName().copy(nullable = false)) {
            String::class.asTypeName() -> CodeBlock.of("%S", default)
            Type.WithTypeName.Enum::class.asTypeName() -> CodeBlock.of("enumval")
            JsonPrimitive::class.asTypeName() -> default.makeDefaultPrimitive()!!.makeCodeBlock()
            List::class.asTypeName().parameterizedBy(String::class.asTypeName()) -> {
                (default as? List<String>)?.let {
                    CodeBlock.of(
                        "listOf(" + it.joinToString(", ") { "%S" } + ")",
                        args = it.toTypedArray()
                    )
                } ?: (default as List<*>).let {
                        CodeBlock.of(
                            "listOf(" + it.joinToString(", ") { "%L" } + ")",
                            args = it.toTypedArray()
                        )
                    }
            }
            else -> CodeBlock.of("%L", default)
        }
    }

    private fun interfaceMappingForOneOf(typeStore: TypeStore): Map<Type, List<Type.WithTypeName.OneOf>> {
        return typeStore
            .getTypes()
            .values
            .asSequence()
            .filterIsInstance<Type.WithTypeName.OneOf>().flatMap { oneOf ->
                oneOf.childrenMapping.keys.map { it to oneOf}
            }.groupBy { it.first
            }.mapValues { interfaceData ->
                interfaceData.value.flatMap { interfaceData.value }.map { it.second }
            }.toMap()
    }

    private fun customPolymorphicSerializer(superType: Type.WithTypeName.OneOf): Pair<ClassName, TypeSpec> {
        val superClassName = (superType.typeName as ClassName)
        val serializerName = ClassName(
            packageName = superClassName.packageName,
            superClassName.simpleName + "PolymorphicSerializer"
        )

        val chooseSerializerCode = CodeBlock.builder()
            .beginControlFlow("return when (element.%M[%S]?.%M?.content)",
                MemberName("kotlinx.serialization.json", "jsonObject"),
                superType.discriminator,
                MemberName("kotlinx.serialization.json", "jsonPrimitive"))
            .run {
                superType.childrenMapping.entries.fold(this) { codeBlockBuilder, (subType, discriminatorValue) ->
                    codeBlockBuilder.addStatement("%S -> %T.serializer()", discriminatorValue, subType.makeTypeName())
                }
            }
            .addStatement("else -> error(%S)", "Unknown discriminator value!")
            .endControlFlow()
            .build()

        val function = FunSpec.builder("selectDeserializer")
            .addModifiers(KModifier.OVERRIDE)
            .addParameter("element", JsonElement::class)
            .returns(DeserializationStrategy::class.asTypeName().parameterizedBy(superClassName))
            .addCode(chooseSerializerCode)
            .build()

        return serializerName to TypeSpec.classBuilder(serializerName)
            .superclass(JsonContentPolymorphicSerializer::class.asClassName().parameterizedBy(superClassName))
            .addSuperclassConstructorParameter("%T::class", superClassName)
            .addFunction(function)
            .build()
    }

    private fun <T: Type> T.makeTypeName(): TypeName {
        return when (this) {
            is Type.WithTypeName -> typeName as ClassName
            is Type.Reference -> typeStore.resolveReference(jsonReference).makeTypeName()
            is Type.List -> List::class.asTypeName().parameterizedBy(itemsType.makeTypeName())
            is Type.SimpleType -> this.kotlinType
            else -> { error("What type is this? $this") }
        }
    }

    /*private fun generateOptionalType(): FileSpec {
        val className = ClassName("com.dshatz.openapi2ktor.support", "Opt")
        val fileSpec = FileSpec.builder(className)
        val typeParam = TypeVariableName("T")
        val typeSpec = TypeSpec.classBuilder("Opt")
            .addTypeVariable(typeParam)
            .addType(
                TypeSpec.classBuilder("None")
                    .addTypeVariable(typeParam)
                    .superclass(className.parameterizedBy(typeParam))
                    .build()
            )
            .addType(
                TypeSpec.classBuilder("Data")
                    .addTypeVariable(typeParam)
                    .superclass(className.parameterizedBy(typeParam))
                    .addModifiers(KModifier.DATA)
                    .addProperty(PropertySpec.builder("data", typeParam).initializer("data").build())
                    .primaryConstructor(FunSpec.constructorBuilder().addParameter(
                        ParameterSpec.builder("data", typeParam).build()
                    ).build())
                    .build()
            )
            .addFunction(
                FunSpec.builder("hasData")
                    .returns(Boolean::class.asTypeName())
                    .addCode(CodeBlock.of("return this is Data"))
                    .build()
            )
            .addFunction(
                FunSpec.builder("getOrNull")
                    .returns(typeParam.copy(nullable = true))
                    .addCode(CodeBlock.of("return (this as? Data)?.data"))
                    .build()
            )
            .addModifiers(KModifier.SEALED)

        return fileSpec.addType(typeSpec.build()).build()
    }*/
}