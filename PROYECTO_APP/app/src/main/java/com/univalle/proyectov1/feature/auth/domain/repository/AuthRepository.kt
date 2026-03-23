package com.univalle.proyectov1.feature.auth.domain.repository

import com.univalle.proyectov1.feature.auth.domain.model.User

interface AuthRepository {
    suspend fun signUp(user: User, password: String): Result<Unit>
    suspend fun signIn(email: String, password: String): Result<Unit>
    suspend fun signInWithGoogle(idToken: String): Result<Unit>
    suspend fun sendPasswordReset(email: String): Result<Unit>
    suspend fun checkEmailVerification(): Boolean
    suspend fun resendVerificationEmail()
    fun signOut()
    // Verifica si una cuenta existente no está verificada y reenvía el correo.
    // Retorna Result.success(true) si no está verificada (correo reenviado),
    // Result.success(false) si ya está verificada,
    // Result.failure si las credenciales no coinciden.
    suspend fun signInAndResendVerification(email: String, password: String): Result<Boolean>
    suspend fun confirmPasswordReset(oobCode: String, newPassword: String): Result<Unit>
}