# About

This project aims to generate easy-to-use and portable Ktor clients from OpenApi V3 yaml specifications.

# Installation

## 1. Add gradle plugin
**libs.version.toml**
```toml
openapi = { id = "com.dshatz.openapi2ktor", version = "1.0.0" }
```

**build.gradle.kts**
```kotlin
plugins {
  // your kotlin plugin - jvm or multiplatform
  alias(libs.plugins.openapi)

  // If you are not using version catalog
  id("com.dshatz.openapi2ktor") version "1.0.0"
}
```

## 2. Configure what to generate
```kotlin
openapi {
  generators {
     // Give your api a name.
    create("binance") {
      inputSpec.set(file("openapi/binance.yaml")) // This should be a module-relative path.
    }
  }
}
```

## 3. Generate
A gradle task will be added under group `openapi3` with the following name:
`generate<name>Clients`. 

In the above example, it is `generateBinanceClients`.

This task will be automatically executed when there are any changes to the input spec file.

## Additional configuration
You can customize some of the behaviour of the generators. Please open an issue if you need additional configuration.

### Additional properties
Some openapi spec files you find on the internet are not exactly complete. In case the API gives you an object with an unknown field, it will be dropped.
[Kotlinx Serialization does not support this]([url](https://github.com/Kotlin/kotlinx.serialization/issues/1978)) out of the box, but this plugin gives you a possibility to not lose those fields.

**To enable this on per-url basis:**

```kotlin
openapi {
  generators {
     // Give your api a name.
    create("binance") {
      inputSpec.set(file("openapi/binance.yaml")) // This should be a module-relative path.
      config {
        parseUnknownProps {
          all() // To parse additional properties on all urls.
          urlIs("/your/url")
          urlStartsWith("/your-url-prefix")
          regex("[your url regex]")
        }
      }
    }
  }
}
```

Note: this may have a performance penalty so only enable on urls which are known to misalign with the API spec.

# Usage
**Note**: It is recommended to inspect `module/build/openapi/<name>/client` directory to know what clients are available generated. 

## Create client
The generated client constructor's signature is identical to that of ktor's `HttpClient` except that it also takes an optional `baseUrl` argument.
You can access the baseUrls through `<name>.client.Servers` enum or pass a custom `String`.

```kotlin
val apiClient = V3Client(engine = CIO)
val apiClientStaging = V3Client(engine = CIO, baseUrl = "https://staging.example.com")
val apiClientProduction = V3Client(engine = CIO, baseUrl = Servers.PRODUCTION)
```

## Authentication
| Scheme | Supported |
| ------ | --------- |
| Bearer | ✅        |
| Api key| ✅        |
| Basic  | ✅        |
| OAuth  | ❌        |

To apply your authentication information, use the method that starts with `set` in on your client instance. The exact name depends on the `securityScheme` name in the ymal file.

## Making calls
Read this to know how to call your API.

### Return type
**Note**: All methods are suspending.
The return type of all methods is always `HttpResult<D, E: Exception>`.

Both `D` and `E` are Kotlin classes representing success and error response models respectively.
If the YAML specifies multiple possible response models (depending on HTTP status code), `D` and `E` will be sealed classes.

*Using Binance as an example.*

```kotlin
val client = V3Client(CIO)
val response: HttpResult<GetApiV3AvgPriceResponse, GetApiV3AvgPriceResponse400> = client.getAvgPrice("BTCEUR")
when (response) {
  is GetApiV3AvgPriceResponse -> {
    println("BTC price: ${response.price} EUR")
  }
  is GetApiV3AvgPriceResponse400 -> {
    println("Status code 400 error: ${response.data.code}")
  }
}
```

### Exceptions
The generated code may throw a couple of errors:
 - `UnknownSuccessCodeError` if API returned a success code that is not described in the YAML. The `body: String` property will have the original body.
 - `io.ktor.client.plugins.ClientRequestException` if API returned a 4xx code that is not described in the YAML.
 - `RedirectResponseException` for 3xx, and `ServerResponseException` for 5xx - see https://ktor.io/docs/client-response-validation.html
### Helpers
**dataOrNull**
Returns the success response object or null.
```kotlin
val response: HttpResult<GetApiV3AvgPriceResponse, GetApiV3AvgPriceResponse400> = client.getAvgPrice("BTCEUR")
val data: GetApiV3AvgPriceResponse? = response.dataOrNull()
```

**dataOrThrow**
Returns the success response or throws the error response object as an exception.
```kotlin
val response: HttpResult<GetApiV3AvgPriceResponse, GetApiV3AvgPriceResponse400> = client.getAvgPrice("BTCEUR")
try {
  response.dataOrThrow()
} catch (e: GetApiV3AvgPriceResponse400) {
  println("Error occured: ${e.data.code}")
}
```
## Supported OpenAPI3 features
|    Feature    |   Supported   |   Notes   |
| ------------- | ------------- | --------- |
|  type: number |    ✅         | generates either float or int |
|  type: boolean|    ✅         | generates Boolean |
|  type: array  |    ✅         | generates List<T> and T from `items` schema |
| type: object  |  ✅           | generates a kotlin data class |
|  oneOf multiple objects       | ✅            | generates a sealed class      |
|  oneOf objects and primitives | ❌            | becomes a JsonElement |
|  oneOf primitives | ❌            | becomes a JsonPrimitive |
|       allOf   | ✅            | generates a summary kotlin data class with fields from all of the subtypes |
|       enums   | ✅            |  enum class  |
|     anyOf     |  ❌           | becomes a JsonElement |
|  description  | ✅            | generates simple KDocs  |

# Status
Give it a try and let me know how it goes. I still expect some OpenAPI specs not to compile, so please attach those if this happens.
