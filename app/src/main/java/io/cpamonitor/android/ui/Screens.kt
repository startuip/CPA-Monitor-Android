@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.cpamonitor.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.cpamonitor.android.data.remote.TimelinePointDto
import io.cpamonitor.android.domain.RangePreset
import io.cpamonitor.android.ui.theme.GlanceColors
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

private val PagePadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp)

@Composable
fun OverviewScreen(state: MonitorUiState, padding: PaddingValues) {
    val dashboard = state.dashboard
    if (dashboard == null) {
        LazyColumn(modifier = Modifier.padding(padding), contentPadding = PagePadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { ScreenHeader("运行总览", state.dashboardUpdatedAt, "今日服务状态与实时消耗") }
            item { EmptyCard("暂无缓存数据", "连接恢复后下拉刷新") }
        }
        return
    }
    val pager = rememberPagerState(pageCount = { 3 })
    Column(Modifier.fillMaxSize().padding(padding)) {
        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { page ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PagePadding,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ScreenHeader("运行总览", state.dashboardUpdatedAt, "今日服务状态与实时消耗") }
                if (state.offline) item { OfflineBanner(state.dashboardUpdatedAt) }
                when (page) {
                    0 -> {
                        item { OverviewHero(dashboard.today.totalCalls, dashboard.today.totalTokens, dashboard.today.totalCost, dashboard.today.successRate) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                MetricCard("RPM", decimal(dashboard.rolling30m.rpm), Modifier.weight(1f), "近 30 分钟", Icons.Outlined.Speed, GlanceColors.Blue)
                                MetricCard("TPM", compact(dashboard.rolling30m.tpm.toLong()), Modifier.weight(1f), "实时吞吐", Icons.Outlined.Bolt, GlanceColors.Green)
                            }
                        }
                        item { SectionCard("今日流量", "调用次数随时间变化") { if (dashboard.traffic.isEmpty()) InlineEmpty("暂无流量数据") else LineChart(dashboard.traffic) } }
                    }
                    1 -> {
                        item {
                            SectionCard("模型排行", "按今日调用量排序") {
                                if (dashboard.topModels.isEmpty()) InlineEmpty("暂无模型数据") else {
                                    val maxCalls = dashboard.topModels.maxOfOrNull { it.calls }?.coerceAtLeast(1) ?: 1
                                    dashboard.topModels.forEachIndexed { index, model ->
                                        ModelRow(index + 1, model.model, model.calls, model.cost, model.calls.toFloat() / maxCalls)
                                        if (index < dashboard.topModels.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }
                        item { CollectorCard(state) }
                    }
                    else -> {
                        item { SectionHeading("最近失败", "快速定位异常请求") }
                        if (dashboard.recentFailures.isEmpty()) item { EmptyCard("今日没有失败请求", "服务状态良好") }
                        items(dashboard.recentFailures) { failure -> FailureCard(failure) }
                    }
                }
            }
        }
        PagerDots(pageCount = 3, selected = pager.currentPage)
    }
}

@Composable
private fun CollectorCard(state: MonitorUiState) {
    val presentation = collectorPresentation(state)
    val healthy = presentation.kind == CollectorStateKind.RUNNING
    val pending = presentation.kind == CollectorStateKind.LOADING ||
        presentation.kind == CollectorStateKind.STARTING
    val containerColor = when {
        healthy -> MaterialTheme.colorScheme.tertiaryContainer
        pending -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = when {
        healthy -> MaterialTheme.colorScheme.tertiary
        pending -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val icon = when (presentation.kind) {
        CollectorStateKind.RUNNING -> Icons.Outlined.Sensors
        CollectorStateKind.LOADING, CollectorStateKind.STARTING -> Icons.Outlined.Sync
        else -> Icons.Outlined.SensorsOff
    }
    SectionCard("采集器", "CPAMP 请求监控服务") {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = containerColor, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, tint = contentColor) }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(presentation.title, fontWeight = FontWeight.SemiBold)
                Text(
                    presentation.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("${compact(state.status?.events ?: 0)} 条", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

internal enum class CollectorStateKind { LOADING, RUNNING, STARTING, STOPPED, ERROR, UNKNOWN }

internal data class CollectorPresentation(
    val kind: CollectorStateKind,
    val title: String,
    val detail: String,
)

internal fun collectorPresentation(state: MonitorUiState): CollectorPresentation {
    if (state.statusError != null) {
        return CollectorPresentation(
            CollectorStateKind.ERROR,
            "状态读取失败",
            sanitizeSummary(state.statusError),
        )
    }
    if (state.statusLoading && state.status == null) {
        return CollectorPresentation(CollectorStateKind.LOADING, "正在读取状态", "正在连接 CPAMP 监控服务")
    }
    val collector = state.status?.collector
        ?: return CollectorPresentation(CollectorStateKind.UNKNOWN, "尚未读取状态", "下拉刷新以重新读取")
    if (collector.lastError.isNotBlank()) {
        return CollectorPresentation(CollectorStateKind.ERROR, "采集异常", sanitizeSummary(collector.lastError))
    }
    val rawState = collector.collector.trim().lowercase(Locale.ROOT)
    val channel = listOf(
        collector.transport.trim().uppercase(Locale.ROOT),
        collector.mode.trim().uppercase(Locale.ROOT),
    ).filter(String::isNotBlank).distinct().joinToString(" · ")
    return when (rawState) {
        "running" -> CollectorPresentation(
            CollectorStateKind.RUNNING,
            "运行正常",
            channel.ifBlank { "监控通道已建立" },
        )
        "starting" -> CollectorPresentation(
            CollectorStateKind.STARTING,
            "正在启动",
            channel.ifBlank { "正在建立采集通道" },
        )
        "stopped" -> CollectorPresentation(
            CollectorStateKind.STOPPED,
            "已停止",
            "CPAMP 当前未运行请求采集",
        )
        else -> CollectorPresentation(
            CollectorStateKind.UNKNOWN,
            "状态未知",
            rawState.ifBlank { "服务未返回采集器运行状态" },
        )
    }
}

@Composable
private fun FailureCard(failure: io.cpamonitor.android.data.remote.FailureDto) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.elevatedCardElevation(4.dp)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(GlanceColors.Rose))
            Column(Modifier.padding(15.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(failure.model, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    StatusBadge(failure.statusCode?.toString() ?: "失败", error = true)
                }
                Text(failure.account.ifBlank { failure.label.ifBlank { failure.provider } }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sanitizeSummary(failure.summary), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun PagerDots(pageCount: Int, selected: Int) {
    Row(Modifier.fillMaxWidth().height(22.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        repeat(pageCount) { index ->
            Box(Modifier.padding(horizontal = 4.dp).size(if (index == selected) 8.dp else 6.dp).background(if (index == selected) GlanceColors.Navy else MaterialTheme.colorScheme.outline, CircleShape))
        }
    }
}

@Composable
private fun OverviewHero(calls: Long, tokens: Long, cost: Double, successRate: Double) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = GlanceColors.Navy),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
    ) {
        Box(
            Modifier.background(
                Brush.horizontalGradient(
                    listOf(GlanceColors.Navy, Color(0xFF16213A), GlanceColors.NavySoft),
                ),
            ),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(15.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("总 TOKEN", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.68f))
                    Spacer(Modifier.weight(1f))
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f),
                        modifier = Modifier.border(1.dp, Color.White.copy(alpha = 0.28f), CircleShape),
                    ) {
                        Text("今天 ▾", color = Color.White, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
                    }
                }
                Text(compact(tokens), color = Color.White, fontSize = 42.sp, lineHeight = 47.sp, fontWeight = FontWeight.Bold)
                Row {
                    Text("今日调用", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                    Text(" ${compact(calls)} · 成功率 ${percent(successRate)}", color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                Row(Modifier.fillMaxWidth()) {
                    HeroStat("估算费用", currency(cost), Modifier.weight(1f))
                    HeroStat("请求", compact(calls), Modifier.weight(1f))
                    HeroStat("状态", "在线", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), color = Color.White.copy(alpha = 0.58f), style = MaterialTheme.typography.labelSmall)
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1)
    }
}

@Composable
fun UsageScreen(
    state: MonitorUiState,
    padding: PaddingValues,
    onRange: (RangePreset) -> Unit,
    onProvider: (String) -> Unit,
    onModel: (String) -> Unit,
    onAccount: (String) -> Unit,
    onFailedOnly: (Boolean) -> Unit,
    onCustomRange: (LocalDate, LocalDate) -> Unit,
) {
    val analytics = state.analytics
    val providers = analytics?.accountStats?.map { it.provider }?.filter(String::isNotBlank)?.distinct().orEmpty()
    val models = analytics?.modelStats?.map { it.model }?.filter(String::isNotBlank)?.distinct().orEmpty()
    val accounts = analytics?.accountStats?.map { it.account.ifBlank { it.label } }?.filter(String::isNotBlank)?.distinct().orEmpty()
    var showDatePicker by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier.padding(padding),
        contentPadding = PagePadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { ScreenHeader("用量分析", state.analyticsUpdatedAt, "按时间与维度拆解消耗") }
        if (state.offline) item { OfflineBanner(state.analyticsUpdatedAt) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RangePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = state.rangePreset == preset,
                            onClick = { if (preset == RangePreset.CUSTOM) showDatePicker = true else onRange(preset) },
                            label = { Text(preset.label) },
                            leadingIcon = if (preset == RangePreset.CUSTOM) ({ Icon(Icons.Outlined.DateRange, null, Modifier.size(17.dp)) }) else null,
                        )
                    }
                }
                if (state.rangePreset == RangePreset.CUSTOM && state.customStart != null) {
                    InfoStrip(Icons.Outlined.Event, "${state.customStart} 至 ${state.customEnd ?: state.customStart}")
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterMenu("Provider", state.providerFilter, providers, onProvider)
                    FilterMenu("模型", state.modelFilter, models, onModel)
                    FilterMenu("账号", state.accountFilter, accounts, onAccount)
                    FilterChip(
                        selected = state.failedOnly,
                        onClick = { onFailedOnly(!state.failedOnly) },
                        label = { Text("仅失败") },
                        leadingIcon = { Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(17.dp)) },
                    )
                }
            }
        }
        if (analytics?.summary == null) {
            item {
                if (state.loading || state.refreshing) LoadingBlock() else EmptyCard("没有用量数据", "调整时间范围或筛选条件后重试")
            }
            return@LazyColumn
        }
        item {
            AnalyticsSummary(
                analytics.summary.totalCalls,
                analytics.summary.totalTokens,
                analytics.summary.totalCost,
                analytics.summary.successRate,
            )
        }
        item {
            SectionCard("调用趋势", "当前时间范围内的请求分布") {
                if (analytics.timeline.isEmpty()) InlineEmpty("当前范围没有趋势数据") else LineChart(analytics.timeline)
            }
        }
        item { SectionHeading("模型排行", "费用与调用表现") }
        if (analytics.modelStats.isEmpty()) item { EmptyCard("没有模型统计", "") }
        itemsIndexed(analytics.modelStats) { index, model ->
            RankingCard(index + 1, model.model, model.calls, model.totalTokens, model.cost, model.successRate)
        }
        item { SectionHeading("账号排行", "账号消耗与成功率") }
        if (analytics.accountStats.isEmpty()) item { EmptyCard("没有账号统计", "") }
        itemsIndexed(analytics.accountStats) { index, account ->
            RankingCard(
                index + 1,
                account.account.ifBlank { account.label.ifBlank { account.id } },
                account.calls,
                account.totalTokens,
                account.cost,
                account.successRate,
                account.provider,
            )
        }
    }
    if (showDatePicker) {
        val pickerState = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedStartDateMillis != null,
                    onClick = {
                        val start = Instant.ofEpochMilli(pickerState.selectedStartDateMillis!!).atZone(ZoneId.of("UTC")).toLocalDate()
                        val end = Instant.ofEpochMilli(pickerState.selectedEndDateMillis ?: pickerState.selectedStartDateMillis!!).atZone(ZoneId.of("UTC")).toLocalDate()
                        onCustomRange(start, end)
                        showDatePicker = false
                    },
                ) { Text("应用") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("取消") } },
        ) {
            DateRangePicker(state = pickerState, title = { Text("选择日期范围", Modifier.padding(20.dp)) })
        }
    }
}

