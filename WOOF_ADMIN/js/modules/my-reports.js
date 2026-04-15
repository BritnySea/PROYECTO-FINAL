import { db } from '../firebase-config.js'
import {
  collection, query, where, getDocs, doc, updateDoc,
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js'
import { waitForAdminState, currentUser } from './admin-state.js'
import { showToast } from './utils.js'

// ── Estado ────────────────────────────────────────────────────────────────────
let allMyReports       = []
let myDateFilter       = 'all'
let myStatusFilter     = 'all'
let myRangeFrom        = null
let myRangeTo          = null
let mySearchQuery      = ''
let currentModalReport = null

// ═════════════════════════════════════════════════════════════════════════════
//  INICIALIZACIÓN
// ═════════════════════════════════════════════════════════════════════════════
export function initMyReports() {
  myDateFilter       = 'all'
  myStatusFilter     = 'all'
  myRangeFrom        = null
  myRangeTo          = null
  mySearchQuery      = ''
  currentModalReport = null

  // Buscador
  const searchInput = document.getElementById('my-reports-search')
  searchInput.value = ''
  searchInput.addEventListener('input', () => {
    mySearchQuery = searchInput.value.trim().toLowerCase()
    renderMyReports()
  })

  // Botón refrescar
  document.getElementById('btn-refresh-my-reports').addEventListener('click', loadMyReports)

  // Filtro por estado
  document.querySelectorAll('#my-status-filter-bar .filter-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('#my-status-filter-bar .filter-tab').forEach(t => t.classList.remove('active'))
      tab.classList.add('active')
      myStatusFilter = tab.dataset.status
      renderMyReports()
    })
  })

  // Filtro por fecha
  document.querySelectorAll('#my-date-filter-bar .filter-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('#my-date-filter-bar .filter-tab').forEach(t => t.classList.remove('active'))
      tab.classList.add('active')
      myDateFilter = tab.dataset.filter
      document.getElementById('my-date-range-inputs').classList.toggle('hidden', myDateFilter !== 'range')
      if (myDateFilter !== 'range') renderMyReports()
    })
  })

  // Rango de fechas
  const dateFrom = document.getElementById('my-date-from')
  const dateTo   = document.getElementById('my-date-to')
  dateFrom.addEventListener('change', () => {
    dateTo.min = dateFrom.value
    if (dateTo.value && dateTo.value < dateFrom.value) dateTo.value = ''
  })
  document.getElementById('my-btn-apply-range').addEventListener('click', () => {
    myRangeFrom = dateFrom.value || null
    myRangeTo   = dateTo.value   || null
    renderMyReports()
  })

  // Modal detalle: cerrar con X
  document.getElementById('my-modal-close').addEventListener('click', closeMyModal)
  document.getElementById('my-modal-overlay').addEventListener('click', e => {
    if (e.target === document.getElementById('my-modal-overlay')) closeMyModal()
  })

  // Modal detalle: botón desactivar → abrir modal de razón
  document.getElementById('my-btn-toggle-status').addEventListener('click', () => {
    if (!currentModalReport) return
    const isActive = _isActive(currentModalReport)

    document.getElementById('my-reason-title').textContent = isActive
      ? '¿Por qué desactivas este reporte?'
      : '¿Por qué reactivas este reporte?'

    document.getElementById('my-reason-desc').textContent = isActive
      ? 'El reporte dejará de ser visible para otros usuarios.'
      : 'El reporte volverá a ser visible para otros usuarios.'

    const confirmBtn = document.getElementById('my-reason-confirm')
    confirmBtn.textContent = isActive ? 'Sí, desactivar' : 'Sí, reactivar'
    confirmBtn.className   = isActive
      ? 'btn-confirm-yes btn-confirm-deactivate'
      : 'btn-confirm-yes btn-confirm-activate'
    confirmBtn.disabled = true

    // Opciones fijas para el refugio (admin)
    const reasons = isActive
      ? ['Fue devuelto al dueño', 'Información incorrecta en el reporte', 'Otro']
      : ['El perro sigue perdido', 'Fue un error desactivarlo', 'Otro']

    const listEl = document.getElementById('my-reason-list')
    listEl.innerHTML = reasons.map(r => `
      <label class="reason-option">
        <input type="radio" name="my-deactivate-reason" value="${r}" />
        <span>${r}</span>
      </label>
    `).join('')

    // Textarea para "Otro" (oculta inicialmente)
    listEl.insertAdjacentHTML('beforeend', `
      <textarea id="my-reason-other-text"
        placeholder="Describe el motivo..."
        class="reason-other-textarea hidden"
        maxlength="300"
        rows="3"></textarea>
    `)

    const otherTextarea = document.getElementById('my-reason-other-text')

    listEl.querySelectorAll('input[type=radio]').forEach(radio => {
      radio.addEventListener('change', () => {
        const isOther = radio.value === 'Otro'
        otherTextarea.classList.toggle('hidden', !isOther)
        if (isOther) {
          otherTextarea.focus()
          // Habilitar solo si ya hay texto
          confirmBtn.disabled = otherTextarea.value.trim() === ''
        } else {
          confirmBtn.disabled = false
        }
      })
    })

    otherTextarea.addEventListener('input', () => {
      confirmBtn.disabled = otherTextarea.value.trim() === ''
    })

    document.getElementById('my-reason-overlay').classList.remove('hidden')
  })

  // Modal razón: cancelar
  document.getElementById('my-reason-cancel').addEventListener('click', () => {
    document.getElementById('my-reason-overlay').classList.add('hidden')
  })

  // Modal razón: confirmar
  document.getElementById('my-reason-confirm').addEventListener('click', async () => {
    if (!currentModalReport) return
    const report    = currentModalReport
    const isActive  = _isActive(report)
    const newStatus = isActive ? 'inactive' : 'active'
    const confirmBtn = document.getElementById('my-reason-confirm')

    // Recoger razón seleccionada (si es "Otro", usar el texto escrito)
    const selectedRadio = document.querySelector('input[name="my-deactivate-reason"]:checked')
    const otherText     = document.getElementById('my-reason-other-text')?.value.trim() || ''
    const reason = selectedRadio?.value === 'Otro' ? otherText : (selectedRadio?.value || '')

    confirmBtn.disabled = true
    confirmBtn.innerHTML = '<div class="spinner white" style="width:16px;height:16px;border-width:2px"></div>'

    try {
      const collName = report.type === 'lost' ? 'lost_dogs' : 'found_dog_reports'
      const updateData = { status: newStatus }
      if (reason) updateData.deactivation_reason = reason
      await updateDoc(doc(db, collName, report.id), updateData)

      report.status = newStatus
      const cached = allMyReports.find(r => r.id === report.id)
      if (cached) cached.status = newStatus

      document.getElementById('my-reason-overlay').classList.add('hidden')
      showToast(newStatus === 'active' ? '✅ Publicación activada' : '✅ Publicación desactivada', false)

      _refreshModalStatusUI(newStatus === 'active')
      renderMyReports()
    } catch (err) {
      showToast(err.code === 'permission-denied' ? '❌ Sin permisos en Firestore.' : `❌ Error: ${err.message}`)
      console.error('[my-reports toggle]', err)
    } finally {
      confirmBtn.disabled = false
      confirmBtn.textContent = isActive ? 'Sí, desactivar' : 'Sí, reactivar'
    }
  })
}

