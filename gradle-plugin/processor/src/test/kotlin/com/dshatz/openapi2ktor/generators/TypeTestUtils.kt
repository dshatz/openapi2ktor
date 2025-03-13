package com.dshatz.openapi2ktor.generators

import com.dshatz.openapi2ktor.generators.Type.Companion.simpleType
import com.squareup.kotlinpoet.asClassName
import kotlin.reflect.KClass
import kotlin.test.*


@DslMarker
annotation class TypeScopeMarker

@DslMarker
annotation class ObjectScopeMarker

fun KClass<*>.type(nullable: Boolean = false): Type.SimpleType {
    return this.asClassName().simpleType(nullable)
}

@TypeScopeMarker
internal data class TypeAssertScope(val currentType: Type) {
    fun assertType(type: Type) {
        assertEquals(type, currentType)
    }

    fun assertObject(objectScope: ObjectAssertScope.() -> Unit): Type.WithTypeName.Object {
        assertIs<Type.WithTypeName.Object>(currentType)
        ObjectAssertScope(currentType).objectScope()
        return currentType
    }

    fun assertPrimitiveWrapper(block: TypeAssertScope.() -> Unit): Type.WithTypeName.PrimitiveWrapper {
        assertIs<Type.WithTypeName.PrimitiveWrapper>(currentType)
        TypeAssertScope(currentType.wrappedType).block()
        return currentType
    }

    fun assertReferenceToComponent(schemaName: String, typeStore: TypeStore? = null, type: String = "schemas", block: TypeAssertScope.() -> Unit = {}): String {
        assertIs<Type.Reference>(currentType, message = "Actual type $currentType")
        assertEquals("#/components/$type/$schemaName", currentType.jsonReference)
        typeStore?.resolveReference(currentType.jsonReference)?.let {
            TypeAssertScope(it).block()
        }
        return currentType.jsonReference
    }

    fun assertReferenceToSchema(schemaName: String, typeStore: TypeStore? = null, block: TypeAssertScope.() -> Unit = {}) {
        assertReferenceToComponent(schemaName, typeStore, "schemas", block)
    }

    fun assertReferenceToResponse(schemaName: String, typeStore: TypeStore? = null, block: TypeAssertScope.() -> Unit) {
        assertReferenceToComponent(schemaName, typeStore, "responses", block)
    }

    fun Type.check(block: TypeAssertScope.() -> Unit) = TypeAssertScope(this).block()

    fun assertArray(itemTypeScope: TypeAssertScope.() -> Unit) {
        assertIs<Type.List>(currentType, message = "Not an Array")
        val itemType = currentType.itemsType
        itemTypeScope(TypeAssertScope(itemType))
    }


    fun assertOneOf(block: OneOfAssertScope.() -> Unit) {
        assertIs<Type.WithTypeName.OneOf>(currentType, message = "Not a oneOf!")
        OneOfAssertScope(currentType).block()
    }

    fun assertAlias(block: TypeAssertScope.() -> Unit) {
        assertIs<Type.WithTypeName.Alias>(currentType, message = "Not an alias!")
        TypeAssertScope(currentType.aliasTarget).block()
    }

    /**
     * Assert that this is an enum.
     * @param items pairs of (value, name) where name is to be used as enum entry identifier and value as serialname.
     */
    fun assertEnum(vararg items: Pair<String, String>) {
        assertIs<Type.WithTypeName.Enum<String>>(currentType)
        assertEquals(items.toSet(), currentType.elements.entries.map { it.key to it.value }.toSet())
    }

    fun assertNullable(nullable: Boolean = true) {
        if (currentType is Type.WithTypeName) {
            if (nullable)
                assertTrue(currentType.typeName.isNullable, "Expected to be nullable: $currentType")
            else
                assertFalse(currentType.typeName.isNullable, "Expected to be non-nullable: $currentType")
        } else {
            fail("Type $currentType does not contain nullability information.")
        }
    }
}

internal data class OneOfAssertScope(val oneOf: Type.WithTypeName.OneOf) {
    fun assertOneOfType(discriminatorValue: String, block: TypeAssertScope.() -> Unit) {
        val entry = oneOf.childrenMapping.entries.find { it.value == discriminatorValue}?.key ?: fail("No such oneOf entry: $discriminatorValue")
        TypeAssertScope(entry).block()
    }

    fun assertDiscriminatorKey(key: String) = assertEquals(key, oneOf.discriminator)
}

@ObjectScopeMarker
internal data class ObjectAssertScope(val obj: Type.WithTypeName.Object) {
    fun assertProp(
        name: String,
        block: TypeAssertScope.() -> Unit
    ) {
        val prop = obj.props[name] ?: fail("Prop $name not found in object ${obj.simpleName()}")
        block(TypeAssertScope(prop.type))
    }

    fun assertProp(
        name: String,
        type: Type
    ) {
        assertProp(name) {
            assertType(type)
        }
    }
}


