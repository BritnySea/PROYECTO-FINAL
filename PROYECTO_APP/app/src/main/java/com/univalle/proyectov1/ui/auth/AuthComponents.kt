package com.univalle.proyectov1.ui.auth

import android.content.Context
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.univalle.proyectov1.R
import com.univalle.proyectov1.ui.theme.*

// Lanza el flujo de Google Sign-In y devuelve el token al callback onSuccess,
// o el mensaje de error a onError. Elimina la duplicación entre Login y Registro.
suspend fun launchGoogleSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val googleOption = GetSignInWithGoogleOption.Builder(
        context.getString(R.string.default_web_client_id)
    ).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(googleOption)
        .build()
    try {
        val result = credentialManager.getCredential(context, request)
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
            onSuccess(idToken)
        }
    } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
        // El usuario canceló el selector de cuenta, no mostramos error
    } catch (e: androidx.credentials.exceptions.NoCredentialException) {
        Log.e("GoogleSignIn", "NoCredentialException: ${e.message}", e)
        onError("No hay cuentas de Google configuradas en este dispositivo")
    } catch (e: androidx.credentials.exceptions.GetCredentialUnknownException) {
        Log.e("GoogleSignIn", "UnknownException: ${e.message}", e)
        onError("Error desconocido (${e.message?.take(60)})")
    } catch (e: androidx.credentials.exceptions.GetCredentialException) {
        Log.e("GoogleSignIn", "CredentialException [${e.javaClass.simpleName}]: ${e.message}", e)
        onError("Error de credencial: ${e.message?.take(80)}")
    } catch (e: Exception) {
        Log.e("GoogleSignIn", "Exception [${e.javaClass.simpleName}]: ${e.message}", e)
        onError("Error [${e.javaClass.simpleName}]: ${e.message?.take(80)}")
    }
}

// Burbuja de notificación que se muestra en la parte inferior de la pantalla.
// Soporta mensajes de error (rojo), éxito (verde) e informativos de correo/contraseña (azul).
@Composable
fun NotificationBubbleInternal(text: String, isError: Boolean) {
    val bgColor = when {
        text.startsWith("📧") || text.startsWith("🔒") -> InfoBackground
        isError -> ErrorBackground
        else -> SuccessBackground
    }
    val textColor = when {
        text.startsWith("📧") || text.startsWith("🔒") -> InfoText
        isError -> ErrorText
        else -> SuccessText
    }
    val icon = when {
        text.startsWith("📧") -> Icons.Default.Email
        text.startsWith("🔒") -> Icons.Default.Lock
        isError -> Icons.Default.Error
        else -> Icons.Default.CheckCircle
    }
    // Elimina el emoji del inicio porque ya se muestra como icono
    val cleanText = text
        .removePrefix("🔴 ").removePrefix("🔴")
        .removePrefix("❌ ").removePrefix("❌")
        .removePrefix("⚠️ ").removePrefix("⚠️")
        .removePrefix("📧 ").removePrefix("📧")
        .removePrefix("🔒 ").removePrefix("🔒")
        .trim()

    Surface(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        border = BorderStroke(1.dp, textColor.copy(alpha = 0.3f)),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = textColor)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = cleanText,
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

// Barra visual de seguridad de la contraseña (4 segmentos).
@Composable
fun PasswordStrengthBarInternal(strength: Int, activeColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .background(
                        if (index < strength) activeColor else Color.White.copy(alpha = 0.1f),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

// Campo de texto reutilizable para formularios de autenticación.
// Soporta validación con mensaje de error y toggle de visibilidad para contraseñas.
@Composable
fun RegisterFieldInternal(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    placeholder: String,
    isPassword: Boolean = false,
    goldColor: Color,
    errorText: String? = null,
    onFocusLost: (() -> Unit)? = null,
    showPasswordToggle: Boolean = false
) {
    var isVisible by remember { mutableStateOf(false) }

    Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.5f))
    Spacer(modifier = Modifier.height(6.dp))
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { if (!it.isFocused) onFocusLost?.invoke() },
        shape = RoundedCornerShape(12.dp),
        isError = errorText != null,
        placeholder = { Text(placeholder, color = Color.White.copy(alpha = 0.2f), fontSize = 14.sp) },
        leadingIcon = {
            Icon(
                icon, null,
                tint = if (errorText != null) ErrorRed.copy(alpha = 0.7f) else goldColor.copy(alpha = 0.5f)
            )
        },
        trailingIcon = if (showPasswordToggle) ({
            IconButton(onClick = { isVisible = !isVisible }) {
                Icon(
                    imageVector = if (isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.3f)
                )
            }
        }) else null,
        visualTransformation = if (isPassword && !isVisible) PasswordVisualTransformation() else VisualTransformation.None,
        colors = authTextFieldColors(goldColor),
        singleLine = true
    )
    if (errorText != null) {
        Text(
            text = errorText,
            color = ErrorRed,
            fontSize = 11.sp,
            modifier = Modifier.padding(start = 8.dp, top = 3.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
    } else {
        Spacer(modifier = Modifier.height(16.dp))
    }
}

// Colores estándar para los TextField de autenticación.
@Composable
fun authTextFieldColors(goldColor: Color) = TextFieldDefaults.colors(
    focusedContainerColor = Color.Black.copy(alpha = 0.3f),
    unfocusedContainerColor = Color.Black.copy(alpha = 0.3f),
    focusedIndicatorColor = goldColor,
    unfocusedIndicatorColor = Color.Transparent,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    errorIndicatorColor = ErrorRed,
    errorContainerColor = Color.Black.copy(alpha = 0.3f),
    errorTextColor = Color.White,
    errorLeadingIconColor = ErrorRed.copy(alpha = 0.7f)
)
