package io.cpamonitor.android.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UrlNormalizerTest {
    @Test
    fun addsHttpsAndRemovesTrailingSlash() {
        assertEquals("https://cpamp.example.com", normalizeServerUrl("cpamp.example.com/"))
    }

    @Test
    fun preservesExplicitPort() {
        assertEquals("https://cpamp.example.com:18317", normalizeServerUrl(" https://cpamp.example.com:18317 "))
    }

    @Test
    fun rejectsHttpPathsAndCredentials() {
        assertThrows(IllegalArgumentException::class.java) { normalizeServerUrl("http://cpamp.example.com") }
        assertThrows(IllegalArgumentException::class.java) { normalizeServerUrl("https://cpamp.example.com/admin") }
        assertThrows(IllegalArgumentException::class.java) { normalizeServerUrl("https://user:pass@cpamp.example.com") }
    }

    @Test
    fun rejectsUnreasonablyLongInput() {
        assertThrows(IllegalArgumentException::class.java) { normalizeServerUrl("a".repeat(2_049)) }
    }
}
