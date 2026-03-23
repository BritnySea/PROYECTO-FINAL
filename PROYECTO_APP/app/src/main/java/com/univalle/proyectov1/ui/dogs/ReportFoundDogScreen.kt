package com.univalle.proyectov1.ui.dogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.Image
import com.univalle.proyectov1.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.univalle.proyectov1.core.result.UiState
import com.univalle.proyectov1.ui.theme.*

private val FoundValidGreen = Color(0xFF4CAF50)
private val FoundInvalidRed = Color(0xFFE53935)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportFoundDogScreen(
    viewModel: DogsViewModel,
    onBack: () -> Unit,
    onNavigateToMatches: () -> Unit
) {
    val context = LocalContext.current
    val matchState by viewModel.matchState.collectAsState()

    val user = FirebaseAuth.getInstance().currentUser
    var reporterName by remember { mutableStateOf(user?.displayName ?: "") }
    var reporterPhone by remember { mutableStateOf("") }
    var reporterEmail by remember { mutableStateOf(user?.email ?: "") }

    LaunchedEffect(Unit) {
        viewModel.resetMatchState()
        val phone = viewModel.getUserPhone()
        if (!phone.isNullOrBlank()) reporterPhone = phone
    }

    var showPhotoTipDialog by remember { mutableStateOf(false) }
    val goldGradient = Brush.horizontalGradient(colors = listOf(Gold, GoldLight))
    val foundPhotoValidationState = viewModel.foundPhotoValidationState

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.foundDogPhotoUri = uri
            viewModel.validateFoundPhoto(context, uri)
        }
    }

    LaunchedEffect(matchState) {
        if (matchState is UiState.Success) {
            onNavigateToMatches()
            viewModel.resetMatchState()
            viewModel.resetFoundDogForm()
        }
    }

    // ─── Diálogo consejo de foto ──────────────────────────────────────────────
    if (showPhotoTipDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoTipDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Consejo para mejor resultado", color = Gold, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Image(
                        painter = painterResource(id = R.drawable.dog_photo_reference),
                        contentDescription = "Ejemplo de foto correcta",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Toma la foto de frente al perro. Nuestro modelo de IA fue entrenado principalmente con fotos frontales, aunque también funciona con otros ángulos.\n\nLa foto debe ser clara y bien iluminada.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPhotoTipDialog = false
                    imagePicker.launch("image/*")
                }) {
                    Text("Entendido, continuar", color = Gold, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPhotoTipDialog = false }) {
                    Text("Cancelar", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // ─── Cabecera ─────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Gold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Encontré un perro",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sube una foto y completa los datos del perro para ayudar a encontrar a su dueño",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Selector de foto ─────────────────────────────────────────────
            val borderBrush = when (foundPhotoValidationState) {
                is PhotoValidationState.Valid ->
                    Brush.horizontalGradient(listOf(FoundValidGreen, FoundValidGreen))
                is PhotoValidationState.Invalid ->
                    Brush.horizontalGradient(listOf(FoundInvalidRed, FoundInvalidRed))
                else -> Brush.horizontalGradient(listOf(Gold, GoldLight))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, borderBrush, RoundedCornerShape(16.dp))
                    .background(DarkSurface)
                    .clickable { showPhotoTipDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (viewModel.foundDogPhotoUri != null) {
                    AsyncImage(
                        model = viewModel.foundDogPhotoUri,
                        contentDescription = "Foto del perro encontrado",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    when (foundPhotoValidationState) {
                        is PhotoValidationState.Loading -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(color = Gold, modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Analizando foto...", color = Color.White, fontSize = 13.sp)
                                }
                            }
                        }
                        is PhotoValidationState.Valid -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp)
                                    .background(FoundValidGreen, RoundedCornerShape(50))
                                    .padding(4.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Cambiar foto", color = GoldLight, fontSize = 12.sp)
                            }
                        }
                        is PhotoValidationState.Invalid -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = FoundInvalidRed, modifier = Modifier.size(40.dp))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text("Toca para cambiar foto", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                        else -> {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(8.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("Cambiar foto", color = GoldLight, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CameraAlt, contentDescription = null, tint = Gold, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Toca para agregar foto del perro", color = Color.White.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                }
            }

            // Estado de validación debajo del recuadro
            Spacer(modifier = Modifier.height(6.dp))
            when (foundPhotoValidationState) {
                is PhotoValidationState.Invalid -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = FoundInvalidRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text((foundPhotoValidationState as PhotoValidationState.Invalid).message, color = FoundInvalidRed, fontSize = 12.sp)
                    }
                }
                is PhotoValidationState.Valid -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FoundValidGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Foto válida", color = FoundValidGreen, fontSize = 12.sp)
                    }
                }
                else -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Gold.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Solo 1 foto", color = Gold.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Tamaño (obligatorio) ─────────────────────────────────────────
            FoundDogChipGroup(
                title = "Tamaño *",
                options = listOf("Grande", "Mediano", "Pequeño"),
                selected = viewModel.foundDogSize,
                onSelect = { viewModel.foundDogSize = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Color principal (obligatorio) ────────────────────────────────
            FoundDogChipGroup(
                title = "Color principal *",
                options = listOf("Negro", "Blanco", "Marrón", "Dorado/Amarillo", "Gris", "Atigrado", "Manchado/Pinto", "Otro"),
                selected = viewModel.foundDogColor,
                onSelect = { viewModel.foundDogColor = it; if (it != "Otro") viewModel.foundDogColorOther = "" },
                otherValue = viewModel.foundDogColorOther,
                onOtherChange = { viewModel.foundDogColorOther = it },
                otherLabel = "Describe el color"
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Sexo (opcional) ──────────────────────────────────────────────
            FoundDogChipGroup(
                title = "Sexo (opcional)",
                options = listOf("Macho", "Hembra"),
                selected = viewModel.foundDogSex,
                onSelect = { viewModel.foundDogSex = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Señas particulares (opcional) ────────────────────────────────
            FoundDogTextField(
                value = viewModel.foundDogSigns,
                onValueChange = { viewModel.foundDogSigns = it },
                label = "Señas particulares (opcional)",
                icon = Icons.Default.Notes,
                placeholder = "Ej: mancha en el ojo, collar azul, sin collar...",
                maxLines = 3,
                singleLine = false
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Datos del reportero ──────────────────────────────────────────
            Text(
                text = "Tus datos de contacto",
                color = Gold,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(12.dp))

            FoundDogTextField(
                value = reporterName,
                onValueChange = { reporterName = it },
                label = "Tu nombre *",
                icon = Icons.Default.Person
            )

            Spacer(modifier = Modifier.height(12.dp))

            FoundDogTextField(
                value = reporterPhone,
                onValueChange = { reporterPhone = it },
                label = "Teléfono *",
                icon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone,
                tintGold = true
            )

            Spacer(modifier = Modifier.height(12.dp))

            FoundDogTextField(
                value = reporterEmail,
                onValueChange = { reporterEmail = it },
                label = "Correo electrónico *",
                icon = Icons.Default.Email,
                keyboardType = KeyboardType.Email,
                tintGold = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ─── Mensaje de error ─────────────────────────────────────────────
            if (matchState is UiState.Error) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = (matchState as UiState.Error).message,
                        color = ErrorRed,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Botón buscar coincidencias ───────────────────────────────────
            val isLoading = matchState is UiState.Loading
            val finalColorCheck = if (viewModel.foundDogColor == "Otro") viewModel.foundDogColorOther else viewModel.foundDogColor
            val canSubmit = !isLoading &&
                foundPhotoValidationState is PhotoValidationState.Valid &&
                viewModel.foundDogSize.isNotBlank() &&
                finalColorCheck.isNotBlank() &&
                reporterName.isNotBlank() &&
                reporterPhone.isNotBlank() &&
                reporterEmail.isNotBlank()

            Button(
                onClick = {
                    if (viewModel.foundDogPhotoUri != null && foundPhotoValidationState is PhotoValidationState.Valid) {
                        viewModel.matchFoundDog(
                            context = context,
                            photoUri = viewModel.foundDogPhotoUri!!,
                            reporterName = reporterName,
                            reporterPhone = reporterPhone,
                            reporterEmail = reporterEmail
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                enabled = canSubmit
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (canSubmit) goldGradient
                            else Brush.horizontalGradient(listOf(Color.Gray, Color.Gray)),
                            RoundedCornerShape(16.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TextOnGold, strokeWidth = 2.dp)
                            Text("Analizando con IA...", color = TextOnGold, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Text(
                            "Buscar coincidencias",
                            color = if (canSubmit) TextOnGold else Color.White.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoundDogChipGroup(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    otherValue: String = "",
    onOtherChange: ((String) -> Unit)? = null,
    otherLabel: String = "Otro"
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            color = Gold,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                val isSelected = selected == option
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(option) },
                    label = {
                        Text(
                            text = option,
                            fontSize = 13.sp,
                            color = if (isSelected) Color.Black else Color.White.copy(alpha = 0.75f)
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Gold,
                        containerColor = Color.Transparent
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = Gold.copy(alpha = 0.4f),
                        selectedBorderColor = Gold
                    )
                )
            }
        }
        AnimatedVisibility(visible = selected == "Otro" && onOtherChange != null) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                FoundDogTextField(
                    value = otherValue,
                    onValueChange = { onOtherChange?.invoke(it) },
                    label = otherLabel,
                    icon = Icons.Default.Edit
                )
            }
        }
    }
}

@Composable
private fun FoundDogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    tintGold: Boolean = false,
    maxLines: Int = 1,
    singleLine: Boolean = true,
    placeholder: String = ""
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = Color.White.copy(alpha = 0.6f)) },
        placeholder = if (placeholder.isNotEmpty()) {
            { Text(placeholder, color = Color.White.copy(alpha = 0.35f), fontSize = 13.sp) }
        } else null,
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, tint = Gold)
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        maxLines = maxLines,
        singleLine = singleLine,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Gold,
            unfocusedBorderColor = if (tintGold) Gold.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.2f),
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            cursorColor = Gold,
            focusedContainerColor = Color.White.copy(alpha = 0.05f),
            unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
        )
    )
}
