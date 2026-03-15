package com.univalle.proyectov1.feature.auth.domain.model

data class User(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = "USER",
    val isBlocked: Boolean = false
)