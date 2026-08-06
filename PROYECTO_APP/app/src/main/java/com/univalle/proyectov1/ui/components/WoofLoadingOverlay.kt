package com.univalle.proyectov1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.univalle.proyectov1.ui.theme.Gold

/**
 * Overlay de carga semitransparente que cubre toda la pantalla.
 * Usar dentro de un Box con fillMaxSize para que se muestre sobre el contenido.
 */
@Composable
fun WoofLoadingOverlay(message: String = "Cargando...") {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Gold, strokeWidth = 3.dp)
            if (message.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = message, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}
