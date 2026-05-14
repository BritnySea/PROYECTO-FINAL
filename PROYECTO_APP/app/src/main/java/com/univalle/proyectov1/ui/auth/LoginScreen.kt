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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    viewModel: AuthViewModel = hiltViewModel(),
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    showBlockedMessage: Boolean = false,
    onBlockedMessageShown: () -> Unit = {},
    showEmailVerifiedSuccess: Boolean = false,
    onEmailVerifiedMessageShown: () -> Unit = {}
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var emailTouched by remember { mutableStateOf(false) }
    val signInState by viewModel.signInState
    val resetPasswordState by viewModel.resetPasswordState

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = androidx.credentials.CredentialManager.create(context)

    val goldGradient = Brush.horizontalGradient(colors = listOf(Gold, GoldLight))
    val emailError: String? = if (emailTouched && email.isNotEmpty() &&
        !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
        "Formato de correo no válido" else null

    // Navegar al Home cuando el login es exitoso
    LaunchedEffect(signInState) {
        if (signInState is UiState.Success) {
            onLoginSuccess()
        }
    }

    // Auto-ocultar error después de 5 segundos
    LaunchedEffect(signInState) {
        if (signInState is UiState.Error) {
            delay(5000)
            viewModel.resetStates()
        }
    }

    Box(modifier = Modifier
        .fillMaxSize()
        .background(DarkBackground)) {
        // Imagen de fondo
        Image(
            painter = painterResource(id = R.drawable.perrito),
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.55f)
                .align(Alignment.TopCenter),
            contentScale = ContentScale.Crop,
            alpha = 0.5f
        )

        // Degradado de fusión
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Transparent,
                            0.10f to Color.Transparent,
                            0.22f to DarkBackground.copy(alpha = 0.08f),
                            0.34f to DarkBackground.copy(alpha = 0.25f),
                            0.44f to DarkBackground.copy(alpha = 0.58f),
                            0.51f to DarkBackground.copy(alpha = 0.88f),
                            0.55f to DarkBackground,
                            1.00f to DarkBackground
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(80.dp))

            // LOGO
            Surface(
                modifier = Modifier.size(90.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_refugio),
                    contentDescription = null,
                    modifier = Modifier.padding(2.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "¡Bienvenido!",
                fontSize = 34.sp,
                fontWeight = FontWeight.Light,
                color = Color.White
            )
            Text(
                "Ingresa tus datos para continuar",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(40.dp))

            // TARJETA DE CRISTAL
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = DarkSurface.copy(alpha = 0.75f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "CORREO ELECTRÓNICO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = email,
                        onValueChange = { email = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (!it.isFocused) emailTouched = true },
                        shape = RoundedCornerShape(12.dp),
                        isError = emailError != null,
                        leadingIcon = {
                            Icon(
                                Icons.Default.Person,
                                null,
                                tint = if (emailError != null) ErrorRed.copy(alpha = 0.7f) else Gold.copy(alpha = 0.6f)
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                            focusedIndicatorColor = Gold,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            errorIndicatorColor = ErrorRed,
                            errorContainerColor = Color.Black.copy(alpha = 0.3f),
                            errorTextColor = Color.White,
                            errorLeadingIconColor = ErrorRed.copy(alpha = 0.7f)
                        )
                    )
                    if (emailError != null) {
                        Text(
                            text = emailError,
                            color = ErrorRed,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(start = 8.dp, top = 3.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "CONTRASEÑA",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            "¿Olvidaste?",
                            fontSize = 11.sp,
                            color = Gold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable { showResetDialog = true }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        value = password,
                        onValueChange = { password = it },
                        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(
                                Icons.Default.Lock,
                                null,
                                tint = Gold.copy(alpha = 0.6f)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                Icon(
                                    imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = null, tint = Color.White.copy(alpha = 0.3f)
                                )
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                            focusedIndicatorColor = Gold,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(30.dp))

                    // BOTÓN INICIAR SESIÓN
                    Button(
                        onClick = { viewModel.onSignIn(email, password) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .background(
                                goldGradient,
                                RoundedCornerShape(16.dp)
                            ), // Gradiente dorado
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                        enabled = signInState !is UiState.Loading
                    ) {
                        if (signInState is UiState.Loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = Color.Black
                            )
                        } else {
                            Text(
                                "INICIAR SESIÓN",
                                fontWeight = FontWeight.Bold,
                                color = TextOnGold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                "O INICIA SESIÓN CON",
                fontSize = 11.sp,
                color = Color.White.copy(alpha = 0.4f),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))

            IconButton(
                onClick = {
                    scope.launch {
                        launchGoogleSignIn(
                            context = context,
                            credentialManager = credentialManager,
                            onSuccess = { idToken -> viewModel.onGoogleSignIn(idToken) },
                            onError = { viewModel.onSignInError(it) }
                        )
                    }
                },
                modifier = Modifier
                    .size(54.dp)
                    .background(DarkSurface.copy(alpha = 0.75f), CircleShape)
                    .border(1.dp, Gold.copy(alpha = 0.3f), CircleShape) // Borde dorado sutil
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_google),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Row {
                Text("¿Eres nuevo? ", color = Color.White.copy(alpha = 0.5f), fontSize = 15.sp)
                Text(
                    text = "Crea una cuenta",
                    color = Gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable { onNavigateToRegister() }
                )
            }
            Spacer(modifier = Modifier.height(30.dp))
        }

        // BURBUJA DE NOTIFICACIÓN (Login)
        if (signInState is UiState.Error) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                NotificationBubbleInternal(
                    text = (signInState as UiState.Error).message,
                    isError = true
                )
            }
        }

        // BURBUJA DE NOTIFICACIÓN (Recuperar contraseña)
        val resetState = resetPasswordState
        if (resetState is UiState.Error || resetState is UiState.Success) {
            LaunchedEffect(resetState) {
                delay(5000)
                viewModel.resetPasswordStateToIdle()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 24.dp, bottom = 40.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                NotificationBubbleInternal(
                    text = when (resetState) {
                        is UiState.Success -> "📧 Se envió un correo para restablecer tu contraseña. Si no lo ves, revisa tu carpeta de spam."
                        is UiState.Error -> resetState.message
                        else -> ""
                    },
                    isError = resetState is UiState.Error
                )
            }
        }

        // BURBUJA DE CUENTA BLOQUEADA (cuando el admin expulsa al usuario mientras usa la app)
        if (showBlockedMessage) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(6000)
                onBlockedMessageShown()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 24.dp, bottom = 110.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                NotificationBubbleInternal(
                    text = "🚫 Tu cuenta ha sido bloqueada por uso indebido.",
                    isError = true
                )
            }
        }

        // BURBUJA CORREO VERIFICADO (cuando viene del deep link)
        if (showEmailVerifiedSuccess) {
            LaunchedEffect(Unit) {
                delay(5000)
                onEmailVerifiedMessageShown()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 24.dp, bottom = 110.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                NotificationBubbleInternal(
                    text = "✅ ¡Correo verificado! Ya puedes iniciar sesión.",
                    isError = false
                )
            }
        }

        // DIÁLOGO RECUPERAR CONTRASEÑA
        if (showResetDialog) {
            var resetEmail by remember { mutableStateOf(email) }

            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                containerColor = DarkSurface,
                shape = RoundedCornerShape(24.dp),
                title = {
                    Text(
                        "Recuperar contraseña",
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            "Ingresa tu correo y te enviaremos un enlace para restablecer tu contraseña.",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        TextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            placeholder = {
                                Text(
                                    "ejemplo@correo.com",
                                    color = Color.White.copy(alpha = 0.2f)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Email,
                                    null,
                                    tint = Gold.copy(alpha = 0.6f)
                                )
                            },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
                                focusedIndicatorColor = Gold,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.onResetPassword(resetEmail)
                            showResetDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Gold)
                    ) {
                        Text("Enviar", color = TextOnGold, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Cancelar", color = Color.White.copy(alpha = 0.5f))
                    }
                }
            )
        }
    }
}