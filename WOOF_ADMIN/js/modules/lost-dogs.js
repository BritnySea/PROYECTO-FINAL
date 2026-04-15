import { db } from '../firebase-config.js'
import {
  collection, getDocs, doc, updateDoc,
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js'
import { showToast, buildDogCard } from './utils.js'

// ── Estado ────────────────────────────────────────────────────────────────────
let allLostDogs   = []
let currentFilter = 'all'
let statusFilter  = 'all'
let rangeFrom     = null
let rangeTo       = null
let searchQuery   = ''
let currentModalDog = null

// ═════════════════════════════════════════════════════════════════════════════
//  INICIALIZACIÓN (listeners de UI)
// ═════════════════════════════════════════════════════════════════════════════
export function initLostDogs() {
  // Resetear estado de filtros al entrar a la sección
  currentFilter = 'all'
  statusFilter  = 'all'
  rangeFrom     = null
  rangeTo       = null
  searchQuery   = ''
  currentModalDog = null

  // Buscador por nombre, color, tamaño y sexo
  const searchInput = document.getElementById('lost-search')
  searchInput.value = ''
  searchInput.addEventListener('input', () => {
    searchQuery = searchInput.value.trim().toLowerCase()
    renderLostDogs()
  })

  // Botón refrescar
  document.getElementById('btn-refresh-lost').addEventListener('click', loadLostDogs)

  // Filtro por estado
  document.querySelectorAll('#status-filter-bar .filter-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('#status-filter-bar .filter-tab').forEach(t => t.classList.remove('active'))
      tab.classList.add('active')
      statusFilter = tab.dataset.status
      renderLostDogs()
    })
  })

  // Filtro por fecha
  document.querySelectorAll('#date-filter-bar .filter-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('#date-filter-bar .filter-tab').forEach(t => t.classList.remove('active'))
      tab.classList.add('active')
      currentFilter = tab.dataset.filter
      document.getElementById('date-range-inputs').classList.toggle('hidden', currentFilter !== 'range')
      if (currentFilter !== 'range') renderLostDogs()
    })
  })

  // Rango de fechas
  const dateFromInput = document.getElementById('date-from')
  const dateToInput   = document.getElementById('date-to')

  dateFromInput.addEventListener('change', () => {
    dateToInput.min = dateFromInput.value
    if (dateToInput.value && dateToInput.value < dateFromInput.value) {
      dateToInput.value = ''
    }
  })

  document.getElementById('btn-apply-range').addEventListener('click', () => {
    rangeFrom = dateFromInput.value || null
    rangeTo   = dateToInput.value   || null
    renderLostDogs()
  })

  // Modal: cerrar con X
  document.getElementById('dog-modal-close').addEventListener('click', closeDogModal)

  // Modal: botón toggle estado → abrir modal de razón
  document.getElementById('btn-toggle-status').addEventListener('click', () => {
    if (!currentModalDog) return
    const isActive = _isActive(currentModalDog)

    document.getElementById('lost-reason-title').textContent = isActive
      ? '¿Por qué desactivas esta publicación?'
      : '¿Por qué reactivas esta publicación?'

    document.getElementById('lost-reason-desc').textContent = isActive
      ? 'La publicación dejará de ser visible para otros usuarios.'
      : 'La publicación volverá a ser visible para otros usuarios.'

    const confirmBtn = document.getElementById('lost-reason-confirm')
    confirmBtn.textContent = isActive ? 'Sí, desactivar' : 'Sí, reactivar'
    confirmBtn.className   = isActive
      ? 'btn-confirm-yes btn-confirm-deactivate'
      : 'btn-confirm-yes btn-confirm-activate'
    confirmBtn.disabled = true

    const reasons = isActive
      ? ['Publicación duplicada', 'Información falsa o incorrecta', 'Foto inapropiada', 'El perro ya fue recuperado', 'Otro']
      : ['La publicación cumple las normas', 'Error al desactivarla', 'Otro']

    const listEl = document.getElementById('lost-reason-list')
    listEl.innerHTML = reasons.map(r => `
      <label class="reason-option">
        <input type="radio" name="lost-deactivate-reason" value="${r}" />
        <span>${r}</span>
      </label>
    `).join('')

    listEl.insertAdjacentHTML('beforeend', `
      <textarea id="lost-reason-other-text"
        placeholder="Describe el motivo..."
        class="reason-other-textarea hidden"
        maxlength="300"
        rows="3"></textarea>
    `)

    const otherTextarea = document.getElementById('lost-reason-other-text')

    listEl.querySelectorAll('input[type=radio]').forEach(radio => {
      radio.addEventListener('change', () => {
        const isOther = radio.value === 'Otro'
        otherTextarea.classList.toggle('hidden', !isOther)
        if (isOther) {
          otherTextarea.focus()
          confirmBtn.disabled = otherTextarea.value.trim() === ''
        } else {
          confirmBtn.disabled = false
        }
      })
    })

    otherTextarea.addEventListener('input', () => {
      confirmBtn.disabled = otherTextarea.value.trim() === ''
    })

    document.getElementById('lost-reason-overlay').classList.remove('hidden')
  })

  // Modal razón: cancelar
  document.getElementById('lost-reason-cancel').addEventListener('click', () => {
    document.getElementById('lost-reason-overlay').classList.add('hidden')
  })

  // Modal razón: confirmar
  document.getElementById('lost-reason-confirm').addEventListener('click', async () => {
    if (!currentModalDog) return
    const dog       = currentModalDog
    const isActive  = _isActive(dog)
    const newStatus = isActive ? 'inactive' : 'active'
    const confirmBtn = document.getElementById('lost-reason-confirm')

    const selectedRadio = document.querySelector('input[name="lost-deactivate-reason"]:checked')
    const otherText     = document.getElementById('lost-reason-other-text')?.value.trim() || ''
    const reason = selectedRadio?.value === 'Otro' ? otherText : (selectedRadio?.value || '')

    confirmBtn.disabled = true
    confirmBtn.innerHTML = '<div class="spinner white" style="width:16px;height:16px;border-width:2px"></div>'

    try {
      const updateData = { status: newStatus }
      if (reason) updateData.deactivation_reason = reason
      await updateDoc(doc(db, 'lost_dogs', dog.id), updateData)

      dog.status = newStatus
      const cached = allLostDogs.find(d => d.id === dog.id)
      if (cached) cached.status = newStatus

      document.getElementById('lost-reason-overlay').classList.add('hidden')

      const msg = newStatus === 'active'
        ? '✅ Publicación activada correctamente'
        : '✅ Publicación desactivada correctamente'
      showToast(msg, false)

      _refreshModalStatusUI(newStatus === 'active')
      renderLostDogs()
    } catch (err) {
      const msg = err.code === 'permission-denied'
        ? '❌ Sin permisos en Firestore. Revisa las reglas.'
        : `❌ Error: ${err.code || err.message}`
      showToast(msg)
      console.error('[toggle-status]', err.code, err.message, err)
    } finally {
      confirmBtn.disabled = false
      confirmBtn.textContent = isActive ? 'Sí, desactivar' : 'Sí, reactivar'
    }
  })
}

