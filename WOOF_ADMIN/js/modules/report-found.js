import { auth } from '../firebase-config.js'
import { showToast } from './utils.js'
import { waitForAdminState, currentUser, currentUserData } from './admin-state.js'

const API_BASE = 'http://localhost:8000'

let selectedPhoto = null
let selectedSize  = ''
let selectedColor = ''
let selectedSex   = ''
let photoValidationState = 'idle'  // 'idle' | 'loading' | 'valid' | 'invalid'
let _skipNextUploadClick = false   // evita que el click fantasma reabra el diálogo

// ── Refs (se resuelven en initReportFound) ────────────────────────────────
let _nameInput, _phoneInput, _emailInput, _matchBtn

export function initReportFound() {
  // ── Foto ──────────────────────────────────────────────────────────────────
  const photoInput          = document.getElementById('photo-input')
  const uploadBox           = document.getElementById('upload-box')
  const uploadPreview       = document.getElementById('upload-preview')
  const uploadPlaceholder   = document.getElementById('upload-placeholder')
  const photoError          = document.getElementById('photo-error')
  const photoLoadingOverlay = document.getElementById('photo-loading-overlay')
  const photoValidBadge     = document.getElementById('photo-valid-badge')
  const photoChangeLabel    = document.getElementById('photo-change-label')
  const photoErrorOverlay   = document.getElementById('photo-error-overlay')
  const photoStatusRow      = document.getElementById('photo-status-row')

  // ── Modal consejo de foto ─────────────────────────────────────────────────
  const photoTipOverlay  = document.getElementById('photo-tip-overlay')
  const photoTipCancel   = document.getElementById('photo-tip-cancel')
  const photoTipContinue = document.getElementById('photo-tip-continue')

  // Interceptar clic en el recuadro → mostrar consejo primero
  uploadBox.addEventListener('click', () => {
    // Si venimos del botón "Entendido, continuar", ignorar este click fantasma
    if (_skipNextUploadClick) { _skipNextUploadClick = false; return }
    photoTipOverlay.classList.remove('hidden')
  })

  photoTipCancel.addEventListener('click', (e) => {
    e.stopPropagation()
    photoTipOverlay.classList.add('hidden')
  })

  photoTipContinue.addEventListener('click', (e) => {
    e.stopPropagation()  // evita que el click burbujee hasta el upload-box
    photoTipOverlay.classList.add('hidden')
    _skipNextUploadClick = true  // protege contra el click fantasma que genera el navegador
    setTimeout(() => photoInput.click(), 50)
  })

  // ── Selección de foto + validación con el backend ─────────────────────────
  photoInput.addEventListener('change', async (e) => {
    const file = e.target.files[0]
    if (!file) return

    selectedPhoto = file
    uploadPreview.src = URL.createObjectURL(file)
    uploadPreview.style.display = 'block'
    uploadPlaceholder.style.display = 'none'
    photoError.textContent = ''
    e.target.value = '' // permite re-seleccionar el mismo archivo

    _setPhotoState('loading', photoLoadingOverlay, photoValidBadge,
      photoChangeLabel, photoErrorOverlay, photoStatusRow, uploadBox)
    _updateSubmitBtn()

    try {
      const token = await auth.currentUser.getIdToken()
      const formData = new FormData()
      formData.append('photo', file)

      const res = await fetch(`${API_BASE}/api/v1/validate-found-photo`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
        body: formData,
      })

      if (res.status === 409) {
        const err = await res.json().catch(() => ({}))
        const msg = err.detail || 'Esta foto ya fue reportada anteriormente.'
        _setPhotoState('invalid', photoLoadingOverlay, photoValidBadge,
          photoChangeLabel, photoErrorOverlay, photoStatusRow, uploadBox, msg)
      } else if (!res.ok) {
        const err = await res.json().catch(() => ({}))
        const msg = err.detail || 'Error al validar la foto.'
        _setPhotoState('invalid', photoLoadingOverlay, photoValidBadge,
          photoChangeLabel, photoErrorOverlay, photoStatusRow, uploadBox, msg)
      } else {
        const data = await res.json()
        if (data.is_dog) {
          _setPhotoState('valid', photoLoadingOverlay, photoValidBadge,
            photoChangeLabel, photoErrorOverlay, photoStatusRow, uploadBox)
        } else {
          const msg = data.message || 'La foto no muestra un perro.'
          _setPhotoState('invalid', photoLoadingOverlay, photoValidBadge,
            photoChangeLabel, photoErrorOverlay, photoStatusRow, uploadBox, msg)
        }
      }
    } catch {
      _setPhotoState('invalid', photoLoadingOverlay, photoValidBadge,
        photoChangeLabel, photoErrorOverlay, photoStatusRow, uploadBox,
        'No se pudo conectar con el servidor para validar la foto.')
    }

    _updateSubmitBtn()
  })

  // ── Chips ─────────────────────────────────────────────────────────────────
  _bindChips('chips-size', val => {
    selectedSize = val
    document.getElementById('size-error').textContent = ''
    _updateSubmitBtn()
  })
  _bindChips('chips-sex', val => { selectedSex = val })
  _bindChips('chips-color', val => {
    selectedColor = val
    document.getElementById('color-other-wrapper').classList.toggle('hidden', val !== 'Otro')
    if (val !== 'Otro') document.getElementById('color-other').value = ''
    document.getElementById('color-error').textContent = ''
    _updateSubmitBtn()
  })

  document.getElementById('color-other').addEventListener('input', _updateSubmitBtn)

  // ── Campos de contacto ────────────────────────────────────────────────────
  _nameInput  = document.getElementById('reporter-name')
  _phoneInput = document.getElementById('reporter-phone')
  _emailInput = document.getElementById('reporter-email')
  _matchBtn   = document.getElementById('btn-match')

  // Auto-rellenar datos del perfil del administrador
  waitForAdminState().then(() => {
    const user = currentUser
    const data = currentUserData
    if (data?.name  && !_nameInput.value)  _nameInput.value  = data.name
    if (user?.email && !_emailInput.value) _emailInput.value = user.email
    if (data?.phone && !_phoneInput.value) _phoneInput.value = data.phone
    _updateSubmitBtn()
  })

  _phoneInput.addEventListener('input', () => {
    _phoneInput.value = _phoneInput.value.replace(/[^\d+\s\-()]/g, '')
    document.getElementById('phone-wrapper').classList.remove('error')
    document.getElementById('phone-error').textContent = ''
    _updateSubmitBtn()
  })

  _nameInput.addEventListener('input', () => {
    document.getElementById('name-wrapper').classList.remove('error')
    document.getElementById('name-error').textContent = ''
    _updateSubmitBtn()
  })

  let emailTouched = false
  _emailInput.addEventListener('blur',  () => { emailTouched = true; _validateEmail(_emailInput); _updateSubmitBtn() })
  _emailInput.addEventListener('input', () => {
    if (emailTouched) _validateEmail(_emailInput)
    _updateSubmitBtn()
  })

  // ── Botón buscar coincidencias ────────────────────────────────────────────
  _matchBtn.disabled = true

  _matchBtn.addEventListener('click', async () => {
    const { valid, finalColor } = _validateForm(_nameInput, _phoneInput, _emailInput)
    if (!valid) return

    _matchBtn.disabled = true
    _matchBtn.innerHTML = '<div class="spinner"></div> Analizando con IA...'

    try {
      const token = await auth.currentUser.getIdToken()

      const formData = new FormData()
      formData.append('photo',          selectedPhoto)
      formData.append('size',           selectedSize)
      formData.append('color',          finalColor)
      formData.append('sex',            selectedSex)
      formData.append('description',    document.getElementById('signs').value.trim())
      formData.append('reporter_name',  _nameInput.value.trim())
      formData.append('reporter_phone', _phoneInput.value.trim())
      formData.append('reporter_email', _emailInput.value.trim())

      const res = await fetch(`${API_BASE}/api/v1/match-found-dog`, {
        method: 'POST',
        headers: { 'Authorization': `Bearer ${token}` },
        body: formData,
      })

      if (!res.ok) {
        const err = await res.json().catch(() => ({}))
        throw new Error(err.detail || `Error ${res.status}`)
      }

      _showMatchResults(await res.json())
      _resetForm()
    } catch (err) {
      const msg = err.message.includes('fetch')
        ? '❌ No se pudo conectar con el servidor. Verifica que el backend esté corriendo.'
        : `❌ ${err.message}`
      showToast(msg)
    } finally {
      _matchBtn.disabled = false
      _matchBtn.innerHTML = 'Buscar coincidencias'
      _updateSubmitBtn()
    }
  })
}

