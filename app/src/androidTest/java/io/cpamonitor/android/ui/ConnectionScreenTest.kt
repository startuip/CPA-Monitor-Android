package io.cpamonitor.android.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import io.cpamonitor.android.ui.theme.CpaMonitorTheme
import org.junit.Rule
import org.junit.Test

class ConnectionScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun connectRequiresUrlAndKey() {
        compose.setContent {
            CpaMonitorTheme {
                ConnectionScreen(ConnectUiState(), { _, _ -> }, {})
            }
        }
        compose.onNodeWithText("验证并连接").assertIsNotEnabled()
        compose.onNodeWithText("服务器地址").performTextInput("https://example.com")
        compose.onNodeWithText("Admin Key").performTextInput("secret")
        compose.onNodeWithText("验证并连接").assertIsEnabled()
    }

    @Test
    fun errorStateIsVisible() {
        compose.setContent {
            ConnectionScreen(ConnectUiState(error = "Admin Key 无效"), { _, _ -> }, {})
        }
        compose.onNodeWithText("Admin Key 无效").assertIsDisplayed()
    }
}
