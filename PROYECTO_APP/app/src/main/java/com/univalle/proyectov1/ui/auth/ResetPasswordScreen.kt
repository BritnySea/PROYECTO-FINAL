package com.univalle.proyectov1.ui.auth

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.univalle.proyectov1.R
import com.univalle.proyectov1.core.result.UiState
import com.univalle.proyectov1.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun ResetPasswordScreen(
    oobCode: String,
    onSuccess: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordTouched by remember { mutableStateOf(false) }
    var confirmPasswordTouched by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }

    val confirmResetState by viewModel.confirmResetState

    val passwordError: String? = if (passwordTouched && password.isNotEmpty() && password.length < 8)
        "Mínimo 8 caracteres requeridos" else null
    val confirmPasswordError: String? = if (confirmPasswordTouched && confirmPassword.isNotEmpty() &&
        confirmPassword != password) "Las contraseñas no coinciden" else null

    val passwordStrength = remember(password) {
        var strength = 0
        if (password.length >= 8) strength++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) strength++
        if (password.any { it.isDigit() }) strength++
        if (password.any { !it.isLetterOrDigit() } && password.isNotEmpty()) strength++
        strength
    }
    val strengthColor = when (passwordStrength) {
        1 -> Color(0xFFEF4444)
        2 -> Color(0xFFF97316)
        3 -> Color(0xFFEAB308)
        4 -> Color(0xFF22C55E)
        else -> Color.Transparent
    }
    val strengthLabel = when (passwordStrength) {
        1 -> "Muy débil"
        2 -> "Débil"
        3 -> "Buena"
        4 -> "Fuerte"
        else -> ""
    }

    LaunchedEffect(Unit) {
        delay(100)
        visible = true
    }

    LaunchedEffect(confirmResetState) {
        if (confirmResetState is UiState.Success) {
            delay(2000)
            viewModel.resetStates()
            onSuccess()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 })
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Logo
                Surface(
                    modifier = Modifier.size(90.dp),
                    shape = RoundedCornerShape(50),
                    color = DarkSurface.copy(alpha = 0.7f),
                    border = BorderStroke(1.5.dp, Gold.copy(alpha = 0.4f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.logo_refugio),
                            contentDescription = null,
                            modifier = Modifier.size(70.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Nueva contraseña",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Elige una contraseña segura para tu cuenta",
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                // Card con formulario
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = DarkSurface.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {

                        Text(
                            "Crear nueva contraseña",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White.copy(alpha = 0.1f)
                        )

                        RegisterFieldInternal(
                            label = "NUEVA CONTRASEÑA",
                            value = password,
                            onValueChange = { password = it },
                            icon = Icons.Default.Lock,
                            placeholder = "Mínimo 8 caracteres",
                            isPassword = true,
                            goldColor = Gold,
                            errorText = passwordError,
                            onFocusLost = { passwordTouched = true },
                            showPasswordToggle = true
                        )

                        if (password.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Seguridad:",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                                Text(
                                    text = strengthLabel,
                                    fontSize = 11.sp,
                                    color = strengthColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            PasswordStrengthBarInternal(
                                strength = passwordStrength,
                                activeColor = strengthColor
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        RegisterFieldInternal(
                            label = "CONFIRMAR CONTRASEÑA",
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it },
                            icon = Icons.Default.Lock,
                            placeholder = "Repite la contraseña",
                            isPassword = true,
                            goldColor = Gold,
                            errorText = confirmPasswordError,
                            onFocusLost = { confirmPasswordTouched = true },
                            showPasswordToggle = true
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Requisitos de contraseña
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            PasswordRequirement("Mínimo 8 caracteres", password.length >= 8)
                            PasswordRequirement("Mayúsculas y minúsculas", password.any { it.isUpperCase() } && password.any { it.isLowerCase() })
                            PasswordRequirement("Al menos un número", password.any { it.isDigit() })
                            PasswordRequirement("Al menos un símbolo (!@#...)", password.any { !it.isLetterOrDigit() } && password.isNotEmpty())
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Botón
                        val isLoading = confirmResetState is UiState.Loading
                        val isFormValid = password.length >= 8 && password == confirmPassword

                        Button(
                            onClick = {
                                passwordTouched = true
                                confirmPasswordTouched = true
                                if (isFormValid) {
                                    viewModel.confirmPasswordReset(oobCode, password, confirmPassword)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent
                            ),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        brush = Brush.horizontalGradient(
                                            colors = if (isFormValid && !isLoading)
                                                listOf(Gold, GoldLight)
                                            else
                                                listOf(
                                                    Color.White.copy(alpha = 0.15f),
                                                    Color.White.copy(alpha = 0.15f)
                                                )
                                        ),
                                        shape = RoundedCornerShape(14.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color.White,
                                        strokeWidth = 2.5.dp
                                    )
                                } else {
                                    Text(
                                        "Cambiar contraseña",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        color = if (isFormValid) Color(0xFF0A0A0A) else Color.White.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Burbuja de estado
                when (val state = confirmResetState) {
                    is UiState.Error -> NotificationBubbleInternal(text = state.message, isError = true)
                    is UiState.Success -> NotificationBubbleInternal(
                        text = "✅ ¡Contraseña cambiada! Redirigiendo al inicio de sesión...",
                        isError = false
                    )
                    else -> {}
                }
            }
        }
    }
}

@Composable
private fun PasswordRequirement(text: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (met) "✓" else "○",
            fontSize = 12.sp,
            color = if (met) Color(0xFF22C55E) else Color.White.copy(alpha = 0.3f),
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (met) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.3f)
        )
    }
}