// ── Resetea el formulario completo tras un envío exitoso ──────────────────
function _resetForm() {
  // Variables de estado
  selectedPhoto        = null
  selectedSize         = ''
  selectedColor        = ''
  selectedSex          = ''
  photoValidationState = 'idle'
  _skipNextUploadClick = false

  // ── Foto ──────────────────────────────────────────────────────────────────
  const uploadPreview       = document.getElementById('upload-preview')
  const uploadPlaceholder   = document.getElementById('upload-placeholder')
  const uploadBox           = document.getElementById('upload-box')
  const photoLoadingOverlay = document.getElementById('photo-loading-overlay')
  const photoValidBadge     = document.getElementById('photo-valid-badge')
  const photoChangeLabel    = document.getElementById('photo-change-label')
  const photoErrorOverlay   = document.getElementById('photo-error-overlay')
  const photoStatusRow      = document.getElementById('photo-status-row')

  uploadPreview.style.display = 'none'
  uploadPreview.src = ''
  uploadPlaceholder.style.display = ''
  uploadBox.classList.remove('has-photo', 'valid-photo', 'error-photo')
  photoLoadingOverlay.classList.add('hidden')
  photoValidBadge.classList.add('hidden')
  photoChangeLabel.classList.add('hidden')
  photoErrorOverlay.classList.add('hidden')
  document.getElementById('photo-error').textContent = ''

  photoStatusRow.className = 'photo-status-row photo-status-row--hint'
  photoStatusRow.innerHTML = `
    <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
      <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
      <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
    </svg>
    <span>Solo 1 foto</span>`

  // ── Chips ─────────────────────────────────────────────────────────────────
  ;['chips-size', 'chips-color', 'chips-sex'].forEach(groupId => {
    document.getElementById(groupId)
      .querySelectorAll('.chip')
      .forEach(c => c.classList.remove('selected'))
  })
  document.getElementById('color-other-wrapper').classList.add('hidden')
  document.getElementById('color-other').value = ''

  // ── Señas y errores ───────────────────────────────────────────────────────
  document.getElementById('signs').value = ''
  ;['size-error', 'color-error', 'name-error', 'phone-error', 'email-r-error'].forEach(id => {
    const el = document.getElementById(id)
    if (el) el.textContent = ''
  })
  ;['name-wrapper', 'phone-wrapper', 'email-r-wrapper'].forEach(id => {
    document.getElementById(id)?.classList.remove('error')
  })

  // ── Datos de contacto: re-rellenar desde el perfil ────────────────────────
  if (_nameInput)  _nameInput.value  = currentUserData?.name  || ''
  if (_emailInput) _emailInput.value = currentUser?.email      || ''
  if (_phoneInput) _phoneInput.value = currentUserData?.phone  || ''

  _updateSubmitBtn()
}