// ═════════════════════════════════════════════════════════════════════════════
//  CARGA DE DATOS
// ═════════════════════════════════════════════════════════════════════════════
export async function loadMyReports() {
  const container = document.getElementById('my-reports-content')
  container.innerHTML = '<p class="empty-state">Cargando...</p>'

  await waitForAdminState()
  const uid = currentUser?.uid
  if (!uid) {
    container.innerHTML = '<p class="empty-state">No se pudo obtener tu usuario.</p>'
    return
  }

  try {
    // Perros extraviados registrados por el usuario actual
    const lostSnap = await getDocs(
      query(collection(db, 'lost_dogs'), where('registered_by_uid', '==', uid))
    )
    const lostReports = []
    lostSnap.forEach(d => lostReports.push({ id: d.id, type: 'lost', ...d.data() }))

    // Reportes de perros encontrados por el usuario actual
    const foundSnap = await getDocs(
      query(collection(db, 'found_dog_reports'), where('found_by_uid', '==', uid))
    )
    const foundReports = []
    foundSnap.forEach(d => foundReports.push({ id: d.id, type: 'found', ...d.data() }))

    allMyReports = [...lostReports, ...foundReports]
    allMyReports.sort((a, b) => {
      const ta = a.created_at?.toDate?.() ?? new Date(0)
      const tb = b.created_at?.toDate?.() ?? new Date(0)
      return tb - ta
    })

    renderMyReports()
  } catch (err) {
    container.innerHTML = '<p class="empty-state">Error al cargar tus reportes.</p>'
    console.error('[my-reports] load error:', err)
  }
}

