package com.univalle.proyectov1.ui.dogs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.univalle.proyectov1.core.result.UiState
import com.univalle.proyectov1.feature.dogs.domain.model.Dog
import com.univalle.proyectov1.feature.dogs.domain.model.DogMatch
import com.univalle.proyectov1.feature.dogs.domain.model.FoundDogMatchForOwner
import com.univalle.proyectov1.feature.dogs.domain.repository.DogsRepository
import com.univalle.proyectov1.feature.user.domain.repository.UserRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

sealed class PhotoValidationState {
    object Idle : PhotoValidationState()
    object Loading : PhotoValidationState()
    object Valid : PhotoValidationState()
    data class Invalid(val message: String) : PhotoValidationState()
}

@HiltViewModel
class DogsViewModel @Inject constructor(
    private val dogsRepository: DogsRepository,
    private val userRepository: UserRepository
) : ViewModel() {

    // ─── Register Lost Dog ────────────────────────────────────────────────────
    private val _registerState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val registerState: StateFlow<UiState<Unit>> = _registerState

    // ─── Photo validation ─────────────────────────────────────────────────────
    var photoValidationState by mutableStateOf<PhotoValidationState>(PhotoValidationState.Idle)
    var lostDogPhotoUri by mutableStateOf<android.net.Uri?>(null)

    // Form state for new structured fields
    var dogSize by mutableStateOf("")
    var dogColor by mutableStateOf("")
    var dogColorOther by mutableStateOf("")
    var dogBreed by mutableStateOf("")
    var dogSex by mutableStateOf("")
    var particularSigns by mutableStateOf("")
    var lostLocation by mutableStateOf("")

    // ─── Photo validation (found dog) ─────────────────────────────────────────
    var foundPhotoValidationState by mutableStateOf<PhotoValidationState>(PhotoValidationState.Idle)
    var foundDogPhotoUri by mutableStateOf<android.net.Uri?>(null)

    // Form state for found dog
    var foundDogSize by mutableStateOf("")
    var foundDogColor by mutableStateOf("")
    var foundDogColorOther by mutableStateOf("")
    var foundDogSex by mutableStateOf("")
    var foundDogSigns by mutableStateOf("")

    // ─── Match Found Dog ──────────────────────────────────────────────────────
    private val _matchState = MutableStateFlow<UiState<List<DogMatch>>>(UiState.Idle)
    val matchState: StateFlow<UiState<List<DogMatch>>> = _matchState

    // Last results (shared with MatchResultsScreen)
    private val _matchResults = MutableStateFlow<List<DogMatch>>(emptyList())
    val matchResults: StateFlow<List<DogMatch>> = _matchResults

    // ─── My Reports ───────────────────────────────────────────────────────────
    private val _myDogs = MutableStateFlow<List<Dog>>(emptyList())
    val myDogs: StateFlow<List<Dog>> = _myDogs

    private val _myDogsLoading = MutableStateFlow(false)
    val myDogsLoading: StateFlow<Boolean> = _myDogsLoading

    private val _ownerMatches = MutableStateFlow<List<FoundDogMatchForOwner>>(emptyList())
    val ownerMatches: StateFlow<List<FoundDogMatchForOwner>> = _ownerMatches

    private val _ownerMatchesLoading = MutableStateFlow(false)
    val ownerMatchesLoading: StateFlow<Boolean> = _ownerMatchesLoading

    // ─── Phone gate ───────────────────────────────────────────────────────────
    suspend fun hasPhone(): Boolean = userRepository.hasPhone()
    suspend fun getUserPhone(): String? = userRepository.getUserPhone()

    // ─── Actions ──────────────────────────────────────────────────────────────

    fun validatePhoto(context: Context, uri: Uri) {
        viewModelScope.launch {
            photoValidationState = PhotoValidationState.Loading
            try {
                val file = uriToFile(context, uri)
                val result = dogsRepository.validatePhoto(file)
                photoValidationState = result.fold(
                    onSuccess = { PhotoValidationState.Valid },
                    onFailure = { PhotoValidationState.Invalid(it.message ?: "Foto no válida") }
                )
            } catch (e: Exception) {
                photoValidationState = PhotoValidationState.Invalid("Error al analizar la foto")
            }
        }
    }

    fun registerLostDog(
        context: Context,
        dogName: String,
        ownerName: String,
        ownerPhone: String,
        ownerEmail: String,
        photoUri: Uri
    ) {
        viewModelScope.launch {
            val finalColor = if (dogColor == "Otro") dogColorOther else dogColor

            if (dogName.isBlank() || ownerName.isBlank() || ownerPhone.isBlank() || ownerEmail.isBlank()) {
                _registerState.value = UiState.Error("Completa todos los campos obligatorios")
                return@launch
            }
            if (dogSize.isBlank()) {
                _registerState.value = UiState.Error("Selecciona el tamaño del perro")
                return@launch
            }
            if (finalColor.isBlank()) {
                _registerState.value = UiState.Error("Selecciona o describe el color del perro")
                return@launch
            }
            if (dogSex.isBlank()) {
                _registerState.value = UiState.Error("Selecciona el sexo del perro")
                return@launch
            }
            _registerState.value = UiState.Loading
            try {
                val file = uriToFile(context, photoUri)
                val result = dogsRepository.registerLostDog(
                    dogName = dogName,
                    description = particularSigns,
                    ownerName = ownerName,
                    ownerPhone = ownerPhone,
                    ownerEmail = ownerEmail,
                    photo = file,
                    size = dogSize,
                    color = finalColor,
                    breed = dogBreed,
                    lostLocation = lostLocation,
                    sex = dogSex
                )
                _registerState.value = result.fold(
                    onSuccess = { UiState.Success(Unit) },
                    onFailure = { UiState.Error(it.message ?: "Error al registrar") }
                )
            } catch (e: Exception) {
                _registerState.value = UiState.Error(e.message ?: "Error inesperado")
            }
        }
    }

    fun resetLostDogForm() {
        dogSize = ""
        dogColor = ""
        dogColorOther = ""
        dogBreed = ""
        dogSex = ""
        particularSigns = ""
        lostLocation = ""
        photoValidationState = PhotoValidationState.Idle
        lostDogPhotoUri = null
    }

    fun validateFoundPhoto(context: Context, uri: Uri) {
        viewModelScope.launch {
            foundPhotoValidationState = PhotoValidationState.Loading
            try {
                val file = uriToFile(context, uri)
                val result = dogsRepository.validatePhoto(file)
                foundPhotoValidationState = result.fold(
                    onSuccess = { PhotoValidationState.Valid },
                    onFailure = { PhotoValidationState.Invalid(it.message ?: "Foto no válida") }
                )
            } catch (e: Exception) {
                foundPhotoValidationState = PhotoValidationState.Invalid("Error al analizar la foto")
            }
        }
    }

    fun matchFoundDog(
        context: Context,
        photoUri: Uri,
        reporterName: String,
        reporterPhone: String,
        reporterEmail: String
    ) {
        viewModelScope.launch {
            val finalColor = if (foundDogColor == "Otro") foundDogColorOther else foundDogColor

            if (foundDogSize.isBlank()) {
                _matchState.value = UiState.Error("Selecciona el tamaño del perro")
                return@launch
            }
            if (finalColor.isBlank()) {
                _matchState.value = UiState.Error("Selecciona o describe el color del perro")
                return@launch
            }
            if (reporterName.isBlank()) {
                _matchState.value = UiState.Error("Ingresa tu nombre")
                return@launch
            }
            if (reporterPhone.isBlank()) {
                _matchState.value = UiState.Error("Ingresa tu teléfono de contacto")
                return@launch
            }
            if (reporterPhone.filter { it.isDigit() }.length < 7) {
                _matchState.value = UiState.Error("El teléfono debe tener al menos 7 dígitos")
                return@launch
            }
            if (reporterEmail.isBlank()) {
                _matchState.value = UiState.Error("Ingresa tu correo electrónico")
                return@launch
            }
            if (!reporterEmail.contains("@") || !reporterEmail.contains(".")) {
                _matchState.value = UiState.Error("El correo electrónico no es válido")
                return@launch
            }

            _matchState.value = UiState.Loading
            try {
                val file = uriToFile(context, photoUri)
                val result = dogsRepository.matchFoundDog(
                    photo = file,
                    size = foundDogSize,
                    color = finalColor,
                    sex = foundDogSex,
                    description = foundDogSigns,
                    reporterName = reporterName,
                    reporterPhone = reporterPhone,
                    reporterEmail = reporterEmail
                )
                result.fold(
                    onSuccess = { matches ->
                        _matchResults.value = matches
                        _matchState.value = UiState.Success(matches)
                    },
                    onFailure = {
                        _matchState.value = UiState.Error(it.message ?: "Error al buscar coincidencias")
                    }
                )
            } catch (e: Exception) {
                _matchState.value = UiState.Error(e.message ?: "Error inesperado")
            }
        }
    }

    fun resetFoundDogForm() {
        foundDogSize = ""
        foundDogColor = ""
        foundDogColorOther = ""
        foundDogSex = ""
        foundDogSigns = ""
        foundPhotoValidationState = PhotoValidationState.Idle
        foundDogPhotoUri = null
    }

    fun loadMyDogs() {
        viewModelScope.launch {
            _myDogsLoading.value = true
            _myDogs.value = dogsRepository.getMyLostDogs()
            _myDogsLoading.value = false
        }
    }

    fun loadMatchesForDog(dogId: String) {
        viewModelScope.launch {
            _ownerMatchesLoading.value = true
            val result = dogsRepository.getMatchesForDog(dogId)
            _ownerMatches.value = result.getOrDefault(emptyList())
            _ownerMatchesLoading.value = false
        }
    }

    fun resetRegisterState() { _registerState.value = UiState.Idle }
    fun resetMatchState() { _matchState.value = UiState.Idle }

    private fun uriToFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: error("No se pudo abrir la imagen")
        val original = BitmapFactory.decodeStream(inputStream)
            ?: error("No se pudo decodificar la imagen")

        val target = 800
        val w = original.width
        val h = original.height

        // Escalar manteniendo aspect ratio hasta que el lado más corto sea 800px
        val scale = target.toFloat() / minOf(w, h)
        val scaledW = (w * scale).toInt()
        val scaledH = (h * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(original, scaledW, scaledH, true)

        // Center crop a 800×800
        val left = (scaledW - target) / 2
        val top = (scaledH - target) / 2
        val cropped = Bitmap.createBitmap(scaled, left, top, target, target)

        val tempFile = File(context.cacheDir, "woof_temp_${System.currentTimeMillis()}.jpg")
        FileOutputStream(tempFile).use { output ->
            cropped.compress(Bitmap.CompressFormat.JPEG, 85, output)
        }

        if (scaled != original) original.recycle()
        if (cropped != scaled) scaled.recycle()

        return tempFile
    }
}
