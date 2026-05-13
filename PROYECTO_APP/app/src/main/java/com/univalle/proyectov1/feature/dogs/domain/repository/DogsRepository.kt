package com.univalle.proyectov1.feature.dogs.domain.repository

import com.univalle.proyectov1.feature.dogs.domain.model.Dog
import com.univalle.proyectov1.feature.dogs.domain.model.DogMatch
import com.univalle.proyectov1.feature.dogs.domain.model.FoundDogMatchForOwner
import com.univalle.proyectov1.feature.dogs.domain.model.MyReport
import com.univalle.proyectov1.feature.dogs.domain.model.ReportType
import java.io.File

interface DogsRepository {
    suspend fun validateLostPhoto(photo: File): Result<String>
    suspend fun validateFoundPhoto(photo: File): Result<String>

    suspend fun registerLostDog(
        dogName: String,
        description: String,
        ownerName: String,
        ownerPhone: String,
        ownerEmail: String,
        photos: List<File>,
        size: String = "",
        color: String = "",
        breed: String = "",
        lostLocation: String = "",
        sex: String = ""
    ): Result<String>

    suspend fun matchFoundDog(
        photos: List<File>,
        size: String = "",
        color: String = "",
        sex: String = "",
        description: String = "",
        reporterName: String = "",
        reporterPhone: String = "",
        reporterEmail: String = ""
    ): Result<List<DogMatch>>

    suspend fun getMyLostDogs(): List<Dog>

    suspend fun getMyReports(): List<MyReport>

    suspend fun updateReportStatus(id: String, type: ReportType, active: Boolean, reason: String? = null): Result<Unit>

    suspend fun deactivateDog(dogId: String): Result<Unit>

    suspend fun getMatchesForDog(dogId: String): Result<List<FoundDogMatchForOwner>>
}
