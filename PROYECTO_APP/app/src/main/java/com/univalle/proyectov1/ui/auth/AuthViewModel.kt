package com.univalle.proyectov1.ui.auth

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.univalle.proyectov1.core.result.UiState
import com.univalle.proyectov1.feature.auth.domain.model.User
import com.univalle.proyectov1.feature.auth.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _signUpState = mutableStateOf<UiState<Unit>>(UiState.Idle)
    val signUpState: State<UiState<Unit>> = _signUpState

    private val _signInState = mutableStateOf<UiState<Unit>>(UiState.Idle)
    val signInState: State<UiState<Unit>> = _signInState

    private val _resetPasswordState = mutableStateOf<UiState<Unit>>(UiState.Idle)
    val resetPasswordState: State<UiState<Unit>> = _resetPasswordState

    // ==================== REGISTRO ====================
    fun onSignUp(user: User, password: String, confirmPassword: String) {
        viewModelScope.launch {
            if (user.name.isBlank()) {
                _signUpState.value = UiState.Error("El nombre no puede estar vacío")
                return@launch
            }
            if (user.email.isBlank()) {
                _signUpState.value = UiState.Error("El correo no puede estar vacío")
                return@launch
            }
            if (password.isBlank()) {
                _signUpState.value = UiState.Error("La contraseña no puede estar vacía")
                return@launch
            }
            if (confirmPassword.isBlank()) {
                _signUpState.value = UiState.Error("Debes confirmar tu contraseña")
                return@launch
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(user.email).matches()) {
                _signUpState.value = UiState.Error("El correo no tiene un formato válido")
                return@launch
            }
            if (password != confirmPassword) {
                _signUpState.value = UiState.Error("Las contraseñas no coinciden")
                return@launch
            }
            if (password.length < 8) {
                _signUpState.value = UiState.Error("La contraseña debe tener mínimo 8 caracteres")
                return@launch
            }

            val hasUpperCase = password.any { it.isUpperCase() }
            val hasLowerCase = password.any { it.isLowerCase() }
            val hasDigit = password.any { it.isDigit() }
            val hasSpecialChar = password.any { !it.isLetterOrDigit() }

            if (!hasUpperCase || !hasLowerCase || !hasDigit || !hasSpecialChar) {
                _signUpState.value = UiState.Error(
                    "🔒 Contraseña insegura. Debe contener:\n" +
                            "• Mayúsculas (A-Z)\n" +
                            "• Minúsculas (a-z)\n" +
                            "• Números (0-9)\n" +
                            "• Símbolos (!@#$%)"
                )
                return@launch
            }

            _signUpState.value = UiState.Loading
            try {
                val result = repository.signUp(user, password)
                result.onSuccess {
                    _signUpState.value = UiState.Success(Unit)
                }.onFailure { error ->
                    when (error) {
                        is FirebaseAuthWeakPasswordException ->
                            _signUpState.value = UiState.Error("La contraseña es demasiado débil")
                        is FirebaseAuthInvalidCredentialsException ->
                            _signUpState.value = UiState.Error("El correo tiene un formato inválido")
                        is FirebaseAuthUserCollisionException -> {
                            // La cuenta ya existe en Firebase. Verificar si está sin confirmar.
                            val checkResult = repository.signInAndResendVerification(user.email, password)
                            checkResult.onSuccess { isUnverified ->
                                if (isUnverified) {
                                    // Cuenta existente no verificada: reenviar correo y continuar normal
                                    _signUpState.value = UiState.Success(Unit)
                                } else {
                                    _signUpState.value = UiState.Error("Este correo ya está registrado. Intenta iniciar sesión.")
                                }
                            }.onFailure {
                                // Contraseña incorrecta: la cuenta pertenece a otro usuario
                                _signUpState.value = UiState.Error("Este correo ya está en uso. Si olvidaste tu contraseña, recupérala desde el inicio de sesión.")
                            }
                        }
                        else -> _signUpState.value = UiState.Error("No se pudo crear la cuenta. Intenta nuevamente.")
                    }
                }
            } catch (e: Exception) {
                _signUpState.value = UiState.Error("Error inesperado. Intenta nuevamente.")
            }
        }
    }

    // ==================== VERIFICAR EMAIL ====================
    fun checkEmailVerification(onVerified: () -> Unit, onNotVerified: () -> Unit) {
        viewModelScope.launch {
            _signUpState.value = UiState.Loading
            try {
                if (auth.currentUser == null) {
                    _signUpState.value = UiState.Error("⚠️ No hay sesión activa")
                    return@launch
                }
                if (repository.checkEmailVerification()) {
                    _signUpState.value = UiState.Success(Unit)
                    onVerified()
                } else {
                    _signUpState.value = UiState.Error(
                        "📧 Tu correo aún no ha sido verificado.\n" +
                                "Revisa tu bandeja de entrada o spam."
                    )
                    onNotVerified()
                }
            } catch (e: Exception) {
                _signUpState.value = UiState.Error("❌ Error al verificar el correo. Intenta nuevamente.")
            }
        }
    }

    // ==================== REENVIAR EMAIL ====================
    fun resendVerificationEmail() {
        viewModelScope.launch {
            try {
                repository.resendVerificationEmail()
                _signUpState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                _signUpState.value = UiState.Error("❌ Error al reenviar el correo de verificación. Intenta nuevamente.")
            }
        }
    }

    // ==================== LOGIN ====================
    fun onSignIn(email: String, password: String) {
        viewModelScope.launch {
            if (email.isBlank()) {
                _signInState.value = UiState.Error("⚠️ El correo no puede estar vacío")
                return@launch
            }
            if (password.isBlank()) {
                _signInState.value = UiState.Error("⚠️ La contraseña no puede estar vacía")
                return@launch
            }
            _signInState.value = UiState.Loading
            try {
                val result = repository.signIn(email, password)
                result.onSuccess {
                    if (auth.currentUser?.isEmailVerified == true) {
                        _signInState.value = UiState.Success(Unit)
                    } else {
                        _signInState.value = UiState.Error(
                            "📧 Debes verificar tu correo antes de iniciar sesión.\n" +
                                    "Revisa tu bandeja de entrada o carpeta de spam."
                        )
                        repository.signOut()
                    }
                }.onFailure { error ->
                    val mensajeError = when {
                        error.message == "CUENTA_BLOQUEADA" ->
                            "🚫 Tu cuenta ha sido bloqueada por uso indebido."
                        error is FirebaseAuthInvalidCredentialsException ->
                            "🔴 Correo o contraseña incorrectos"
                        else -> "Error al iniciar sesión. Intenta nuevamente."
                    }
                    _signInState.value = UiState.Error(mensajeError)
                }
            } catch (e: Exception) {
                _signInState.value = UiState.Error("❌ Error inesperado. Intenta nuevamente.")
            }
        }
    }

    // ==================== LOGIN CON GOOGLE ====================
    fun onGoogleSignIn(idToken: String) {
        viewModelScope.launch {
            _signInState.value = UiState.Loading
            try {
                val result = repository.signInWithGoogle(idToken)
                result.onSuccess {
                    _signInState.value = UiState.Success(Unit)
                }.onFailure { error ->
                    val mensaje = when {
                        error.message == "CUENTA_BLOQUEADA" ->
                            "🚫 Tu cuenta ha sido bloqueada por uso indebido."
                        error.message?.contains("network", ignoreCase = true) == true ||
                        error.message?.contains("Network", ignoreCase = true) == true ->
                            "Sin conexión a internet. Verifica tu red e intenta nuevamente."
                        error.message?.contains("timeout", ignoreCase = true) == true ->
                            "La conexión tardó demasiado. Intenta nuevamente."
                        else -> "No se pudo iniciar sesión con Google. Intenta nuevamente."
                    }
                    _signInState.value = UiState.Error(mensaje)
                }
            } catch (e: Exception) {
                _signInState.value = UiState.Error("Error inesperado. Intenta nuevamente.")
            }
        }
    }

    // ==================== RECUPERAR CONTRASEÑA ====================
    fun onResetPassword(email: String) {
        viewModelScope.launch {
            if (email.isBlank()) {
                _resetPasswordState.value = UiState.Error("⚠️ Ingresa tu correo electrónico")
                return@launch
            }
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                _resetPasswordState.value = UiState.Error("⚠️ El correo no tiene un formato válido")
                return@launch
            }
            _resetPasswordState.value = UiState.Loading
            try {
                val result = repository.sendPasswordReset(email)
                result.onSuccess {
                    _resetPasswordState.value = UiState.Success(Unit)
                }.onFailure { error ->
                    val mensaje = when {
                        error.message?.contains("user-not-found") == true ||
                        error.message?.contains("INVALID_EMAIL") == true ->
                            "🔴 No existe una cuenta con ese correo"
                        else -> "Error al enviar el correo. Intenta nuevamente."
                    }
                    _resetPasswordState.value = UiState.Error(mensaje)
                }
            } catch (e: Exception) {
                _resetPasswordState.value = UiState.Error("❌ Error inesperado. Intenta nuevamente.")
            }
        }
    }

    fun resetPasswordStateToIdle() {
        _resetPasswordState.value = UiState.Idle
    }

    // ==================== ERROR DE LOGIN ====================
    fun onSignInError(message: String) {
        _signInState.value = UiState.Error(message)
    }

    // ==================== ERROR DE REGISTRO ====================
    fun onSignUpError(message: String) {
        _signUpState.value = UiState.Error(message)
    }

    // ==================== RESETEAR ESTADOS ====================
    fun resetStates() {
        _signInState.value = UiState.Idle
        _signUpState.value = UiState.Idle
        _resetPasswordState.value = UiState.Idle
    }
}
