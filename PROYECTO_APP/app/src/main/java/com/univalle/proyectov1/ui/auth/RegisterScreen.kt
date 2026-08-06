package com.univalle.proyectov1.ui.auth

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.univalle.proyectov1.R
import com.univalle.proyectov1.core.result.UiState
import com.univalle.proyectov1.ui.theme.*
import com.univalle.proyectov1.feature.auth.domain.model.User
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun RegisterScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onRegisterSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    onGoogleSignInSuccess: () -> Unit = {}
) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var emailTouched by remember { mutableStateOf(false) }
    var passwordTouched by remember { mutableStateOf(false) }
    var confirmPasswordTouched by remember { mutableStateOf(false) }

    val signUpState by viewModel.signUpState
    val signInState by viewModel.signInState
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = androidx.credentials.CredentialManager.create(context)

    // Controla si el botón "IR A INICIAR SESIÓN" se muestra (persiste hasta que el usuario lo presione)
    var registrationSuccess by remember { mutableStateOf(false) }
    // Controla si la burbuja de éxito se muestra (desaparece sola después de unos segundos)
    var showSuccessBubble by remember { mutableStateOf(false) }

    LaunchedEffect(signUpState) {
        if (signUpState is UiState.Success) {
            registrationSuccess = true
            showSuccessBubble = true
            delay(5000)
            showSuccessBubble = false
            viewModel.resetStates()
        }
    }

    // Cuando Google Sign-In tiene éxito en la pantalla de registro, ir directo al Home
    LaunchedEffect(signInState) {
        if (signInState is UiState.Success) {
            viewModel.resetStates()
            onGoogleSignInSuccess()
        }
    }

    val goldGradient = Brush.horizontalGradient(colors = listOf(Gold, GoldLight))
    val emailError: String? = if (emailTouched && email.isNotEmpty() &&
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
        "Formato de correo no válido" else null
    val passwordError: String? = if (passwordTouched && password.isNotEmpty() && password.length < 8)
        "Mínimo 8 caracteres requeridos" else null
    val confirmPasswordError: String? = if (confirmPasswordTouched && confirmPassword.isNotEmpty() &&
        confirmPassword != password) "Las contraseñas no coinciden" else null

    // LÓGICA DE FUERZA DE CONTRASEÑA
    val passwordStrength = remember(password) {
        var strength = 0
        if (password.length >= 8) strength++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) strength++
        if (password.any { it.isDigit() }) strength++
        if (password.any { !it.isLetterOrDigit() } && password.isNotEmpty()) strength++
        strength
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        // FONDO PERRITO
        Image(
            painter = painterResource(id = R.drawable.perrito),
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.5f).align(Alignment.TopCenter),
            contentScale = ContentScale.Crop,
            alpha = 0.4f
        )

        // DEGRADADO FUSIÓN
        Box(modifier = Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Transparent,
                    0.10f to Color.Transparent,
                    0.20f to DarkBackground.copy(alpha = 0.08f),
                    0.32f to DarkBackground.copy(alpha = 0.25f),
                    0.41f to DarkBackground.copy(alpha = 0.58f),
                    0.47f to DarkBackground.copy(alpha = 0.88f),
                    0.50f to DarkBackground,
                    1.00f to DarkBackground
                )
            )
        ))

        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // LOGO
            Surface(modifier = Modifier.size(80.dp), shape = CircleShape, color = Color.White.copy(alpha = 0.1f), border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))) {
                Image(painter = painterResource(id = R.drawable.logo_refugio), contentDescription = null, modifier = Modifier.padding(2.dp), contentScale = ContentScale.Fit)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Refugio WOOF", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)

            Spacer(modifier = Modifier.height(35.dp))

            // TARJETA DE REGISTRO (EL CUADRADO)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = DarkSurface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text("Crear cuenta", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color.White.copy(alpha = 0.1f))

                    // Campos personalizados
                    RegisterFieldInternal(label = "Nombre completo", value = name, onValueChange = { name = it }, icon = Icons.Default.Person, placeholder = "Tu nombre", goldColor = Gold)
                    RegisterFieldInternal(label = "Correo electrónico", value = email, onValueChange = { email = it }, icon = Icons.Default.Email, placeholder = "ejemplo@correo.com", goldColor = Gold, errorText = emailError, onFocusLost = { emailTouched = true })

                    Text("Contraseña", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(6.dp))
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) passwordTouched = true },
                        shape = RoundedCornerShape(12.dp),
                        isError = passwordError != null,
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = if (passwordError != null) ErrorRed.copy(alpha = 0.7f) else Gold.copy(alpha = 0.5f)) },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = Color.White.copy(alpha = 0.3f))
                            }
                        },
                        colors = authTextFieldColors(Gold)
                    )
                    if (passwordError != null) {
                        Text(
                            text = passwordError,
                            color = ErrorRed,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 3.dp)
                        )
                    }

                    // BARRA DE SEGURIDAD
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SEGURIDAD", fontSize = 9.sp, color = Color.White.copy(alpha = 0.4f))
                        Text(text = when(passwordStrength) { 0, 1 -> "DÉBIL"; 2, 3 -> "MEDIA"; else -> "FUERTE" }, fontSize = 9.sp, color = Gold, fontWeight = FontWeight.Bold)
                    }
                    PasswordStrengthBarInternal(strength = passwordStrength, activeColor = Gold)

                    Spacer(modifier = Modifier.height(12.dp))
                    RegisterFieldInternal(label = "Confirmar contraseña", value = confirmPassword, onValueChange = { confirmPassword = it }, icon = Icons.Default.Refresh, placeholder = "********", isPassword = true, goldColor = Gold, errorText = confirmPasswordError, onFocusLost = { confirmPasswordTouched = true }, showPasswordToggle = true)

                    // Requisitos de contraseña
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        PasswordRequirementItem("Mínimo 8 caracteres", password.length >= 8)
                        PasswordRequirementItem("Mayúsculas y minúsculas", password.any { it.isUpperCase() } && password.any { it.isLowerCase() })
                        PasswordRequirementItem("Al menos un número", password.any { it.isDigit() })
                        PasswordRequirementItem("Al menos un símbolo (!@#...)", password.any { !it.isLetterOrDigit() } && password.isNotEmpty())
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            viewModel.onSignUp(
                                user = User(name = name, email = email),
                                password = password,
                                confirmPassword = confirmPassword
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(goldGradient, RoundedCornerShape(16.dp)),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        enabled = signUpState !is UiState.Loading
                    ) {
                        if (signUpState is UiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                        } else {
                            Text("CREAR CUENTA", fontWeight = FontWeight.Bold, color = TextOnGold)
                        }
                    }

                    // Después de registrarse, indicar al usuario que vaya a login
                    if (registrationSuccess) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { onNavigateToLogin() },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Gold),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text("IR A INICIAR SESIÓN", color = Gold, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text("O CONTINÚA CON", fontSize = 10.sp, color = Color.White.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(12.dp))

            // Google Login
            IconButton(
                onClick = {
                    scope.launch {
                        launchGoogleSignIn(
                            context = context,
                            credentialManager = credentialManager,
                            onSuccess = { idToken -> viewModel.onGoogleSignIn(idToken) },
                            onError = { viewModel.onSignUpError(it) }
                        )
                    }
                },
                modifier = Modifier.size(54.dp).background(DarkSurface.copy(alpha = 0.85f), CircleShape).border(1.dp, Gold.copy(alpha = 0.3f), CircleShape)
            ) {
                Icon(painter = painterResource(id = R.drawable.ic_google), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
            Row {
                Text("¿Ya tienes una cuenta? ", color = Color.White.copy(alpha = 0.5f))
                Text("Iniciar sesión", color = Gold, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { onNavigateToLogin() })
            }
            Spacer(modifier = Modifier.height(30.dp))
        }

        // Auto-ocultar errores después de 5 segundos
        LaunchedEffect(signUpState) {
            if (signUpState is UiState.Error) {
                delay(5000)
                viewModel.resetStates()
            }
        }
        LaunchedEffect(signInState) {
            if (signInState is UiState.Error) {
                delay(5000)
                viewModel.resetStates()
            }
        }

        // BURBUJAS DE NOTIFICACIÓN
        Box(Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, bottom = 40.dp), contentAlignment = Alignment.BottomCenter) {
            val errorFromGoogle = signInState as? UiState.Error
            when {
                errorFromGoogle != null -> NotificationBubbleInternal(text = errorFromGoogle.message, isError = true)
                signUpState is UiState.Error -> NotificationBubbleInternal(text = (signUpState as UiState.Error).message, isError = true)
                showSuccessBubble -> NotificationBubbleInternal(text = "¡Correo de verificación enviado! Si no lo ves, revisa tu carpeta de spam.", isError = false)
            }
        }
    }
}

@Composable
private fun PasswordRequirementItem(text: String, met: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = if (met) "✓" else "○",
            fontSize = 12.sp,
            color = if (met) Color(0xFF22C55E) else Color.White.copy(alpha = 0.3f),
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            fontSize = 12.sp,
            color = if (met) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.3f)
        )
    }
}
