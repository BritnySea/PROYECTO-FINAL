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
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    var selectedTypeFilter by remember { mutableStateOf<ReportType?>(ReportType.LOST) }
    var searchQuery by remember { mutableStateOf("") }
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

    val displayedReports = myReports.filter { report ->
        val matchesStatus = if (selectedTab == 0) report.status == "active" else report.status != "active"
        val matchesType = selectedTypeFilter == null || report.type == selectedTypeFilter
        val matchesSearch = searchQuery.isBlank() || run {
            val q = searchQuery.trim().lowercase()
            when (selectedTypeFilter) {
                ReportType.LOST  -> report.dogName.lowercase().contains(q)
                ReportType.FOUND -> report.color.lowercase().contains(q) || report.size.lowercase().contains(q)
                null             -> report.dogName.lowercase().contains(q) ||
                                    report.color.lowercase().contains(q) ||
                                    report.size.lowercase().contains(q)
            }
        }
        matchesStatus && matchesType && matchesSearch
    }

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

            Spacer(modifier = Modifier.height(14.dp))

            // Buscador
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    val hint = when (selectedTypeFilter) {
                        ReportType.LOST  -> "Buscar por nombre del perro..."
                        ReportType.FOUND -> "Buscar por color o tamaño..."
                        null             -> "Buscar por nombre, color, tamaño..."
                    }
                    Text(hint, color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Gold) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Gold,
                    focusedContainerColor = DarkSurface,
                    unfocusedContainerColor = DarkSurface
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Filtro por tipo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    ReportType.LOST to "Extraviados",
                    ReportType.FOUND to "Encontrados"
                ).forEach { (type, label) ->
                    val isSelected = selectedTypeFilter == type
                    OutlinedButton(
                        onClick = {
                            selectedTypeFilter = if (isSelected) null else type
                            searchQuery = ""
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isSelected) Gold else Color.Transparent,
                            contentColor = if (isSelected) TextOnGold else Color.White.copy(alpha = 0.7f)
                        ),
                        border = BorderStroke(1.dp, if (isSelected) Gold else Color.White.copy(alpha = 0.2f))
                    ) {
                        Text(label, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Filtro por estado
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

            Spacer(modifier = Modifier.height(14.dp))

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
                                text = when {
                                    searchQuery.isNotBlank() -> "Sin resultados para \"$searchQuery\""
                                    selectedTypeFilter == ReportType.LOST -> if (selectedTab == 0) "No tienes perros extraviados activos" else "No tienes perros extraviados inactivos"
                                    selectedTypeFilter == ReportType.FOUND -> if (selectedTab == 0) "No tienes reportes de encontrados activos" else "No tienes reportes de encontrados inactivos"
                                    else -> if (selectedTab == 0) "No tienes reportes activos" else "No tienes reportes inactivos"
                                },
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
    val photos = report.photoUrls.ifEmpty { listOfNotNull(report.photoUrl.ifBlank { null }) }
    var galleryIndex by remember { mutableIntStateOf(-1) }

    // Galería de fotos a pantalla completa
    if (galleryIndex >= 0 && photos.isNotEmpty()) {
        Dialog(
            onDismissRequest = { galleryIndex = -1 },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { galleryIndex = -1 },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = photos[galleryIndex],
                        contentDescription = "Foto ${galleryIndex + 1}",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Fit
                    )
                    if (photos.size > 1) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            photos.forEachIndexed { idx, url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { galleryIndex = idx }
                                        .then(
                                            if (idx == galleryIndex)
                                                Modifier.padding(2.dp)
                                                    .background(Gold, RoundedCornerShape(8.dp))
                                            else Modifier
                                        ),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Foto ${galleryIndex + 1} de ${photos.size}",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Toca fuera de la foto para cerrar",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                }
            }
        }
    }

    Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onActionClick() },
            shape = RoundedCornerShape(16.dp),
            color = DarkSurface,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {

                // Fotos
                if (photos.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(DarkBackground),
                        contentAlignment = Alignment.Center
                    ) { Text("🐕", fontSize = 28.sp) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        photos.forEachIndexed { idx, url ->
                            val photoSize = if (photos.size == 1) 72.dp else 60.dp
                            Box {
                                AsyncImage(
                                    model = url,
                                    contentDescription = "Foto ${idx + 1}",
                                    modifier = Modifier
                                        .size(photoSize)
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { galleryIndex = idx },
                                    contentScale = ContentScale.Crop
                                )
                                if (photos.size > 1) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text("${idx + 1}", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    if (photos.size > 1) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Toca una foto para verla ampliada",
                            color = Gold.copy(alpha = 0.55f),
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        ) {
                            Text("$label: ", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Text(value, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isActive) "Toca la tarjeta para desactivar este reporte"
                           else "Toca la tarjeta para reactivar este reporte",
                    color = if (isActive) Gold.copy(alpha = 0.6f) else Color(0xFF4CAF50).copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
}
