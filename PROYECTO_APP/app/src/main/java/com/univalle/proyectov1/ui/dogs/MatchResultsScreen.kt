package com.univalle.proyectov1.ui.dogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import com.univalle.proyectov1.feature.dogs.domain.model.Dog
import com.univalle.proyectov1.feature.dogs.domain.model.FoundDogMatchForOwner
import com.univalle.proyectov1.feature.dogs.domain.model.LostDogMatchForFinder
import com.univalle.proyectov1.feature.dogs.domain.model.MyReport
import com.univalle.proyectov1.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchResultsScreen(
    viewModel: DogsViewModel
) {
    // ─── Tab 0: Mis perdidos ──────────────────────────────────────────────────
    val myDogs by viewModel.myDogs.collectAsState()
    val myDogsLoading by viewModel.myDogsLoading.collectAsState()
    val ownerMatches by viewModel.ownerMatches.collectAsState()
    val ownerMatchesLoading by viewModel.ownerMatchesLoading.collectAsState()

    // ─── Tab 1: Mis encontrados ───────────────────────────────────────────────
    val myFoundReports by viewModel.myFoundReports.collectAsState()
    val myFoundReportsLoading by viewModel.myFoundReportsLoading.collectAsState()
    val finderMatches by viewModel.finderMatches.collectAsState()
    val finderMatchesLoading by viewModel.finderMatchesLoading.collectAsState()

    var selectedTab by remember { mutableIntStateOf(0) }

    // Estado Tab 0
    var selectedDog by remember { mutableStateOf<Dog?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedMatch by remember { mutableStateOf<FoundDogMatchForOwner?>(null) }
    var galleryPhotos by remember { mutableStateOf<List<String>>(emptyList()) }
    var galleryIndex by remember { mutableIntStateOf(0) }

    // Estado Tab 1
    var selectedFoundReport by remember { mutableStateOf<MyReport?>(null) }
    var foundReportDropdownExpanded by remember { mutableStateOf(false) }
    var selectedFinderMatch by remember { mutableStateOf<LostDogMatchForFinder?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadMyDogs()
        viewModel.loadMyFoundReports()
    }

    LaunchedEffect(selectedDog) {
        selectedDog?.let { viewModel.loadMatchesForDog(it.id) }
    }

    LaunchedEffect(selectedFoundReport) {
        selectedFoundReport?.let { viewModel.loadMatchesForFoundReport(it.id) }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "Coincidencias",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = if (selectedTab == 0) "Selecciona un perro para ver quién lo encontró"
                       else "Selecciona un reporte para ver perros extraviados similares",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Tabs ─────────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DarkSurface,
                contentColor = Gold,
                indicator = { tabPositions ->
                    Box(
                        modifier = Modifier
                            .tabIndicatorOffset(tabPositions[selectedTab])
                            .height(3.dp)
                            .background(Gold, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                    )
                }
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Mis perdidos",
                            color = if (selectedTab == 0) Gold else Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Perros encontrados",
                            color = if (selectedTab == 1) Gold else Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Contenido por tab ────────────────────────────────────────────
            if (selectedTab == 0) {
                // ── Tab 0: vista del dueño del perro perdido ──────────────────
                if (myDogsLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gold)
                    }
                } else if (myDogs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Pets,
                                contentDescription = null,
                                tint = Gold.copy(alpha = 0.4f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Registra un perro perdido para ver coincidencias",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedDog?.name?.uppercase() ?: "Seleccionar perro...",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            leadingIcon = { Icon(Icons.Default.Pets, contentDescription = null, tint = Gold) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Gold,
                                unfocusedBorderColor = Gold.copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = if (selectedDog != null) Color.White else Color.White.copy(alpha = 0.4f),
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            myDogs.forEach { dog ->
                                DropdownMenuItem(
                                    text = { Text(dog.name.uppercase(), color = Color.White, fontSize = 14.sp) },
                                    onClick = { selectedDog = dog; dropdownExpanded = false },
                                    leadingIcon = {
                                        Icon(Icons.Default.Pets, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    when {
                        selectedDog == null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Selecciona un perro para ver sus coincidencias",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        ownerMatchesLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Gold)
                            }
                        }
                        ownerMatches.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.SearchOff,
                                        contentDescription = null,
                                        tint = Gold.copy(alpha = 0.4f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Aún no hay coincidencias para ${selectedDog!!.name}",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Cuando alguien reporte un perro encontrado similar, aparecerá aquí",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        else -> {
                            Text(
                                text = "Top ${ownerMatches.size} coincidencias para ${selectedDog!!.name}",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(ownerMatches) { match ->
                                    OwnerMatchCard(
                                        match = match,
                                        lostDogPhotoUrl = selectedDog!!.photoUrl,
                                        onClick = { selectedMatch = match },
                                        onFoundPhotoClick = {
                                            galleryPhotos = match.foundDogPhotoUrls.ifEmpty {
                                                listOfNotNull(match.foundDogPhotoUrl.ifBlank { null })
                                            }
                                            galleryIndex = 0
                                        }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(100.dp)) }
                            }
                        }
                    }
                }
            } else {
                // ── Tab 1: vista del que encontró el perro ────────────────────
                if (myFoundReportsLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gold)
                    }
                } else if (myFoundReports.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Pets,
                                contentDescription = null,
                                tint = Gold.copy(alpha = 0.4f),
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Aún no tienes reportes de perros encontrados",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    ExposedDropdownMenuBox(
                        expanded = foundReportDropdownExpanded,
                        onExpandedChange = { foundReportDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedFoundReport?.let {
                                if (it.createdAt.isNotBlank()) "Reporte del ${it.createdAt}" else "Reporte encontrado"
                            } ?: "Seleccionar reporte...",
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = foundReportDropdownExpanded) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Gold) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Gold,
                                unfocusedBorderColor = Gold.copy(alpha = 0.5f),
                                focusedTextColor = Color.White,
                                unfocusedTextColor = if (selectedFoundReport != null) Color.White else Color.White.copy(alpha = 0.4f),
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = foundReportDropdownExpanded,
                            onDismissRequest = { foundReportDropdownExpanded = false },
                            modifier = Modifier.background(DarkSurface)
                        ) {
                            myFoundReports.forEachIndexed { index, report ->
                                val label = if (report.createdAt.isNotBlank())
                                    "Reporte del ${report.createdAt}"
                                else
                                    "Reporte #${index + 1}"
                                DropdownMenuItem(
                                    text = { Text(label, color = Color.White, fontSize = 13.sp) },
                                    onClick = { selectedFoundReport = report; foundReportDropdownExpanded = false },
                                    leadingIcon = {
                                        Icon(Icons.Default.Search, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    when {
                        selectedFoundReport == null -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Selecciona un reporte para ver coincidencias",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        finderMatchesLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = Gold)
                            }
                        }
                        finderMatches == null || finderMatches!!.matches.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Default.SearchOff,
                                        contentDescription = null,
                                        tint = Gold.copy(alpha = 0.4f),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "No hay coincidencias con perros extraviados de otros usuarios",
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 15.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Aparecerán aquí si alguien reporta su perro extraviado similar al que encontraste",
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                        else -> {
                            val matches = finderMatches!!.matches
                            Text(
                                text = "${matches.size} coincidencia(s) con perros extraviados",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(matches) { match ->
                                    FinderMatchCard(
                                        match = match,
                                        foundDogPhotoUrl = finderMatches!!.foundDogPhotoUrl,
                                        onClick = { selectedFinderMatch = match }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(100.dp)) }
                            }
                        }
                    }
                }
            }
        }
    }

    // ─── Diálogos Tab 0 ──────────────────────────────────────────────────────
    selectedMatch?.let { match ->
        OwnerMatchDetailDialog(
            match = match,
            lostDogPhotoUrl = selectedDog?.photoUrl ?: "",
            onDismiss = { selectedMatch = null },
            onFoundPhotoClick = {
                galleryPhotos = match.foundDogPhotoUrls.ifEmpty {
                    listOfNotNull(match.foundDogPhotoUrl.ifBlank { null })
                }
                galleryIndex = 0
            }
        )
    }

    // ─── Diálogos Tab 1 ──────────────────────────────────────────────────────
    selectedFinderMatch?.let { match ->
        FinderMatchDetailDialog(
            match = match,
            foundDogPhotoUrl = finderMatches?.foundDogPhotoUrl ?: "",
            onDismiss = { selectedFinderMatch = null }
        )
    }

    // ─── Galería pantalla completa ─────────────────────────────────────────────
    if (galleryPhotos.isNotEmpty()) {
        FoundDogPhotoGallery(
            photos = galleryPhotos,
            initialIndex = galleryIndex,
            onDismiss = { galleryPhotos = emptyList() }
        )
    }
}

// ─── Tarjeta de coincidencia ──────────────────────────────────────────────────

@Composable
private fun OwnerMatchCard(
    match: FoundDogMatchForOwner,
    lostDogPhotoUrl: String,
    onClick: () -> Unit,
    onFoundPhotoClick: () -> Unit
) {
    val simColor = when {
        match.similarityPercent >= 80f -> Color(0xFF4CAF50)
        match.similarityPercent >= 50f -> Gold
        else -> Color.White.copy(alpha = 0.7f)
    }
    val photoCount = match.foundDogPhotoUrls.size.takeIf { it > 1 }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, simColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Fotos lado a lado
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Tu perro", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    MatchDogPhoto(url = lostDogPhotoUrl, size = 90.dp)
                }

                Column(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CompareArrows, contentDescription = null, tint = simColor, modifier = Modifier.size(24.dp))
                    Text("${match.similarityPercent}%", color = simColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Encontrado", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Box {
                        MatchDogPhoto(
                            url = match.foundDogPhotoUrl,
                            size = 90.dp,
                            onClick = onFoundPhotoClick
                        )
                        if (photoCount != null) {
                            Surface(
                                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.7f)
                            ) {
                                Text(
                                    text = "1/$photoCount",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Barra de similitud
            LinearProgressIndicator(
                progress = { (match.similarityPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = simColor,
                trackColor = Color.White.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (match.reportedAt.isNotBlank()) {
                    Text(
                        text = "Reportado: ${match.reportedAt.take(10)}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp
                    )
                }
                Text("Ver detalles →", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─── Diálogo de detalle ───────────────────────────────────────────────────────

@Composable
private fun OwnerMatchDetailDialog(
    match: FoundDogMatchForOwner,
    lostDogPhotoUrl: String,
    onDismiss: () -> Unit,
    onFoundPhotoClick: () -> Unit
) {
    val simColor = when {
        match.similarityPercent >= 80f -> Color(0xFF4CAF50)
        match.similarityPercent >= 50f -> Gold
        else -> Color.White.copy(alpha = 0.7f)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {

                Text("Detalles de coincidencia", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(16.dp))

                // Fotos más grandes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Tu perro", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        MatchDogPhoto(url = lostDogPhotoUrl, size = 120.dp)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Perro encontrado", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        val photoCount = match.foundDogPhotoUrls.size.takeIf { it > 1 }
                        Box {
                            MatchDogPhoto(
                                url = match.foundDogPhotoUrl,
                                size = 120.dp,
                                onClick = onFoundPhotoClick
                            )
                            if (photoCount != null) {
                                Surface(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(3.dp),
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "$photoCount fotos",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Badge similitud
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = simColor.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${match.similarityPercent}% de similitud",
                        color = simColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Info del perro encontrado
                if (match.foundDogSize.isNotBlank() || match.foundDogColor.isNotBlank() || match.foundDogSex.isNotBlank()) {
                    Text("Descripción del perro encontrado", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (match.foundDogSize.isNotBlank()) MatchDetailRow(Icons.Default.Straighten, "Tamaño", match.foundDogSize)
                    if (match.foundDogColor.isNotBlank()) MatchDetailRow(Icons.Default.Palette, "Color", match.foundDogColor)
                    if (match.foundDogSex.isNotBlank()) MatchDetailRow(Icons.Default.Male, "Sexo", match.foundDogSex)
                    if (match.foundDogDescription.isNotBlank()) MatchDetailRow(Icons.Default.Notes, "Señas", match.foundDogDescription)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                // Datos de quien encontró
                Text("Quien encontró el perro", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                if (match.reporterName.isNotBlank()) MatchDetailRow(Icons.Default.Person, "Nombre", match.reporterName)
                if (match.reporterPhone.isNotBlank()) MatchDetailRow(Icons.Default.Phone, "Teléfono", match.reporterPhone)
                if (match.reporterEmail.isNotBlank()) MatchDetailRow(Icons.Default.Email, "Correo", match.reporterEmail)

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold)
                ) {
                    Text("Cerrar", color = TextOnGold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Componentes auxiliares ───────────────────────────────────────────────────

@Composable
private fun MatchDogPhoto(
    url: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: (() -> Unit)? = null
) {
    val modifier = Modifier
        .size(size)
        .clip(RoundedCornerShape(12.dp))
        .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)

    if (url.isNotBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = modifier.background(DarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Text("🐕", fontSize = (size.value * 0.4f).sp)
        }
    }
}

@Composable
private fun FoundDogPhotoGallery(
    photos: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(initialIndex) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Foto principal
            AsyncImage(
                model = photos[currentIndex],
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center),
                contentScale = ContentScale.Fit
            )

            // Botón cerrar
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = Color.White)
            }

            // Contador
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.5f)
            ) {
                Text(
                    text = "Foto ${currentIndex + 1} de ${photos.size}",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }

            // Miniaturas
            if (photos.size > 1) {
                LazyRow(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(photos) { index, url ->
                        val isSelected = index == currentIndex
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { currentIndex = index }
                                .then(
                                    if (isSelected) Modifier.border(
                                        2.dp, Gold, RoundedCornerShape(8.dp)
                                    ) else Modifier
                                ),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MatchDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
        Icon(icon, contentDescription = null, tint = Gold.copy(alpha = 0.7f), modifier = Modifier.size(15.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("$label: ", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp)
    }
}

// ─── Tarjeta de coincidencia (vista del encontrador) ─────────────────────────

@Composable
private fun FinderMatchCard(
    match: LostDogMatchForFinder,
    foundDogPhotoUrl: String,
    onClick: () -> Unit
) {
    val simColor = when {
        match.similarityPercent >= 80f -> Color(0xFF4CAF50)
        match.similarityPercent >= 50f -> Gold
        else -> Color.White.copy(alpha = 0.7f)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, simColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Perro que encontraste", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    MatchDogPhoto(url = foundDogPhotoUrl, size = 90.dp)
                }

                Column(
                    modifier = Modifier.align(Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.CompareArrows, contentDescription = null, tint = simColor, modifier = Modifier.size(24.dp))
                    Text("${match.similarityPercent.toInt()}%", color = simColor, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Perro extraviado", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    MatchDogPhoto(url = match.lostDogPhotoUrl, size = 90.dp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { (match.similarityPercent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = simColor,
                trackColor = Color.White.copy(alpha = 0.1f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (match.lostDogName.isNotBlank()) {
                    Text(
                        text = match.lostDogName.uppercase(),
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text("Ver detalles →", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─── Diálogo de detalle (vista del encontrador) ───────────────────────────────

@Composable
private fun FinderMatchDetailDialog(
    match: LostDogMatchForFinder,
    foundDogPhotoUrl: String,
    onDismiss: () -> Unit
) {
    val simColor = when {
        match.similarityPercent >= 80f -> Color(0xFF4CAF50)
        match.similarityPercent >= 50f -> Gold
        else -> Color.White.copy(alpha = 0.7f)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = DarkSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {

                Text("Perro extraviado encontrado", color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Perro que encontraste", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        MatchDogPhoto(url = foundDogPhotoUrl, size = 120.dp)
                    }
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Perro extraviado", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        MatchDogPhoto(url = match.lostDogPhotoUrl, size = 120.dp)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = simColor.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "${match.similarityPercent.toInt()}% de similitud",
                        color = simColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (match.lostDogSize.isNotBlank() || match.lostDogColor.isNotBlank() || match.lostDogSex.isNotBlank()) {
                    Text("Descripción del perro extraviado", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(6.dp))
                    if (match.lostDogSize.isNotBlank()) MatchDetailRow(Icons.Default.Straighten, "Tamaño", match.lostDogSize)
                    if (match.lostDogColor.isNotBlank()) MatchDetailRow(Icons.Default.Palette, "Color", match.lostDogColor)
                    if (match.lostDogSex.isNotBlank()) MatchDetailRow(Icons.Default.Male, "Sexo", match.lostDogSex)
                    if (match.lostDogDescription.isNotBlank()) MatchDetailRow(Icons.Default.Notes, "Señas", match.lostDogDescription)
                    Spacer(modifier = Modifier.height(12.dp))
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(modifier = Modifier.height(12.dp))

                Text("Dueño del perro extraviado", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                if (match.ownerName.isNotBlank()) MatchDetailRow(Icons.Default.Person, "Nombre", match.ownerName)
                if (match.ownerPhone.isNotBlank()) MatchDetailRow(Icons.Default.Phone, "Teléfono", match.ownerPhone)
                if (match.ownerEmail.isNotBlank()) MatchDetailRow(Icons.Default.Email, "Correo", match.ownerEmail)

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold)
                ) {
                    Text("Cerrar", color = TextOnGold, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
