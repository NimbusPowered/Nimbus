package dev.nimbuspowered.nimbus.api

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Compatibility check for the deprecated [ApiErrors] facade. Ensures that the
 * wire codes behind the legacy `const val` entries stay stable until 0.14.0,
 * when the facade is removed.
 */
@Suppress("DEPRECATION")
class ApiErrorsTest {

    @Test
    fun `apiError produces a failed ApiMessage with matching code`() {
        val msg = apiError("service missing", ApiErrors.SERVICE_NOT_FOUND)
        assertFalse(msg.success)
        assertEquals("service missing", msg.message)
        assertEquals("SERVICE_NOT_FOUND", msg.error)
    }

    @Test
    fun `legacy error codes are stable strings`() {
        assertEquals("AUTH_FAILED", ApiErrors.AUTH_FAILED)
        assertEquals("UNAUTHORIZED", ApiErrors.UNAUTHORIZED)
        assertEquals("NOT_FOUND", ApiErrors.NOT_FOUND)
        assertEquals("VALIDATION_FAILED", ApiErrors.VALIDATION_FAILED)
        assertEquals("SERVICE_NOT_FOUND", ApiErrors.SERVICE_NOT_FOUND)
        assertEquals("GROUP_NOT_FOUND", ApiErrors.GROUP_NOT_FOUND)
        assertEquals("DEDICATED_ALREADY_RUNNING", ApiErrors.DEDICATED_ALREADY_RUNNING)
        assertEquals("PATH_TRAVERSAL", ApiErrors.PATH_TRAVERSAL)
        assertEquals("PUNISHMENT_NOT_FOUND", ApiErrors.PUNISHMENT_NOT_FOUND)
        assertEquals("RESOURCE_PACK_NOT_FOUND", ApiErrors.RESOURCE_PACK_NOT_FOUND)
    }

    @Test
    fun `INVALID_INPUT legacy wire string is preserved during deprecation window`() {
        // All in-tree call-sites were explicitly migrated to ApiError.VALIDATION_FAILED.
        // The legacy wire string is kept intact so any out-of-tree caller still
        // switching on "INVALID_INPUT" keeps working until the facade is removed in 0.14.
        assertEquals("INVALID_INPUT", ApiErrors.INVALID_INPUT)
    }
}
