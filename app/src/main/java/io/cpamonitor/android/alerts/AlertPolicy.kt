package io.cpamonitor.android.alerts

import io.cpamonitor.android.data.local.AlertStateEntity

data class AlertDecision(val notify: Boolean, val next: AlertStateEntity)

object AlertPolicy {
    fun evaluate(
        key: String,
        active: Boolean,
        previous: AlertStateEntity?,
        nowMs: Long,
        cooldownMs: Long = Long.MAX_VALUE,
        failureCount: Int = previous?.failureCount ?: 0,
    ): AlertDecision {
        val wasActive = previous?.active == true
        val cooldownElapsed = previous == null || nowMs - previous.lastNotifiedAtMs >= cooldownMs
        val notify = active && (!wasActive || cooldownElapsed)
        return AlertDecision(
            notify = notify,
            next = AlertStateEntity(
                alertKey = key,
                active = active,
                lastNotifiedAtMs = if (notify) nowMs else previous?.lastNotifiedAtMs ?: 0,
                failureCount = failureCount,
            ),
        )
    }
}

