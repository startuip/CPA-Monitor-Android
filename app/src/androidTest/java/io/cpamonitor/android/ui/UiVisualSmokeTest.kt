package io.cpamonitor.android.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import io.cpamonitor.android.data.AccountBundle
import io.cpamonitor.android.data.local.UserSettings
import io.cpamonitor.android.data.remote.*
import io.cpamonitor.android.domain.Account
import io.cpamonitor.android.ui.theme.CpaMonitorTheme
import java.io.File
import org.junit.Rule
import org.junit.Test

class UiVisualSmokeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun connectionRenders() {
        render("connection") { ConnectionScreen(ConnectUiState(), { _, _ -> }, {}) }
        compose.onNodeWithText("连接你的 CPAMP").assertIsDisplayed()
        compose.onNodeWithText("验证并连接").assertIsDisplayed()
    }

    @Test fun overviewRendersDenseData() {
        render("overview") { OverviewScreen(fakeState(), PaddingValues()) }
        compose.onNodeWithText("运行总览").assertIsDisplayed()
        compose.onNodeWithText("今日调用").assertIsDisplayed()
    }

    @Test fun usageRendersFiltersAndSummary() {
        render("usage") {
            UsageScreen(fakeState(), PaddingValues(), {}, {}, {}, {}, {}, { _, _ -> })
        }
        compose.onNodeWithText("用量分析").assertIsDisplayed()
        compose.onNodeWithText("总调用").assertIsDisplayed()
    }

    @Test fun requestDetailsRender() {
        render("requests") { RequestsScreen(fakeState(), PaddingValues(), {}, {}) }
        compose.onNodeWithText("请求明细").assertIsDisplayed()
        compose.onNodeWithText("GET /v1/responses").assertIsDisplayed()
    }

    @Test fun accountsAndQuotasRender() {
        render("accounts") { AccountsScreen(fakeState(), PaddingValues()) }
        compose.onNodeWithText("账号与配额").assertIsDisplayed()
        compose.onNodeWithText("主力账号").assertIsDisplayed()
        compose.onNodeWithText("套餐 · Plus").assertIsDisplayed()
    }

    @Test fun accountQuotaEmptyStatesExplainWhatToDo() {
        val codex = Account("codex-row", "Codex 账号", "codex", "auth-1", "acct-1", "", false, "codex.json")
        val unsupported = Account("gemini-row", "Gemini 账号", "gemini-cli", "auth-2", "", "project-1", false, "gemini.json")
        val state = MonitorUiState(
            accounts = AccountBundle(
                accounts = listOf(codex, unsupported),
                quotas = listOf(QuotaAccountDto(rowKey = codex.rowKey, provider = "codex")),
                generatedAtMs = System.currentTimeMillis(),
                quotaEligibleRows = setOf(codex.rowKey),
            ),
        )

        render("quota-empty-states") { AccountsScreen(state, PaddingValues()) }

        compose.onNodeWithText("CPAMP 尚无可用快照，请先运行配额检查").assertIsDisplayed()
        compose.onNodeWithText("此 Provider 暂不支持配额快照").assertIsDisplayed()
    }

    @Test fun settingsRender() {
        render("settings") {
            SettingsScreen(PaddingValues(), "https://cpamp.example.com", UserSettings(), {}, {}, {}, {})
        }
        compose.onNodeWithText("设置").assertIsDisplayed()
        compose.onNodeWithText("服务器连接").assertIsDisplayed()
    }

    @Test fun landscapeChromeRenders() {
        render("landscape") {
            Row(Modifier.fillMaxSize()) {
                GlanceSideRail(Tab.OVERVIEW) {}
                VerticalDivider()
                OverviewScreen(fakeState(), PaddingValues())
            }
        }
        compose.onNodeWithText("总览").assertIsDisplayed()
        compose.onNodeWithText("运行总览").assertIsDisplayed()
    }

    private fun render(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            CpaMonitorTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                    content = content,
                )
            }
        }
        compose.waitForIdle()
        val directory = InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null)!!
        File(directory, "ui-$name.png").outputStream().use {
            compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}

