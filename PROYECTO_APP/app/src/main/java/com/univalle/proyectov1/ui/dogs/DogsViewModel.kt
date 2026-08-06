package com.univalle.proyectov1.ui.dogs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.univalle.proyectov1.core.result.RateLimitException
import com.univalle.proyectov1.core.result.UiState
import com.univalle.proyectov1.feature.dogs.domain.model.Dog
import com.univalle.proyectov1.feature.dogs.domain.model.DogMatch
import com.univalle.proyectov1.feature.dogs.domain.model.FoundDogMatchForOwner
import com.univalle.proyectov1.feature.dogs.domain.model.MyFoundReportWithMatches
import com.univalle.proyectov1.feature.dogs.domain.model.MyReport
import com.univalle.proyectov1.feature.dogs.domain.model.ReportType
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
    private val userRepository: UserRepository,
    private val auth: FirebaseAuth
) : ViewModel() {

    private var _currentUid: String? = auth.currentUser?.uid
    private val _authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val newUid = firebaseAuth.currentUser?.uid
        if (newUid != _currentUid) {
            _currentUid = newUid
            clearAllState()
        }
    }

    init {
        auth.addAuthStateListener(_authListener)
    }

    override fun onCleared() {
        super.onCleared()
        auth.removeAuthStateListener(_authListener)
    }

    fun clearAllState() {
        resetLostDogForm()
        resetFoundDogForm()
        _registerState.value = UiState.Idle
        _matchState.value = UiState.Idle
        _myDogs.value = emptyList()
        _myReports.value = emptyList()
        _ownerMatches.value = emptyList()
        _deactivateState.value = UiState.Idle
        _reportActionState.value = UiState.Idle
        _myFoundReports.value = emptyList()
        _finderMatches.value = null
    }

    // ─── Register Lost Dog ────────────────────────────────────────────────────
    private val _registerState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val registerState: StateFlow<UiState<Unit>> = _registerState

    // ─── Lost dog photos (hasta 3) ────────────────────────────────────────────
    val lostDogPhotoUris = mutableStateListOf<Uri>()
    val lostPhotoValidationStates = mutableStateListOf<PhotoValidationState>()

    // Form state
    var dogSize by mutableStateOf("")
    var dogColor by mutableStateOf("")
    var dogColorOther by mutableStateOf("")
    var dogBreed by mutableStateOf("")
    var dogSex by mutableStateOf("")
    var particularSigns by mutableStateOf("")
    var lostLocation by mutableStateOf("")

    // ─── Found dog photos (hasta 3) ───────────────────────────────────────────
    val foundDogPhotoUris = mutableStateListOf<Uri>()
    val foundPhotoValidationStates = mutableStateListOf<PhotoValidationState>()

    // Form state for found dog
    var foundDogSize by mutableStateOf("")
    var foundDogColor by mutableStateOf("")
    var foundDogColorOther by mutableStateOf("")
    var foundDogSex by mutableStateOf("")
    var foundDogSigns by mutableStateOf("")

    // ─── Match Found Dog ──────────────────────────────────────────────────────
    private val _matchState = MutableStateFlow<UiState<List<DogMatch>>>(UiState.Idle)
    val matchState: StateFlow<UiState<List<DogMatch>>> = _matchState

    // ─── My Reports ───────────────────────────────────────────────────────────
    private val _myDogs = MutableStateFlow<List<Dog>>(emptyList())
    val myDogs: StateFlow<List<Dog>> = _myDogs

    private val _myDogsLoading = MutableStateFlow(false)
    val myDogsLoading: StateFlow<Boolean> = _myDogsLoading

    private val _deactivateState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val deactivateState: StateFlow<UiState<Unit>> = _deactivateState

    private val _myReports = MutableStateFlow<List<MyReport>>(emptyList())
    val myReports: StateFlow<List<MyReport>> = _myReports

    private val _myReportsLoading = MutableStateFlow(false)
    val myReportsLoading: StateFlow<Boolean> = _myReportsLoading

    private val _reportActionState = MutableStateFlow<UiState<Unit>>(UiState.Idle)
    val reportActionState: StateFlow<UiState<Unit>> = _reportActionState

    private val _ownerMatches = MutableStateFlow<List<FoundDogMatchForOwner>>(emptyList())
    val ownerMatches: StateFlow<List<FoundDogMatchForOwner>> = _ownerMatches

    private val _ownerMatchesLoading = MutableStateFlow(false)
    val ownerMatchesLoading: StateFlow<Boolean> = _ownerMatchesLoading

    private val _myFoundReports = MutableStateFlow<List<MyReport>>(emptyList())
    val myFoundReports: StateFlow<List<MyReport>> = _myFoundReports

    private val _myFoundReportsLoading = MutableStateFlow(false)
    val myFoundReportsLoading: StateFlow<Boolean> = _myFoundReportsLoading

    private val _finderMatches = MutableStateFlow<MyFoundReportWithMatches?>(null)
    val finderMatches: StateFlow<MyFoundReportWithMatches?> = _finderMatches

    private val _finderMatchesLoading = MutableStateFlow(false)
    val finderMatchesLoading: StateFlow<Boolean> = _finderMatchesLoading

    // ─── Phone gate ───────────────────────────────────────────────────────────
    suspend fun hasPhone(): Boolean = userRepository.hasPhone()
    suspend fun getUserPhone(): String? = userRepository.getUserPhone()

    // ─── Lost dog photo management ────────────────────────────────────────────

    fun addOrReplaceLostDogPhoto(context: Context, uri: Uri, index: Int) {
        if (index < lostDogPhotoUris.size) {
            lostDogPhotoUris[index] = uri
            lostPhotoValidationStates[index] = PhotoValidationState.Loading
        } else {
            lostDogPhotoUris.add(uri)
            lostPhotoValidationStates.add(PhotoValidationState.Loading)
        }
        val targetIndex = index
        viewModelScope.launch {
            try {
                val file = uriToFile(context, uri)
                val result = dogsRepository.validateLostPhoto(file)
                val state = result.fold(
                    onSuccess = { PhotoValidationState.Valid },
                    onFailure = { PhotoValidationState.Invalid(it.message ?: "Foto no válida") }
                )
                if (targetIndex < lostPhotoValidationStates.size) {
                    lostPhotoValidationStates[targetIndex] = state
                }
            } catch (e: Exception) {
                if (targetIndex < lostPhotoValidationStates.size) {
                    lostPhotoValidationStates[targetIndex] = PhotoValidationState.Invalid("Error al analizar la foto")
                }
            }
        }
    }

    fun removeLostDogPhoto(index: Int) {
        if (index < lostDogPhotoUris.size) {
            lostDogPhotoUris.removeAt(index)
            lostPhotoValidationStates.removeAt(index)
        }
    }

    // ─── Found dog photo management ───────────────────────────────────────────

    fun addOrReplaceFoundDogPhoto(context: Context, uri: Uri, index: Int) {
        if (index < foundDogPhotoUris.size) {
            foundDogPhotoUris[index] = uri
            foundPhotoValidationStates[index] = PhotoValidationState.Loading
        } else {
            foundDogPhotoUris.add(uri)
            foundPhotoValidationStates.add(PhotoValidationState.Loading)
        }
        val targetIndex = index
        viewModelScope.launch {
            try {
                val file = uriToFile(context, uri)
                val result = dogsRepository.validateFoundPhoto(file)
                val state = result.fold(
                    onSuccess = { PhotoValidationState.Valid },
                    onFailure = { PhotoValidationState.Invalid(it.message ?: "Foto no válida") }
                )
                if (targetIndex < foundPhotoValidationStates.size) {
                    foundPhotoValidationStates[targetIndex] = state
                }
            } catch (e: Exception) {
                if (targetIndex < foundPhotoValidationStates.size) {
                    foundPhotoValidationStates[targetIndex] = PhotoValidationState.Invalid("Error al analizar la foto")
                }
            }
        }
    }

    fun removeFoundDogPhoto(index: Int) {
        if (index < foundDogPhotoUris.size) {
            foundDogPhotoUris.removeAt(index)
            foundPhotoValidationStates.removeAt(index)
        }
    }

    // ─── Actions ──────────────────────────────────────────────────────────────

    fun registerLostDog(
        context: Context,
        dogName: String,
        ownerName: String,
        ownerPhone: String,
        ownerEmail: String
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
            if (lostDogPhotoUris.isEmpty() || lostPhotoValidationStates.getOrNull(0) !is PhotoValidationState.Valid) {
                _registerState.value = UiState.Error("Agrega al menos una foto válida del perro")
                return@launch
            }

            _registerState.value = UiState.Loading
            try {
                val files = lostDogPhotoUris.map { uri -> uriToFile(context, uri) }
                val result = dogsRepository.registerLostDog(
                    dogName = dogName,
                    description = particularSigns,
                    ownerName = ownerName,
                    ownerPhone = ownerPhone,
                    ownerEmail = ownerEmail,
                    photos = files,
                    size = dogSize,
                    color = finalColor,
                    breed = dogBreed,
                    lostLocation = lostLocation,
                    sex = dogSex
                )
                result.fold(
                    onSuccess = { _registerState.value = UiState.Success(Unit) },
                    onFailure = { e ->
                        if (e is RateLimitException) {
                            _registerState.value = UiState.Error(e.message ?: "Límite semanal alcanzado")
                        } else {
                            _registerState.value = UiState.Error(e.message ?: "Error al registrar el perro")
                        }
                    }
                )
            } catch (e: Exception) {
                _registerState.value = UiState.Error(e.message ?: "Error inesperado al registrar")
            }
        }
    }

    fun resetLostDogForm() {
        lostDogPhotoUris.clear()
        lostPhotoValidationStates.clear()
        dogSize = ""
        dogColor = ""
        dogColorOther = ""
        dogBreed = ""
        dogSex = ""
        particularSigns = ""
        lostLocation = ""
    }

    fun matchFoundDog(
        context: Context,
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
            if (foundDogPhotoUris.isEmpty() || foundPhotoValidationStates.getOrNull(0) !is PhotoValidationState.Valid) {
                _matchState.value = UiState.Error("Agrega al menos una foto válida del perro")
                return@launch
            }

            _matchState.value = UiState.Loading
            try {
                val files = foundDogPhotoUris.map { uri -> uriToFile(context, uri) }
                val result = dogsRepository.matchFoundDog(
                    photos = files,
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
                        val filtered = matches.filter { it.similarityPercent >= 50 }
                        _matchState.value = UiState.Success(filtered)
                    },
                    onFailure = { e ->
                        if (e is RateLimitException) {
                            _matchState.value = UiState.Error(e.message ?: "Límite semanal alcanzado")
                        } else {
                            _matchState.value = UiState.Error(e.message ?: "Error al buscar coincidencias")
                        }
                    }
                )
            } catch (e: Exception) {
                _matchState.value = UiState.Error(e.message ?: "Error inesperado al buscar coincidencias")
            }
        }
    }

    fun resetFoundDogForm() {
        foundDogPhotoUris.clear()
        foundPhotoValidationStates.clear()
        foundDogSize = ""
        foundDogColor = ""
        foundDogColorOther = ""
        foundDogSex = ""
        foundDogSigns = ""
    }

    fun loadMyDogs() {
        viewModelScope.launch {
            _myDogsLoading.value = true
            _myDogs.value = dogsRepository.getMyLostDogs()
            _myDogsLoading.value = false
        }
    }

    fun deactivateDog(dogId: String) {
        viewModelScope.launch {
            _deactivateState.value = UiState.Loading
            val result = dogsRepository.deactivateDog(dogId)
            result.fold(
                onSuccess = {
                    _myDogs.value = _myDogs.value.map { dog ->
                        if (dog.id == dogId) dog.copy(status = "inactive") else dog
                    }
                    _deactivateState.value = UiState.Success(Unit)
                },
                onFailure = {
                    _deactivateState.value = UiState.Error(it.message ?: "Error al desactivar")
                }
            )
        }
    }

    fun resetDeactivateState() { _deactivateState.value = UiState.Idle }

    fun loadMyReports() {
        viewModelScope.launch {
            _myReportsLoading.value = true
            _myReports.value = dogsRepository.getMyReports()
            _myReportsLoading.value = false
        }
    }

    fun updateReportStatus(id: String, type: ReportType, active: Boolean, reason: String? = null) {
        viewModelScope.launch {
            _reportActionState.value = UiState.Loading
            val result = dogsRepository.updateReportStatus(id, type, active, reason)
            result.fold(
                onSuccess = {
                    val newStatus = if (active) "active" else "inactive"
                    _myReports.value = _myReports.value.map { report ->
                        if (report.id == id) report.copy(status = newStatus) else report
                    }
                    if (type == ReportType.LOST) {
                        _myDogs.value = _myDogs.value.map { dog ->
                            if (dog.id == id) dog.copy(status = newStatus) else dog
                        }
                    }
                    _reportActionState.value = UiState.Success(Unit)
                },
                onFailure = {
                    _reportActionState.value = UiState.Error(it.message ?: "Error al actualizar el reporte")
                }
            )
        }
    }

    fun resetReportActionState() { _reportActionState.value = UiState.Idle }

    fun loadMatchesForDog(dogId: String) {
        viewModelScope.launch {
            _ownerMatchesLoading.value = true
            val result = dogsRepository.getMatchesForDog(dogId)
            _ownerMatches.value = result.getOrDefault(emptyList())
            _ownerMatchesLoading.value = false
        }
    }

    fun loadMyFoundReports() {
        viewModelScope.launch {
            _myFoundReportsLoading.value = true
            _myFoundReports.value = dogsRepository.getMyFoundReportsOnly()
            _myFoundReportsLoading.value = false
        }
    }

    fun loadMatchesForFoundReport(reportId: String) {
        viewModelScope.launch {
            _finderMatchesLoading.value = true
            val result = dogsRepository.getMatchesForFoundReport(reportId)
            _finderMatches.value = result.getOrNull()
            _finderMatchesLoading.value = false
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

        val scale = target.toFloat() / minOf(w, h)
        val scaledW = (w * scale).toInt()
        val scaledH = (h * scale).toInt()
        val scaled = Bitmap.createScaledBitmap(original, scaledW, scaledH, true)

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
