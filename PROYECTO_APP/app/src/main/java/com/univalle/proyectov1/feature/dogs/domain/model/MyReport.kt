package com.univalle.proyectov1.feature.dogs.domain.model

enum class ReportType { LOST, FOUND }

data class MyReport(
    val id: String = "",
    val type: ReportType = ReportType.LOST,
    val photoUrl: String = "",
    val status: String = "active",
    val createdAt: String = "",
    val dogName: String = "",
    val size: String = "",
    val color: String = "",
    val sex: String = "",
    val breed: String = "",
    val description: String = ""
)