@Composable
private fun AnalyticsSummary(calls: Long, tokens: Long, cost: Double, successRate: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = GlanceColors.Navy),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            Modifier.background(
                Brush.horizontalGradient(
                    listOf(GlanceColors.Navy, GlanceColors.NavySoft),
                ),
            ).padding(17.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("总调用", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.68f))
                    Text(compact(calls), style = MaterialTheme.typography.headlineMedium, color = Color.White)
                }
                StatusBadge("成功 ${percent(successRate)}", error = successRate < 0.9)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCell("Token", compact(tokens), Modifier.weight(1f))
                SummaryCell("估算费用", currency(cost), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SummaryCell(label: String, value: String, modifier: Modifier) {
    Surface(
        modifier,
        shape = MaterialTheme.shapes.small,
        color = Color.White.copy(alpha = 0.12f),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.62f))
            Text(value, style = MaterialTheme.typography.titleMedium, color = Color.White)
        }
    }
}

@Composable
private fun RankingCard(
    rank: Int,
    name: String,
    calls: Long,
    tokens: Long,
    cost: Double,
    successRate: Double,
    supporting: String = "",
) {
    ElevatedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(4.dp),
    ) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer) {
                Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                    Text(rank.toString().padStart(2, '0'), color = if (rank <= 3) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(supporting.takeIf(String::isNotBlank), "${compact(calls)} 次", "${compact(tokens)} Token").joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(currency(cost), fontWeight = FontWeight.SemiBold)
                Text(percent(successRate), style = MaterialTheme.typography.labelSmall, color = if (successRate < 0.9) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun FilterMenu(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        FilterChip(
            selected = value.isNotBlank(),
            onClick = { expanded = true },
            label = { Text(value.ifBlank { label }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            trailingIcon = { Icon(Icons.Outlined.KeyboardArrowDown, null, Modifier.size(17.dp)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("全部$label") }, onClick = { onSelect(""); expanded = false })
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = { onSelect(option); expanded = false },
                    leadingIcon = if (option == value) ({ Icon(Icons.Outlined.Check, null) }) else null,
                )
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, updatedAt: Long, subtitle: String = "") {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (updatedAt > 0) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                ) {
                    Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Schedule, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(" ${time(updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun OfflineBanner(updatedAt: Long) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer, MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.45f), MaterialTheme.shapes.small).padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(21.dp))
        Column {
            Text("离线模式", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
            Text("正在显示 ${if (updatedAt > 0) time(updatedAt) else "上次"} 的缓存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
fun MetricCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    supporting: String = "",
    icon: ImageVector? = null,
    accent: Color = GlanceColors.Blue,
) {
    ElevatedCard(
        modifier,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(5.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, Modifier.size(17.dp), tint = accent)
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, maxLines = 1)
                if (supporting.isNotBlank()) Text(supporting, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SectionCard(title: String, subtitle: String = "", content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
fun EmptyCard(title: String, subtitle: String) {
    Card(
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Inbox, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Text(title, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
            if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))
        }
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String = "") {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(2.dp).height(38.dp).background(MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.extraSmall))
            Column(Modifier.padding(start = 9.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StatusBadge(text: String, error: Boolean = false) {
    val container = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = container.copy(alpha = 0.72f),
        modifier = Modifier.border(1.dp, (if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary).copy(alpha = 0.55f), MaterialTheme.shapes.extraSmall),
    ) {
        Text(text.uppercase(), color = content, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp))
    }
}

@Composable
private fun InlineEmpty(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InfoStrip(icon: ImageVector, text: String) {
    Row(
        Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.extraSmall)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall).padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 7.dp))
    }
}

@Composable
fun LoadingBlock() {
    Row(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Text("  LOADING DATA", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ModelRow(rank: Int, name: String, calls: Long, cost: Double, progress: Float) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rank.toString().padStart(2, '0'), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(currency(cost), style = MaterialTheme.typography.labelMedium)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f).height(5.dp),
                    color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainer,
                )
                Text("${compact(calls)} 次", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

@Composable
fun LineChart(points: List<TimelinePointDto>) {
    val lineColor = GlanceColors.Blue
    val fillTop = lineColor.copy(alpha = 0.24f)
    val fillBottom = lineColor.copy(alpha = 0.01f)
    val grid = GlanceColors.Line
    val pointFill = Color.White
    Column {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            if (points.isEmpty()) return@Canvas
            val topPadding = 10.dp.toPx()
            val bottomPadding = 8.dp.toPx()
            val usableHeight = size.height - topPadding - bottomPadding
            val maxValue = max(1L, points.maxOf { it.calls }).toFloat()
            repeat(4) { row ->
                val y = topPadding + usableHeight * row / 3f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            val coordinates = points.mapIndexed { index, point ->
                val x = if (points.size == 1) size.width / 2 else index * size.width / (points.size - 1)
                val y = topPadding + usableHeight - point.calls / maxValue * usableHeight
                Offset(x, y)
            }
            val linePath = Path().apply {
                moveTo(coordinates.first().x, coordinates.first().y)
                coordinates.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val fillPath = Path().apply {
                moveTo(coordinates.first().x, size.height)
                lineTo(coordinates.first().x, coordinates.first().y)
                coordinates.drop(1).forEach { lineTo(it.x, it.y) }
                lineTo(coordinates.last().x, size.height)
                close()
            }
            drawPath(fillPath, Brush.verticalGradient(listOf(fillTop, fillBottom), endY = size.height))
            drawPath(linePath, lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
            coordinates.forEach { point ->
                drawCircle(pointFill, 4.5.dp.toPx(), point)
                drawCircle(lineColor, 4.5.dp.toPx(), point, style = Stroke(2.dp.toPx()))
            }
        }
        if (points.size > 1) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(chartLabel(points.first()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(chartLabel(points.last()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun chartLabel(point: TimelinePointDto): String = point.label.ifBlank {
    Instant.ofEpochMilli(point.bucketMs).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))
}

fun compact(value: Long): String = when {
    value >= 1_000_000_000 -> decimal(value / 1_000_000_000.0) + "B"
    value >= 1_000_000 -> decimal(value / 1_000_000.0) + "M"
    value >= 1_000 -> decimal(value / 1_000.0) + "K"
    else -> NumberFormat.getIntegerInstance(Locale.CHINA).format(value)
}

fun decimal(value: Double): String = if (value >= 100) "%.0f".format(value) else "%.1f".format(value)
fun currency(value: Double): String = "$" + if (value < 0.01) "%.4f".format(value) else "%.2f".format(value)
fun percent(value: Double): String = "%.1f%%".format(if (value <= 1.0) value * 100 else value)
fun time(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

private val UNSAFE_TEXT_CONTROLS = Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F\\u202A-\\u202E\\u2066-\\u2069]")
private val REPEATED_WHITESPACE = Regex("\\s+")
private val BEARER_SECRET = Regex("(?i)\\bbearer\\s+[A-Za-z0-9._~+/=-]{4,}")
private val LABELED_SECRET = Regex(
    "(?i)\\b(authorization|api[_-]?key|admin[_-]?key|access[_-]?token|refresh[_-]?token|id[_-]?token|token|secret|password)\\b" +
        "(?:[\"']?\\s*[:=]\\s*[\"']?|\\s+)[A-Za-z0-9._~+/=-]{4,}",
)
private val STANDALONE_SECRET = Regex(
    "(?i)\\b(?:cpamp_[A-Za-z0-9]{8,}|(?:sk|rk)-[A-Za-z0-9_-]{16,}|AKIA[A-Z0-9]{16}|" +
        "AIza[A-Za-z0-9_-]{20,}|eyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{4,})\\b",
)

fun sanitizeSummary(value: String): String {
    var sanitized = value.take(4_096).replace(UNSAFE_TEXT_CONTROLS, " ").replace(REPEATED_WHITESPACE, " ")
    sanitized = sanitized.replace(BEARER_SECRET, "Bearer ••••")
    sanitized = sanitized.replace(LABELED_SECRET) { "${it.groupValues[1]}=••••" }
    sanitized = sanitized.replace(STANDALONE_SECRET, "••••")
    return sanitized.take(240).ifBlank { "未提供错误摘要" }
}
