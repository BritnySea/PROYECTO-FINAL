package com.univalle.proyectov1.ui.dogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.univalle.proyectov1.core.result.UiState
import com.univalle.proyectov1.feature.dogs.domain.model.MyReport
import com.univalle.proyectov1.feature.dogs.domain.model.ReportType
import com.univalle.proyectov1.ui.components.WoofLoadingOverlay
import com.univalle.proyectov1.ui.theme.*

@Composable
fun MyReportsScreen(
    viewModel: DogsViewModel,
    onNavigateToReport: () -> Unit
) {
    val myReports by viewModel.myReports.collectAsState()
    val isLoading by viewModel.myReportsLoading.collectAsState()
    val reportActionState by viewModel.reportActionState.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }
    // null = ningún diálogo, true = deactivate, false = reactivate
    var actionReport by remember { mutableStateOf<Pair<MyReport, Boolean>?>(null) }
    var selectedReason by remember { mutableStateOf("") }
    var otherText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.loadMyReports()
    }

    LaunchedEffect(reportActionState) {
        if (reportActionState is UiState.Success) {
            viewModel.resetReportActionState()
        }
    }

    LaunchedEffect(actionReport) {
        selectedReason = ""
        otherText = ""
    }

    val activeReports = myReports.filter { it.status == "active" }
    val inactiveReports = myReports.filter { it.status != "active" }
    val displayedReports = if (selectedTab == 0) activeReports else inactiveReports

    // Diálogo de desactivar/reactivar con selección de razón
    actionReport?.let { (report, isDeactivating) ->
        val reasons = when {
            isDeactivating && report.type == ReportType.LOST ->
                listOf("Ya encontré a mi perro", "Publicación duplicada", "Información incorrecta", "Otro")
            isDeactivating ->
                listOf("El perro ya fue entregado a su dueño", "Publicación duplicada", "Información incorrecta", "Otro")
            else ->
                listOf("Error al desactivarlo", "Quiero que siga visible", "Otro")
        }
        val isConfirmEnabled = selectedReason.isNotEmpty() &&
            (selectedReason != "Otro" || otherText.isNotBlank())

        AlertDialog(
            onDismissRequest = { actionReport = null },
            containerColor = DarkSurface,
            title = {
                Text(
                    text = if (isDeactivating) "¿Por qué desactivas esta publicación?"
                           else "¿Por qué reactivas esta publicación?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isDeactivating)
                            "La publicación dejará de ser visible para otros usuarios."
                        else
                            "La publicación volverá a ser visible para otros usuarios.",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    reasons.forEach { reason ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedReason = reason }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedReason == reason,
                                onClick = { selectedReason = reason },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = Gold,
                                    unselectedColor = Color.White.copy(alpha = 0.5f)
                                )
                            )
                            Text(
                                text = reason,
                                color = Color.White.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    if (selectedReason == "Otro") {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = otherText,
                            onValueChange = { if (it.length <= 300) otherText = it },
                            placeholder = {
                                Text("Describe el motivo...", color = Color.White.copy(alpha = 0.4f))
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Gold,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                                cursorColor = Gold
                            ),
                            maxLines = 3
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalReason = if (selectedReason == "Otro") otherText.trim() else selectedReason
                        viewModel.updateReportStatus(report.id, report.type, active = !isDeactivating, reason = finalReason)
                        actionReport = null
                    },
                    enabled = isConfirmEnabled
                ) {
                    Text(
                        text = if (isDeactivating) "Sí, desactivar" else "Sí, reactivar",
                        color = if (isConfirmEnabled)
                            if (isDeactivating) Color(0xFFEF5350) else Color(0xFF4CAF50)
                        else Color.White.copy(alpha = 0.3f)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { actionReport = null }) {
                    Text("Cancelar", color = Gold)
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Mis reportes",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
            ) {
                listOf("Activos", "Inactivos").forEachIndexed { index, label ->
                    val isSelected = selectedTab == index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Gold else Color.Transparent)
                            .clickable { selectedTab = index }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) TextOnGold else Color.White.copy(alpha = 0.6f),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gold)
                    }
                }
                displayedReports.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Pets,
                                contentDescription = null,
                                tint = Gold.copy(alpha = 0.4f),
                                modifier = Modifier.size(80.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (selectedTab == 0)
                                    "No tienes reportes activos"
                                else
                                    "No tienes reportes inactivos",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(displayedReports) { report ->
                            MyReportCard(
                                report = report,
                                onActionClick = {
                                    actionReport = Pair(report, report.status == "active")
                                }
                            )
                        }
                        item { Spacer(modifier = Modifier.height(100.dp)) }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = onNavigateToReport,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .padding(bottom = 80.dp),
            containerColor = Gold,
            contentColor = TextOnGold
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Agregar reporte")
        }

        if (reportActionState is UiState.Loading) {
            WoofLoadingOverlay(message = "Actualizando reporte...")
        }
    }
}

@Composable
private fun MyReportCard(report: MyReport, onActionClick: () -> Unit) {
    val isActive = report.status == "active"
    val isLost = report.type == ReportType.LOST

    Column {
        Text(
            text = if (isLost) "Reporte de mi perro extraviado" else "Reporte de perro encontrado",
            color = if (isLost) Color(0xFFCE93D8) else Color(0xFF90CAF9),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onActionClick() },
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (report.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model = report.photoUrl,
                        contentDescription = "Foto del reporte",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkBackground),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🐕", fontSize = 28.sp)
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (isLost && report.dogName.isNotBlank()) {
                        Text(
                            text = report.dogName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                    if (report.createdAt.isNotBlank()) {
                        Text(
                            text = "Registrado el ${report.createdAt}",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) Color(0xFF1B5E20).copy(alpha = 0.8f)
                           else Color(0xFF424242).copy(alpha = 0.8f)
                ) {
                    Text(
                        text = if (isActive) "Activo" else "Inactivo",
                        color = if (isActive) Color(0xFF4CAF50) else Color(0xFFBDBDBD),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            val details = buildList {
                if (report.size.isNotBlank()) add(Pair("Tamaño", report.size))
                if (report.color.isNotBlank()) add(Pair("Color", report.color))
                if (report.sex.isNotBlank()) add(Pair("Sexo", report.sex))
                if (isLost && report.breed.isNotBlank()) add(Pair("Raza", report.breed))
                if (report.description.isNotBlank()) add(Pair("Señas particulares", report.description))
            }

            if (details.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(8.dp))
                details.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Text(
                            text = "$label: ",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = value,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (isActive) "Toca para desactivar este reporte"
                       else "Toca para reactivar este reporte",
                color = if (isActive) Gold.copy(alpha = 0.6f) else Color(0xFF4CAF50).copy(alpha = 0.7f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
    }
}
