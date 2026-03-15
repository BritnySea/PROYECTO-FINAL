package com.univalle.proyectov1.ui.dogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.univalle.proyectov1.feature.dogs.domain.model.Dog
import com.univalle.proyectov1.ui.theme.*

@Composable
fun MyReportsScreen(
    viewModel: DogsViewModel,
    onNavigateToReport: () -> Unit
) {
    val myDogs by viewModel.myDogs.collectAsState()
    val isLoading by viewModel.myDogsLoading.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadMyDogs()
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

            Spacer(modifier = Modifier.height(24.dp))

            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Gold)
                    }
                }
                myDogs.isEmpty() -> {
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
                                text = "Aún no has registrado ningún perro perdido",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(myDogs) { dog ->
                            MyDogCard(dog = dog)
                        }
                        item { Spacer(modifier = Modifier.height(100.dp)) }
                    }
                }
            }
        }

        // FAB para agregar reporte
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
    }
}

@Composable
private fun MyDogCard(dog: Dog) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Foto
            if (dog.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = dog.photoUrl,
                    contentDescription = "Foto de ${dog.name}",
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
                Text(
                    text = dog.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                if (dog.createdAt.isNotBlank()) {
                    Text(
                        text = "Registrado el ${dog.createdAt.take(10)}",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }

            // Badge estado
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1B5E20).copy(alpha = 0.8f)
            ) {
                Text(
                    text = if (dog.status == "active") "Activo" else dog.status,
                    color = Color(0xFF4CAF50),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
