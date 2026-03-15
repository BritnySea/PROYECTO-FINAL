package com.univalle.proyectov1.feature.dogs.data.remote

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

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

// ─── Retrofit interface ───────────────────────────────────────────────────────

interface WoofApiService {

    @Multipart
    @POST("api/v1/validate-photo")
    suspend fun validatePhoto(
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
        @Part("lost_location") lostLocation: RequestBody
    ): RegisterLostDogResponse

    @Multipart
    @POST("api/v1/match-found-dog")
    suspend fun matchFoundDog(
        @Header("Authorization") token: String,
        @Part photo: MultipartBody.Part
    ): MatchFoundDogResponse
}
