package io.cpamonitor.android.alerts

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import io.cpamonitor.android.data.MonitorRepository
import io.cpamonitor.android.data.local.AlertStateEntity
import io.cpamonitor.android.data.local.MonitorDao
import io.cpamonitor.android.data.local.PreferencesStore
import io.cpamonitor.android.domain.RangePreset
import io.cpamonitor.android.domain.currentRange
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import retrofit2.HttpException

@HiltWorker
class MonitorWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MonitorRepository,
    private val preferences: PreferencesStore,
    private val dao: MonitorDao,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val settings = preferences.settings.first()
        if (!settings.alertsEnabled || preferences.connection() == null) return Result.success()
        val now = System.currentTimeMillis()
        return try {
            val status = repository.status()
            if (settings.connectionAlerts) {
                updateConnectionState(active = status.collector.lastError.isNotBlank(), immediate = true, now = now)
            }

            if (settings.quotaAlerts) {
                val bundle = repository.accountsAndQuotas(now).value
                val accounts = bundle.accounts.associateBy { it.rowKey }
                bundle.quotas.forEach { quota ->
                    quota.windows.forEach { window ->
                        val remaining = window.remainingPercent ?: window.usedPercent?.let { 100.0 - it }
                        val active = !window.stale && remaining != null && remaining <= settings.quotaThreshold
                        val cycle = window.cycleEndMs ?: 0
                        val key = "quota:${quota.rowKey}:${window.id}:$cycle"
                        val previous = dao.alert(key)
                        val decision = AlertPolicy.evaluate(key, active, previous, now)
                        dao.putAlert(decision.next)
                        if (decision.notify) {
                            val account = accounts[quota.rowKey]?.name ?: quota.accountKey
                            val exhausted = remaining != null && remaining <= 0.0
                            notifications.show(
                                id = key.hashCode(),
                                channel = AlertChannel.QUOTA,
                                title = if (exhausted) "配额已耗尽" else "配额即将用尽",
                                message = "$account · ${window.kind} 剩余 ${remaining?.toInt() ?: 0}%",
                                deepLink = "cpamonitor://open/accounts?row=${Uri.encode(quota.rowKey)}",
                            )
                        }
                    }
                }
            }

            if (settings.failureAlerts) {
                val from = Instant.ofEpochMilli(now - 30 * 60_000L)
                val range = currentRange(RangePreset.CUSTOM, Instant.ofEpochMilli(now), customStart =
                    from.atZone(java.time.ZoneId.systemDefault()).toLocalDate())
                    .copy(fromMs = from.toEpochMilli(), toMs = now)
                val summary = repository.analytics(range).value.summary
                val active = summary != null && summary.totalCalls >= 10 &&
                    summary.failureCalls.toDouble() / summary.totalCalls >= 0.20
                val key = "failure-rate"
                val decision = AlertPolicy.evaluate(
                    key,
                    active,
                    dao.alert(key),
                    now,
                    cooldownMs = 6 * 60 * 60_000L,
                )
                dao.putAlert(decision.next)
                if (decision.notify && summary != null) {
                    val rate = summary.failureCalls * 100 / summary.totalCalls.coerceAtLeast(1)
                    notifications.show(
                        id = key.hashCode(),
                        channel = AlertChannel.FAILURE,
                        title = "请求失败率偏高",
                        message = "最近 30 分钟 ${summary.totalCalls} 次调用，失败率 $rate%",
                        deepLink = "cpamonitor://open/requests?failed=true",
                    )
                }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (preferences.connection() == null) return Result.success()
            val unauthorized = e is HttpException && e.code() in setOf(401, 403)
            if (settings.connectionAlerts) {
                updateConnectionState(active = true, immediate = unauthorized, now = now)
            }
            if (runAttemptCount < 3) Result.retry() else Result.success()
        }
    }

    private suspend fun updateConnectionState(active: Boolean, immediate: Boolean, now: Long) {
        val key = "connection"
        val previous = dao.alert(key)
        val failures = if (active) (previous?.failureCount ?: 0) + 1 else 0
        val alertActive = active && (immediate || failures >= 2)
        val decision = AlertPolicy.evaluate(
            key = key,
            active = alertActive,
            previous = previous,
            nowMs = now,
            cooldownMs = 6 * 60 * 60_000L,
            failureCount = failures,
        )
        dao.putAlert(decision.next)
        if (decision.notify) {
            notifications.show(
                id = key.hashCode(),
                channel = AlertChannel.CONNECTION,
                title = "CPA Monitor 连接异常",
                message = "无法读取服务器或采集器报告错误，请检查连接设置",
                deepLink = "cpamonitor://open/settings",
            )
        }
    }

    companion object {
        private const val UNIQUE_WORK = "cpa-monitor-alert-check"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MonitorWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) = WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK)
    }
}
