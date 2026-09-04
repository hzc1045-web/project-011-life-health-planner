package com.project011.lifehealthplanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.project011.lifehealthplanner.data.local.PlanItemEntity
import com.project011.lifehealthplanner.ui.AppUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun TodayScreen(
    state: AppUiState,
    onCheckIn: (String, String, Int, Int, String) -> Unit,
    padding: PaddingValues,
) {
    var pending by remember { mutableStateOf<PendingCheckIn?>(null) }
    val zone = ZoneId.systemDefault()
    val today = Instant.now().atZone(zone).toLocalDate()
    val todayItems = state.planItems.filter {
        Instant.ofEpochMilli(it.startAt).atZone(zone).toLocalDate() == today
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "${today.monthValue}月${today.dayOfMonth}日",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = if (todayItems.isEmpty()) "今天没有已确认安排" else "${todayItems.size} 项安排",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (todayItems.isEmpty()) {
            item {
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("保持可执行", style = MaterialTheme.typography.titleMedium)
                        Text("可前往“计划”生成离线或 AI 周计划，确认后会在这里出现。")
                    }
                }
            }
        } else {
            items(todayItems, key = { it.id }) { item ->
                TodayItem(item) { status -> pending = PendingCheckIn(item.id, item.title, status) }
                HorizontalDivider()
            }
        }
    }
    pending?.let { checkIn ->
        CheckInDialog(
            pending = checkIn,
            onDismiss = { pending = null },
            onConfirm = { difficulty, energy, note ->
                onCheckIn(checkIn.itemId, checkIn.status, difficulty, energy, note)
                pending = null
            },
        )
    }
}

@Composable
private fun TodayItem(item: PlanItemEntity, onStatus: (String) -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    val zone = ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(item.startAt).atZone(zone).format(formatter)
    val end = Instant.ofEpochMilli(item.endAt).atZone(zone).format(formatter)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text("$start - $end · ${domainLabel(item.domain)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(statusLabel(item.status), color = statusColor(item.status))
        }
        if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onStatus("completed") },
                enabled = item.status == "planned",
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Text("完成", Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = { onStatus("partial") },
                enabled = item.status == "planned",
            ) {
                Icon(Icons.Default.MoreHoriz, contentDescription = null)
                Text("部分", Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = { onStatus("skipped") },
                enabled = item.status == "planned",
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null)
                Text("跳过", Modifier.padding(start = 6.dp))
            }
        }
    }
}

private data class PendingCheckIn(val itemId: String, val title: String, val status: String)

@Composable
private fun CheckInDialog(
    pending: PendingCheckIn,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, String) -> Unit,
) {
    var difficulty by remember { mutableFloatStateOf(3f) }
    var energy by remember { mutableFloatStateOf(3f) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pending.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("难度 ${difficulty.toInt()}/5")
                Slider(value = difficulty, onValueChange = { difficulty = it }, valueRange = 1f..5f, steps = 3)
                Text("当前精力 ${energy.toInt()}/5")
                Slider(value = energy, onValueChange = { energy = it }, valueRange = 1f..5f, steps = 3)
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(difficulty.toInt(), energy.toInt(), note) }) { Text("保存反馈") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

private fun domainLabel(value: String) = when (value) {
    "health" -> "健康"
    "career" -> "事业"
    "learning" -> "学习"
    "finance" -> "财务"
    "relationships" -> "关系"
    "leisure" -> "休闲"
    else -> value
}

private fun statusLabel(value: String) = when (value) {
    "completed" -> "已完成"
    "partial" -> "部分完成"
    "skipped" -> "已跳过"
    else -> "待执行"
}

@Composable
private fun statusColor(value: String) = when (value) {
    "completed" -> MaterialTheme.colorScheme.primary
    "partial" -> MaterialTheme.colorScheme.tertiary
    "skipped" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
