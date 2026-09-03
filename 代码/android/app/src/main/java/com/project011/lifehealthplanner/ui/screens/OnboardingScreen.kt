package com.project011.lifehealthplanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun OnboardingScreen(
    loading: Boolean,
    message: String?,
    onSave: (
        String,
        String,
        String,
        Double?,
        Double?,
        String,
        String,
        String,
        String,
        String,
        String,
        Double,
        Double?,
    ) -> Unit,
) {
    var displayName by remember { mutableStateOf("") }
    var birthDate by remember { mutableStateOf("") }
    var ageBand by remember { mutableStateOf("30-39") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("中国") }
    var emergency by remember { mutableStateOf("120") }
    var conditions by remember { mutableStateOf("") }
    var allergies by remember { mutableStateOf("") }
    var preferences by remember { mutableStateOf("") }
    var workSchedule by remember { mutableStateOf("") }
    var sleepHours by remember { mutableStateOf("8") }
    var weeklyBudget by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("建立你的个人画像", style = MaterialTheme.typography.headlineMedium)
        Text("这些完整资料只保存在手机。发送给 AI 前会生成去标识摘要并再次确认。")
        Field("称呼（仅本地）", displayName) { displayName = it }
        Field("出生日期（YYYY-MM-DD）", birthDate) { birthDate = it }
        Text("年龄段")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("18-29", "30-39", "40-49", "50-59").forEach {
                FilterChip(selected = ageBand == it, onClick = { ageBand = it }, label = { Text(it) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericField("身高 cm", height, { height = it }, Modifier.weight(1f))
            NumericField("体重 kg", weight, { weight = it }, Modifier.weight(1f))
        }
        Field("国家或地区", region) { region = it }
        Field("当地急救电话", emergency) { emergency = it }
        Field("既往病史或健康限制（逗号分隔）", conditions) { conditions = it }
        Field("过敏史（逗号分隔）", allergies) { allergies = it }
        Field("生活偏好（逗号分隔）", preferences) { preferences = it }
        Field("固定工作或学习时间", workSchedule) { workSchedule = it }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericField("目标睡眠小时", sleepHours, { sleepHours = it }, Modifier.weight(1f))
            NumericField("每周可用预算", weeklyBudget, { weeklyBudget = it }, Modifier.weight(1f))
        }
        Text(
            "本软件不提供疾病诊断、处方或药物剂量调整；紧急情况请联系当地急救服务。",
            color = MaterialTheme.colorScheme.error,
        )
        message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Button(
            enabled = !loading && region.isNotBlank() && emergency.isNotBlank(),
            onClick = {
                onSave(
                    displayName,
                    birthDate,
                    ageBand,
                    height.toDoubleOrNull(),
                    weight.toDoubleOrNull(),
                    region,
                    emergency,
                    conditions,
                    allergies,
                    preferences,
                    workSchedule,
                    sleepHours.toDoubleOrNull() ?: 8.0,
                    weeklyBudget.toDoubleOrNull(),
                )
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) { Text("保存并进入应用") }
    }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun NumericField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) onValueChange(it) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
