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

class KotlinxCodeGenerator(override val typeStore: TypeStore, private val packages: Packages): IModelGenerator {

    internal lateinit var responseMappings: ResponseInterfaceResult

    override fun generate(): List<FileSpec> {

        responseMappings = generateResponseInterfaces()

        val objectSpecs = generateObjects()
        val superInterfaceSpecs = generateSuperInterfacesForOneOf(typeStore)
//        val exceptionWrappers = generateExceptionWrappers()
        val aliases = generateTypeAliases(typeStore)
        val wrappers = generatePrimitiveWrappers()
        val enums = generateEnums(typeStore)
        return objectSpecs + superInterfaceSpecs + aliases + enums + responseMappings.files + wrappers
    }

    private fun generatePrimitiveWrapper(wrapper: Type.WithTypeName.PrimitiveWrapper, superClass: TypeName? = null): FileSpec {
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
                PropertySpec.builder(WRAPPER_PROP_NAME, wrappedType)
                    .addModifiers(KModifier.OVERRIDE)
                    .initializer(WRAPPER_PROP_NAME).build()
            )
            .primaryConstructor(
                FunSpec.constructorBuilder().addParameter(
                    ParameterSpec.builder(WRAPPER_PROP_NAME, wrappedType).build()
                ).build()
            )
            .apply {
                superClass?.let(::superclass)
            }
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
                    .addCode("encoder.encodeSerializableValue(%M(), value.%L)", serializerMember, WRAPPER_PROP_NAME)
                    .build()
            ).build()
        return fileSpec.addType(typeSpec).addType(serializer).build()
    }

    private fun generatePrimitiveWrappers(): List<FileSpec> {
        return typeStore.getTypes().values.filterIsInstance<Type.WithTypeName.PrimitiveWrapper>()
            .map {
                val superclass = responseMappings.responseSuperclasses[it]
                val exception = typeStore.shouldExtendException(it)
                generatePrimitiveWrapper(it, superclass ?: (Exception::class.asTypeName().takeIf { exception }))
            }
    }

    internal fun generateObjects(): List<FileSpec> {
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
                typeSpecBuilder.addSuperinterfaces(interfacesForOneOf.map { it.typeName.copy(nullable = false) })
            }

            responseMappings.responseSuperclasses[type]?.let {
                typeSpecBuilder.superclass(it.copy(nullable = false))
            }

            val exception = typeStore.shouldExtendException(type)
            val constructorBuilder = FunSpec.constructorBuilder()
            uniqueProps.forEach { (prop, param) ->
                constructorBuilder.addParameter(param)
                typeSpecBuilder.addProperty(prop)
            }

            typeSpecBuilder
                .primaryConstructor(constructorBuilder.build())
                .addModifiers(KModifier.DATA)
                .apply {
                    type.description?.let { addKdoc(type.description.toCodeBlock(::findConcreteType)) }
                    if (exception) {
                        superclass(Exception::class)
                    }
                }
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
                oneOf to customPolymorphicSerializer(oneOf)
            }


        return polymorphicSerializers.map { (superType, serializer) ->
            val serializerClass = serializer.first
            val serializerSpec = serializer.second
            val className = superType.typeName as ClassName
            val fileSpec = FileSpec.builder(className)
            val typeSpec = TypeSpec.interfaceBuilder(className)
                .addAnnotation(AnnotationSpec.builder(Serializable::class).addMember("%T::class", serializerClass).build())
                .apply {
                    superType.description?.let { addKdoc(it.toCodeBlock(::findConcreteType)) }
                }
            fileSpec.addType(typeSpec.build())
            fileSpec.addType(serializerSpec)
            fileSpec.build()
        }
    }

    private fun generatePrimitiveResponseWrappers(types: Collection<TypeStore.ResponseTypeInfo>, superInterface: TypeName): Map<Type, FileSpec> {
        val wrappers = types.associateWithNotNull { (type, jsonReference) ->
            val wrapper = type as? Type.WithTypeName.PrimitiveWrapper
            wrapper
        }.mapKeys { it.key.type }
        return wrappers.mapValues { (type, wrapper) ->
            generatePrimitiveWrapper(wrapper, superInterface)
        }
    }

    private fun generateResponseInterfaces(): ResponseInterfaceResult {
        val results = typeStore.getAllResponseTypes().map { pathId -> // e.g.GetUsers
            val responseTypeInfos = typeStore.getResponseMapping(pathId)
            val successTypes = responseTypeInfos.filterKeys { it.isSuccessCode() }
            val otherTypes = responseTypeInfos.filterKeys { !it.isSuccessCode() }

            val iResponseClass = typeStore.getResponseSuccessInterface(pathId)
            val iErrorClass = typeStore.getResponseErrorInterface(pathId)

            val iResponse = iResponseClass?.let {
                TypeSpec.classBuilder(iResponseClass).addModifiers(KModifier.SEALED).build()
            }
            val iError = iErrorClass?.let {
                TypeSpec.classBuilder(iErrorClass).addModifiers(KModifier.SEALED).superclass(Exception::class).build()
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
                first = listOfNotNull(successFile, errorFile),
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

    internal data class ResponseInterfaceResult(val files: List<FileSpec>, val responseSuperclasses: Map<Type, ClassName?>)

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
                it.elements.forEach { (value, entryName) ->
                    enumSpec.addEnumConstant(
                        entryName,
                        TypeSpec.anonymousClassBuilder()
                            .addAnnotation(
                                AnnotationSpec.builder(SerialName::class)
                                    .addMember("%S", value.toString()).build()
                            ).build()
                    )
                }
                FileSpec.builder(classname)
                    .addType(enumSpec.build())
                    .build()
            }
    }

    private fun Type.WithTypeName.Object.makeDataClassProps(
        typeStore: TypeStore,
        name: String,
        propInfo: Type.WithTypeName.Object.PropInfo
    ): DataClassProp {
        val type = propInfo.type
        val isRequired = name in requiredProps
        val defaultValue = defaultValues[name].run {
            if (type is Type.WithTypeName.Enum<*> && this is String) {
                if (this != "null") {
                    "${(type.typeName as ClassName).simpleName}.${type.elements[this]}"
                } else {
                    "null"
                }
            }
            else this
        }

        val actualType = if (type is Type.Reference) typeStore.resolveReference(type.jsonReference) else type
        val isNullable = actualType.makeTypeName().isNullable


        val default = actualType.makeDefaultValueCodeBlock(isRequired, defaultValue)
        val finalType = actualType.nullableIfNoDefault(isRequired, default)

        val prop = PropertySpec.builder(name, finalType)
            .apply {
                if (propInfo.doc != null) {
                    addKdoc(propInfo.doc.toCodeBlock(::findConcreteType))
                }
            }
            .initializer(name)
        val param = ParameterSpec.builder(name, finalType).defaultValue(default)

        return DataClassProp(prop.build(), param.build(), name)
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
        val superClassName = (superType.typeName.copy(nullable = false) as ClassName)
        val serializerName = ClassName(
            packageName = superClassName.packageName,
            superClassName.simpleName + "PolymorphicSerializer"
        )

        val anyNullable = superType.childrenMapping.entries.any { it.key.makeTypeName().isNullable }
        val chooseSerializerCode = CodeBlock.builder()
            .beginControlFlow("return when (element.%M[%S]?.%M?.content)",
                MemberName("kotlinx.serialization.json", "jsonObject"),
                superType.discriminator,
                MemberName("kotlinx.serialization.json", "jsonPrimitive"))
            .run {
                superType.childrenMapping.entries.fold(this) { codeBlockBuilder, (subType, discriminatorValue) ->
                    codeBlockBuilder.addStatement("%S -> %T.serializer()", discriminatorValue, subType.makeTypeName().copy(nullable = false))
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

    companion object {
        const val WRAPPER_PROP_NAME = "data"
    }
}