package com.project011.lifehealthplanner.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.project011.lifehealthplanner.domain.MedicationSafety
import com.project011.lifehealthplanner.ui.AppUiState
import com.project011.lifehealthplanner.ui.AppViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun HealthScreen(state: AppUiState, viewModel: AppViewModel, padding: PaddingValues) {
    var kind by remember { mutableStateOf("weight") }
    var value by remember { mutableStateOf("") }
    var medicationName by remember { mutableStateOf("") }
    var ingredient by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf("") }
    val permissionsLauncher = rememberLauncherForActivityResult(viewModel.healthPermissionContract()) { granted ->
        if (granted.containsAll(viewModel.healthPermissions())) viewModel.syncHealthConnect()
    }
    val allergies = remember(state.profile?.allergiesJson) {
        runCatching {
            @Suppress("UNCHECKED_CAST")
            Gson().fromJson(state.profile?.allergiesJson ?: "[]", List::class.java) as List<String>
        }.getOrDefault(emptyList())
    }
    val medicationWarnings = MedicationSafety.warnings(state.medications, allergies)
    val healthConnectAvailable = viewModel.healthConnectAvailable()

    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("健康记录", style = MaterialTheme.typography.headlineSmall)
            OutlinedButton(
                onClick = { permissionsLauncher.launch(viewModel.healthPermissions()) },
                enabled = healthConnectAvailable,
            ) {
                Icon(Icons.Default.Sync, contentDescription = null)
                Text("同步", Modifier.padding(start = 6.dp))
            }
        }
        if (!healthConnectAvailable) {
            Text("此设备未提供 Health Connect，可继续使用手工记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("weight" to "体重", "sleep" to "睡眠", "heart_rate" to "心率").forEach { (key, label) ->
                FilterChip(selected = kind == key, onClick = { kind = key }, label = { Text(label) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = value,
                onValueChange = { if (it.all { c -> c.isDigit() || c == '.' }) value = it },
                label = { Text("数值 (${unitFor(kind)})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    viewModel.addHealthRecord(kind, value, unitFor(kind))
                    value = ""
                },
                enabled = value.toDoubleOrNull() != null,
                modifier = Modifier.padding(top = 8.dp),
            ) { Icon(Icons.Default.Add, contentDescription = "添加健康记录") }
        }
        state.healthRecords.take(8).forEach { record ->
            val time = Instant.ofEpochMilli(record.observedAt).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("M月d日 HH:mm"))
            Text("${healthKindLabel(record.kind)}  ${"%.1f".format(record.value)} ${record.unit} · $time")
        }
        HorizontalDivider()
        Text("用药记录", style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = medicationName,
            onValueChange = { medicationName = it },
            label = { Text("药物名称") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ingredient,
                onValueChange = { ingredient = it },
                label = { Text("有效成分") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = dose,
                onValueChange = { dose = it },
                label = { Text("记录剂量") },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(
            value = schedule,
            onValueChange = { schedule = it },
            label = { Text("服用安排") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                viewModel.addMedication(medicationName, ingredient, dose, schedule)
                medicationName = ""
                ingredient = ""
                dose = ""
                schedule = ""
            },
            enabled = medicationName.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text("保存用药记录", Modifier.padding(start = 8.dp))
        }
        state.medications.forEach { medication ->
            Text("${medication.name} · ${medication.activeIngredient} · ${medication.schedule}")
        }
        medicationWarnings.forEach { warning ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                Text(warning, Modifier.fillMaxWidth().padding(10.dp), color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

private fun unitFor(kind: String) = when (kind) {
    "weight" -> "kg"
    "sleep" -> "小时"
    else -> "bpm"
}

private fun healthKindLabel(kind: String) = when (kind) {
    "weight" -> "体重"
    "sleep" -> "睡眠"
    "heart_rate" -> "心率"
    "resting_heart_rate" -> "静息心率"
    "steps" -> "步数"
    "distance" -> "距离"
    "active_calories" -> "活动热量"
    else -> kind
}
