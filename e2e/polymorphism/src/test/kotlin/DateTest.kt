import kotlinx.datetime.*
import kotlinx.serialization.json.*
import sample.models.components.schemas.AdminUser.AdminUser
import sample.models.components.schemas.User.User
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate as LocalDateKotlinx
import sampleKotlinxDatetime.models.components.schemas.AdminUser.AdminUser as AdminUserKotlinx
import sampleKotlinxDatetime.models.components.schemas.User.User as UserKotlinx

class DateTest {

    @Test
    fun `java LocalDate`() {
        val user = User(
            name = "Bob",
            userType = "normal",
            registeredOn = LocalDate.of(2025, 12, 31),
        )

        val encoded = Json.encodeToJsonElement(user)
        assertEquals(LocalDate.of(2025, 12, 31).toString(), encoded.jsonObject.get("registered_on")?.jsonPrimitive?.content)
    }

    @Test
    fun `java Instant`() {
        val instant = Instant.now()
        val admin = AdminUser(registeredOn = instant)
        val encoded = Json.encodeToJsonElement(admin)
        assertEquals(
            instant.toString(), encoded.jsonObject.get("registered_on")?.jsonPrimitive?.content
        )
    }

    @Test
    fun `java Instant with timezone`() {
        val instant = Instant.now()
        val zoned = instant.atOffset(ZoneOffset.ofHours(3))
        val input = buildJsonObject {
            // Pass to json as 2025-02-02T10:00:00+0300
            put("registered_on", zoned.format(DateTimeFormatter.ISO_DATE_TIME))
        }
        val output = Json.decodeFromJsonElement<AdminUser>(input)
        println(output)
        // Decoded as UTC instant.
        assertEquals(output.registeredOn, zoned.withOffsetSameInstant(ZoneOffset.UTC).toInstant())
    }

    @Test
    fun `kotlinx LocalDateTime`() {
        val user = UserKotlinx(
            name = "Bob",
            userType = "normal",
            registeredOn = LocalDateKotlinx(2025, 12, 31),
        )

        val encoded = Json.encodeToJsonElement(user)
        assertEquals(LocalDateKotlinx(2025, 12, 31).toString(), encoded.jsonObject.get("registered_on")?.jsonPrimitive?.content)
    }

    @Test
    fun `kotlinx Instant`() {
        val json = buildJsonObject {
            // Send with offset
            put("registered_on", "2025-02-02T08:00:00+0300")
        }
        val output = Json.decodeFromJsonElement<AdminUserKotlinx>(json)
        println(output)
        // Decoded as UTC instant.
        assertEquals(
            LocalDateTime(2025, 2, 2, 5, 0, 0)
                .toInstant(TimeZone.UTC),
            output.registeredOn
        )
    }

}