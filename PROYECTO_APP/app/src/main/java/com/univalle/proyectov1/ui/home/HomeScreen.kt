package com.univalle.proyectov1.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.univalle.proyectov1.ui.theme.*

@Composable
fun HomeScreen(
    onNavigateToReportLost: () -> Unit,
    onNavigateToReportFound: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val user = FirebaseAuth.getInstance().currentUser
    val firstName = (user?.displayName ?: "Usuario").split(" ").first()
    val photoUrl = user?.photoUrl?.toString()

    val goldGradient = Brush.horizontalGradient(colors = listOf(Gold, GoldLight))

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // Fondo decorativo superior
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Gold.copy(alpha = 0.12f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(52.dp))

            // ─── Cabecera ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "¡Hola, $firstName!",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "¿Qué necesitas hoy?",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }

                // Avatar pequeño con clic a perfil
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    border = BorderStroke(2.dp, Gold),
                    onClick = onNavigateToProfile
                ) {
                    if (!photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = photoUrl,
                            contentDescription = "Perfil",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().background(DarkSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = Gold,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ─── Tarjeta: Mi perro se perdió ──────────────────────────────────
            ActionCard(
                title = "Mi perro se perdió",
                subtitle = "Regístralo para que podamos encontrarlo",
                buttonText = "Reportar",
                icon = Icons.Default.Pets,
                gradient = goldGradient,
                onClick = onNavigateToReportLost
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Tarjeta: Encontré un perro ───────────────────────────────────
            ActionCard(
                title = "Encontré un perro",
                subtitle = "Busca si alguien lo está buscando",
                buttonText = "Buscar coincidencias",
                icon = Icons.Default.Search,
                gradient = goldGradient,
                onClick = onNavigateToReportFound
            )

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@Composable
private fun ActionCard(
    title: String,
    subtitle: String,
    buttonText: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    gradient: Brush,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(Gold.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Gold,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(gradient, RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = buttonText,
                        color = TextOnGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}
