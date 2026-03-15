package com.univalle.proyectov1.feature.dogs.domain.repository

import com.univalle.proyectov1.feature.dogs.domain.model.Dog
import com.univalle.proyectov1.feature.dogs.domain.model.DogMatch
import java.io.File

interface DogsRepository {
    suspend fun validatePhoto(photo: File): Result<String>

    suspend fun registerLostDog(
        dogName: String,
        description: String,
        ownerName: String,
        ownerPhone: String,
        ownerEmail: String,
        photo: File,
        size: String = "",
        color: String = "",
        breed: String = "",
        lostLocation: String = ""
    ): Result<String>

    suspend fun matchFoundDog(photo: File): Result<List<DogMatch>>

    suspend fun getMyLostDogs(): List<Dog>
}
