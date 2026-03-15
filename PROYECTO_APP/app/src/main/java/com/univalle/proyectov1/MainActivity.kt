package com.univalle.proyectov1

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.firestore.FirebaseFirestore
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.univalle.proyectov1.ui.auth.LoginScreen
import com.univalle.proyectov1.ui.auth.RegisterScreen
import com.univalle.proyectov1.ui.dogs.DogsViewModel
import com.univalle.proyectov1.ui.dogs.MatchResultsScreen
import com.univalle.proyectov1.ui.dogs.MyReportsScreen
import com.univalle.proyectov1.ui.dogs.ReportFoundDogScreen
import com.univalle.proyectov1.ui.dogs.ReportLostDogScreen
import com.univalle.proyectov1.ui.home.HomeScreen
import com.univalle.proyectov1.ui.navigation.Routes
import com.univalle.proyectov1.ui.profile.ProfileScreen
import com.univalle.proyectov1.ui.profile.ProfileViewModel
import com.univalle.proyectov1.ui.session.SessionViewModel
import com.univalle.proyectov1.ui.splash.SplashScreen
import com.univalle.proyectov1.ui.theme.Gold
import com.univalle.proyectov1.ui.theme.Proyectov1Theme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    private fun saveFcmToken() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
            FirebaseFirestore.getInstance()
                .collection("users").document(uid)
                .update("fcmToken", token)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()
        saveFcmToken()
        setContent {
            Proyectov1Theme {
                val navController = rememberNavController()
                val currentBackStack by navController.currentBackStackEntryAsState()
                val currentRoute = currentBackStack?.destination?.route

                val currentUser = FirebaseAuth.getInstance().currentUser
                val startRoute =
                    if (currentUser != null && currentUser.isEmailVerified) Routes.HOME
                    else Routes.LOGIN

                // Pantallas que muestran BottomNav
                val bottomNavRoutes = setOf(
                    Routes.HOME, Routes.MY_REPORTS, Routes.MATCH_RESULTS, Routes.PROFILE
                )
                val showBottomNav = currentRoute in bottomNavRoutes

                // ViewModels compartidos entre pantallas del flujo principal
                val dogsViewModel: DogsViewModel = hiltViewModel()
                val profileViewModel: ProfileViewModel = hiltViewModel()
                val sessionViewModel: SessionViewModel = hiltViewModel()

                // Detectar bloqueo en tiempo real
                val isBlockedByAdmin by sessionViewModel.isBlocked.collectAsState()
                var showBlockedMessage by remember { mutableStateOf(false) }

                LaunchedEffect(isBlockedByAdmin) {
                    if (isBlockedByAdmin && FirebaseAuth.getInstance().currentUser != null) {
                        FirebaseAuth.getInstance().signOut()
                        showBlockedMessage = true
                        navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                }

                val scope = rememberCoroutineScope()

                // Función puerta de teléfono: verifica si el usuario tiene teléfono registrado
                // Si no → va a Perfil con banner de advertencia; si sí → ejecuta la acción
                val navigateWithPhoneGate: (action: () -> Unit) -> Unit = { action ->
                    scope.launch {
                        if (dogsViewModel.hasPhone()) {
                            action()
                        } else {
                            navController.navigate(Routes.PROFILE)
                        }
                    }
                }

                Scaffold(
                    bottomBar = {
                        if (showBottomNav) {
                            NavigationBar(
                                containerColor = Color(0xFF0A0A0A),
                                contentColor = Gold
                            ) {
                                val items = listOf(
                                    Triple(Routes.HOME, Icons.Default.Home, "Inicio"),
                                    Triple(Routes.MY_REPORTS, Icons.Default.Pets, "Mis Reportes"),
                                    Triple(Routes.MATCH_RESULTS, Icons.Default.Search, "Coincidencias"),
                                    Triple(Routes.PROFILE, Icons.Default.Person, "Perfil")
                                )
                                items.forEach { (route, icon, label) ->
                                    NavigationBarItem(
                                        modifier = if (label == "Coincidencias") Modifier.weight(1.4f) else Modifier.weight(1f),
                                        selected = currentRoute == route,
                                        onClick = {
                                            navController.navigate(route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = icon,
                                                contentDescription = label
                                            )
                                        },
                                        label = { Text(label, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = Gold,
                                            selectedTextColor = Gold,
                                            unselectedIconColor = Color.White.copy(alpha = 0.4f),
                                            unselectedTextColor = Color.White.copy(alpha = 0.4f),
                                            indicatorColor = Gold.copy(alpha = 0.15f)
                                        )
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavHost(
                            navController = navController,
                            startDestination = Routes.SPLASH
                        ) {
                            // ── Auth ──────────────────────────────────────────
                            composable(Routes.SPLASH) {
                                SplashScreen(
                                    onSplashFinished = {
                                        navController.navigate(startRoute) {
                                            popUpTo(Routes.SPLASH) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            composable(Routes.LOGIN) {
                                LoginScreen(
                                    onLoginSuccess = {
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(Routes.LOGIN) { inclusive = true }
                                        }
                                    },
                                    onNavigateToRegister = {
                                        navController.navigate(Routes.REGISTER)
                                    },
                                    showBlockedMessage = showBlockedMessage,
                                    onBlockedMessageShown = { showBlockedMessage = false }
                                )
                            }

                            composable(Routes.REGISTER) {
                                RegisterScreen(
                                    onRegisterSuccess = {
                                        navController.navigate(Routes.LOGIN) {
                                            popUpTo(Routes.REGISTER) { inclusive = true }
                                        }
                                    },
                                    onNavigateToLogin = {
                                        navController.popBackStack()
                                    },
                                    onGoogleSignInSuccess = {
                                        navController.navigate(Routes.HOME) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // ── Main tabs ─────────────────────────────────────
                            composable(Routes.HOME) {
                                HomeScreen(
                                    onNavigateToReportLost = {
                                        navigateWithPhoneGate {
                                            navController.navigate(Routes.REPORT_LOST)
                                        }
                                    },
                                    onNavigateToReportFound = {
                                        navigateWithPhoneGate {
                                            navController.navigate(Routes.REPORT_FOUND)
                                        }
                                    },
                                    onNavigateToProfile = {
                                        navController.navigate(Routes.PROFILE)
                                    }
                                )
                            }

                            composable(Routes.MY_REPORTS) {
                                MyReportsScreen(
                                    viewModel = dogsViewModel,
                                    onNavigateToReport = {
                                        navigateWithPhoneGate {
                                            navController.navigate(Routes.REPORT_LOST)
                                        }
                                    }
                                )
                            }

                            composable(Routes.MATCH_RESULTS) {
                                MatchResultsScreen(viewModel = dogsViewModel)
                            }

                            composable(Routes.PROFILE) {
                                val showBanner = currentBackStack
                                    ?.arguments?.getBoolean("show_phone_banner") == true
                                ProfileScreen(
                                    viewModel = profileViewModel,
                                    showPhoneBanner = showBanner,
                                    onSignOut = {
                                        FirebaseAuth.getInstance().signOut()
                                        navController.navigate(Routes.LOGIN) {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                )
                            }

                            // ── Report flows ──────────────────────────────────
                            composable(Routes.REPORT_LOST) {
                                ReportLostDogScreen(
                                    viewModel = dogsViewModel,
                                    onBack = { navController.popBackStack() },
                                    onSuccess = {
                                        navController.navigate(Routes.MY_REPORTS) {
                                            popUpTo(Routes.HOME)
                                        }
                                    }
                                )
                            }

                            composable(Routes.REPORT_FOUND) {
                                ReportFoundDogScreen(
                                    viewModel = dogsViewModel,
                                    onBack = { navController.popBackStack() },
                                    onNavigateToMatches = {
                                        navController.navigate(Routes.MATCH_RESULTS) {
                                            popUpTo(Routes.HOME)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
