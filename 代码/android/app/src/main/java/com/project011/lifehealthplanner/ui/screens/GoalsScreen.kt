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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.project011.lifehealthplanner.data.local.LifeGoalEntity

@Composable
fun GoalsScreen(
    goals: List<LifeGoalEntity>,
    onAddGoal: (String, String, String, Int) -> Unit,
    padding: PaddingValues,
) {
    var domain by remember { mutableStateOf("health") }
    var title by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var priority by remember { mutableIntStateOf(3) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("长期目标", style = MaterialTheme.typography.headlineSmall) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("health" to "健康", "career" to "事业", "learning" to "学习").forEach { (key, label) ->
                    FilterChip(selected = domain == key, onClick = { domain = key }, label = { Text(label) })
                }
            }
        }
        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("目标名称") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            OutlinedTextField(
                value = target,
                onValueChange = { target = it },
                label = { Text("可衡量的结果") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text("优先级")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..5).forEach { value ->
                    FilterChip(selected = priority == value, onClick = { priority = value }, label = { Text("$value") })
                }
            }
        }
        item {
            Button(
                onClick = {
                    onAddGoal(domain, title, target, priority)
                    title = ""
                    target = ""
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("添加目标", Modifier.padding(start = 8.dp))
            }
        }
        if (goals.isEmpty()) {
            item { Text("尚未添加目标。") }
        } else {
            items(goals, key = { it.id }) { goal ->
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(goal.title, style = MaterialTheme.typography.titleMedium)
                    Text("${goalDomainLabel(goal.domain)} · 优先级 ${goal.priority}")
                    if (goal.target.isNotBlank()) Text(goal.target, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider()
            }
        }
    }
}

private fun goalDomainLabel(value: String) = when (value) {
    "health" -> "健康"
    "career" -> "事业"
    "learning" -> "学习"
    else -> value
}
