package com.univalle.proyectov1.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univalle.proyectov1.feature.user.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

// ViewModel que vive durante toda la sesión en MainActivity.
// Escucha en tiempo real si el admin bloquea al usuario activo.
@HiltViewModel
class SessionViewModel @Inject constructor(
    userRepository: UserRepository
) : ViewModel() {

    val isBlocked: StateFlow<Boolean> = userRepository
        .observeBlockedStatus()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = false
        )
}
