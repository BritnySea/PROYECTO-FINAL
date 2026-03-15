package com.univalle.proyectov1.feature.dogs.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.univalle.proyectov1.feature.dogs.data.remote.WoofApiService
import com.univalle.proyectov1.feature.dogs.domain.model.Dog
import com.univalle.proyectov1.feature.dogs.domain.model.DogMatch
import com.univalle.proyectov1.feature.dogs.domain.repository.DogsRepository
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

class DogsRepositoryImpl @Inject constructor(
    private val api: WoofApiService,
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) : DogsRepository {

    private suspend fun getBearerToken(): String {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token
            ?: error("Usuario no autenticado")
        return "Bearer $token"
    }

    override suspend fun validatePhoto(photo: File): Result<String> {
        return try {
            val token = getBearerToken()
            val photoBody = photo.asRequestBody("image/*".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", photo.name, photoBody)
            val response = api.validatePhoto(token = token, photo = photoPart)
            if (response.isDog) Result.success(response.message)
            else Result.failure(Exception(response.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun registerLostDog(
        dogName: String,
        description: String,
        ownerName: String,
        ownerPhone: String,
        ownerEmail: String,
        photo: File,
        size: String,
        color: String,
        breed: String,
        lostLocation: String
    ): Result<String> {
        return try {
            val token = getBearerToken()
            val photoBody = photo.asRequestBody("image/*".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", photo.name, photoBody)

            val response = api.registerLostDog(
                token = token,
                dogName = dogName.toRequestBody("text/plain".toMediaTypeOrNull()),
                description = description.toRequestBody("text/plain".toMediaTypeOrNull()),
                ownerName = ownerName.toRequestBody("text/plain".toMediaTypeOrNull()),
                ownerPhone = ownerPhone.toRequestBody("text/plain".toMediaTypeOrNull()),
                ownerEmail = ownerEmail.toRequestBody("text/plain".toMediaTypeOrNull()),
                photo = photoPart,
                size = size.toRequestBody("text/plain".toMediaTypeOrNull()),
                color = color.toRequestBody("text/plain".toMediaTypeOrNull()),
                breed = breed.toRequestBody("text/plain".toMediaTypeOrNull()),
                lostLocation = lostLocation.toRequestBody("text/plain".toMediaTypeOrNull())
            )
            Result.success(response.dogId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun matchFoundDog(photo: File): Result<List<DogMatch>> {
        return try {
            val token = getBearerToken()
            val photoBody = photo.asRequestBody("image/*".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", photo.name, photoBody)

            val response = api.matchFoundDog(token = token, photo = photoPart)

            if (!response.isDog) {
                return Result.failure(Exception("La foto no muestra un perro"))
            }

            val matches = response.matches.map { m ->
                DogMatch(
                    dogId = m.dogId,
                    name = m.name,
                    ownerPhone = m.ownerPhone,
                    ownerEmail = m.ownerEmail,
                    similarityPercent = m.similarityPercent,
                    photoUrl = m.photoUrl
                )
            }
            Result.success(matches)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMyLostDogs(): List<Dog> {
        return try {
            val uid = auth.currentUser?.uid ?: return emptyList()
            val snapshot = db.collection("lost_dogs")
                .whereEqualTo("registered_by_uid", uid)
                .get()
                .await()
            snapshot.documents.mapNotNull { doc ->
                Dog(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    description = doc.getString("description") ?: "",
                    ownerName = doc.getString("owner_name") ?: "",
                    ownerPhone = doc.getString("owner_phone") ?: "",
                    ownerEmail = doc.getString("owner_email") ?: "",
                    photoUrl = doc.getString("photo_url") ?: "",
                    status = doc.getString("status") ?: "active",
                    createdAt = doc.getTimestamp("created_at")?.toDate()?.toString() ?: "",
                    uid = uid
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
