package com.univalle.proyectov1.feature.user.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserRepository {
    suspend fun getUserPhone(): String?
    suspend fun updateUserPhone(phone: String): Result<Unit>
    suspend fun hasPhone(): Boolean
    fun observeBlockedStatus(): Flow<Boolean>
}
