package com.univalle.proyectov1.feature.dogs.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale
import com.univalle.proyectov1.feature.dogs.data.remote.WoofApiService
import com.univalle.proyectov1.feature.dogs.domain.model.Dog
import com.univalle.proyectov1.feature.dogs.domain.model.DogMatch
import com.univalle.proyectov1.feature.dogs.domain.model.FoundDogMatchForOwner
import com.univalle.proyectov1.feature.dogs.domain.model.MyReport
import com.univalle.proyectov1.feature.dogs.domain.model.ReportType
import com.univalle.proyectov1.feature.dogs.domain.repository.DogsRepository
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.HttpException
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

    override suspend fun validateLostPhoto(photo: File): Result<String> {
        return try {
            val token = getBearerToken()
            val photoBody = photo.asRequestBody("image/*".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", photo.name, photoBody)
            val response = api.validateLostPhoto(token = token, photo = photoPart)
            if (response.isDog) Result.success(response.message)
            else Result.failure(Exception(response.message))
        } catch (e: HttpException) {
            val detail = parseErrorDetail(e)
            Result.failure(Exception(detail))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun validateFoundPhoto(photo: File): Result<String> {
        return try {
            val token = getBearerToken()
            val photoBody = photo.asRequestBody("image/*".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", photo.name, photoBody)
            val response = api.validateFoundPhoto(token = token, photo = photoPart)
            if (response.isDog) Result.success(response.message)
            else Result.failure(Exception(response.message))
        } catch (e: HttpException) {
            val detail = parseErrorDetail(e)
            Result.failure(Exception(detail))
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
        lostLocation: String,
        sex: String
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
            // Guardar sex directamente en Firestore ya que el campo no está en la API
            if (sex.isNotBlank()) {
                db.collection("lost_dogs").document(response.dogId)
                    .update("sex", sex).await()
            }
            Result.success(response.dogId)
        } catch (e: HttpException) {
            val detail = parseErrorDetail(e)
            Result.failure(Exception(detail))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun matchFoundDog(
        photo: File,
        size: String,
        color: String,
        sex: String,
        description: String,
        reporterName: String,
        reporterPhone: String,
        reporterEmail: String
    ): Result<List<DogMatch>> {
        return try {
            val token = getBearerToken()
            val photoBody = photo.asRequestBody("image/*".toMediaTypeOrNull())
            val photoPart = MultipartBody.Part.createFormData("photo", photo.name, photoBody)

            val response = api.matchFoundDog(
                token = token,
                photo = photoPart,
                size = size.toRequestBody("text/plain".toMediaTypeOrNull()),
                color = color.toRequestBody("text/plain".toMediaTypeOrNull()),
                sex = sex.toRequestBody("text/plain".toMediaTypeOrNull()),
                description = description.toRequestBody("text/plain".toMediaTypeOrNull()),
                reporterName = reporterName.toRequestBody("text/plain".toMediaTypeOrNull()),
                reporterPhone = reporterPhone.toRequestBody("text/plain".toMediaTypeOrNull()),
                reporterEmail = reporterEmail.toRequestBody("text/plain".toMediaTypeOrNull()),
            )

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
        } catch (e: HttpException) {
            val detail = parseErrorDetail(e)
            Result.failure(Exception(detail))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorDetail(e: HttpException): String {
        return try {
            val body = e.response()?.errorBody()?.string() ?: ""
            JSONObject(body).getString("detail")
        } catch (_: Exception) {
            "Error al procesar la solicitud"
        }
    }

    override suspend fun getMyLostDogs(): List<Dog> {
        return try {
            val uid = auth.currentUser?.uid ?: return emptyList()
            val snapshot = db.collection("lost_dogs")
                .whereEqualTo("registered_by_uid", uid)
                .get()
                .await()
            val dateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "ES"))
            snapshot.documents.mapNotNull { doc ->
                val date = doc.getTimestamp("created_at")?.toDate()
                Dog(
                    id = doc.id,
                    name = doc.getString("name") ?: "",
                    description = doc.getString("description") ?: "",
                    ownerName = doc.getString("owner_name") ?: "",
                    ownerPhone = doc.getString("owner_phone") ?: "",
                    ownerEmail = doc.getString("owner_email") ?: "",
                    photoUrl = doc.getString("photo_url") ?: "",
                    status = doc.getString("status") ?: "active",
                    createdAt = if (date != null) dateFormat.format(date) else "",
                    uid = uid,
                    size = doc.getString("size") ?: "",
                    color = doc.getString("color") ?: "",
                    breed = doc.getString("breed") ?: "",
                    sex = doc.getString("sex") ?: ""
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun getMyReports(): List<MyReport> {
        val uid = auth.currentUser?.uid ?: return emptyList()
        val dateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale("es", "ES"))

        val lostReports = try {
            val snapshot = db.collection("lost_dogs")
                .whereEqualTo("registered_by_uid", uid)
                .get().await()
            snapshot.documents.map { doc ->
                val date = doc.getTimestamp("created_at")?.toDate()
                MyReport(
                    id = doc.id,
                    type = ReportType.LOST,
                    photoUrl = doc.getString("photo_url") ?: "",
                    status = doc.getString("status") ?: "active",
                    createdAt = if (date != null) dateFormat.format(date) else "",
                    dogName = doc.getString("name") ?: "",
                    size = doc.getString("size") ?: "",
                    color = doc.getString("color") ?: "",
                    sex = doc.getString("sex") ?: "",
                    breed = doc.getString("breed") ?: "",
                    description = doc.getString("description") ?: ""
                )
            }
        } catch (e: Exception) {
            emptyList()
        }

        val foundReports = try {
            val token = getBearerToken()
            val response = api.getMyFoundReports(token)
            response.reports.map { r ->
                val parsedDate = try {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US)
                    val d = sdf.parse(r.createdAt)
                    if (d != null) dateFormat.format(d) else r.createdAt
                } catch (_: Exception) { r.createdAt }
                MyReport(
                    id = r.reportId,
                    type = ReportType.FOUND,
                    photoUrl = r.photoUrl,
                    status = r.status,
                    createdAt = parsedDate,
                    dogName = "",
                    size = r.size,
                    color = r.color,
                    sex = r.sex,
                    breed = "",
                    description = r.description
                )
            }
        } catch (e: Exception) {
            emptyList()
        }

        return lostReports + foundReports
    }

    override suspend fun updateReportStatus(id: String, type: ReportType, active: Boolean): Result<Unit> {
        return try {
            if (type == ReportType.LOST) {
                val newStatus = if (active) "active" else "inactive"
                db.collection("lost_dogs").document(id)
                    .update("status", newStatus).await()
            } else {
                val token = getBearerToken()
                api.updateFoundReportStatus(token = token, reportId = id, active = active)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deactivateDog(dogId: String): Result<Unit> {
        return try {
            db.collection("lost_dogs").document(dogId)
                .update("status", "inactive").await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getMatchesForDog(dogId: String): Result<List<FoundDogMatchForOwner>> {
        return try {
            val token = getBearerToken()
            val response = api.getMatchesForDog(token = token, dogId = dogId)
            val matches = response.matches.map { m ->
                FoundDogMatchForOwner(
                    reportId = m.reportId,
                    foundDogPhotoUrl = m.foundDogPhotoUrl,
                    similarityPercent = m.similarityPercent,
                    reporterName = m.reporterName,
                    reporterPhone = m.reporterPhone,
                    reporterEmail = m.reporterEmail,
                    foundDogSize = m.foundDogSize,
                    foundDogColor = m.foundDogColor,
                    foundDogSex = m.foundDogSex,
                    foundDogDescription = m.foundDogDescription,
                    reportedAt = m.reportedAt
                )
            }
            Result.success(matches)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
