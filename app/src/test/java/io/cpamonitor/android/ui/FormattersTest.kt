package io.cpamonitor.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FormattersTest {
    @Test fun formatsTokensAndCosts() {
        assertEquals("1.5K", compact(1_500))
        assertEquals("1.5M", compact(1_500_000))
        assertEquals("$0.0040", currency(0.004))
    }

    @Test fun sanitizesBearerSecrets() {
        val value = sanitizeSummary("upstream rejected Bearer abc.def-secret")
        assertFalse(value.contains("abc.def-secret"))
    }

    @Test fun sanitizesAssignedAndStandaloneSecrets() {
        val value = sanitizeSummary(
            "token=superSecret123 admin_key: cpamp_1234567890 jwt eyJabcdefgh.ijklmnop.qrstuvwx",
        )
        assertFalse(value.contains("superSecret123"))
        assertFalse(value.contains("cpamp_1234567890"))
        assertFalse(value.contains("eyJabcdefgh"))
    }

    @Test fun stripsUnsafeControlAndBidiCharacters() {
        assertEquals("safe text", sanitizeSummary("safe\u202E text"))
    }

    @Test fun requestTargetDoesNotDuplicateMethod() {
        assertEquals("GET /v1/responses", requestTarget("GET", "GET /v1/responses", "/ignored"))
        assertEquals("POST /v1/messages", requestTarget("POST", "", "/v1/messages"))
    }
}
