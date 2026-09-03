package com.project011.lifehealthplanner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Color(0xFF176B5B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5ECE5),
    onPrimaryContainer = Color(0xFF073C32),
    secondary = Color(0xFF315D91),
    secondaryContainer = Color(0xFFDCE8F8),
    tertiary = Color(0xFF8A5A12),
    error = Color(0xFFB3261E),
    background = Color(0xFFF9FAF9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE9EEEB),
    outline = Color(0xFF747B77),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9DD2C5),
    primaryContainer = Color(0xFF075044),
    secondary = Color(0xFFABC7ED),
    tertiary = Color(0xFFF5BD66),
    error = Color(0xFFFFB4AB),
    background = Color(0xFF111513),
    surface = Color(0xFF171C19),
    surfaceVariant = Color(0xFF303633),
)

@Composable
fun LifeHealthTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= 31 && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= 31 -> dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
