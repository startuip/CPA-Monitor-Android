package io.cpamonitor.android.alerts

import io.cpamonitor.android.data.local.AlertStateEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPolicyTest {
    @Test
    fun activeConditionNotifiesOnceAndRecoveryRearms() {
        val first = AlertPolicy.evaluate("quota", true, null, 1_000)
        assertTrue(first.notify)
        val duplicate = AlertPolicy.evaluate("quota", true, first.next, 2_000)
        assertFalse(duplicate.notify)
        val recovered = AlertPolicy.evaluate("quota", false, duplicate.next, 3_000)
        assertFalse(recovered.notify)
        val nextCycle = AlertPolicy.evaluate("quota", true, recovered.next, 4_000)
        assertTrue(nextCycle.notify)
    }

    @Test
    fun cooldownAllowsFailureReminderAfterSixHours() {
        val previous = AlertStateEntity("failure", true, 1_000)
        assertFalse(AlertPolicy.evaluate("failure", true, previous, 2_000, 6 * 60 * 60_000L).notify)
        assertTrue(AlertPolicy.evaluate("failure", true, previous, 1_000 + 6 * 60 * 60_000L, 6 * 60 * 60_000L).notify)
    }
}

