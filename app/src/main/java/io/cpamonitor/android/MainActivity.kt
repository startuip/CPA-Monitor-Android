package io.cpamonitor.android

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.cpamonitor.android.alerts.NotificationHelper
import io.cpamonitor.android.ui.CpaMonitorRoot
import io.cpamonitor.android.ui.MonitorViewModel
import io.cpamonitor.android.ui.Tab
import io.cpamonitor.android.ui.theme.CpaMonitorTheme
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MonitorViewModel by viewModels()
    private val deepLinkTab = MutableStateFlow<Tab?>(null)
    @Inject lateinit var notifications: NotificationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        notifications.createChannels()
        handleIntent(intent)
        setContent {
            val requestedTab by deepLinkTab.collectAsStateWithLifecycle()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { }
            CpaMonitorTheme {
                CpaMonitorRoot(
                    viewModel = viewModel,
                    requestedTab = requestedTab,
                    onTabConsumed = { deepLinkTab.value = null },
                    requestNotificationPermission = {
                        if (
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val data = intent.trustedDeepLink()
        val path = data?.pathSegments?.firstOrNull()
        deepLinkTab.value = data.toTab()
        viewModel.applyDeepLink(
            path = path,
            failedOnly = data?.let { path == "requests" && it.getQueryParameter("failed") == "true" } == true,
            accountRow = data?.getQueryParameter("row")?.takeIf(SAFE_ACCOUNT_ROW::matches),
        )
    }
}

private fun Intent?.trustedDeepLink(): Uri? = this?.data?.takeIf {
    action == Intent.ACTION_VIEW && it.scheme == "cpamonitor" && it.host == "open"
}

private fun Uri?.toTab(): Tab? = when (this?.pathSegments?.firstOrNull()) {
    "accounts" -> Tab.ACCOUNTS
    "requests" -> Tab.REQUESTS
    "settings" -> Tab.SETTINGS
    "usage" -> Tab.USAGE
    else -> null
}

private val SAFE_ACCOUNT_ROW = Regex("[A-Za-z0-9_-]{1,64}")
