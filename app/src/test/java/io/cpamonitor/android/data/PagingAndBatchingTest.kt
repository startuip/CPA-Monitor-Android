package io.cpamonitor.android.data

import io.cpamonitor.android.domain.Account
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PagingAndBatchingTest {
    @Test
    fun quotaRequestsNeverExceedBackendLimit() {
        val batches = quotaBatches((1..451).toList())
        assertEquals(listOf(200, 200, 51), batches.map { it.size })
    }

    @Test
    fun eventCursorUsesTimestampAndIdTogether() {
        val first = eventPageRequest(null, null)
        assertEquals(50, first.limit)
        assertNull(first.beforeMs)
        assertNull(first.beforeId)

        val next = eventPageRequest(123_000, 456)
        assertEquals(123_000L, next.beforeMs)
        assertEquals(456L, next.beforeId)
    }

    @Test
    fun unsupportedQuotaProvidersDoNotPoisonTheBatch() {
        val accounts = listOf(
            account("codex"),
            account("future-provider"),
            account("XAI"),
        )

        assertEquals(listOf("codex", "XAI"), quotaEligibleAccounts(accounts).map { it.provider })
    }

    @Test
    fun quotaProviderAliasesMatchCpampNormalization() {
        val accounts = listOf(account("x_ai"), account("x-ai"), account("grok"), account("gemini-cli"))

        assertEquals(listOf("x_ai", "x-ai", "grok"), quotaEligibleAccounts(accounts).map { it.provider })
    }

    private fun account(provider: String) = Account(
        rowKey = provider,
        name = provider,
        provider = provider,
        authIndex = "",
        accountId = "",
        projectId = "",
        disabled = false,
        source = "",
    )
}
