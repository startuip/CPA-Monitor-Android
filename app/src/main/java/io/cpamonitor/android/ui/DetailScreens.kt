@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.cpamonitor.android.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.cpamonitor.android.data.local.UserSettings
import io.cpamonitor.android.data.remote.EventDto
import io.cpamonitor.android.data.remote.ModelPriceDto
import io.cpamonitor.android.data.remote.QuotaAccountDto
import io.cpamonitor.android.data.remote.QuotaWindowDto
import io.cpamonitor.android.domain.Account
import io.cpamonitor.android.domain.planDisplayLabel
import io.cpamonitor.android.ui.theme.GlanceColors
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt

private val DetailPagePadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp)

@Composable
fun RequestsScreen(
    state: MonitorUiState,
    padding: PaddingValues,
    onReload: () -> Unit,
    onLoadMore: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = DetailPagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("请求明细", 0, "逐条查看模型、延迟与失败原因") }
        item {
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                    modifier = Modifier.size(38.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f), MaterialTheme.shapes.extraSmall),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text("共 ${compact(state.eventsTotal)} 条 · 每页 50 条", fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.failedOnly) "当前仅显示失败请求" else "按时间倒序展示",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalIconButton(onClick = onReload, enabled = !state.eventsLoading) {
                    Icon(Icons.Outlined.Refresh, "刷新")
                }
            }
        }
        if (state.offline) item { OfflineBanner(state.analyticsUpdatedAt) }
        if (state.events.isEmpty() && !state.eventsLoading) item { EmptyCard("没有请求记录", "调整用量页筛选或时间范围后重试") }
        itemsIndexed(state.events, key = { index, item -> "${item.eventHash}:$index" }) { _, event ->
            EventCard(event, state.prices[event.model])
        }
        if (state.eventsLoading) item { LoadingBlock() }
        if (state.eventsHasMore && !state.eventsLoading) item {
            OutlinedButton(
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(Icons.Outlined.ExpandMore, null)
                Text("加载下一页", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun EventCard(event: EventDto, price: ModelPriceDto?) {
    val account = event.account.ifBlank { event.label.ifBlank { event.authIndex.ifBlank { "未知账号" } } }
    val cost = price?.let {
        (event.inputTokens * it.prompt + event.outputTokens * it.completion + event.cachedTokens * it.cache) / 1_000_000.0
    }
    ElevatedCard(
        Modifier.fillMaxWidth().then(
            if (event.failed) Modifier.border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f), MaterialTheme.shapes.medium) else Modifier,
        ),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(5.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (event.failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.size(38.dp).border(
                        1.dp,
                        (if (event.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary).copy(alpha = 0.40f),
                        MaterialTheme.shapes.extraSmall,
                    ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (event.failed) Icons.Outlined.ErrorOutline else Icons.Outlined.Check,
                            if (event.failed) "失败" else "成功",
                            tint = if (event.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(start = 11.dp)) {
                    Text(event.model, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("$account · ${event.provider}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(time(event.timestampMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventMetric("TOKEN", compact(event.totalTokens), Modifier.weight(1f))
                EventMetric("费用", cost?.let(::currency) ?: "—", Modifier.weight(1f))
                EventMetric("延迟", event.latencyMs?.let { "${it}ms" } ?: "—", Modifier.weight(1f))
            }
            Row(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small).padding(horizontal = 11.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Route, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                Text(
                    requestTarget(event.method, event.endpoint, event.path),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 7.dp),
                )
            }
            if (event.failed) {
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.40f), MaterialTheme.shapes.small).padding(11.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(event.statusCode?.toString() ?: "失败", color = MaterialTheme.colorScheme.onErrorContainer, fontWeight = FontWeight.Bold)
                    Text(sanitizeSummary(event.failSummary), color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun EventMetric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

internal fun requestTarget(method: String, endpoint: String, path: String): String {
    val cleanMethod = method.trim()
    val target = endpoint.ifBlank { path }.trim()
    if (cleanMethod.isBlank()) return target
    if (target.startsWith("$cleanMethod ", ignoreCase = true)) return target
    return listOf(cleanMethod, target).filter(String::isNotBlank).joinToString(" ")
}

@Composable
fun AccountsScreen(state: MonitorUiState, padding: PaddingValues) {
    val bundle = state.accounts
    val quotas = bundle?.quotas?.associateBy { it.rowKey }.orEmpty()
    val grouped = bundle?.accounts?.groupBy { it.provider }?.toSortedMap().orEmpty()
    val enabledCount = bundle?.accounts?.count { !it.disabled } ?: 0
    val snapshotCount = bundle?.quotas?.count { it.windows.isNotEmpty() } ?: 0
    val quotaEligibleRows = bundle?.quotaEligibleRows.orEmpty()
    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = DetailPagePadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("账号与配额", state.accountsUpdatedAt, "追踪账号健康和配额窗口") }
        if (state.offline) item { OfflineBanner(state.accountsUpdatedAt) }
        if (bundle != null) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MetricCard("账号总数", compact(bundle.accounts.size.toLong()), Modifier.weight(1f), "$enabledCount 个已启用", Icons.Outlined.Group, GlanceColors.Blue)
                    MetricCard("配额快照", compact(snapshotCount.toLong()), Modifier.weight(1f), "有可用窗口的账号", Icons.Outlined.DataUsage, GlanceColors.Green)
                }
            }
        }
        if (bundle == null && (state.loading || state.refreshing)) {
            item { LoadingBlock() }
        } else if (grouped.isEmpty()) {
            item { EmptyCard("没有账号", "CPAMP Auth Files 暂未返回账号") }
        }
        grouped.forEach { (provider, accounts) ->
            item { ProviderHeading(provider, accounts.size) }
            items(accounts, key = { it.rowKey }) { account ->
                val quota = quotas[account.rowKey]
                val planLabel = accountPlanLabel(account, quota)
                val stats = state.analytics?.accountStats?.firstOrNull {
                    it.account == account.name || account.authIndex in it.authIndices
                }
                ElevatedCard(
                    Modifier.fillMaxWidth().then(if (state.focusedAccountRow == account.rowKey) Modifier.border(2.dp, GlanceColors.Blue, MaterialTheme.shapes.medium) else Modifier),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    elevation = CardDefaults.elevatedCardElevation(5.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.size(42.dp).border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.38f), MaterialTheme.shapes.extraSmall),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(provider.take(1).uppercase(), color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Bold)
                                }
                            }
                            Column(Modifier.weight(1f).padding(start = 11.dp)) {
                                Text(account.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PlanBadge(planLabel)
                                    if (account.source.isNotBlank()) {
                                        Text(
                                            account.source,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(start = 7.dp).weight(1f),
                                        )
                                    }
                                }
                            }
                            StatusBadge(if (account.disabled) "已禁用" else "已启用", error = account.disabled)
                        }
                        if (stats != null) {
                            Row(
                                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLowest, MaterialTheme.shapes.small)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small).padding(11.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                AccountStat("调用", compact(stats.calls))
                                AccountStat("Token", compact(stats.totalTokens))
                                AccountStat("成功率", percent(stats.successRate))
                            }
                        }
                        if (quota == null || quota.windows.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val eligible = account.rowKey in quotaEligibleRows
                                Icon(
                                    if (eligible) Icons.Outlined.Schedule else Icons.Outlined.Info,
                                    null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    if (eligible) "CPAMP 尚无可用快照，请先运行配额检查" else "此 Provider 暂不支持配额快照",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 7.dp),
                                )
                            }
                        } else {
                            quota.windows.forEachIndexed { index, window ->
                                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                                QuotaWindow(window)
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun accountPlanLabel(account: Account, quota: QuotaAccountDto?): String {
    val snapshotPlans = quota?.windows.orEmpty()
        .sortedByDescending { it.observedAtMs }
        .map { it.planType.trim() }
        .filter(String::isNotBlank)
    val rawPlan = snapshotPlans.firstOrNull { !it.equals("unknown", ignoreCase = true) }
        ?: account.planType.takeIf { it.isNotBlank() && !it.equals("unknown", ignoreCase = true) }
        ?: snapshotPlans.firstOrNull()
        ?: account.planType
    return planDisplayLabel(account.provider, rawPlan)
}

@Composable
private fun PlanBadge(label: String) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
    ) {
        Text(
            "套餐 · $label",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 112.dp).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun ProviderHeading(provider: String, count: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("PROVIDER / ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        Text(provider.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleMedium)
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.padding(start = 8.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
        ) {
            Text(count.toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun AccountStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun QuotaWindow(window: QuotaWindowDto) {
    val remaining = (window.remainingPercent ?: window.usedPercent?.let { 100.0 - it })?.coerceIn(0.0, 100.0)
    val isLow = window.stale || remaining != null && remaining <= 20
    val accent = when {
        remaining != null && remaining <= 0 -> MaterialTheme.colorScheme.error
        isLow -> GlanceColors.Amber
        else -> MaterialTheme.colorScheme.primary
    }
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(window.kind.ifBlank { window.id }, fontWeight = FontWeight.Medium)
            Text(
                when {
                    window.stale -> "数据已过期"
                    remaining == null -> "剩余未知"
                    remaining <= 0 -> "已耗尽"
                    else -> "剩余 ${remaining.roundToInt()}%"
                },
                color = accent,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        remaining?.let {
            LinearProgressIndicator(
                progress = { (it / 100).toFloat() },
                modifier = Modifier.fillMaxWidth().height(7.dp),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceContainer,
                drawStopIndicator = {},
            )
        }
        val usage = if (window.usedValue != null && window.limitValue != null) "${decimal(window.usedValue)} / ${decimal(window.limitValue)} ${window.unit}" else ""
        val reset = window.cycleEndMs?.let(::countdown).orEmpty()
        if (usage.isNotBlank() || reset.isNotBlank()) {
            Text(listOf(usage, reset).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    baseUrl: String,
    settings: UserSettings,
    onSettings: (UserSettings) -> Unit,
    onClearCache: () -> Unit,
    onDisconnect: () -> Unit,
    requestNotificationPermission: () -> Unit,
) {
    var disconnectDialog by rememberSaveable { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = DetailPagePadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("设置", 0, "连接、安全与提醒偏好") }
        item {
            SectionCard("服务器连接", "当前 CPAMP 实例") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                        modifier = Modifier.size(42.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.38f), MaterialTheme.shapes.extraSmall),
                    ) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.CloudDone, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                    Column(Modifier.weight(1f).padding(start = 11.dp)) {
                        Text(baseUrl, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("已安全连接", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.small).padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.EnhancedEncryption, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Text("Admin Key 已使用 Android Keystore 加密", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = { disconnectDialog = true }, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Outlined.LinkOff, null, modifier = Modifier.size(19.dp))
                    Text("断开连接并清除数据", modifier = Modifier.padding(start = 7.dp))
                }
            }
        }
        item {
            SectionCard("前台刷新", "离开前台后自动停止") {
                SettingRow(Icons.Outlined.Sync, "刷新频率", "当前每 ${settings.refreshSeconds} 秒")
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 30, 60, 120).forEach { seconds ->
                        FilterChip(
                            selected = settings.refreshSeconds == seconds,
                            onClick = { onSettings(settings.copy(refreshSeconds = seconds)) },
                            label = { Text("${seconds}秒") },
                        )
                    }
                }
            }
        }
        item {
            SectionCard("后台提醒", "约每 15 分钟机会性检查") {
                SettingSwitch(Icons.Outlined.NotificationsActive, "启用提醒", "允许 CPA Monitor 发送告警", settings.alertsEnabled) { enabled ->
                    if (enabled && Build.VERSION.SDK_INT >= 33) requestNotificationPermission()
                    onSettings(settings.copy(alertsEnabled = enabled))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                SettingSwitch(Icons.Outlined.DataUsage, "配额提醒", "账号额度过低或耗尽", settings.quotaAlerts) { onSettings(settings.copy(quotaAlerts = it)) }
                SettingSwitch(Icons.Outlined.ErrorOutline, "失败率提醒", "30 分钟失败率异常", settings.failureAlerts) { onSettings(settings.copy(failureAlerts = it)) }
                SettingSwitch(Icons.Outlined.CloudOff, "连接与采集器", "连接中断或采集错误", settings.connectionAlerts) { onSettings(settings.copy(connectionAlerts = it)) }
                Text("配额剩余阈值", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 30, 50).forEach { threshold ->
                        FilterChip(
                            selected = settings.quotaThreshold == threshold,
                            onClick = { onSettings(settings.copy(quotaThreshold = threshold)) },
                            label = { Text("$threshold%") },
                        )
                    }
                }
            }
        }
        item {
            SectionCard("本地数据", "缓存与隐私") {
                SettingRow(Icons.Outlined.Storage, "离线缓存", "聚合和配额加密后保存在 Room；请求明细仅驻留内存")
                OutlinedButton(onClick = onClearCache, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Outlined.CleaningServices, null, modifier = Modifier.size(19.dp))
                    Text("清除缓存", modifier = Modifier.padding(start = 7.dp))
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("CPA Monitor", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("0.1.0 · 只读客户端 · CPAMP v1.12.6+", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (disconnectDialog) {
        AlertDialog(
            onDismissRequest = { disconnectDialog = false },
            icon = { Icon(Icons.Outlined.LinkOff, null) },
            title = { Text("断开连接？") },
            text = { Text("这会删除加密凭据、离线缓存和提醒状态，且无法撤销。") },
            confirmButton = {
                Button(
                    onClick = { disconnectDialog = false; onDisconnect() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("断开并清除") }
            },
            dismissButton = { TextButton(onClick = { disconnectDialog = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingIcon(icon)
        Column(Modifier.weight(1f).padding(start = 11.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingSwitch(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SettingIcon(icon)
        Column(Modifier.weight(1f).padding(start = 11.dp, end = 8.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

@Composable
private fun SettingIcon(icon: ImageVector) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.size(38.dp).border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.30f), MaterialTheme.shapes.extraSmall),
    ) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
    }
}

private fun countdown(timestampMs: Long): String {
    val duration = Duration.between(Instant.now(), Instant.ofEpochMilli(timestampMs))
    if (duration.isNegative || duration.isZero) return "即将重置"
    val days = duration.toDays()
    val hours = duration.minusDays(days).toHours()
    val minutes = duration.minusDays(days).minusHours(hours).toMinutes()
    return when {
        days > 0 -> "${days}天${hours}小时后重置"
        hours > 0 -> "${hours}小时${minutes}分钟后重置"
        else -> "${minutes}分钟后重置"
    }
}
