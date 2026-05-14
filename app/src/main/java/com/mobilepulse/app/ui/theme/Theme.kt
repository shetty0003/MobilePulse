package com.mobilepulse.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import com.mobilepulse.app.ui.viewmodel.ThemeViewModel

// ── Forest (default) ──────────────────────────────────────────────────────────
private val ForestColorScheme = darkColorScheme(
    primary          = Primary,
    onPrimary        = OnPrimary,
    background       = Background,
    surface          = Surface,
    surfaceVariant   = CardColor,
    onBackground     = TextPrimary,
    onSurface        = TextPrimary,
    error            = Danger,
    secondary        = Accent,
    onSecondary      = Background,
    outline          = Border,
)

// ── Classic dark (navy/blue) ──────────────────────────────────────────────────
private val DarkColorScheme = darkColorScheme(
    primary          = Color(0xFF4F8EF7),
    onPrimary        = Color(0xFFFFFFFF),
    background       = Color(0xFF0A0E1A),
    surface          = Color(0xFF111827),
    onBackground     = Color(0xFFF1F5F9),
    onSurface        = Color(0xFFF1F5F9),
    error            = Color(0xFFEF4444),
    secondary        = Color(0xFF818CF8),
)

// ── Light (warm cream + forest green accents) ─────────────────────────────────
private val LightColorScheme = lightColorScheme(
    primary          = Color(0xFF2D7A50),
    onPrimary        = Color(0xFFFFFFFF),
    background       = Color(0xFFF5F0E4),
    surface          = Color(0xFFFFFDF5),
    onBackground     = Color(0xFF1A2E1E),
    onSurface        = Color(0xFF1A2E1E),
    error            = Color(0xFFEF4444),
    secondary        = Color(0xFF8B6914),
)

@Composable
fun MobilePulseTheme(content: @Composable () -> Unit) {
    val themeVm: ThemeViewModel = hiltViewModel()
    val theme by themeVm.theme.collectAsState(initial = AppTheme.FOREST)

    val colorScheme = when (theme) {
        AppTheme.FOREST -> ForestColorScheme
        AppTheme.DARK   -> DarkColorScheme
        AppTheme.LIGHT  -> LightColorScheme
        AppTheme.SYSTEM -> if (isSystemInDarkTheme()) ForestColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content
    )
}
