package com.dshatz.openapi2ktor.generators.models

import com.dshatz.openapi2ktor.generators.Type
import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.utils.makeCodeBlock
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import javax.lang.model.element.ExecutableElement

class KotlinxCodeGenerator: IModelGenerator {
    @OptIn(ExperimentalSerializationApi::class)
    override fun generate(typeStore: TypeStore): List<FileSpec> {
        val typeToSuperInterfaces = interfaceMappingForOneOf(typeStore)
        val polymorphicSerializers = typeStore.getTypes().values
            .filterIsInstance<Type.OneOf>()
            .associate { oneOf ->
                oneOf.typeName to customPolymorphicSerializer(oneOf)
            }
        val objectSpecs = typeStore.getTypes().values.filterIsInstance<Type.WithProps>().map { type ->
            val className = type.typeName as ClassName
            val fileSpec = FileSpec.builder(className)

            val interfacesForOneOf = typeToSuperInterfaces[type]
            val typeSpecBuilder = TypeSpec.classBuilder(className)

            val propsParams = type.props.entries.map { makeDataClassProps(it.key, it.value) }.toMutableList()

            if (!interfacesForOneOf.isNullOrEmpty()) {
                typeSpecBuilder.addSuperinterfaces(interfacesForOneOf.map { it.typeName })
            }

            val constructorBuilder = FunSpec.constructorBuilder()
            propsParams.forEach { (prop, param) ->
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

        val superInterfaceSpecs = polymorphicSerializers.map { (superType, serializer) ->
            val serializerClass = serializer.first
            val serializerSpec = serializer.second
            val className = superType as ClassName
            val fileSpec = FileSpec.builder(className)
            val typeSpec = TypeSpec.interfaceBuilder(className)
                .addAnnotation(AnnotationSpec.builder(Serializable::class).addMember("%T::class", serializerClass).build())
                .addModifiers(KModifier.SEALED)
            fileSpec.addType(typeSpec.build())
            fileSpec.addType(serializerSpec)
            fileSpec.build()
        }
        return objectSpecs + superInterfaceSpecs
    }


    private fun makeDataClassProps(name: String, type: Type): Pair<PropertySpec, ParameterSpec> {
        val safeName = name.safePropName()
        val prop = PropertySpec.builder(safeName, type.typeName).run {
            if (safeName != name)
                addAnnotation(AnnotationSpec.builder(SerialName::class).addMember("%S", name).build())
            else this
        }.initializer(safeName).build()

        val param = ParameterSpec.builder(safeName, type.typeName).run {
            if (type is Type.SimpleType && type.default != null) {
                defaultValue(type.defaultValue())
            } else this
        }.build()
        return prop to param
    }

    private fun Type.SimpleType.defaultValue(): CodeBlock {
        return when (this.typeName) {
            String::class.asTypeName() -> CodeBlock.of("%S", default)
            JsonPrimitive::class.asTypeName() -> (default as JsonPrimitive).makeCodeBlock()
            else -> CodeBlock.of("%L", default)
        }
    }

    private fun interfaceMappingForOneOf(typeStore: TypeStore): Map<Type, List<Type.OneOf>> {
        return typeStore
            .getTypes()
            .values
            .asSequence()
            .filterIsInstance<Type.OneOf>().flatMap { oneOf ->
                oneOf.childrenMapping.keys.map { it to oneOf}
            }.groupBy { it.first
            }.mapValues { interfaceData ->
                interfaceData.value.flatMap { interfaceData.value }.map { it.second }
            }.toMap()
    }

    private fun customPolymorphicSerializer(superType: Type.OneOf): Pair<ClassName, TypeSpec> {
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
                    codeBlockBuilder.addStatement("%S -> %T.serializer()", discriminatorValue, subType.typeName)
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
}