// ═════════════════════════════════════════════════════════════════════════════
//  CARGA Y RENDERIZADO
// ═════════════════════════════════════════════════════════════════════════════
export async function loadLostDogs() {
  const container = document.getElementById('list-lost-content')
  container.innerHTML = '<p class="empty-state">Cargando...</p>'
  try {
    const snap = await getDocs(collection(db, 'lost_dogs'))
    if (snap.empty) {
      container.innerHTML = '<p class="empty-state">No hay perros extraviados registrados.</p>'
      return
    }
    allLostDogs = []
    snap.forEach(d => allLostDogs.push({ id: d.id, ...d.data() }))
    allLostDogs.sort((a, b) => {
      const ta = a.created_at?.toDate?.() ?? new Date(0)
      const tb = b.created_at?.toDate?.() ?? new Date(0)
      return tb - ta
    })
    renderLostDogs()
  } catch (err) {
    container.innerHTML = '<p class="empty-state">Error al cargar los datos.</p>'
    console.error(err)
  }
}

function renderLostDogs() {
  const container = document.getElementById('list-lost-content')
  const now = new Date()

  const filtered = allLostDogs.filter(d => {
    // Filtro por nombre, color, tamaño y sexo
    if (searchQuery) {
      const haystack = [
        d.name        || '',
        d.color       || '',
        d.size        || '',
        d.sex         || '',
      ].join(' ').toLowerCase()
      if (!haystack.includes(searchQuery)) return false
    }

    // Filtro por estado
    const active = _isActive(d)
    if (statusFilter === 'active'   && !active) return false
    if (statusFilter === 'inactive' && active)  return false

    // Filtro por fecha
    if (currentFilter === 'all') return true

    const date = d.created_at?.toDate?.()
    if (!date) return false

    if (currentFilter === 'daily') {
      return date.toDateString() === now.toDateString()
    }
    if (currentFilter === 'weekly') {
      const weekAgo = new Date(now); weekAgo.setDate(weekAgo.getDate() - 7)
      return date >= weekAgo
    }
    if (currentFilter === 'monthly') {
      return date.getFullYear() === now.getFullYear() &&
             date.getMonth()    === now.getMonth()
    }
    if (currentFilter === 'range') {
      if (!rangeFrom && !rangeTo) return false   // sin rango aplicado → ocultar todo
      const from = rangeFrom ? new Date(rangeFrom + 'T00:00:00') : null
      const to   = rangeTo   ? new Date(rangeTo   + 'T23:59:59') : null
      if (from && date < from) return false
      if (to   && date > to)   return false
      return true
    }
    return true
  })

  if (filtered.length === 0) {
    const msg = (currentFilter === 'range' && !rangeFrom && !rangeTo)
      ? 'Selecciona un rango de fechas y presiona Aplicar'
      : 'No hay registros para el período seleccionado.'
    container.innerHTML = `<p class="empty-state">${msg}</p>`
    return
  }

  const grid = document.createElement('div')
  grid.className = 'dog-grid'
  filtered.forEach(d => {
    const card = buildDogCard(d.id, d)
    card.classList.add('dog-card--clickable')
    card.addEventListener('click', () => openDogModal(d))
    grid.appendChild(card)
  })
  container.innerHTML = ''
  container.appendChild(grid)
}

