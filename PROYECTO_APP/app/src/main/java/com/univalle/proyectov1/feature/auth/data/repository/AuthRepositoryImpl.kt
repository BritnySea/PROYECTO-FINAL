package com.univalle.proyectov1.feature.auth.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.univalle.proyectov1.feature.auth.domain.model.User
import com.univalle.proyectov1.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.tasks.await

class AuthRepositoryImpl(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : AuthRepository {

    // 1. REGISTRO MANUAL (Solo crea cuenta en Auth y envía verificación, NO guarda en Firestore)
    override suspend fun signUp(user: User, password: String): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(user.email, password).await()

            // Guardamos el nombre en el perfil de Firebase Auth para recuperarlo después
            result.user?.updateProfile(
                userProfileChangeRequest { displayName = user.name }
            )?.await()

            // Enviamos el correo de verificación
            result.user?.sendEmailVerification()?.await()

            // Cerramos sesión: el usuario NO debe quedar logueado sin verificar
            auth.signOut()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 2. INICIO DE SESIÓN MANUAL
    // Si el email está verificado y no existe documento en Firestore, lo crea ahora
    override suspend fun signIn(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()

            val firebaseUser = auth.currentUser
            if (firebaseUser != null && firebaseUser.isEmailVerified) {
                val uid = firebaseUser.uid
                val userExists = db.collection("users").document(uid).get().await().exists()
                if (!userExists) {
                    val newUser = User(
                        uid = uid,
                        name = firebaseUser.displayName ?: "Usuario",
                        email = firebaseUser.email ?: ""
                    )
                    db.collection("users").document(uid).set(newUser).await()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 3. INICIO DE SESIÓN CON GOOGLE (Nuevo metodo)
    override suspend fun signInWithGoogle(idToken: String): Result<Unit> {
        return try {
            // Convertimos el token de Google en una credencial de Firebase
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val result = auth.signInWithCredential(credential).await()

            // Si es la primera vez que entra con Google, lo guardamos en la base de datos
            val uid = result.user?.uid ?: throw Exception("Error al obtener UID de Google")
            val userExists = db.collection("users").document(uid).get().await().exists()

            if (!userExists) {



                
                val newUser = User(
                    uid = uid,
                    name = result.user?.displayName ?: "Usuario de Google",
                    email = result.user?.email ?: ""
                )
                db.collection("users").document(uid).set(newUser).await()
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 4. RECUPERAR CONTRASEÑA
    override suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 5. CERRAR SESIÓN
    override fun signOut() {
        auth.signOut()
    }

    // 6. VERIFICAR EMAIL (recarga el estado del usuario y devuelve si está verificado)
    override suspend fun checkEmailVerification(): Boolean {
        return try {
            val user = auth.currentUser ?: return false
            user.reload().await()
            user.isEmailVerified
        } catch (e: Exception) {
            false
        }
    }

    // 7. REENVIAR EMAIL DE VERIFICACIÓN
    override suspend fun resendVerificationEmail() {
        auth.currentUser?.sendEmailVerification()?.await()
    }

    // 8. VERIFICAR SI CUENTA EXISTENTE NO ESTÁ VERIFICADA Y REENVIAR CORREO
    override suspend fun signInAndResendVerification(email: String, password: String): Result<Boolean> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            val user = auth.currentUser
            val isVerified = user?.isEmailVerified == true
            if (!isVerified) {
                user?.sendEmailVerification()?.await()
            }
            auth.signOut()
            Result.success(!isVerified)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}