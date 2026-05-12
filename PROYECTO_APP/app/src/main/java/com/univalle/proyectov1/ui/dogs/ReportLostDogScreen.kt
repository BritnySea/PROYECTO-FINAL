package com.univalle.proyectov1.ui.dogs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.univalle.proyectov1.R
import com.univalle.proyectov1.core.result.UiState
import com.univalle.proyectov1.ui.theme.*

private val ValidGreen = Color(0xFF4CAF50)
private val InvalidRed = Color(0xFFE53935)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReportLostDogScreen(
    viewModel: DogsViewModel,
    onBack: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val registerState by viewModel.registerState.collectAsState()

    val user = FirebaseAuth.getInstance().currentUser
    var dogName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf(user?.displayName ?: "") }
    var ownerPhone by remember { mutableStateOf("") }
    var ownerEmail by remember { mutableStateOf(user?.email ?: "") }

    DisposableEffect(Unit) {
        onDispose { viewModel.resetLostDogForm() }
    }

    LaunchedEffect(Unit) {
        val phone = viewModel.getUserPhone()
        if (!phone.isNullOrBlank()) ownerPhone = phone
    }

    var showPhotoTipDialog by remember { mutableStateOf(false) }
    var targetPhotoIndex by remember { mutableIntStateOf(0) }

    val goldGradient = Brush.horizontalGradient(colors = listOf(Gold, GoldLight))

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.addOrReplaceLostDogPhoto(context, uri, targetPhotoIndex)
        }
    }

    LaunchedEffect(registerState) {
        if (registerState is UiState.Success) {
            onSuccess()
            viewModel.resetRegisterState()
            viewModel.resetLostDogForm()
        }
    }

    // ─── Dialog consejo de foto ───────────────────────────────────────────────
    if (showPhotoTipDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoTipDialog = false },
            containerColor = DarkSurface,
            title = {
                Text("Consejos para mejor resultado", color = Gold, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Image(
                        painter = painterResource(id = R.drawable.dog_photo_reference),
                        contentDescription = "Ejemplo de foto correcta",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "La primera foto debe ser de frente al perro. Nuestro modelo de IA fue entrenado principalmente con fotos frontales.",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Gold.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lightbulb,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(16.dp).padding(top = 1.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Para mejores resultados, agrega 2 o 3 fotos desde diferentes ángulos (frente y costado). Más fotos = mayor precisión.",
                            color = Gold,
                            fontSize = 13.sp,
                            lineHeight = 18.sp
                        )
                    }
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
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Atrás", tint = Gold)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reportar perro perdido",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ─── Banner de recomendación de fotos ─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Gold.copy(alpha = 0.1f), RoundedCornerShape(10.dp))
                    .border(1.dp, Gold.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = null,
                    tint = Gold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        "Agrega hasta 3 fotos del perro",
                        color = Gold,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "Fotos desde diferentes ángulos (frente y costado) mejoran la precisión de búsqueda. Mínimo 1 foto obligatoria.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ─── Selección multi-foto ─────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Foto 1 (obligatoria)
                LostDogPhotoSlot(
                    index = 0,
                    label = "Foto 1 *",
                    uri = viewModel.lostDogPhotoUris.getOrNull(0),
                    validationState = viewModel.lostPhotoValidationStates.getOrNull(0) ?: PhotoValidationState.Idle,
                    isRequired = true,
                    onTap = {
                        targetPhotoIndex = 0
                        if (viewModel.lostDogPhotoUris.isEmpty()) {
                            showPhotoTipDialog = true
                        } else {
                            imagePicker.launch("image/*")
                        }
                    },
                    onRemove = null, // primera foto no se puede eliminar
                    modifier = Modifier.weight(1f)
                )
                // Foto 2 (opcional)
                LostDogPhotoSlot(
                    index = 1,
                    label = "Foto 2",
                    uri = viewModel.lostDogPhotoUris.getOrNull(1),
                    validationState = viewModel.lostPhotoValidationStates.getOrNull(1) ?: PhotoValidationState.Idle,
                    isRequired = false,
                    isDisabled = viewModel.lostPhotoValidationStates.getOrNull(0) !is PhotoValidationState.Valid,
                    onTap = {
                        targetPhotoIndex = 1
                        imagePicker.launch("image/*")
                    },
                    onRemove = { viewModel.removeLostDogPhoto(1) },
                    modifier = Modifier.weight(1f)
                )
                // Foto 3 (opcional)
                LostDogPhotoSlot(
                    index = 2,
                    label = "Foto 3",
                    uri = viewModel.lostDogPhotoUris.getOrNull(2),
                    validationState = viewModel.lostPhotoValidationStates.getOrNull(2) ?: PhotoValidationState.Idle,
                    isRequired = false,
                    isDisabled = viewModel.lostDogPhotoUris.size < 2,
                    onTap = {
                        targetPhotoIndex = 2
                        imagePicker.launch("image/*")
                    },
                    onRemove = { viewModel.removeLostDogPhoto(2) },
                    modifier = Modifier.weight(1f)
                )
            }

            // Estado de validación global
            Spacer(modifier = Modifier.height(6.dp))
            val firstState = viewModel.lostPhotoValidationStates.getOrNull(0) ?: PhotoValidationState.Idle
            when {
                firstState is PhotoValidationState.Invalid -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = InvalidRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(firstState.message, color = InvalidRed, fontSize = 12.sp)
                    }
                }
                firstState is PhotoValidationState.Valid -> {
                    val validCount = viewModel.lostPhotoValidationStates.count { it is PhotoValidationState.Valid }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, null, tint = ValidGreen, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "$validCount foto${if (validCount > 1) "s" else ""} válida${if (validCount > 1) "s" else ""}",
                            color = ValidGreen,
                            fontSize = 12.sp
                        )
                    }
                }
                else -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = Gold.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Foto principal obligatoria", color = Gold.copy(alpha = 0.7f), fontSize = 12.sp)
                    }
                }
            }

            // Error de foto 2 o 3 si hay
            viewModel.lostPhotoValidationStates.forEachIndexed { idx, state ->
                if (idx > 0 && state is PhotoValidationState.Invalid) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ErrorOutline, null, tint = InvalidRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Foto ${idx + 1}: ${state.message}", color = InvalidRed, fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Nombre del perro ─────────────────────────────────────────────
            LostDogTextField(
                value = dogName,
                onValueChange = { dogName = it.filter { c -> c.isLetter() || c == ' ' }.take(20) },
                label = "Nombre del perro *",
                icon = Icons.Default.Pets,
                maxLength = 20
            )

            Spacer(modifier = Modifier.height(20.dp))

            DogChipGroup(
                title = "Tamaño *",
                options = listOf("Grande", "Mediano", "Pequeño"),
                selected = viewModel.dogSize,
                onSelect = { viewModel.dogSize = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            DogChipGroup(
                title = "Color principal *",
                options = listOf("Negro", "Blanco", "Marrón", "Dorado/Amarillo", "Gris", "Atigrado", "Manchado/Pinto", "Otro"),
                selected = viewModel.dogColor,
                onSelect = { viewModel.dogColor = it; if (it != "Otro") viewModel.dogColorOther = "" },
                otherValue = viewModel.dogColorOther,
                onOtherChange = { viewModel.dogColorOther = it.filter { c -> c.isLetter() || c == ' ' }.take(20) },
                otherLabel = "Describe el color",
                otherMaxLength = 20
            )

            Spacer(modifier = Modifier.height(20.dp))

            DogChipGroup(
                title = "Sexo *",
                options = listOf("Macho", "Hembra"),
                selected = viewModel.dogSex,
                onSelect = { viewModel.dogSex = it }
            )

            Spacer(modifier = Modifier.height(20.dp))

            LostDogTextField(
                value = viewModel.dogBreed,
                onValueChange = { viewModel.dogBreed = it.filter { c -> c.isLetter() || c == ' ' || c == '-' }.take(30) },
                label = "Raza (opcional)",
                icon = Icons.Default.Pets,
                placeholder = "Ej: Labrador, Mestizo, Bulldog...",
                maxLength = 30
            )

            Spacer(modifier = Modifier.height(12.dp))

            LostDogTextField(
                value = viewModel.lostLocation,
                onValueChange = { viewModel.lostLocation = it.take(150) },
                label = "Lugar donde se perdió (opcional)",
                icon = Icons.Default.LocationOn,
                placeholder = "Ej: Parque El Lago, Calle 5 con Av. 3...",
                maxLength = 150
            )

            Spacer(modifier = Modifier.height(12.dp))

            LostDogTextField(
                value = viewModel.particularSigns,
                onValueChange = { viewModel.particularSigns = it.take(150) },
                label = "Señas particulares (opcional)",
                icon = Icons.Default.Notes,
                placeholder = "Ej: mancha blanca en el ojo derecho, collar rojo",
                maxLines = 3,
                singleLine = false,
                maxLength = 150
            )

            Spacer(modifier = Modifier.height(20.dp))

            LostDogTextField(
                value = ownerName,
                onValueChange = { ownerName = it.filter { c -> c.isLetter() || c == ' ' }.take(50) },
                label = "Nombre del dueño *",
                icon = Icons.Default.Person,
                maxLength = 50
            )
            Spacer(modifier = Modifier.height(12.dp))

            LostDogTextField(
                value = ownerPhone,
                onValueChange = { ownerPhone = it },
                label = "Teléfono *",
                icon = Icons.Default.Phone,
                keyboardType = KeyboardType.Phone,
                tintGold = true
            )
            Spacer(modifier = Modifier.height(12.dp))

            LostDogTextField(
                value = ownerEmail,
                onValueChange = { ownerEmail = it },
                label = "Email *",
                icon = Icons.Default.Email,
                keyboardType = KeyboardType.Email,
                tintGold = true
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (registerState is UiState.Error) {
                Text(
                    text = (registerState as UiState.Error).message,
                    color = ErrorRed,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Botón registrar ──────────────────────────────────────────────
            val isLoading = registerState is UiState.Loading
            val finalColorCheck = if (viewModel.dogColor == "Otro") viewModel.dogColorOther else viewModel.dogColor
            val allPhotosReady = viewModel.lostPhotoValidationStates.isNotEmpty() &&
                viewModel.lostPhotoValidationStates.all { it is PhotoValidationState.Valid }
            val canSubmit = !isLoading &&
                allPhotosReady &&
                dogName.isNotBlank() &&
                ownerName.isNotBlank() &&
                ownerPhone.isNotBlank() &&
                ownerEmail.isNotBlank() &&
                viewModel.dogSize.isNotBlank() &&
                finalColorCheck.isNotBlank() &&
                viewModel.dogSex.isNotBlank()

            Button(
                onClick = {
                    if (canSubmit) {
                        viewModel.registerLostDog(
                            context = context,
                            dogName = dogName,
                            ownerName = ownerName,
                            ownerPhone = ownerPhone,
                            ownerEmail = ownerEmail
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = TextOnGold,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            "Registrar perro perdido",
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

@Composable
private fun LostDogPhotoSlot(
    index: Int,
    label: String,
    uri: android.net.Uri?,
    validationState: PhotoValidationState,
    isRequired: Boolean,
    isDisabled: Boolean = false,
    onTap: () -> Unit,
    onRemove: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val borderBrush = when {
        isDisabled -> Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f)))
        validationState is PhotoValidationState.Valid -> Brush.horizontalGradient(listOf(ValidGreen, ValidGreen))
        validationState is PhotoValidationState.Invalid -> Brush.horizontalGradient(listOf(InvalidRed, InvalidRed))
        isRequired -> Brush.horizontalGradient(listOf(Gold, GoldLight))
        else -> Brush.horizontalGradient(listOf(Gold.copy(alpha = 0.4f), GoldLight.copy(alpha = 0.4f)))
    }

    Box(
        modifier = modifier
            .aspectRatio(0.72f)
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, borderBrush, RoundedCornerShape(12.dp))
            .background(if (isDisabled) DarkSurface.copy(alpha = 0.5f) else DarkSurface)
            .then(if (!isDisabled) Modifier.clickable { onTap() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        if (uri != null) {
            AsyncImage(
                model = uri,
                contentDescription = "Foto $label",
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop
            )
            // Overlay validación
            when (validationState) {
                is PhotoValidationState.Loading -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Gold, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
                    }
                }
                is PhotoValidationState.Valid -> {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .background(ValidGreen, CircleShape)
                            .padding(3.dp)
                    ) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
                is PhotoValidationState.Invalid -> {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ErrorOutline, null, tint = InvalidRed, modifier = Modifier.size(28.dp))
                    }
                }
                else -> {}
            }
            // Botón eliminar foto opcional
            if (onRemove != null) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .clickable { onRemove() }
                        .padding(3.dp)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(4.dp)
            ) {
                Icon(
                    imageVector = if (isDisabled) Icons.Default.LockClock else Icons.Default.AddAPhoto,
                    contentDescription = null,
                    tint = if (isDisabled) Color.White.copy(alpha = 0.2f)
                    else if (isRequired) Gold
                    else Gold.copy(alpha = 0.5f),
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    color = if (isDisabled) Color.White.copy(alpha = 0.2f)
                    else if (isRequired) Gold
                    else Color.White.copy(alpha = 0.45f),
                    fontSize = 11.sp,
                    fontWeight = if (isRequired) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
                if (!isRequired && !isDisabled) {
                    Text(
                        text = "opcional",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DogChipGroup(
    title: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    otherValue: String = "",
    onOtherChange: ((String) -> Unit)? = null,
    otherLabel: String = "Otro",
    otherMaxLength: Int = Int.MAX_VALUE
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
                LostDogTextField(
                    value = otherValue,
                    onValueChange = { onOtherChange?.invoke(it) },
                    label = otherLabel,
                    icon = Icons.Default.Edit,
                    maxLength = otherMaxLength
                )
            }
        }
    }
}

@Composable
private fun LostDogTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text,
    tintGold: Boolean = false,
    maxLines: Int = 1,
    singleLine: Boolean = true,
    placeholder: String = "",
    maxLength: Int = Int.MAX_VALUE
) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
        if (maxLength != Int.MAX_VALUE) {
            Text(
                text = "${value.length}/$maxLength",
                color = if (value.length >= maxLength) InvalidRed else Color.White.copy(alpha = 0.35f),
                fontSize = 11.sp,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 4.dp, top = 2.dp)
            )
        }
    }
}
