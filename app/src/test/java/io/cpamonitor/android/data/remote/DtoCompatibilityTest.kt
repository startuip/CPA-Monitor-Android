package io.cpamonitor.android.data.remote

import io.cpamonitor.android.domain.toAccount
import io.cpamonitor.android.domain.planDisplayLabel
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DtoCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun analyticsIgnoresNewFieldsAndUnknownProvider() {
        val payload = """
            {
              "generated_at_ms": 10,
              "future_field": {"anything": true},
              "account_stats": [{
                "id": "future-1",
                "auth_provider_snapshot": "new-provider",
                "calls": 4,
                "new_metric": 99
              }]
            }
        """.trimIndent()
        val result = json.decodeFromString<AnalyticsDto>(payload)
        assertEquals("new-provider", result.accountStats.single().provider)
        assertEquals(4, result.accountStats.single().calls)
    }

    @Test
    fun authFileAcceptsSnakeCamelAndUnknownFields() {
        val account = json.parseToJsonElement(
            """{"name":"a.json","type":"future","auth_index":"idx","accountId":"acct","disabled":false,"new":1}""",
        ).jsonObject.toAccount()
        assertEquals("future", account.provider)
        assertEquals("idx", account.authIndex)
        assertEquals("acct", account.accountId)
        assertFalse(account.disabled)
        assertTrue(account.rowKey.isNotBlank())
    }

    @Test
    fun codexIdentityUsesNestedAccountIdButNeverRuntimeId() {
        val account = json.parseToJsonElement(
            """
            {
              "id":"runtime-selector-7",
              "name":"codex.json",
              "provider":"codex",
              "account":"user@example.com",
              "id_token":{"chatgpt_account_id":"acct-stable-7"}
            }
            """.trimIndent(),
        ).jsonObject.toAccount()

        assertEquals("", account.authIndex)
        assertEquals("acct-stable-7", account.accountId)
        assertEquals("user@example.com", account.accountSnapshot)
        assertEquals("", account.label)
        assertEquals("acct-stable-7", account.quotaTarget().authAccountIdSnapshot)

        val runtimeOnly = json.parseToJsonElement(
            """{"id":"runtime-only","name":"codex.json","provider":"codex","email":"user@example.com"}""",
        ).jsonObject.toAccount()
        assertEquals("", runtimeOnly.authIndex)
        assertEquals("", runtimeOnly.accountId)
    }

    @Test
    fun codexIdentityReadsJwtPayloadAndIgnoresGenericProjectId() {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
            """{"chatgpt_account_id":"acct-jwt"}""".toByteArray(),
        )
        val account = json.parseToJsonElement(
            """
            {
              "name":"codex.json",
              "provider":"codex",
              "project_id":"must-not-be-codex-account",
              "id_token":"e30.$payload.signature",
              "plan_type":"plus"
            }
            """.trimIndent(),
        ).jsonObject.toAccount()

        assertEquals("acct-jwt", account.accountId)
        assertEquals("", account.projectId)
        assertEquals("plus", account.planType)
        assertEquals("Plus", planDisplayLabel(account.provider, account.planType))
    }

    @Test
    fun plansResolveFromNestedTokensAndSubscriptions() {
        val claude = json.parseToJsonElement(
            """{"name":"c.json","provider":"claude","metadata":{"id_token":{"plan_type":"plan_max20"}}}""",
        ).jsonObject.toAccount()
        val antigravity = json.parseToJsonElement(
            """{"name":"a.json","provider":"antigravity","subscription":{"plan":"unknown","tierName":"ultra_lite"}}""",
        ).jsonObject.toAccount()
        val custom = json.parseToJsonElement(
            """{"name":"x.json","provider":"xai","accountType":"Premium Custom"}""",
        ).jsonObject.toAccount()

        assertEquals("plan_max20", claude.planType)
        assertEquals("Max 20x", planDisplayLabel(claude.provider, claude.planType))
        assertEquals("ultra_lite", antigravity.planType)
        assertEquals("Ultra Lite", planDisplayLabel(antigravity.provider, antigravity.planType))
        assertEquals("Premium Custom", custom.planType)
        assertEquals("Premium Custom", planDisplayLabel(custom.provider, custom.planType))
    }

    @Test
    fun authFilesAcceptsAllCpampWrapperShapesAndDirectArrays() {
        val file = """{"name":"a.json","provider":"codex"}"""
        val payloads = listOf(
            "[$file]",
            """{"auth_files":[$file]}""",
            """{"authFiles":[$file]}""",
            """{"files":[$file]}""",
            """{"items":[$file]}""",
            """{"data":{"files":[$file]}}""",
        )

        payloads.forEach { payload ->
            val parsed = parseAuthFiles(json.parseToJsonElement(payload))
            assertEquals(payload, 1, parsed.size)
            assertEquals("a.json", parsed.single()["name"]?.toString()?.trim('"'))
        }
    }

    @Test
    fun malformedAuthFileContainersStayEmpty() {
        assertTrue(parseAuthFiles(json.parseToJsonElement("{}" )).isEmpty())
        assertTrue(parseAuthFiles(JsonArray(emptyList())).isEmpty())
    }

    @Test
    fun missingQuotaFieldsRemainDisplayable() {
        val result = json.decodeFromString<QuotaQueryDto>(
            """{"items":[{"row_key":"x","provider":"mystery","windows":[{"provider_window_id":"w"}]}]}""",
        )
        assertEquals("mystery", result.items.single().provider)
        assertEquals(null, result.items.single().windows.single().remainingPercent)
    }
}