private fun fakeState(): MonitorUiState {
    val now = System.currentTimeMillis()
    val summary = UsageSummaryDto(
        totalCalls = 1_284, successCalls = 1_251, failureCalls = 33, successRate = 0.974,
        inputTokens = 86_000_000, outputTokens = 2_400_000, totalTokens = 88_400_000,
        totalCost = 126.48, averageLatencyMs = 2_830.0,
    )
    val timeline = (0..7).map { index ->
        TimelinePointDto(
            bucketMs = now - (7 - index) * 60 * 60_000L,
            label = "${index + 8}:00",
            calls = listOf(42, 78, 55, 126, 94, 172, 146, 201)[index].toLong(),
            totalTokens = (index + 1) * 1_200_000L,
        )
    }
    val models = listOf(
        ModelStatDto("gpt-5.6-sol", calls = 760, totalTokens = 64_200_000, cost = 92.31, successRate = 0.986),
        ModelStatDto("claude-sonnet-4-5", calls = 348, totalTokens = 19_800_000, cost = 30.86, successRate = 0.951),
        ModelStatDto("gemini-2.5-flash", calls = 176, totalTokens = 4_400_000, cost = 3.31, successRate = 0.972),
    )
    val accountsStats = listOf(
        AccountStatDto("main", account = "主力账号", label = "主力账号", provider = "codex", authIndices = listOf("auth-1"), calls = 824, successCalls = 812, failureCalls = 12, successRate = 0.985, totalTokens = 62_000_000, cost = 88.2),
        AccountStatDto("backup", account = "备用账号", label = "备用账号", provider = "codex", authIndices = listOf("auth-2"), calls = 460, successCalls = 439, failureCalls = 21, successRate = 0.954, totalTokens = 26_400_000, cost = 38.28),
    )
    val accounts = listOf(
        Account("row-1", "主力账号", "codex", "auth-1", "account-1", "", false, "codex-main.json", planType = "plus"),
        Account("row-2", "备用账号", "codex", "auth-2", "account-2", "", false, "codex-backup.json", planType = "pro"),
    )
    return MonitorUiState(
        dashboard = DashboardDto(
            generatedAtMs = now,
            today = summary,
            rolling30m = RollingDto(rpm = 18.6, tpm = 842_000.0, totalCalls = 558, totalTokens = 25_260_000),
            topModels = models,
            traffic = timeline,
            recentFailures = listOf(FailureDto(now - 90_000, "claude-sonnet-4-5", "备用账号", provider = "claude", endpoint = "/v1/messages", durationMs = 18_400, statusCode = 429, summary = "upstream rate limit exceeded")),
        ),
        dashboardUpdatedAt = now,
        analytics = AnalyticsDto(now, summary, timeline, models, accountsStats),
        analyticsUpdatedAt = now,
        accounts = AccountBundle(
            accounts,
            listOf(
                QuotaAccountDto("row-1", "main", "codex", listOf(QuotaWindowDto("5h", "5 小时窗口", observedAtMs = now, cycleEndMs = now + 2 * 60 * 60_000L, remainingPercent = 68.0))),
                QuotaAccountDto("row-2", "backup", "codex", listOf(QuotaWindowDto("week", "每周窗口", observedAtMs = now, cycleEndMs = now + 3 * 24 * 60 * 60_000L, remainingPercent = 16.0))),
            ),
            now,
        ),
        accountsUpdatedAt = now,
        status = StatusDto("cpa-manager-plus", 18_642, 0, CollectorDto("running", "http", "auto", now, now, 18_642, "")),
        events = listOf(
            EventDto("event-1", now - 25_000, "gpt-5.6-sol", "GET /v1/responses", "GET", "/v1/responses", "主力账号", provider = "codex", authIndex = "auth-1", inputTokens = 82_000, outputTokens = 2_400, totalTokens = 84_400, latencyMs = 2_430),
            EventDto("event-2", now - 95_000, "claude-sonnet-4-5", "POST /v1/messages", "POST", "/v1/messages", "备用账号", provider = "claude", authIndex = "auth-2", latencyMs = 18_400, failed = true, statusCode = 429, failSummary = "upstream rate limit exceeded"),
        ),
        eventsTotal = 1_284,
        eventsHasMore = true,
        prices = mapOf("gpt-5.6-sol" to ModelPriceDto(1.25, 10.0, 0.125)),
    )
}
