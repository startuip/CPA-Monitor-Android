@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.cpamonitor.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.cpamonitor.android.ui.theme.GlanceColors

@Composable
fun ConnectionScreen(
    state: ConnectUiState,
    onConnect: (String, String) -> Unit,
    dismissError: () -> Unit,
) {
    var url by rememberSaveable { mutableStateOf("") }
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    val keyFocus = remember { FocusRequester() }
    val canConnect = !state.connecting && url.isNotBlank() && key.isNotBlank()
    val colors = MaterialTheme.colorScheme
    val submitConnection = {
        if (canConnect) {
            val submittedKey = key
            key = ""
            showKey = false
            onConnect(url, submittedKey)
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(ColorTokens.Top, GlanceColors.Background)),
        ).safeDrawingPadding().imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp).verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.medium, color = GlanceColors.Navy, modifier = Modifier.size(48.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.GridView, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Column(Modifier.padding(start = 12.dp)) {
                    Text("CPA Monitor", style = MaterialTheme.typography.titleMedium)
                    Text("CPAMP 手机看板", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Surface(shape = CircleShape, color = colors.tertiaryContainer) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(7.dp).background(colors.tertiary, CircleShape))
                        Text(" 只读", style = MaterialTheme.typography.labelSmall, color = colors.onTertiaryContainer)
                    }
                }
            }

            Text(
                "连接你的 CPAMP",
                style = MaterialTheme.typography.headlineLarge,
                color = colors.onBackground,
                modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
            )
            Text(
                "验证完成后即可查看调用、Token、费用与账号配额。",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 18.dp),
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(containerColor = colors.surface),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(3.dp).height(38.dp).background(GlanceColors.Blue, CircleShape))
                        Column(Modifier.padding(start = 10.dp)) {
                            Text("服务器连接", style = MaterialTheme.typography.titleMedium)
                            Text("凭据仅加密保存在此设备", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; dismissError() },
                        label = { Text("服务器地址") },
                        placeholder = { Text("https://cpamp.host") },
                        leadingIcon = { Icon(Icons.Outlined.Dns, null) },
                        singleLine = true,
                        enabled = !state.connecting,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { keyFocus.requestFocus() }),
                        shape = MaterialTheme.shapes.small,
                        colors = glanceFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = key,
                        onValueChange = { key = it; dismissError() },
                        label = { Text("Admin Key") },
                        leadingIcon = { Icon(Icons.Outlined.Key, null) },
                        singleLine = true,
                        enabled = !state.connecting,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "显示或隐藏密钥")
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitConnection() }),
                        shape = MaterialTheme.shapes.small,
                        colors = glanceFieldColors(),
                        modifier = Modifier.fillMaxWidth().focusRequester(keyFocus),
                    )
                    state.error?.let {
                        Row(
                            modifier = Modifier.fillMaxWidth().background(colors.errorContainer, MaterialTheme.shapes.small)
                                .border(1.dp, colors.error.copy(alpha = 0.25f), MaterialTheme.shapes.small).padding(11.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.ErrorOutline, null, tint = colors.error, modifier = Modifier.size(19.dp))
                            Text(it, color = colors.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Button(
                        onClick = submitConnection,
                        enabled = canConnect,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = CircleShape,
                    ) {
                        if (state.connecting) {
                            CircularProgressIndicator(strokeWidth = 2.dp, color = colors.onPrimary, modifier = Modifier.size(19.dp))
                            Text("正在验证…", modifier = Modifier.padding(start = 9.dp))
                        } else {
                            Text("验证并连接", fontWeight = FontWeight.Bold)
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, modifier = Modifier.padding(start = 8.dp).size(18.dp))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 17.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TrustItem(Icons.Outlined.Https, "HTTPS")
                TrustItem(Icons.Outlined.Visibility, "只读接口")
                TrustItem(Icons.Outlined.EnhancedEncryption, "Keystore")
            }
            Text(
                "依次验证服务信息、管理员凭据和监控能力；失败不会覆盖已有配置。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

private object ColorTokens {
    val Top = androidx.compose.ui.graphics.Color(0xFFF8FAFC)
}

@Composable
private fun glanceFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
)

@Composable
private fun TrustItem(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
        Text(" $label", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
