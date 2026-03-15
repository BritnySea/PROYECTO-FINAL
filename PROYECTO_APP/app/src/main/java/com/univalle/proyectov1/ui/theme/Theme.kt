package com.univalle.proyectov1.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// La app usa siempre tema oscuro con la paleta dorada del refugio
private val AppColorScheme = darkColorScheme(
    primary = Gold,
    secondary = GoldLight,
    background = DarkBackground,
    surface = DarkSurface,
    error = ErrorRed
)

@Composable
fun Proyectov1Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography = Typography,
        content = content
    )
}