// ═════════════════════════════════════════════════════════════════════════════
//  MODAL DETALLE
// ═════════════════════════════════════════════════════════════════════════════
function openDogModal(data) {
  currentModalDog = data

  const photoEl       = document.getElementById('dog-modal-photo')
  const placeholderEl = document.getElementById('dog-modal-placeholder')
  if (data.photo_url) {
    photoEl.src = data.photo_url
    photoEl.alt = data.name || 'Perro'
    photoEl.classList.remove('hidden')
    placeholderEl.classList.add('hidden')
  } else {
    photoEl.classList.add('hidden')
    placeholderEl.classList.remove('hidden')
  }

  document.getElementById('dog-modal-name').textContent = (data.name || 'Sin nombre').toUpperCase()
  _refreshModalStatusUI(_isActive(data))

  const date = data.created_at?.toDate?.()
    ? data.created_at.toDate().toLocaleDateString('es-CO', { day: '2-digit', month: 'long', year: 'numeric' })
    : '—'

  // Definición de todos los campos posibles del formulario
  const FIELD_DEFS = [
    { key: 'owner_name',       icon: '👤', label: 'Nombre del dueño'     },
    { key: 'owner_phone',      icon: '📞', label: 'Teléfono'              },
    { key: 'owner_email',      icon: '✉️', label: 'Correo'                },
    { key: 'description',      icon: '📝', label: 'Descripción'           },
    { key: 'color',            icon: '🎨', label: 'Color'                 },
    { key: 'color_principal',  icon: '🎨', label: 'Color principal'       },
    { key: 'sexo',             icon: '🐾', label: 'Sexo'                  },
    { key: 'sex',              icon: '🐾', label: 'Sexo'                  },
    { key: 'tamaño',           icon: '📏', label: 'Tamaño'                },
    { key: 'size',             icon: '📏', label: 'Tamaño'                },
    { key: 'señas',            icon: '🔍', label: 'Señas particulares'    },
    { key: 'signs',            icon: '🔍', label: 'Señas particulares'    },
    { key: 'raza',             icon: '🐕', label: 'Raza'                  },
    { key: 'breed',            icon: '🐕', label: 'Raza'                  },
  ]

  // Solo incluir campos que tienen valor en Firebase (no nulos ni vacíos)
  const fields = FIELD_DEFS
    .filter(f => data[f.key] !== null && data[f.key] !== undefined && data[f.key] !== '')
    .map(f => ({ icon: f.icon, label: f.label, value: String(data[f.key]) }))

  // Fecha siempre al final
  fields.push({ icon: '📅', label: 'Fecha de registro', value: date })

  document.getElementById('dog-modal-fields').innerHTML = fields.map(f => `
    <div class="dog-modal-field">
      <p class="dog-modal-field-label">${f.icon} ${f.label}</p>
      <p class="dog-modal-field-value">${f.value}</p>
    </div>
  `).join('')

  document.getElementById('dog-modal-overlay').classList.remove('hidden')
  document.body.style.overflow = 'hidden'
}

function closeDogModal() {
  document.getElementById('dog-modal-overlay').classList.add('hidden')
  document.body.style.overflow = ''
  currentModalDog = null
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function _isActive(dog) {
  return dog.status === 'active' || dog.status === 'activo'
}

function _refreshModalStatusUI(isActive) {
  const tag = document.getElementById('dog-modal-status-tag')
  tag.textContent = isActive ? 'activo' : 'inactivo'
  tag.className   = isActive ? 'dog-modal-status-tag tag-active-modal' : 'dog-modal-status-tag tag-inactive-modal'

  const toggleBtn = document.getElementById('btn-toggle-status')
  toggleBtn.textContent = isActive ? 'Desactivar publicación' : 'Activar publicación'
  toggleBtn.className   = isActive ? 'btn-toggle-status btn-deactivate' : 'btn-toggle-status btn-activate'
}
