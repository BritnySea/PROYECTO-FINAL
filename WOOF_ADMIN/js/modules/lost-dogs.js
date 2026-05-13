import { db } from '../firebase-config.js'
import {
  collection, getDocs, doc, updateDoc, serverTimestamp, deleteField,
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js'
import { showToast, buildDogCard, escapeHtml } from './utils.js'

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

  // Botón exportar PDF
  document.getElementById('btn-pdf-lost').addEventListener('click', _generatePDF)

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
      if (newStatus === 'inactive') {
        updateData.deactivated_at = serverTimestamp()
        if (reason === 'El perro ya fue recuperado') updateData.returned_to_owner = true
      } else {
        updateData.deactivated_at = deleteField()
        updateData.returned_to_owner = deleteField()
      }
      await updateDoc(doc(db, 'lost_dogs', dog.id), updateData)

      const now = new Date()
      dog.status = newStatus
      if (newStatus === 'inactive') dog.deactivated_at = { toDate: () => now }
      else delete dog.deactivated_at
      const cached = allLostDogs.find(d => d.id === dog.id)
      if (cached) {
        cached.status = newStatus
        if (newStatus === 'inactive') cached.deactivated_at = { toDate: () => now }
        else delete cached.deactivated_at
      }

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
      console.error('[toggle-status]', err?.code, err?.message)
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
    console.error('[lost-dogs] error:', err?.code, err?.message)
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
  const thumbsEl      = document.getElementById('dog-modal-thumbs')
  const allUrls = (() => {
    const urls = []
    for (let i = 1; i <= 3; i++) {
      if (data[`photo_url_${i}`]) urls.push(data[`photo_url_${i}`])
    }
    return urls.length > 0 ? urls : (data.photo_url ? [data.photo_url] : [])
  })()

  if (allUrls.length > 0) {
    photoEl.src = allUrls[0]
    photoEl.alt = data.name || 'Perro'
    photoEl.classList.remove('hidden')
    placeholderEl.classList.add('hidden')
    if (allUrls.length > 1) {
      thumbsEl.classList.remove('hidden')
      thumbsEl.innerHTML = allUrls.map((url, i) => `
        <img class="dog-modal-thumb ${i === 0 ? 'active' : ''}"
             src="${url}" alt="Foto ${i + 1}"
             onclick="_selectDogPhoto(event,'${url}')" />
      `).join('')
    } else {
      thumbsEl.classList.add('hidden')
      thumbsEl.innerHTML = ''
    }
  } else {
    photoEl.classList.add('hidden')
    placeholderEl.classList.remove('hidden')
    thumbsEl.classList.add('hidden')
    thumbsEl.innerHTML = ''
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

  // Permanencia: solo si el perro fue desactivado (tiene deactivated_at)
  const deactivatedDate = data.deactivated_at?.toDate?.()
  const createdDate     = data.created_at?.toDate?.()
  if (deactivatedDate && createdDate) {
    const diffMs   = deactivatedDate - createdDate
    const diffDays = Math.round(diffMs / (1000 * 60 * 60 * 24))
    const deactivatedStr = deactivatedDate.toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
    const permanenciaLabel = data.returned_to_owner ? 'Permanencia en el refugio (devuelto)' : 'Permanencia hasta desactivación'
    fields.push({ icon: '⏱️', label: permanenciaLabel, value: `${diffDays} día${diffDays !== 1 ? 's' : ''} (cerrado: ${deactivatedStr})` })
  }

  document.getElementById('dog-modal-fields').innerHTML = fields.map(f => `
    <div class="dog-modal-field">
      <p class="dog-modal-field-label">${f.icon} ${escapeHtml(f.label)}</p>
      <p class="dog-modal-field-value">${escapeHtml(f.value)}</p>
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
  return !dog.status || dog.status === 'active' || dog.status === 'activo'
}

function _refreshModalStatusUI(isActive) {
  const tag = document.getElementById('dog-modal-status-tag')
  tag.textContent = isActive ? 'activo' : 'inactivo'
  tag.className   = isActive ? 'dog-modal-status-tag tag-active-modal' : 'dog-modal-status-tag tag-inactive-modal'

  const toggleBtn = document.getElementById('btn-toggle-status')
  toggleBtn.textContent = isActive ? 'Desactivar publicación' : 'Activar publicación'
  toggleBtn.className   = isActive ? 'btn-toggle-status btn-deactivate' : 'btn-toggle-status btn-activate'
}

function _getFilteredDogs() {
  const now = new Date()
  return allLostDogs.filter(d => {
    if (searchQuery) {
      const haystack = [d.name || '', d.color || '', d.size || '', d.sex || ''].join(' ').toLowerCase()
      if (!haystack.includes(searchQuery)) return false
    }
    const active = _isActive(d)
    if (statusFilter === 'active'   && !active) return false
    if (statusFilter === 'inactive' && active)  return false
    if (currentFilter === 'all') return true
    const date = d.created_at?.toDate?.()
    if (!date) return false
    if (currentFilter === 'daily')   return date.toDateString() === now.toDateString()
    if (currentFilter === 'weekly') { const w = new Date(now); w.setDate(w.getDate() - 7); return date >= w }
    if (currentFilter === 'monthly') return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth()
    if (currentFilter === 'range') {
      if (!rangeFrom && !rangeTo) return false
      const from = rangeFrom ? new Date(rangeFrom + 'T00:00:00') : null
      const to   = rangeTo   ? new Date(rangeTo   + 'T23:59:59') : null
      if (from && date < from) return false
      if (to   && date > to)   return false
      return true
    }
    return true
  })
}

function _filterSummary() {
  const periodMap = { all: 'Todos', daily: 'Hoy', weekly: 'Última semana', monthly: 'Este mes', range: 'Rango personalizado' }
  const statusMap = { all: 'Todos', active: 'Activos', inactive: 'Inactivos' }
  let period = periodMap[currentFilter] || 'Todos'
  if (currentFilter === 'range' && (rangeFrom || rangeTo)) period = `${rangeFrom || '—'} al ${rangeTo || '—'}`
  return { period, status: statusMap[statusFilter] || 'Todos', search: searchQuery || '—' }
}

function _generatePDF() {
  if (!window.jspdf) { alert('La libreria PDF no esta disponible. Verifica tu conexion.'); return }
  const { jsPDF } = window.jspdf
  const dogs = _getFilteredDogs()
  const { period, status } = _filterSummary()

  const DARK  = [26, 26, 26]
  const GOLD  = [212, 175, 55]
  const GRAY  = [110, 110, 110]
  const LGRAY = [245, 245, 245]

  const doc = new jsPDF({ orientation: 'landscape', unit: 'mm', format: 'a4' })
  const pageW = doc.internal.pageSize.width
  const pageH = doc.internal.pageSize.height
  const now = new Date()
  const dateStr = now.toLocaleDateString('es-CO', { day: '2-digit', month: 'long', year: 'numeric' })

  doc.setFillColor(...DARK)
  doc.rect(0, 0, pageW, 22, 'F')
  doc.setFillColor(...GOLD)
  doc.rect(0, 22, pageW, 1.5, 'F')

  doc.setFont('helvetica', 'bold')
  doc.setFontSize(15)
  doc.setTextColor(...GOLD)
  doc.text('Refugio WOOF', 14, 10)

  doc.setFont('helvetica', 'normal')
  doc.setFontSize(8.5)
  doc.setTextColor(180, 180, 180)
  doc.text('Panel Administrativo - Registro de Perros Extraviados', 14, 18)

  doc.setFontSize(8)
  doc.setTextColor(160, 160, 160)
  doc.text(`Generado: ${dateStr}`, pageW - 14, 10, { align: 'right' })
  doc.text(`${dogs.length} registro(s)`, pageW - 14, 17, { align: 'right' })

  let y = 32
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(9)
  doc.setTextColor(80, 80, 80)
  doc.text(`Periodo: ${period}   |   Estado: ${status}`, 14, y)
  y += 7

  const rows = dogs.map(d => [
    d.name || 'N/A',
    d.owner_name || 'N/A',
    d.owner_phone || 'N/A',
    d.color || d.color_principal || 'N/A',
    d.size || d.tamano || 'N/A',
    d.sex || d.sexo || 'N/A',
    (!d.status || d.status === 'active' || d.status === 'activo') ? 'Activo' : 'Inactivo',
    d.created_at?.toDate?.()
      ? d.created_at.toDate().toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
      : 'N/A',
  ])

  doc.autoTable({
    startY: y,
    head: [['Nombre', 'Dueno', 'Telefono', 'Color', 'Tamano', 'Sexo', 'Estado', 'Fecha']],
    body: rows.length ? rows : [['Sin registros', '', '', '', '', '', '', '']],
    styles: { fontSize: 9, cellPadding: 4 },
    headStyles: { fillColor: DARK, textColor: GOLD, fontStyle: 'bold' },
    alternateRowStyles: { fillColor: LGRAY },
    margin: { left: 14, right: 14 },
    theme: 'grid',
  })

  const pages = doc.internal.getNumberOfPages()
  for (let i = 1; i <= pages; i++) {
    doc.setPage(i)
    doc.setDrawColor(...GOLD)
    doc.setLineWidth(0.4)
    doc.line(14, pageH - 10, pageW - 14, pageH - 10)
    doc.setFont('helvetica', 'normal')
    doc.setFontSize(8)
    doc.setTextColor(...GRAY)
    doc.text('Refugio WOOF - Reporte generado automaticamente', 14, pageH - 5)
    doc.text(`Pagina ${i} de ${pages}`, pageW - 14, pageH - 5, { align: 'right' })
  }

  doc.save(`perros-extraviados-${now.toISOString().slice(0, 10)}.pdf`)
}

// ── Galería multi-foto ────────────────────────────────────────────────────────
window._selectDogPhoto = function (e, url) {
  document.getElementById('dog-modal-photo').src = url
  document.querySelectorAll('#dog-modal-thumbs .dog-modal-thumb')
    .forEach(t => t.classList.toggle('active', t.getAttribute('src') === url || t.onclick?.toString().includes(url)))
}