// ── Actualiza enable/disable del botón en tiempo real ─────────────────────
function _updateSubmitBtn() {
  if (!_matchBtn) return
  const finalColor = selectedColor === 'Otro'
    ? (document.getElementById('color-other')?.value.trim() ?? '')
    : selectedColor

  const ready = photoValidationState === 'valid' &&
    selectedSize !== '' &&
    finalColor  !== '' &&
    (_nameInput?.value.trim()  ?? '') !== '' &&
    (_phoneInput?.value.trim() ?? '') !== '' &&
    (_emailInput?.value.trim() ?? '') !== '' &&
    _isValidEmail(_emailInput?.value.trim() ?? '')

  _matchBtn.disabled = !ready
}

// ── Cambia el estado visual del recuadro de foto ──────────────────────────
function _setPhotoState(state, loadingOverlay, validBadge, changeLabel, errorOverlay, statusRow, uploadBox, errorMsg = '') {
  photoValidationState = state

  // Limpiar todo
  loadingOverlay.classList.add('hidden')
  validBadge.classList.add('hidden')
  changeLabel.classList.add('hidden')
  errorOverlay.classList.add('hidden')
  uploadBox.classList.remove('has-photo', 'valid-photo', 'error-photo')

  // Resetear status row
  statusRow.className = 'photo-status-row'
  statusRow.innerHTML = ''

  if (state === 'loading') {
    uploadBox.classList.add('has-photo')
    loadingOverlay.classList.remove('hidden')
    statusRow.classList.add('photo-status-row--loading')
    statusRow.innerHTML = `
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>
      </svg>
      <span>Verificando foto con el servidor...</span>`

  } else if (state === 'valid') {
    uploadBox.classList.add('valid-photo')
    validBadge.classList.remove('hidden')
    changeLabel.classList.remove('hidden')
    statusRow.classList.add('photo-status-row--valid')
    statusRow.innerHTML = `
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
        <polyline points="20 6 9 17 4 12"/>
      </svg>
      <span>Foto válida</span>`

  } else if (state === 'invalid') {
    uploadBox.classList.add('error-photo')
    errorOverlay.classList.remove('hidden')
    statusRow.classList.add('photo-status-row--invalid')
    statusRow.innerHTML = `
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round">
        <circle cx="12" cy="12" r="10"/>
        <line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>
      </svg>
      <span>${errorMsg}</span>`

  } else {
    // idle
    statusRow.classList.add('photo-status-row--hint')
    statusRow.innerHTML = `
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round">
        <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/>
        <line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>
      </svg>
      <span>Solo 1 foto</span>`
  }
}

