package com.univalle.proyectov1.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.univalle.proyectov1.core.result.UiState
import com.univalle.proyectov1.feature.user.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _phone = MutableStateFlow("")
    val phone: StateFlow<String> = _phone

    private val _saveState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val saveState: StateFlow<UiState<Unit>> = _saveState

    private val _phoneSaved = MutableStateFlow(false)
    val phoneSaved: StateFlow<Boolean> = _phoneSaved

    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing

    val displayName: String get() = auth.currentUser?.displayName ?: "Usuario"
    val email: String get() = auth.currentUser?.email ?: ""
    val photoUrl: String? get() = auth.currentUser?.photoUrl?.toString()

    init {
        // Recarga el teléfono cada vez que cambia el usuario autenticado
        auth.addAuthStateListener { firebaseAuth ->
            if (firebaseAuth.currentUser != null) {
                loadPhone()
            } else {
                _phone.value = ""
                _phoneSaved.value = false
                _isEditing.value = false
                _saveState.value = UiState.Idle
            }
        }
    }

    private fun loadPhone() {
        viewModelScope.launch {
            val saved = userRepository.getUserPhone() ?: ""
            _phone.value = saved
            _phoneSaved.value = saved.isNotBlank()
        }
    }

    fun onPhoneChange(value: String) {
        if (value.length <= 8 && value.all { it.isDigit() }) {
            _phone.value = value
        }
    }

    fun savePhone() {
        viewModelScope.launch {
            val phoneValue = _phone.value.trim()
            if (phoneValue.length != 8) {
                _saveState.value = UiState.Error("El celular debe tener exactamente 8 dígitos")
                return@launch
            }
            _saveState.value = UiState.Loading
            val result = userRepository.updateUserPhone(phoneValue)
            _saveState.value = result.fold(
                onSuccess = {
                    _phoneSaved.value = true
                    _isEditing.value = false
                    UiState.Success(Unit)
                },
                onFailure = { UiState.Error(it.message ?: "Error al guardar") }
            )
        }
    }

    fun startEditing() {
        _isEditing.value = true
        _saveState.value = UiState.Idle
    }

    fun resetSaveState() {
        _saveState.value = UiState.Idle
    }
}
