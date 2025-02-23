package com.dshatz.openapi2ktor.generators.models

import com.dshatz.openapi2ktor.generators.Type
import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.utils.makeCodeBlock
import com.dshatz.openapi2ktor.utils.makeDefaultPrimitive
import com.dshatz.openapi2ktor.utils.safePropName
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.serialization.*
import kotlinx.serialization.json.*

class KotlinxCodeGenerator(private val typeStore: TypeStore): IModelGenerator {
    override fun generate(): List<FileSpec> {
        val objectSpecs = generateObjects()
        val superInterfaceSpecs = generateSuperInterfacesForOneOf(typeStore)
        val aliases = generateTypeAliases(typeStore)
        val enums = generateEnums(typeStore)
        return objectSpecs + superInterfaceSpecs + aliases + enums
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

    private fun resolveReference(typeStore: TypeStore, jsonReference: String): Type {
        return typeStore.getTypes()[jsonReference] ?:
        error("Could not resolve reference $jsonReference")
    }

    private fun Type.WithTypeName.Object.makeDataClassProps(typeStore: TypeStore, name: String, type: Type): DataClassProp {
        val isRequired = name in requiredProps
        val defaultValue = defaultValues[name].run {
            if (type is Type.WithTypeName.Enum<*> && this is String) "${(type.typeName as ClassName).simpleName}.${safePropName()}"
            else this
        }

        val actualType = if (type is Type.Reference) resolveReference(typeStore, type.jsonReference) else type
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
            is Type.Reference -> resolveReference(typeStore, jsonReference).makeTypeName()
            is Type.List -> List::class.asTypeName().parameterizedBy(itemsType.makeTypeName())
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