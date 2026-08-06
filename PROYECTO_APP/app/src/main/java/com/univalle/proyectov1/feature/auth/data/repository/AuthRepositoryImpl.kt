package com.univalle.proyectov1.feature.auth.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
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
            try {
                result.user?.updateProfile(
                    userProfileChangeRequest { displayName = user.name }
                )?.await()
            } catch (e: Exception) {
                Log.w("AuthRepo", "updateProfile falló (no crítico): ${e.message}")
            }

            // Enviamos el correo de verificación — si falla no cancelamos el registro
            try {
                result.user?.sendEmailVerification()?.await()
            } catch (e: Exception) {
                Log.w("AuthRepo", "sendEmailVerification falló (no crítico): ${e.message}")
            }

            // Cerramos sesión: el usuario NO debe quedar logueado sin verificar
            auth.signOut()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepo", "signUp falló: [${e.javaClass.simpleName}] ${e.message}")
            Result.failure(e)
        }
    }

    // 2. INICIO DE SESIÓN MANUAL
    // Si el email está verificado y no existe documento en Firestore, lo crea ahora
    override suspend fun signIn(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()

            // Forzar recarga del estado del usuario desde el servidor
            // (isEmailVerified puede estar desactualizado en caché local)
            try {
                auth.currentUser?.reload()?.await()
            } catch (_: Exception) {
                // reload puede fallar si el token fue revocado; signInWithEmailAndPassword ya autenticó
            }

            val firebaseUser = auth.currentUser
            if (firebaseUser != null && firebaseUser.isEmailVerified) {
                val uid = firebaseUser.uid
                try {
                    val userDoc = db.collection("users").document(uid).get(Source.SERVER).await()

                    if (userDoc.exists() && userDoc.getBoolean("isBlocked") == true) {
                        auth.signOut()
                        return Result.failure(Exception("CUENTA_BLOQUEADA"))
                    }

                    if (!userDoc.exists()) {
                        val newUser = User(
                            uid = uid,
                            name = firebaseUser.displayName ?: "Usuario",
                            email = firebaseUser.email ?: ""
                        )
                        db.collection("users").document(uid).set(newUser).await()
                    }
                } catch (e: Exception) {
                    Log.w("AuthRepo", "Firestore post-signIn falló (no crítico): ${e.message}")
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

            try {
                val userDoc = db.collection("users").document(uid).get(Source.SERVER).await()

                if (userDoc.exists() && userDoc.getBoolean("isBlocked") == true) {
                    auth.signOut()
                    return Result.failure(Exception("CUENTA_BLOQUEADA"))
                }

                if (!userDoc.exists()) {
                    val newUser = User(
                        uid = uid,
                        name = result.user?.displayName ?: "Usuario de Google",
                        email = result.user?.email ?: ""
                    )
                    db.collection("users").document(uid).set(newUser).await()
                }
            } catch (e: Exception) {
                // Si Firestore falla pero Auth tuvo éxito, permitimos el login de todas formas
                Log.e("AuthRepo", "Firestore post-Google falló (no crítico): [${e.javaClass.simpleName}] ${e.message}")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepo", "signInWithGoogle falló: [${e.javaClass.simpleName}] ${e.message}", e)
            Result.failure(Exception("[${e.javaClass.simpleName}] ${e.message}", e))
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

    // 9. CONFIRMAR NUEVA CONTRASEÑA (desde enlace de recuperación)
    override suspend fun confirmPasswordReset(oobCode: String, newPassword: String): Result<Unit> {
        return try {
            auth.confirmPasswordReset(oobCode, newPassword).await()
            // Cerrar sesión para que el usuario entre limpio con la nueva contraseña
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}