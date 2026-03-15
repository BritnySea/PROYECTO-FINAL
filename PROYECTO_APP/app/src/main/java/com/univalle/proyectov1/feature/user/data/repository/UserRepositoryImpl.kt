package com.univalle.proyectov1.feature.user.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.univalle.proyectov1.feature.user.domain.repository.UserRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : UserRepository {

    private val uid get() = auth.currentUser?.uid ?: error("Usuario no autenticado")

    override suspend fun getUserPhone(): String? {
        return try {
            val doc = db.collection("users").document(uid).get().await()
            doc.getString("phone")
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun updateUserPhone(phone: String): Result<Unit> {
        return try {
            db.collection("users").document(uid)
                .update("phone", phone)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun hasPhone(): Boolean {
        val phone = getUserPhone()
        return !phone.isNullOrBlank()
    }

    // Escucha en tiempo real si el admin bloquea la cuenta del usuario actual.
    // Cuando el usuario cierra sesión emite false. Se reactiva automáticamente al iniciar sesión.
    override fun observeBlockedStatus(): Flow<Boolean> = callbackFlow {
        var firestoreListener: ListenerRegistration? = null

        fun attach() {
            firestoreListener?.remove()
            val uid = auth.currentUser?.uid
            if (uid == null) {
                trySend(false)
                return
            }
            firestoreListener = db.collection("users").document(uid)
                .addSnapshotListener { snapshot, _ ->
                    trySend(snapshot?.getBoolean("isBlocked") ?: false)
                }
        }

        val authStateListener = FirebaseAuth.AuthStateListener { attach() }
        auth.addAuthStateListener(authStateListener)
        attach()

        awaitClose {
            firestoreListener?.remove()
            auth.removeAuthStateListener(authStateListener)
        }
    }
}
