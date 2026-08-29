@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.cpamonitor.android.ui

import android.os.Build
import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

enum class Tab(val label: String) {
    OVERVIEW("总览"), USAGE("用量"), REQUESTS("明细"), ACCOUNTS("账号"), SETTINGS("设置")
}

@Composable
fun CpaMonitorRoot(
    viewModel: MonitorViewModel,
    requestedTab: Tab?,
    onTabConsumed: () -> Unit,
    requestNotificationPermission: () -> Unit,
) {
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val connectUi by viewModel.connectUi.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val baseUrl by viewModel.baseUrl.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (!connected) {
        ConnectionScreen(connectUi, viewModel::connect, viewModel::dismissMessage)
        return
    }

    var tab by rememberSaveable { mutableStateOf(Tab.OVERVIEW) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(requestedTab) {
        requestedTab?.let { tab = it; onTabConsumed() }
    }
    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }
    LaunchedEffect(settings.refreshSeconds, connected) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                delay(settings.refreshSeconds * 1_000L)
                viewModel.refreshAll(refreshEventList = false)
            }
        }
    }
    LaunchedEffect(connected, settings.alertsEnabled, settings.notificationPermissionAsked) {
        if (
            Build.VERSION.SDK_INT >= 33 && connected && settings.alertsEnabled &&
            !settings.notificationPermissionAsked
        ) {
            viewModel.markNotificationPermissionAsked()
            requestNotificationPermission()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (!landscape) GlanceBottomBar(tab) { tab = it }
        },
    ) { padding ->
        Row(Modifier.fillMaxSize().padding(padding)) {
            if (landscape) {
                GlanceSideRail(tab) { tab = it }
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            PullToRefreshBox(
                isRefreshing = ui.refreshing,
                onRefresh = viewModel::refreshAll,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                if (ui.loading && ui.dashboard == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                            Text(
                                "正在同步数据…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp),
                            )
                        }
                    }
                } else {
                    val contentPadding = PaddingValues()
                    when (tab) {
                        Tab.OVERVIEW -> OverviewScreen(ui, contentPadding)
                        Tab.USAGE -> UsageScreen(
                            ui, contentPadding, viewModel::setRange, viewModel::setProvider,
                            viewModel::setModel, viewModel::setAccount, viewModel::setFailedOnly,
                            viewModel::setCustomRange,
                        )
                        Tab.REQUESTS -> RequestsScreen(ui, contentPadding, { viewModel.refreshEvents(true) }) {
                            viewModel.refreshEvents(false)
                        }
                        Tab.ACCOUNTS -> AccountsScreen(ui, contentPadding)
                        Tab.SETTINGS -> SettingsScreen(
                            padding = contentPadding,
                            baseUrl = baseUrl.orEmpty(),
                            settings = settings,
                            onSettings = viewModel::updateSettings,
                            onClearCache = viewModel::clearCache,
                            onDisconnect = viewModel::disconnect,
                            requestNotificationPermission = requestNotificationPermission,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlanceBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            modifier = Modifier.height(68.dp),
        ) {
            Tab.entries.forEach { item ->
                val active = item == selected
                NavigationBarItem(
                    selected = active,
                    onClick = { onSelect(item) },
                    icon = { Icon(tabIcon(item, active), item.label, modifier = Modifier.size(21.dp)) },
                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Composable
internal fun GlanceSideRail(selected: Tab, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.width(72.dp).fillMaxHeight()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(vertical = 5.dp),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Tab.entries.forEach { item ->
                val active = item == selected
                NavigationRailItem(
                    selected = active,
                    onClick = { onSelect(item) },
                    icon = { Icon(tabIcon(item, active), item.label, modifier = Modifier.size(21.dp)) },
                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                    alwaysShowLabel = true,
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

private fun tabIcon(tab: Tab, selected: Boolean) = when (tab) {
    Tab.OVERVIEW -> if (selected) Icons.Filled.SpaceDashboard else Icons.Outlined.SpaceDashboard
    Tab.USAGE -> if (selected) Icons.Filled.QueryStats else Icons.Outlined.QueryStats
    Tab.REQUESTS -> if (selected) Icons.AutoMirrored.Filled.ReceiptLong else Icons.AutoMirrored.Outlined.ReceiptLong
    Tab.ACCOUNTS -> if (selected) Icons.Filled.ManageAccounts else Icons.Outlined.ManageAccounts
    Tab.SETTINGS -> if (selected) Icons.Filled.Settings else Icons.Outlined.Settings
}
