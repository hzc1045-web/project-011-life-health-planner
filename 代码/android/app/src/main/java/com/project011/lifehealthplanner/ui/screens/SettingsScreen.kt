package com.project011.lifehealthplanner.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.project011.lifehealthplanner.ui.AppUiState
import com.project011.lifehealthplanner.ui.AppViewModel

@Composable
fun SettingsScreen(state: AppUiState, viewModel: AppViewModel, onDismiss: () -> Unit) {
    var serverUrl by remember(state.companionServer) { mutableStateOf(state.companionServer.ifBlank { "https://" }) }
    var code by remember { mutableStateOf("") }
    var recoveryPassword by remember { mutableStateOf("") }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = state.exportJson
        val success = uri != null && content != null && runCatching {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(content) }
                ?: error("无法打开导出文件")
        }.isSuccess
        viewModel.exportHandled(success)
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val healthLauncher = rememberLauncherForActivityResult(viewModel.healthPermissionContract()) { }
    LaunchedEffect(state.exportJson) {
        if (state.exportJson != null) exportLauncher.launch("life-health-planner-export.json")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("设置")
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "关闭") }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("权限", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        calendarLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                    }) { Text("日历") }
                    OutlinedButton(onClick = { healthLauncher.launch(viewModel.healthPermissions()) }) { Text("健康数据") }
                    if (Build.VERSION.SDK_INT >= 33) {
                        OutlinedButton(onClick = { notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                            Text("通知")
                        }
                    }
                }
                OutlinedButton(
                    onClick = viewModel::prepareExport,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("导出本地 JSON") }
                HorizontalDivider()
                Text("电脑配对", style = MaterialTheme.typography.titleMedium)
                if (state.paired) {
                    Text(state.companionServer, color = MaterialTheme.colorScheme.primary)
                    Text(
                        if (state.companionProvider.isBlank()) {
                            "当前 AI：状态未刷新"
                        } else {
                            "当前 AI：${state.companionProvider}"
                        },
                    )
                    if (state.companionProviderThirdParty) {
                        Text(
                            "第三方服务会接收本次发送的最小化健康上下文。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    OutlinedButton(onClick = viewModel::refreshCompanionStatus) {
                        Text("刷新 AI 状态")
                    }
                    OutlinedButton(onClick = viewModel::clearPairing) { Text("清除手机端配对") }
                } else {
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = { serverUrl = it },
                        label = { Text("Tailscale 服务地址") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(8) },
                        label = { Text("一次性配对码") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { viewModel.pair(serverUrl, code) },
                        enabled = serverUrl.startsWith("https://") && code.length >= 6,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Link, contentDescription = null)
                        Text("完成配对", Modifier.padding(start = 8.dp))
                    }
                }
                HorizontalDivider()
                Text("隐私与备份", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("生物识别锁")
                        Text("下次启动生效", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = state.profile?.appLockEnabled == true,
                        onCheckedChange = viewModel::setAppLock,
                    )
                }
                OutlinedTextField(
                    value = recoveryPassword,
                    onValueChange = { recoveryPassword = it },
                    label = { Text("备份恢复密码") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.backup(recoveryPassword) },
                        enabled = state.paired && recoveryPassword.length >= 10,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Backup, contentDescription = null)
                        Text("备份", Modifier.padding(start = 6.dp))
                    }
                    OutlinedButton(
                        onClick = { viewModel.restore(recoveryPassword) },
                        enabled = state.paired && recoveryPassword.length >= 10,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Text("恢复", Modifier.padding(start = 6.dp))
                    }
                }
                Text(
                    "恢复密码不会上传或保存。电脑只保存 AES-256-GCM 加密后的备份。",
                    style = MaterialTheme.typography.bodySmall,
                )
                HorizontalDivider()
                Text("版本 0.1.1 · 个人健康管理与生活决策支持工具")
                Text("不提供诊断、处方或剂量调整。紧急情况请联系当地急救服务。")
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
    )
}
