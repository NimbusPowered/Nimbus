package dev.nimbuspowered.nimbus.api

import io.ktor.http.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Stability checks for the [ApiError] enum. These tests act as a change-detector:
 * renaming an entry or altering its wire code is a breaking API change and must
 * fail here loudly.
 */
class ApiErrorTest {

    @Test
    fun `enum name matches wire code for every entry`() {
        val mismatches = ApiError.entries.filter { it.name != it.code }
        assertTrue(
            mismatches.isEmpty(),
            "enum name must equal wire code, but found: ${mismatches.map { "${it.name}=${it.code}" }}"
        )
    }

    @Test
    fun `wire codes are unique across the enum`() {
        val codes = ApiError.entries.map { it.code }
        assertEquals(codes.size, codes.toSet().size, "duplicate wire codes: $codes")
    }

    @Test
    fun `apiError with enum produces matching failed ApiMessage`() {
        val msg = apiError("service missing", ApiError.SERVICE_NOT_FOUND)
        assertFalse(msg.success)
        assertEquals("service missing", msg.message)
        assertEquals("SERVICE_NOT_FOUND", msg.error)
    }

    @Test
    fun `representative default HTTP statuses are correct`() {
        assertEquals(HttpStatusCode.Unauthorized, ApiError.AUTH_FAILED.defaultStatus)
        assertEquals(HttpStatusCode.Forbidden, ApiError.FORBIDDEN.defaultStatus)
        assertEquals(HttpStatusCode.NotFound, ApiError.SERVICE_NOT_FOUND.defaultStatus)
        assertEquals(HttpStatusCode.Conflict, ApiError.GROUP_ALREADY_EXISTS.defaultStatus)
        assertEquals(HttpStatusCode.BadRequest, ApiError.VALIDATION_FAILED.defaultStatus)
        assertEquals(HttpStatusCode.InternalServerError, ApiError.INTERNAL_ERROR.defaultStatus)
        assertEquals(HttpStatusCode.ServiceUnavailable, ApiError.SERVICE_UNAVAILABLE.defaultStatus)
        assertEquals(HttpStatusCode.PayloadTooLarge, ApiError.PAYLOAD_TOO_LARGE.defaultStatus)
        assertEquals(HttpStatusCode.TooManyRequests, ApiError.AUTH_RATE_LIMITED.defaultStatus)
        assertEquals(HttpStatusCode.UnprocessableEntity, ApiError.BACKUP_VERIFICATION_FAILED.defaultStatus)
        assertEquals(HttpStatusCode.Gone, ApiError.AUTH_LOGIN_CHALLENGE_EXPIRED.defaultStatus)
        assertEquals(HttpStatusCode.FailedDependency, ApiError.CURSEFORGE_API_KEY_MISSING.defaultStatus)
    }

    @Test
    fun `toString returns wire code for logging convenience`() {
        assertEquals("SERVICE_NOT_FOUND", ApiError.SERVICE_NOT_FOUND.toString())
    }
}
