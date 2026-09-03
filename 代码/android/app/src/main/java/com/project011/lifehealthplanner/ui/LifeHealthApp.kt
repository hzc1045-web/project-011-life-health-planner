package com.project011.lifehealthplanner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.project011.lifehealthplanner.ui.screens.AiScreen
import com.project011.lifehealthplanner.ui.screens.GoalsScreen
import com.project011.lifehealthplanner.ui.screens.HealthScreen
import com.project011.lifehealthplanner.ui.screens.PlanScreen
import com.project011.lifehealthplanner.ui.screens.SettingsScreen
import com.project011.lifehealthplanner.ui.screens.TodayScreen

private enum class Destination(val label: String, val icon: ImageVector) {
    TODAY("今日", Icons.Default.Home),
    PLAN("计划", Icons.Default.CalendarMonth),
    GOALS("目标", Icons.Default.Flag),
    HEALTH("健康", Icons.Default.FavoriteBorder),
    AI("AI", Icons.Default.AutoAwesome),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifeHealthApp(viewModel: AppViewModel, state: AppUiState) {
    var destination by rememberSaveable { mutableStateOf(Destination.TODAY) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(destination.label) },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { item ->
                    NavigationBarItem(
                        selected = destination == item,
                        onClick = { destination = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(item.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize()) {
            when (destination) {
                Destination.TODAY -> TodayScreen(state, viewModel::checkIn, padding)
                Destination.PLAN -> PlanScreen(state, viewModel, padding)
                Destination.GOALS -> GoalsScreen(state.goals, viewModel::addGoal, padding)
                Destination.HEALTH -> HealthScreen(state, viewModel, padding)
                Destination.AI -> AiScreen(state, viewModel::sendChat, padding)
            }
            if (state.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
    if (showSettings) {
        SettingsScreen(state, viewModel, onDismiss = { showSettings = false })
    }
}
