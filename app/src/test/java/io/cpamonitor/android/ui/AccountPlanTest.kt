package io.cpamonitor.android.ui

import io.cpamonitor.android.data.remote.QuotaAccountDto
import io.cpamonitor.android.data.remote.QuotaWindowDto
import io.cpamonitor.android.domain.Account
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountPlanTest {
    private val account = Account(
        rowKey = "row-1",
        name = "账号",
        provider = "codex",
        authIndex = "auth-1",
        accountId = "acct-1",
        projectId = "",
        disabled = false,
        source = "codex.json",
        planType = "plus",
    )

    @Test
    fun authFilePlanIsShownWithoutSnapshotPlan() {
        assertEquals("Plus", accountPlanLabel(account, null))
    }

    @Test
    fun newestSnapshotPlanOverridesAuthFilePlan() {
        val quota = QuotaAccountDto(
            rowKey = account.rowKey,
            provider = "codex",
            windows = listOf(
                QuotaWindowDto(observedAtMs = 100, planType = "free"),
                QuotaWindowDto(observedAtMs = 200, planType = "pro"),
            ),
        )

        assertEquals("Pro 20x", accountPlanLabel(account, quota))
    }

    @Test
    fun unknownSnapshotPlanDoesNotHideKnownAuthFilePlan() {
        val quota = QuotaAccountDto(
            rowKey = account.rowKey,
            provider = "codex",
            windows = listOf(QuotaWindowDto(observedAtMs = 300, planType = "unknown")),
        )

        assertEquals("Plus", accountPlanLabel(account, quota))
    }
}
