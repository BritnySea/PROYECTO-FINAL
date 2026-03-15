package com.univalle.proyectov1.ui.splash

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.univalle.proyectov1.R
import com.univalle.proyectov1.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun isInternetAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

@Composable
fun SplashScreen(onSplashFinished: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val logoScale = remember { Animatable(0.4f) }
    val logoAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val ringAlpha = remember { Animatable(0f) }
    val ringScale = remember { Animatable(0.8f) }

    var noInternet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Logo aparece con efecto bounce lento
        launch { logoScale.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
        launch { logoAlpha.animateTo(1f, animationSpec = tween(1200)) }
        // Anillo dorado exterior aparece después del logo
        delay(500)
        launch { ringAlpha.animateTo(0.4f, animationSpec = tween(1200)) }
        launch { ringScale.animateTo(1f, animationSpec = tween(1200, easing = EaseOutCubic)) }
        // Texto aparece al final
        delay(700)
        textAlpha.animateTo(1f, animationSpec = tween(1000))
        // Pausa antes de verificar conexión y navegar
        delay(2500)

        if (isInternetAvailable(context)) {
            onSplashFinished()
        } else {
            noInternet = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Anillo exterior decorativo
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .scale(ringScale.value)
                        .alpha(ringAlpha.value)
                        .background(Color.Transparent)
                        .border(
                            1.dp,
                            Brush.sweepGradient(listOf(Gold, GoldLight, Gold)),
                            CircleShape
                        )
                )

                // Logo principal
                Surface(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(logoScale.value)
                        .alpha(logoAlpha.value),
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.06f),
                    border = BorderStroke(1.5.dp, Gold.copy(alpha = 0.5f))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_refugio),
                        contentDescription = "Logo Refugio WOOF",
                        modifier = Modifier.padding(16.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Refugio WOOF",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Un hogar para cada corazón",
                fontSize = 16.sp,
                color = Gold.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.alpha(textAlpha.value)
            )
        }

        // Mensaje de sin conexión (se muestra en la parte inferior)
        if (noInternet) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 32.dp, vertical = 60.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Sin conexión a internet",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Verifica tu conexión e intenta de nuevo.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        noInternet = false
                        scope.launch {
                            delay(300)
                            if (isInternetAvailable(context)) {
                                onSplashFinished()
                            } else {
                                noInternet = true
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Gold)
                ) {
                    Text(
                        text = "Reintentar",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
