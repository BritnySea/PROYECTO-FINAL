package com.univalle.proyectov1.feature.dogs.domain.model

data class Dog(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val ownerName: String = "",
    val ownerPhone: String = "",
    val ownerEmail: String = "",
    val photoUrl: String = "",
    val status: String = "active",
    val createdAt: String = "",
    val uid: String = "",
    val size: String = "",
    val color: String = "",
    val breed: String = "",
    val sex: String = ""
)

data class DogMatch(
    val dogId: String = "",
    val name: String = "",
    val ownerPhone: String = "",
    val ownerEmail: String = "",
    val similarityPercent: Float = 0f,
    val photoUrl: String = ""
)

data class FoundDogMatchForOwner(
    val reportId: String = "",
    val foundDogPhotoUrl: String = "",
    val foundDogPhotoUrls: List<String> = emptyList(),
    val similarityPercent: Float = 0f,
    val reporterName: String = "",
    val reporterPhone: String = "",
    val reporterEmail: String = "",
    val foundDogSize: String = "",
    val foundDogColor: String = "",
    val foundDogSex: String = "",
    val foundDogDescription: String = "",
    val reportedAt: String = ""
)
