package com.dshatz.openapi2ktor.generators

import com.dshatz.openapi2ktor.generators.Type.Companion.simpleType
import com.dshatz.openapi2ktor.utils.*
import com.reprezen.kaizen.oasparser.OpenApiParser
import com.reprezen.kaizen.oasparser.model3.OpenApi3
import com.squareup.kotlinpoet.asClassName
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File
import kotlin.reflect.KClass
import kotlin.test.*

class AnalyzerTest {

    private lateinit var api: OpenApi3
    private lateinit var typeStore: TypeStore
    private lateinit var analyzer: OpenApiAnalyzer
    private val packages = Packages("com.example")
    @BeforeTest
    fun init() {
        api = OpenApiParser().parse(File("../e2e/polymorphism/src/test/resources/sample.yaml")) as OpenApi3
        typeStore = TypeStore()
        analyzer = OpenApiAnalyzer(typeStore, packages)
    }

    private fun ObjectAssertScope.assertUser() {
        assertProp("id", Int::class.type())
        assertProp("name", String::class.type())
        assertProp("user_type", String::class.type())
    }

    private fun ObjectAssertScope.assertAdminUser() {
        assertUser()
        assertProp("beard", Boolean::class.type())
    }

    @Test
    fun `schema simple`() = runTest {
        analyzer.processComponent("User", api.schemas["User"]!!)
        assertGenerated("User", packages.models + ".components.schemas.User") {
            assertObject {
                assertUser()
            }
        }

        assertFails(message = "Should not generate typealiases for fields") {
            assertGenerated("id", packages.models + ".components.schemas.User.properties.id") {}
        }
    }

    @Test
    fun `schema with a primitive type`() = runTest {
        val schema = api.schemas["webhook-config-url"]!!
        analyzer.processComponent("webhook-config-url", schema)
        schema.jsonReference.assertComponentSchemaGenerated("WebhookConfigUrl") {
            assertType(String::class.type(true))
        }
    }

    @Test
    fun `schema with a oneOf of primitives`() = runTest {
        val schema = api.schemas["webhook-config-insecure-ssl"]!!
        analyzer.processComponent("webhook-config-insecure-ssl", schema)
        schema.jsonReference.assertComponentSchemaGenerated("WebhookConfigInsecureSsl") {
            assertType(JsonPrimitive::class.type())
        }
    }

    @Test
    fun `schema with oneOf`() = runTest {
        val schema = api.schemas["UserOrAdmin"]!!
        analyzer.processComponent("UserOrAdmin", schema)
        schema.jsonReference.assertComponentSchemaGenerated("UserOrAdmin") {
            assertOneOf {
                assertOneOfType("User") {
                    assertReferenceToSchema("User")
                }
                assertOneOfType("AdminUser") {
                    assertReferenceToSchema("AdminUser")
                }
                assertDiscriminatorKey("user_type")
            }
        }
    }

    @Test
    fun `primitive schema`() = runTest {
        val schema = api.schemas["webhook-config-insecure-ssl"]!!
        analyzer.processComponent("webhook-config-insecure-ssl", schema)
        assertContains(typeStore.getTypes(), "#/components/schemas/webhook-config-insecure-ssl")
    }

    @Test
    fun `path response with oneOf and custom mapping`() = runTest {
        val op = api.paths["/users"]!!.get
        val response = op.responses["201"]!!
        analyzer.processPathResponse(op, response, "/users", statusCode = "201")
        assertGenerated("GetUsersResponse201", packages.models + ".paths.users.get.response201") {
            assertAlias {
                assertArray {
                    assertOneOf {
                        assertOneOfType("normal") {
                            assertReferenceToSchema("User")
                        }
                        assertOneOfType("admin") {
                            assertReferenceToSchema("AdminUser")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `path response with enum`() = runTest {
        val op = api.paths["/users"]!!.post
        val response = op.responses["400"]!!
        analyzer.processPathResponse(op, response, "/users", "400", verb = "post")
        assertGenerated("PostUsersResponse400", packages.models + ".paths.users.post.response400") {
            assertObject {
                // allOf
                assertUser()
                assertProp("error", String::class.type())
                assertProp("path") {
                    assertEnum("/", "/", "/docs")
                    assertNullable()
                }
                assertProp("allowed_actions") {
                    assertReferenceToSchema("allowed-actions")
                }
                assertProp("webhook-url") {
                    assertReferenceToSchema("webhook-config-url")
                }
            }
        }
    }

    @Test
    fun `path response with object and no props`() = runTest {
        val op = api.paths["/orders"]!!.get
        val response = op.responses["400"]!!
        analyzer.processPathResponse(op, response, "/orders", statusCode = "400")
        assertFails {
            // No props, so not generated.
            assertGenerated("GetOrdersResponse400", packages.models + ".paths.orders.get.response400") {}
        }
    }

    @Test
    fun `schema with allOf`() = runTest {
        val schema = api.schemas["UserAndOrder"]!!
        analyzer.processComponent("UserAndOrder", schema)
        schema.jsonReference.assertComponentSchemaGenerated("UserAndOrder") {
            assertObject {
                assertUser()
                assertProp("orders") {
                    assertArray {
                        assertObject {
                            assertProp("id", Int::class.type())
                            assertProp("amount", Int::class.type())
                            assertProp("params") {
                                assertArray {
                                    assertType(JsonObject::class.type())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `schema with anyOf`() = runTest {
        val op = api.paths["/users"]!!.post
        val response = op.responses["204"]!!
        val userSchema = api.schemas["UserOrAdmin"]!!
        val issueEventSchema = api.schemas["issue-event-for-issue"]!!
        analyzer.processComponent("UserOrAdmin", userSchema)
        analyzer.processComponent("issue-event-for-issue", issueEventSchema)
        analyzer.processPathResponse(op, response, "/users", "204", verb = "post")
//        typeStore.printTypes()
        assertGenerated("PostUsersResponse204", packages.models + ".paths.users.post.response204") {}
        assertContains(typeStore.getTypes(), "#/components/schemas/issue-event-for-issue")
    }

    @Test
    fun `schema with list`() = runTest {
        val schema = api.schemas["OrderList"]!!
        analyzer.processComponent("OrderList", schema)
        schema.jsonReference.assertComponentSchemaGenerated("OrderList") {
            assertObject {
                assertProp("orders") {
                    assertArray {
                        assertObject {
                            assertProp("id", Int::class.type())
                            assertProp("amount", Int::class.type())
                            assertProp("config") {
                                assertReferenceToSchema("webhook-config-insecure-ssl")
                            }
                            assertProp("params") {
                                assertArray {
                                    /**
                                    *  params:
                                    *   type: array
                                    *   items: {}
                                    */
                                    assertType(JsonObject::class.type())
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun assertGenerated(
        name: String,
        pkg: String = packages.models,
        block: TypeAssertScope.() -> Unit
    ): Type {
        val type = typeStore.getTypes().values.find { it.simpleName() == name && it.packageName() == pkg } ?: fail("Component $name not found in package $pkg")
        TypeAssertScope(type).block()
        return type
    }

    private fun String.assertComponentSchemaGenerated(
        name: String,
        block: (TypeAssertScope).() -> Unit
    ): Type {
        val type = typeStore.getTypes()[this.stripFilePathFromRef()] ?: fail("No component $name found for jsonReference ${this.stripFilePathFromRef()}")
        TypeAssertScope(type).block()
        return type
    }


    private fun KClass<*>.type(nullable: Boolean = false): Type.WithTypeName.SimpleType {
        return this.asClassName().simpleType(nullable)
    }


}