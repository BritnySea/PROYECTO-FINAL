package com.univalle.proyectov1.ui.dogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.univalle.proyectov1.core.result.UiState
import com.univalle.proyectov1.ui.theme.*

@Composable
fun ReportFoundDogScreen(
    viewModel: DogsViewModel,
    onBack: () -> Unit,
    onNavigateToMatches: () -> Unit
) {
    val context = LocalContext.current
    val matchState by viewModel.matchState.collectAsState()

    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val goldGradient = Brush.horizontalGradient(colors = listOf(Gold, GoldLight))

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> selectedPhotoUri = uri }

    LaunchedEffect(matchState) {
        if (matchState is UiState.Success) {
            onNavigateToMatches()
            viewModel.resetMatchState()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(DarkBackground)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // ─── Cabecera ─────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Atrás",
                        tint = Gold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Encontré un perro",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Sube una foto del perro encontrado y buscaremos en nuestra base de datos si alguien lo está buscando",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Selector de foto ─────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(
                        width = 2.dp,
                        brush = Brush.horizontalGradient(listOf(Gold, GoldLight)),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .background(DarkSurface)
                    .clickable { imagePicker.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (selectedPhotoUri != null) {
                    AsyncImage(
                        model = selectedPhotoUri,
                        contentDescription = "Foto del perro encontrado",
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Cambiar foto", color = GoldLight, fontSize = 12.sp)
                    }
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = Gold,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Toca para agregar foto del perro",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Error message
            if (matchState is UiState.Error) {
                Text(
                    text = (matchState as UiState.Error).message,
                    color = ErrorRed,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ─── Botón buscar ─────────────────────────────────────────────────
            val isLoading = matchState is UiState.Loading
            Button(
                onClick = {
                    if (selectedPhotoUri != null) {
                        viewModel.matchFoundDog(context, selectedPhotoUri!!)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp),
                enabled = !isLoading && selectedPhotoUri != null
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (!isLoading && selectedPhotoUri != null) goldGradient
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = TextOnGold,
                                strokeWidth = 2.dp
                            )
                            Text(
                                "Analizando imagen con IA...",
                                color = TextOnGold,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Text(
                            "Buscar coincidencias",
                            color = if (selectedPhotoUri != null) TextOnGold else Color.White.copy(alpha = 0.5f),
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