// ═════════════════════════════════════════════════════════════════════════════
//  RENDER
// ═════════════════════════════════════════════════════════════════════════════
function renderMyReports() {
  const container = document.getElementById('my-reports-content')
  const now = new Date()

  const filtered = allMyReports.filter(r => {
    // Búsqueda por tamaño, color, sexo, nombre, raza, descripción
    if (mySearchQuery) {
      const q = mySearchQuery
      const haystack = [
        r.size        || r.tamaño  || '',
        r.color       || '',
        r.sex         || r.sexo    || '',
        r.name        || '',
        r.breed       || r.raza    || '',
        r.description || '',
      ].join(' ').toLowerCase()
      if (!haystack.includes(q)) return false
    }

    // Filtro por estado
    const active = _isActive(r)
    if (myStatusFilter === 'active'   && !active) return false
    if (myStatusFilter === 'inactive' && active)  return false

    // Filtro por fecha
    if (myDateFilter === 'all') return true
    const date = r.created_at?.toDate?.()
    if (!date) return false
    if (myDateFilter === 'daily')   return date.toDateString() === now.toDateString()
    if (myDateFilter === 'weekly') {
      const weekAgo = new Date(now); weekAgo.setDate(weekAgo.getDate() - 7)
      return date >= weekAgo
    }
    if (myDateFilter === 'monthly') {
      return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth()
    }
    if (myDateFilter === 'range') {
      if (!myRangeFrom && !myRangeTo) return false
      const from = myRangeFrom ? new Date(myRangeFrom + 'T00:00:00') : null
      const to   = myRangeTo   ? new Date(myRangeTo   + 'T23:59:59') : null
      if (from && date < from) return false
      if (to   && date > to)   return false
      return true
    }
    return true
  })

  if (filtered.length === 0) {
    const msg = (myDateFilter === 'range' && !myRangeFrom && !myRangeTo)
      ? 'Selecciona un rango de fechas y presiona Aplicar'
      : mySearchQuery
        ? `Sin resultados para "${mySearchQuery}"`
        : 'No tienes reportes para este período.'
    container.innerHTML = `<p class="empty-state">${msg}</p>`
    return
  }

  const grid = document.createElement('div')
  grid.className = 'dog-grid'
  filtered.forEach(r => {
    const card = _buildMyCard(r)
    card.classList.add('dog-card--clickable')
    card.addEventListener('click', () => openMyModal(r))
    grid.appendChild(card)
  })
  container.innerHTML = ''
  container.appendChild(grid)
}

// ─── Tarjeta individual ───────────────────────────────────────────────────────
function _buildMyCard(data) {
  const card = document.createElement('div')
  card.className = 'dog-card'

  const date = data.created_at?.toDate
    ? data.created_at.toDate().toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
    : '—'

  const isLost   = data.type === 'lost'
  const isActive = _isActive(data)
  const photoUrl = data.photo_url || data.found_dog_photo_url || ''
  const typeLabel = isLost ? '🔴 Perro extraviado' : '🔵 Perro encontrado'
  const typeColor = isLost ? '#CE93D8' : '#90CAF9'
  const size  = data.size  || data.tamaño || ''
  const color = data.color || ''
  const sex   = data.sex   || data.sexo   || ''

  const details = [
    size  && `📏 ${size}`,
    color && `🎨 ${color}`,
    sex   && `🐾 ${sex}`,
  ].filter(Boolean).join('&emsp;')

  card.innerHTML = `
    <div class="my-card-type-label" style="color:${typeColor}">${typeLabel}</div>
    ${photoUrl
      ? `<img class="dog-card-photo my-card-photo-fit" src="${photoUrl}" alt="" loading="lazy" />`
      : `<div class="dog-card-photo-placeholder">🐾</div>`
    }
    <div class="dog-card-body">
      <p class="dog-card-name">${isLost ? (data.name || 'Sin nombre').toUpperCase() : 'PERRO ENCONTRADO'}</p>
      <p class="dog-card-meta">
        ${details ? details + '<br>' : ''}
        📅 ${date}
      </p>
      <span class="dog-card-tag ${isActive ? 'tag-active' : 'tag-inactive'}">${isActive ? 'Activo' : 'Inactivo'}</span>
    </div>
  `
  return card
}

