package com.univalle.proyectov1.ui.profile

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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.univalle.proyectov1.core.result.UiState
import com.univalle.proyectov1.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun ProfileScreen(
    showPhoneBanner: Boolean = false,
    onSignOut: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val phone by viewModel.phone.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val phoneSaved by viewModel.phoneSaved.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()

    val goldGradient = Brush.horizontalGradient(colors = listOf(Gold, GoldLight))

    // Auto-dismiss success message after 3 segundos
    LaunchedEffect(saveState) {
        if (saveState is UiState.Success) {
            delay(3000)
            viewModel.resetSaveState()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // ─── Título ──────────────────────────────────────────────────────
            Text(
                text = "Mi perfil",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Banner teléfono requerido (solo si no tiene teléfono) ────────
            if (showPhoneBanner || !phoneSaved) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Gold.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Completa tu teléfono para hacer reportes",
                            color = GoldLight,
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // ─── Avatar ───────────────────────────────────────────────────────
            Box(contentAlignment = Alignment.BottomEnd) {
                Surface(
                    modifier = Modifier.size(120.dp),
                    shape = CircleShape,
                    border = BorderStroke(2.dp, Gold)
                ) {
                    if (!viewModel.photoUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = viewModel.photoUrl,
                            contentDescription = "Foto de perfil",
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
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Nombre y email (readonly) ────────────────────────────────────
            Text(
                text = viewModel.displayName,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = viewModel.email,
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ─── Sección teléfono ─────────────────────────────────────────────
            if (phoneSaved && !isEditing) {
                // Teléfono guardado: mostrar como tarjeta con ícono de editar
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Gold.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Número de celular",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                            Text(
                                text = phone,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        IconButton(onClick = viewModel::startEditing) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Editar teléfono",
                                tint = Gold
                            )
                        }
                    }
                }
            } else {
                // Modo edición: campo de texto + botón guardar
                OutlinedTextField(
                    value = phone,
                    onValueChange = viewModel::onPhoneChange,
                    label = { Text("Número de celular (8 dígitos)", color = Color.White.copy(alpha = 0.6f)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = Gold
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Gold,
                        unfocusedBorderColor = Gold.copy(alpha = 0.4f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Gold,
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                    ),
                    singleLine = true,
                    supportingText = {
                        Text(
                            text = "${phone.length}/8",
                            color = if (phone.length == 8) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.4f),
                            fontSize = 11.sp
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Botón guardar
                Button(
                    onClick = viewModel::savePhone,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp),
                    enabled = saveState !is UiState.Loading && phone.length == 8
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                if (phone.length == 8) goldGradient
                                else Brush.horizontalGradient(listOf(Gold.copy(alpha = 0.4f), GoldLight.copy(alpha = 0.4f))),
                                RoundedCornerShape(16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (saveState is UiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = TextOnGold,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = if (isEditing) "Guardar nuevo teléfono" else "Guardar teléfono",
                                color = TextOnGold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                        }
                    }
                }
            }

            // ─── Feedback guardar ─────────────────────────────────────────────
            when (val s = saveState) {
                is UiState.Success -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Numero de celular guardado exitosamente",
                            color = Color(0xFF4CAF50),
                            fontSize = 13.sp
                        )
                    }
                }
                is UiState.Error -> {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = ErrorRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(s.message, color = ErrorRed, fontSize = 13.sp)
                    }
                }
                else -> {}
            }

            Spacer(modifier = Modifier.height(40.dp))

            // ─── Botón cerrar sesión ──────────────────────────────────────────
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = 0.7f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = null,
                    tint = ErrorRed
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Cerrar sesión", color = ErrorRed, fontWeight = FontWeight.Medium)
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
