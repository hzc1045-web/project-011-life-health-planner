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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.project011.lifehealthplanner.ui.AppUiState
import com.project011.lifehealthplanner.ui.ChatTurn

@Composable
fun AiScreen(state: AppUiState, onSend: (String) -> Unit, padding: PaddingValues) {
    var message by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
            Text(
                if (state.paired) "电脑中转已连接" else "AI 离线，已有计划仍可正常使用",
                modifier = Modifier.fillMaxWidth().padding(10.dp),
            )
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.chat.isEmpty()) {
                item { Text("可以讨论本周节奏、目标拆解和执行复盘。健康异常只会给出风险提示。") }
            }
            items(state.chat) { turn -> ChatBubble(turn) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("输入问题") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
            )
            Button(
                onClick = {
                    onSend(message)
                    message = ""
                },
                enabled = state.paired && message.isNotBlank() && !state.loading,
            ) { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送") }
        }
    }
}

@Composable
private fun ChatBubble(turn: ChatTurn) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (turn.fromUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (turn.fromUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(0.88f),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(turn.text)
                turn.reply?.takeIf { it.riskLevel != "normal" }?.let {
                    Text(it.riskMessage, color = MaterialTheme.colorScheme.error)
                }
                turn.reply?.suggestedActions?.takeIf { it.isNotEmpty() }?.let { actions ->
                    Text("可选行动", style = MaterialTheme.typography.titleSmall)
                    actions.forEach { Text("· ${it.title}：${it.details}") }
                    Text("行动不会自动进入日历，请在“计划”中生成并确认。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
