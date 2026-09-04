package com.project011.lifehealthplanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OfflineBolt
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.project011.lifehealthplanner.data.remote.PlanItemDto
import com.project011.lifehealthplanner.ui.AppUiState
import com.project011.lifehealthplanner.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun PlanScreen(state: AppUiState, viewModel: AppViewModel, padding: PaddingValues) {
    var focus by remember { mutableStateOf("健康、事业与学习平衡") }
    var useAi by remember { mutableStateOf(true) }
    var days by remember { mutableIntStateOf(7) }
    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("生成下一周安排", style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = useAi,
                onClick = { useAi = true },
                label = { Text("AI 计划") },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
            )
            FilterChip(
                selected = !useAi,
                onClick = { useAi = false },
                label = { Text("离线计划") },
                leadingIcon = { Icon(Icons.Default.OfflineBolt, contentDescription = null) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(1 to "日计划", 7 to "周计划", 30 to "月计划").forEach { (value, label) ->
                FilterChip(selected = days == value, onClick = { days = value }, label = { Text(label) })
            }
        }
        OutlinedTextField(
            value = focus,
            onValueChange = { focus = it },
            label = { Text("本周期重点") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (useAi) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("本次发送字段", style = MaterialTheme.typography.titleSmall)
                    Text(aiFieldSummary(state), style = MaterialTheme.typography.bodyMedium)
                    if (state.companionProvider.isNotBlank()) {
                        Text("数据接收方：${state.companionProvider}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("不发送称呼、出生日期、联系方式或精确地址。", style = MaterialTheme.typography.bodySmall)
                    if (state.companionProviderThirdParty) {
                        Text(
                            "当前为第三方服务，请确认接受其数据处理规则后再生成。",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        Button(
            onClick = { viewModel.generatePlan(focus, useAi, days) },
            enabled = !state.loading && (!useAi || (state.paired && state.aiConfigured)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(if (useAi) Icons.Default.AutoAwesome else Icons.Default.OfflineBolt, contentDescription = null)
            Text(if (useAi) "生成 AI 草案" else "生成离线草案", Modifier.padding(start = 8.dp))
        }
        if (useAi && !state.paired) {
            Text("AI 离线：请先在设置中配对电脑。", color = MaterialTheme.colorScheme.error)
        } else if (useAi && !state.aiConfigured) {
            Text("AI 离线：请在电脑端配置当前提供商密钥。", color = MaterialTheme.colorScheme.error)
        }
        state.draft?.let { draft ->
            HorizontalDivider()
            Text(draft.title, style = MaterialTheme.typography.titleLarge)
            Text(draft.summary)
            if (draft.riskLevel != "normal") {
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                    Text(
                        draft.riskMessage.ifBlank { "草案存在需要关注的健康风险" },
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
            draft.items.forEach { DraftItem(it) }
            state.draftValidation?.errors?.forEach {
                Text("· $it", color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = viewModel::confirmDraft,
                enabled = state.draftValidation?.isValid == true && !state.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Text("确认并写入专属日历", Modifier.padding(start = 8.dp))
            }
        }
        if (state.plans.isNotEmpty()) {
            HorizontalDivider()
            Text("已确认计划", style = MaterialTheme.typography.titleMedium)
            state.plans.take(5).forEach {
                Text("${it.title} · ${it.summary}")
            }
        }
    }
}

@Composable
private fun DraftItem(item: PlanItemDto) {
    val formatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
    val zone = ZoneId.systemDefault()
    val start = runCatching { Instant.parse(item.startAt).atZone(zone).format(formatter) }.getOrDefault(item.startAt)
    val end = runCatching { Instant.parse(item.endAt).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")) }
        .getOrDefault(item.endAt)
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium)
            Text("$start - $end · 优先级 ${item.priority}")
            Text(item.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("预计费用 ¥${"%.2f".format(item.estimatedCost)} · 精力 ${item.energy}")
        }
    }
}

private fun aiFieldSummary(state: AppUiState): String {
    val fields = mutableListOf("年龄段", "地区与时区", "匿名忙碌时段")
    if (state.profile?.conditionsJson != "[]") fields += "健康约束"
    if (state.goals.isNotEmpty()) fields += "目标"
    if (state.healthRecords.isNotEmpty()) fields += "近期健康指标"
    if (state.profile?.weeklyBudget != null) fields += "周预算"
    return fields.joinToString("、")
}