// ── Helpers ───────────────────────────────────────────────────────────────
function _bindChips(groupId, onChange) {
  document.getElementById(groupId).querySelectorAll('.chip').forEach(chip => {
    chip.addEventListener('click', () => {
      document.getElementById(groupId).querySelectorAll('.chip').forEach(c => c.classList.remove('selected'))
      chip.classList.add('selected')
      onChange(chip.dataset.value)
    })
  })
}

function _isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

function _validateEmail(input) {
  const val     = input.value.trim()
  const wrapper = document.getElementById('email-r-wrapper')
  const err     = document.getElementById('email-r-error')
  if (val && !_isValidEmail(val)) {
    wrapper.classList.add('error')
    err.textContent = 'Formato de correo no válido'
  } else {
    wrapper.classList.remove('error')
    err.textContent = ''
  }
}

function _validateForm(nameInput, phoneInput, emailInput) {
  let valid = true

  if (photoValidationState !== 'valid') {
    document.getElementById('photo-error').textContent = 'La foto debe ser válida antes de continuar'
    valid = false
  }
  if (!selectedSize) {
    document.getElementById('size-error').textContent = 'Selecciona el tamaño'
    valid = false
  }
  const finalColor = selectedColor === 'Otro'
    ? document.getElementById('color-other').value.trim()
    : selectedColor
  if (!finalColor) {
    document.getElementById('color-error').textContent = 'Selecciona o describe el color'
    valid = false
  }
  if (!nameInput.value.trim()) {
    document.getElementById('name-wrapper').classList.add('error')
    document.getElementById('name-error').textContent = 'El nombre es requerido'
    valid = false
  }
  if (!phoneInput.value.trim()) {
    document.getElementById('phone-wrapper').classList.add('error')
    document.getElementById('phone-error').textContent = 'El teléfono es requerido'
    valid = false
  }
  const emailVal = emailInput.value.trim()
  if (!emailVal) {
    document.getElementById('email-r-wrapper').classList.add('error')
    document.getElementById('email-r-error').textContent = 'El correo es requerido'
    valid = false
  } else if (!_isValidEmail(emailVal)) {
    document.getElementById('email-r-wrapper').classList.add('error')
    document.getElementById('email-r-error').textContent = 'Formato de correo no válido'
    valid = false
  }

  return { valid, finalColor }
}

function _showMatchResults(data) {
  const matches    = data.matches || []
  const resultsDiv = document.getElementById('match-results')
  const titleEl    = document.getElementById('match-title')
  const cardsEl    = document.getElementById('match-cards')

  resultsDiv.classList.remove('hidden')

  if (matches.length === 0) {
    titleEl.textContent = 'Sin coincidencias encontradas'
    cardsEl.innerHTML = '<p class="empty-state" style="padding:20px 0">No se encontraron perros perdidos que coincidan con la foto.</p>'
    return
  }

  titleEl.textContent = `${matches.length} coincidencia${matches.length !== 1 ? 's' : ''} encontrada${matches.length !== 1 ? 's' : ''}`
  cardsEl.innerHTML = ''

  matches.forEach(m => {
    const card = document.createElement('div')
    card.className = 'match-card'
    card.innerHTML = `
      ${m.photo_url
        ? `<img class="dog-card-photo" src="${m.photo_url}" alt="${m.name}" loading="lazy" />`
        : `<div class="dog-card-photo-placeholder">🐾</div>`
      }
      <div class="match-card-body">
        <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:6px">
          <p class="match-card-name">${m.name || 'Sin nombre'}</p>
          <span class="match-percent">${Math.round(m.similarity_percent)}%</span>
        </div>
        <p class="match-card-contact">
          📞 ${m.owner_phone || '—'}<br>
          ✉️ ${m.owner_email || '—'}
        </p>
      </div>
    `
    cardsEl.appendChild(card)
  })

  resultsDiv.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
