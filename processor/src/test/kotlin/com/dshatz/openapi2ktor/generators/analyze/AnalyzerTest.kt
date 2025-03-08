package com.dshatz.openapi2ktor.generators.analyze

import com.dshatz.openapi2ktor.BaseTestClass
import com.dshatz.openapi2ktor.generators.ObjectAssertScope
import com.dshatz.openapi2ktor.generators.Type
import com.dshatz.openapi2ktor.generators.Type.Companion.simpleType
import com.dshatz.openapi2ktor.generators.TypeAssertScope
import com.dshatz.openapi2ktor.generators.TypeStore
import com.dshatz.openapi2ktor.generators.TypeStore.PathId
import com.dshatz.openapi2ktor.utils.*
import com.reprezen.kaizen.oasparser.model3.Operation
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.asClassName
import com.squareup.kotlinpoet.asTypeName
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.reflect.KClass
import kotlin.test.*

class AnalyzerTest: BaseTestClass() {

    private lateinit var typeStore: TypeStore
    private lateinit var analyzer: OpenApiAnalyzer
    private val packages = Packages("com.example")
    @BeforeTest
    override fun init() {
        super.init()
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
        assertCanResolve("#/components/schemas/webhook-config-url") {
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
                    assertReferenceToComponent("User")
                }
                assertOneOfType("AdminUser") {
                    assertReferenceToComponent("AdminUser")
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
        analyzer.processPathResponse(op, response, "/users", statusCode = 201, wrapPrimitives = true)
        assertGenerated("GetUsersResponse201", packages.models + ".paths.users.get.response") {
            assertPrimitiveWrapper {
                assertArray {
                    assertOneOf {
                        assertOneOfType("normal") {
                            assertReferenceToComponent("User")
                        }
                        assertOneOfType("admin") {
                            assertReferenceToComponent("AdminUser")
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
        analyzer.processPathResponse(op, response, "/users", 400, verb = "post")
        assertGenerated("PostUsersResponse400", packages.models + ".paths.users.post.response") {
            assertObject {
                // allOf
                assertUser()
                assertProp("error", String::class.type())
                assertProp("path") {
                    assertEnum("/" to "SLASH", "/docs" to "SLASHDOCS")
                    assertNullable()
                }
                assertProp("allowed_actions") {
                    assertReferenceToComponent("allowed-actions")
                }
                assertProp("webhook-url") {
                    assertReferenceToComponent("webhook-config-url")
                }
            }
        }
    }

    @Test
    fun `path response with object and no props`() = runTest {
        val op = api.paths["/orders"]!!.get
        val response = op.responses["400"]!!
        analyzer.processPathResponse(op, response, "/orders", statusCode = 400, wrapPrimitives = true)
        assertGenerated("GetOrdersResponse400", packages.models + ".paths.orders.get.response") {
            // No props, so it's going to be a JsonObject.
            // But to use it in client we need it to be a class so wrapper will be made.
            assertPrimitiveWrapper {
                assertType(Type.SimpleType(JsonObject::class.asTypeName()))
            }
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
        analyzer.processPathResponse(op, response, "/users", 204, verb = "post")
//        typeStore.printTypes()
        assertGenerated("PostUsersResponse204", packages.models + ".paths.users.post.response") {}
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
                                assertReferenceToComponent("webhook-config-insecure-ssl")
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

    @Test
    fun `response schema with ref`() = runTest {
        val op = api.paths["/users"]!!.get
        val response = op.responses["205"]!!
        analyzer.processPathResponse(op, response, "/users", 205, verb = "get", wrapPrimitives = true)
        assertGenerated("GetUsersResponse205", packages.models + ".paths.users.get.response") {
            assertPrimitiveWrapper {
                assertReferenceToComponent("User")
            }
        }
    }

    @Test
    fun `wrapped reference in response schema`() = runTest {
        val op = api.paths["/users/{name}"]!!.get
        val response = op.responses["403"]!!
        processComponent("basic-error")
        analyzer.processPathResponse(op, response, "/users/{name}", statusCode = 403, wrapPrimitives = true)
        analyzer.processPathResponse(op, response, "/users/{name}", statusCode = 400, wrapPrimitives = true)
        analyzer.processPathResponse(op, response, "/users/{name}", statusCode = 401, wrapPrimitives = true)
        analyzer.processResponseComponents(api.responses)
        assertGenerated("GetUsersByNameResponse403", packages.models + ".paths.users.byName.get.response") {
            assertPrimitiveWrapper {
                assertReferenceToComponent("error_response", type = "responses", typeStore = typeStore) {
                    assertObject {  }
                }
            }
        }
        assertGenerated("GetUsersByNameResponse400", packages.models + ".paths.users.byName.get.response") {
            assertPrimitiveWrapper {
                assertReferenceToResponse("bad_request", typeStore) {
                    assertReferenceToSchema("basic-error", typeStore) {
                        assertObject {  }
                    }
                }
            }
        }
        assertGenerated("GetUsersByNameResponse401", packages.models + ".paths.users.byName.get.response") {
            assertPrimitiveWrapper {
                assertReferenceToSchema("basic-error", typeStore) {
                    assertObject {  }
                }
            }
        }
    }

    @Test
    fun `response mapping`() = runTest {
        val op = api.paths["/orders"]!!.get
        val response = op.responses["400"]!!
        analyzer.processPathResponse(op, response, "/orders", 400, verb = "get", wrapPrimitives = true)
        val mapping = typeStore.getResponseMapping(PathId("/orders", "get"))
        assertNotEquals(0, mapping.size)
        val type400 = mapping[400]?.type
        assertIs<Type.WithTypeName.PrimitiveWrapper>(type400)
        assertGenerated(type400.simpleName(), type400.packageName()) {

        }
    }

    @Test
    fun `no wrapping`() = runTest {
        val op = api.paths["/orders"]!!.get
        val response = op.responses["405"]!!
        analyzer.processPathResponse(op, response, "/orders", 405, verb = "get", wrapPrimitives = false).join()
        assertCanResolve("#/paths/orders/get/responses/405") {
            assertArray {
                assertReferenceToComponent("OrderList")
            }
        }
    }

    @Test
    fun `with wrapping`() = runTest {
        val op = api.paths["/orders"]!!.get
        val response = op.responses["405"]!!
        analyzer.processPathResponse(op, response, "/orders", 405, verb = "get", wrapPrimitives = true).join()
        assertGenerated("GetOrdersResponse405", pkg = packages.models + ".paths.orders.get.response") {
            assertPrimitiveWrapper {
                assertArray {
                    assertReferenceToComponent("OrderList")
                }
            }
        }
    }

    @Test
    fun `parameter calculation`() = runTest {
        val op = api.paths["/users"]!!.get
        val pathId = PathId("/users", "get")
        analyzer.processComponent("webhook-config-url", api.schemas["webhook-config-url"]!!)
        analyzer.calculateParameters(pathId, op)
        assertCanResolve("#/paths/users/get/parameters/0") {
            assertType(Int::class.type())
        }
        assertCanResolve("#/paths/users/get/parameters/1") {
            assertType(Int::class.type())
        }
        assertCanResolve("#/paths/users/get/parameters/2") {
            assertReferenceToComponent("webhook-config-url")
        }

        pathId.assertParameterRegistered("limit") {
            assertType(Int::class.type())
        }.also {
            assertEquals(TypeStore.OperationParam.ParamLocation.QUERY, it.where)
            assertTrue(it.isRequired)
        }
        pathId.assertParameterRegistered("config") {
            assertReferenceToComponent("webhook-config-url")
        }.also {
            assertEquals(TypeStore.OperationParam.ParamLocation.QUERY, it.where)
            assertFalse(it.isRequired)
        }
        pathId.assertParameterRegistered("min_age") {
            assertType(Int::class.type())
        }.also {
            assertEquals(TypeStore.OperationParam.ParamLocation.QUERY, it.where)
            assertFalse(it.isRequired)
        }
    }

    @Test
    fun `parameter component`() = runTest   {
        val op = api.paths["/orders"]!!.get
        val pathId = PathId("/orders", "get")
        analyzer.calculateParameters(pathId, op)
        assertGenerated("UserTypeParam", packages.models + ".components.parameters") {}
    }

    @Test
    fun `one of with one type nullable should be nullable`() = runTest {
        processComponent("User")
        processComponent("AdminUser")
        processComponent("UserOrAdmin")
        assertGenerated("UserOrAdmin", packages.models + ".components.schemas.UserOrAdmin") {
            assertOneOf {
                assertNullable(true)
                assertOneOfType("User") {
                    assertReferenceToComponent("User", typeStore) {
                        assertNullable(true)
                    }
                }
                assertOneOfType("AdminUser") {
                    assertReferenceToComponent("AdminUser", typeStore) {
                        assertNullable(false)
                    }
                }
            }
        }
    }

    @Test
    fun `enum with an empty '' value`() = runTest {
        val pathId = PathId("/users", "get")
        val op = pathId.getOperation()
        analyzer.calculateParameters(pathId, op)
        assertGenerated("Per", packages.models + ".components.parameters") {
            assertEnum("" to "EMPTY", "day" to "DAY", "week" to "WEEK")
        }
    }

    @Test
    fun `multiple responses with one a reference to responses component`() = runTest {
        val pathId = PathId("/users/{name}", "get")
        val op = pathId.getOperation()
        analyzer.processPath(pathId, op)
        processComponent("basic-error")
        assertEquals(ClassName("com.example.models.paths.users.byName.get.response", "IGetUsersByNameResponseError"),
            typeStore.getResponseErrorInterface(pathId))
        assertGenerated("GetUsersByNameResponse400", packages.models + ".paths.users.byName.get.response") {  }
    }

    @Test
    fun `nested reference in single response`() = runTest {
        val pathId = PathId("/users/by-id/{id}", "get")
        val op = pathId.getOperation()
        analyzer.processResponseComponents(api.responses)
        analyzer.processPath(pathId, op)
        val response400Type = typeStore.getResponseMapping(pathId)[400]?.type
        assertIs<Type.WithTypeName.PrimitiveWrapper>(response400Type)
        assertGenerated(response400Type.simpleName(), response400Type.packageName()) {
            assertPrimitiveWrapper {
                assertReferenceToResponse("bad_request", typeStore) {

                }
            }
        }
    }


    private fun PathId.getOperation(): Operation {
        return api.paths[pathString]!!.getOperation(verb)
    }

    private suspend fun processComponent(name: String) {
        analyzer.processComponent(name, api.schemas[name]!!)
    }

    private fun assertGenerated(
        name: String,
        pkg: String = packages.models,
        block: TypeAssertScope.() -> Unit
    ): Type {
        val type = typeStore.getTypes().values.find { it.simpleName() == name && it.packageName() == pkg } ?: run {
            fail("Component $name not found in package $pkg\n${typeStore.printableSummary()}")
        }
        TypeAssertScope(type).block()
        return type
    }

    private fun String.assertComponentSchemaGenerated(
        name: String,
        block: (TypeAssertScope).() -> Unit
    ): Type {
        val type = typeStore.getTypes()[this.cleanJsonReference()] ?: fail("No component $name found for jsonReference ${this.cleanJsonReference()}")
        TypeAssertScope(type).block()
        return type
    }

    private fun assertCanResolve(
        reference: String,
        block: (TypeAssertScope).() -> Unit
    ): Type {
        val type = typeStore.getTypes()[reference] ?: fail("No reference registered $reference")
        TypeAssertScope(type).block()
        return type
    }

    private fun PathId.assertParameterRegistered(
        name: String,
        block: (TypeAssertScope).() -> Unit
    ): TypeStore.OperationParam {
        val param = typeStore.getParamsForOperation(this).find { it.name == name } ?: fail("Parameter $name not found in $this.")
        TypeAssertScope(param.type).block()
        return param
    }


    private fun KClass<*>.type(nullable: Boolean = false): Type.SimpleType {
        return this.asClassName().simpleType(nullable)
    }


}