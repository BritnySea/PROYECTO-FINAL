package com.univalle.proyectov1.feature.dogs.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

// ─── Response models ─────────────────────────────────────────────────────────

data class ValidatePhotoResponse(
    @SerializedName("is_dog") val isDog: Boolean,
    val confidence: Float,
    val message: String
)

data class RegisterLostDogResponse(
    @SerializedName("dog_id") val dogId: String,
    val message: String
)

data class DogMatchResponse(
    @SerializedName("dog_id") val dogId: String,
    val name: String,
    @SerializedName("owner_phone") val ownerPhone: String,
    @SerializedName("owner_email") val ownerEmail: String,
    @SerializedName("similarity_percent") val similarityPercent: Float,
    @SerializedName("photo_url") val photoUrl: String
)

data class MatchFoundDogResponse(
    val matches: List<DogMatchResponse>,
    @SerializedName("is_dog") val isDog: Boolean,
    val message: String
)

data class OwnerMatchItemResponse(
    @SerializedName("report_id") val reportId: String,
    @SerializedName("found_dog_photo_url") val foundDogPhotoUrl: String,
    @SerializedName("similarity_percent") val similarityPercent: Float,
    @SerializedName("reporter_name") val reporterName: String,
    @SerializedName("reporter_phone") val reporterPhone: String,
    @SerializedName("reporter_email") val reporterEmail: String,
    @SerializedName("found_dog_size") val foundDogSize: String,
    @SerializedName("found_dog_color") val foundDogColor: String,
    @SerializedName("found_dog_sex") val foundDogSex: String,
    @SerializedName("found_dog_description") val foundDogDescription: String,
    @SerializedName("reported_at") val reportedAt: String
)

data class OwnerMatchesApiResponse(
    val matches: List<OwnerMatchItemResponse>
)

data class MyFoundReportItemResponse(
    @SerializedName("report_id") val reportId: String,
    @SerializedName("photo_url") val photoUrl: String,
    val status: String,
    @SerializedName("created_at") val createdAt: String,
    val size: String,
    val color: String,
    val sex: String,
    val description: String
)

data class MyFoundReportsApiResponse(
    val reports: List<MyFoundReportItemResponse>
)

// ─── Retrofit interface ───────────────────────────────────────────────────────

interface WoofApiService {

    @Multipart
    @POST("api/v1/validate-photo")
    suspend fun validatePhoto(
        @Header("Authorization") token: String,
        @Part photo: MultipartBody.Part
    ): ValidatePhotoResponse

    @Multipart
    @POST("api/v1/validate-lost-photo")
    suspend fun validateLostPhoto(
        @Header("Authorization") token: String,
        @Part photo: MultipartBody.Part
    ): ValidatePhotoResponse

    @Multipart
    @POST("api/v1/validate-found-photo")
    suspend fun validateFoundPhoto(
        @Header("Authorization") token: String,
        @Part photo: MultipartBody.Part
    ): ValidatePhotoResponse

    @Multipart
    @POST("api/v1/register-lost-dog")
    suspend fun registerLostDog(
        @Header("Authorization") token: String,
        @Part("dog_name") dogName: RequestBody,
        @Part("description") description: RequestBody,
        @Part("owner_name") ownerName: RequestBody,
        @Part("owner_phone") ownerPhone: RequestBody,
        @Part("owner_email") ownerEmail: RequestBody,
        @Part photo: MultipartBody.Part,
        @Part("size") size: RequestBody,
        @Part("color") color: RequestBody,
        @Part("breed") breed: RequestBody,
        @Part("lost_location") lostLocation: RequestBody,
        @Part("sex") sex: RequestBody
    ): RegisterLostDogResponse

    @Multipart
    @POST("api/v1/match-found-dog")
    suspend fun matchFoundDog(
        @Header("Authorization") token: String,
        @Part photo: MultipartBody.Part,
        @Part("size") size: RequestBody,
        @Part("color") color: RequestBody,
        @Part("sex") sex: RequestBody,
        @Part("description") description: RequestBody,
        @Part("reporter_name") reporterName: RequestBody,
        @Part("reporter_phone") reporterPhone: RequestBody,
        @Part("reporter_email") reporterEmail: RequestBody,
    ): MatchFoundDogResponse

    @GET("api/v1/my-dog-matches")
    suspend fun getMatchesForDog(
        @Header("Authorization") token: String,
        @Query("dog_id") dogId: String
    ): OwnerMatchesApiResponse

    @GET("api/v1/my-found-reports")
    suspend fun getMyFoundReports(
        @Header("Authorization") token: String
    ): MyFoundReportsApiResponse

    @PATCH("api/v1/found-report-status")
    suspend fun updateFoundReportStatus(
        @Header("Authorization") token: String,
        @Query("report_id") reportId: String,
        @Query("active") active: Boolean
    )
}