// ═════════════════════════════════════════════════════════════════════════════
//  MODAL DETALLE
// ═════════════════════════════════════════════════════════════════════════════
function openMyModal(data) {
  currentModalReport = data

  const isLost   = data.type === 'lost'
  const photoUrl = data.photo_url || data.found_dog_photo_url || ''

  // Foto
  const photoEl       = document.getElementById('my-modal-photo')
  const placeholderEl = document.getElementById('my-modal-placeholder')
  if (photoUrl) {
    photoEl.src = photoUrl
    photoEl.classList.remove('hidden')
    placeholderEl.classList.add('hidden')
  } else {
    photoEl.classList.add('hidden')
    placeholderEl.classList.remove('hidden')
  }

  // Encabezado
  document.getElementById('my-modal-name').textContent = isLost
    ? (data.name || 'Sin nombre')
    : 'Reporte de perro encontrado'

  const typeBadge = document.getElementById('my-modal-type')
  typeBadge.textContent = isLost ? 'Perro extraviado' : 'Perro encontrado'
  typeBadge.style.cssText = isLost
    ? 'color:#CE93D8; font-size:12px; font-weight:600'
    : 'color:#90CAF9; font-size:12px; font-weight:600'

  _refreshModalStatusUI(_isActive(data))

  // Campos del formulario
  const date = data.created_at?.toDate?.()
    ? data.created_at.toDate().toLocaleDateString('es-CO', { day: '2-digit', month: 'long', year: 'numeric' })
    : '—'

  const lostFields = [
    data.name         && { icon: '🐕', label: 'Nombre del perro',        value: data.name },
    data.owner_name   && { icon: '👤', label: 'Nombre del dueño',         value: data.owner_name },
    data.owner_phone  && { icon: '📞', label: 'Teléfono del dueño',       value: data.owner_phone },
    data.owner_email  && { icon: '✉️',  label: 'Correo del dueño',         value: data.owner_email },
    (data.size || data.tamaño) && { icon: '📏', label: 'Tamaño',          value: data.size || data.tamaño },
    data.color        && { icon: '🎨', label: 'Color',                    value: data.color },
    (data.sex || data.sexo) && { icon: '🐾', label: 'Sexo',               value: data.sex || data.sexo },
    (data.breed || data.raza) && { icon: '🔍', label: 'Raza',             value: data.breed || data.raza },
    data.description  && { icon: '📝', label: 'Señas particulares',        value: data.description },
    data.lost_location && { icon: '📍', label: 'Lugar donde se perdió',   value: data.lost_location },
    { icon: '📅', label: 'Fecha de registro', value: date },
  ].filter(Boolean)

  const foundFields = [
    data.reporter_name  && { icon: '👤', label: 'Tu nombre',              value: data.reporter_name },
    data.reporter_phone && { icon: '📞', label: 'Tu teléfono',            value: data.reporter_phone },
    data.reporter_email && { icon: '✉️',  label: 'Tu correo',              value: data.reporter_email },
    (data.size || data.tamaño) && { icon: '📏', label: 'Tamaño',          value: data.size || data.tamaño },
    data.color          && { icon: '🎨', label: 'Color',                  value: data.color },
    (data.sex || data.sexo) && { icon: '🐾', label: 'Sexo',               value: data.sex || data.sexo },
    data.description    && { icon: '📝', label: 'Señas particulares',      value: data.description },
    { icon: '📅', label: 'Fecha del reporte', value: date },
  ].filter(Boolean)

  const fields = isLost ? lostFields : foundFields
  document.getElementById('my-modal-fields').innerHTML = fields.map(f => `
    <div class="dog-modal-field">
      <p class="dog-modal-field-label">${f.icon} ${f.label}</p>
      <p class="dog-modal-field-value">${f.value}</p>
    </div>
  `).join('')

  document.getElementById('my-modal-overlay').classList.remove('hidden')
  document.body.style.overflow = 'hidden'
}

function closeMyModal() {
  document.getElementById('my-modal-overlay').classList.add('hidden')
  document.body.style.overflow = ''
  currentModalReport = null
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function _isActive(r) {
  // Sin campo status → el reporte nunca fue modificado → se considera activo
  return !r.status || r.status === 'active' || r.status === 'activo'
}

function _refreshModalStatusUI(isActive) {
  const tag     = document.getElementById('my-modal-status-tag')
  tag.textContent = isActive ? 'activo' : 'inactivo'
  tag.className   = isActive ? 'dog-modal-status-tag tag-active-modal' : 'dog-modal-status-tag tag-inactive-modal'

  const btn     = document.getElementById('my-btn-toggle-status')
  btn.textContent = isActive ? 'Desactivar publicación' : 'Activar publicación'
  btn.className   = isActive ? 'btn-toggle-status btn-deactivate' : 'btn-toggle-status btn-activate'
}
