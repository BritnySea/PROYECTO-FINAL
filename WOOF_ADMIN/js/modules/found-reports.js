import { db } from '../firebase-config.js'
import {
  collection, getDocs, doc, getDoc, updateDoc,
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js'
import { auth } from '../firebase-config.js'
import {
  onAuthStateChanged,
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js'
import { showToast } from './utils.js'

// ── Estado ────────────────────────────────────────────────────────────────────
let allFoundReports    = []
let foundDateFilter    = 'all'
let foundStatusFilter  = 'all'
let foundSearchQuery   = ''
let foundRangeFrom     = null
let foundRangeTo       = null
let currentModalReport = null

function _waitForUser() {
  if (auth.currentUser) return Promise.resolve(auth.currentUser)
  return new Promise(resolve => {
    const unsub = onAuthStateChanged(auth, user => { unsub(); resolve(user) })
  })
}

export function initFoundReports() {
  foundDateFilter   = 'all'
  foundStatusFilter = 'all'
  foundSearchQuery  = ''
  foundRangeFrom    = null
  foundRangeTo      = null
  currentModalReport = null

  // Buscador
  const searchInput = document.getElementById('found-search')
  searchInput.value = ''
  searchInput.addEventListener('input', () => {
    foundSearchQuery = searchInput.value.trim().toLowerCase()
    renderFoundReports()
  })

  document.getElementById('btn-refresh-found').addEventListener('click', loadFoundReports)

  // Botón exportar PDF
  document.getElementById('btn-pdf-found').addEventListener('click', _generatePDF)

  // Filtro por estado
  document.querySelectorAll('#found-status-filter-bar .filter-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('#found-status-filter-bar .filter-tab').forEach(t => t.classList.remove('active'))
      tab.classList.add('active')
      foundStatusFilter = tab.dataset.status
      renderFoundReports()
    })
  })

  // Filtros de fecha
  document.querySelectorAll('#found-date-filter-bar .filter-tab').forEach(tab => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('#found-date-filter-bar .filter-tab').forEach(t => t.classList.remove('active'))
      tab.classList.add('active')
      foundDateFilter = tab.dataset.filter
      document.getElementById('found-date-range-inputs').classList.toggle('hidden', foundDateFilter !== 'range')
      if (foundDateFilter !== 'range') renderFoundReports()
    })
  })

  const dateFrom = document.getElementById('found-date-from')
  const dateTo   = document.getElementById('found-date-to')
  dateFrom.addEventListener('change', () => {
    dateTo.min = dateFrom.value
    if (dateTo.value && dateTo.value < dateFrom.value) dateTo.value = ''
  })
  document.getElementById('found-btn-apply-range').addEventListener('click', () => {
    foundRangeFrom = dateFrom.value || null
    foundRangeTo   = dateTo.value   || null
    renderFoundReports()
  })

  // Cerrar modal
  document.getElementById('found-modal-close').addEventListener('click', closeFoundModal)
  document.getElementById('found-modal-overlay').addEventListener('click', e => {
    if (e.target === document.getElementById('found-modal-overlay')) closeFoundModal()
  })

  // Botón toggle estado → abrir modal de razón
  document.getElementById('btn-toggle-found-status').addEventListener('click', () => {
    if (!currentModalReport) return
    const isActive = _isActive(currentModalReport)

    document.getElementById('found-reason-title').textContent = isActive
      ? '¿Por qué desactivas esta publicación?'
      : '¿Por qué reactivas esta publicación?'

    document.getElementById('found-reason-desc').textContent = isActive
      ? 'La publicación dejará de ser visible para otros usuarios.'
      : 'La publicación volverá a ser visible para otros usuarios.'

    const confirmBtn = document.getElementById('found-reason-confirm')
    confirmBtn.textContent = isActive ? 'Sí, desactivar' : 'Sí, reactivar'
    confirmBtn.className   = isActive
      ? 'btn-confirm-yes btn-confirm-deactivate'
      : 'btn-confirm-yes btn-confirm-activate'
    confirmBtn.disabled = true

    const reasons = isActive
      ? ['Publicación duplicada', 'Información falsa o incorrecta', 'Foto inapropiada', 'El perro ya fue entregado a su dueño', 'Otro']
      : ['La publicación cumple las normas', 'Error al desactivarla', 'Otro']

    const listEl = document.getElementById('found-reason-list')
    listEl.innerHTML = reasons.map(r => `
      <label class="reason-option">
        <input type="radio" name="found-deactivate-reason" value="${r}" />
        <span>${r}</span>
      </label>
    `).join('')

    listEl.insertAdjacentHTML('beforeend', `
      <textarea id="found-reason-other-text"
        placeholder="Describe el motivo..."
        class="reason-other-textarea hidden"
        maxlength="300"
        rows="3"></textarea>
    `)

    const otherTextarea = document.getElementById('found-reason-other-text')

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

    document.getElementById('found-reason-overlay').classList.remove('hidden')
  })

  // Modal razón: cancelar
  document.getElementById('found-reason-cancel').addEventListener('click', () => {
    document.getElementById('found-reason-overlay').classList.add('hidden')
  })

  // Modal razón: confirmar
  document.getElementById('found-reason-confirm').addEventListener('click', async () => {
    if (!currentModalReport) return
    const report    = currentModalReport
    const isActive  = _isActive(report)
    const newStatus = isActive ? 'inactive' : 'active'
    const confirmBtn = document.getElementById('found-reason-confirm')

    const selectedRadio = document.querySelector('input[name="found-deactivate-reason"]:checked')
    const otherText     = document.getElementById('found-reason-other-text')?.value.trim() || ''
    const reason = selectedRadio?.value === 'Otro' ? otherText : (selectedRadio?.value || '')

    confirmBtn.disabled = true
    confirmBtn.innerHTML = '<div class="spinner white" style="width:16px;height:16px;border-width:2px"></div>'

    try {
      const updateData = { status: newStatus }
      if (reason) updateData.deactivation_reason = reason
      await updateDoc(doc(db, 'found_dog_reports', report.id), updateData)

      report.status = newStatus
      const cached = allFoundReports.find(r => r.id === report.id)
      if (cached) cached.status = newStatus

      document.getElementById('found-reason-overlay').classList.add('hidden')

      const msg = newStatus === 'active'
        ? '✅ Publicación activada correctamente'
        : '✅ Publicación desactivada correctamente'
      showToast(msg, false)

      _refreshModalStatusUI(newStatus === 'active')
      renderFoundReports()
    } catch (err) {
      const msg = err.code === 'permission-denied'
        ? '❌ Sin permisos en Firestore. Revisa las reglas.'
        : `❌ Error: ${err.code || err.message}`
      showToast(msg)
      console.error('[found toggle-status]', err)
    } finally {
      confirmBtn.disabled = false
      confirmBtn.textContent = isActive ? 'Sí, desactivar' : 'Sí, reactivar'
    }
  })
}

export async function loadFoundReports() {
  const container = document.getElementById('list-found-content')
  container.innerHTML = '<p class="empty-state">Cargando...</p>'
  try {
    await _waitForUser()
    const snap = await getDocs(collection(db, 'found_dog_reports'))
    if (snap.empty) {
      container.innerHTML = '<p class="empty-state">No hay reportes de perros encontrados.</p>'
      return
    }
    allFoundReports = []
    snap.forEach(d => allFoundReports.push({ id: d.id, ...d.data() }))
    allFoundReports.sort((a, b) => {
      const ta = a.created_at?.toDate?.() ?? new Date(0)
      const tb = b.created_at?.toDate?.() ?? new Date(0)
      return tb - ta
    })
    renderFoundReports()
  } catch (err) {
    container.innerHTML = '<p class="empty-state">Error al cargar los datos.</p>'
    console.error('[found-reports] error:', err.code, err.message, err)
  }
}

function renderFoundReports() {
  const container = document.getElementById('list-found-content')
  const now = new Date()

  const filtered = allFoundReports.filter(d => {
    // Filtro por búsqueda (color, tamaño, sexo, reportante)
    if (foundSearchQuery) {
      const haystack = [
        d.color || '',
        d.size  || '',
        d.sex   || '',
      ].join(' ').toLowerCase()
      if (!haystack.includes(foundSearchQuery)) return false
    }

    // Filtro por estado
    const active = _isActive(d)
    if (foundStatusFilter === 'active'   && !active) return false
    if (foundStatusFilter === 'inactive' && active)  return false

    // Filtro por fecha
    if (foundDateFilter === 'all') return true
    const date = d.created_at?.toDate?.()
    if (!date) return false
    if (foundDateFilter === 'daily')   return date.toDateString() === now.toDateString()
    if (foundDateFilter === 'weekly') {
      const weekAgo = new Date(now); weekAgo.setDate(weekAgo.getDate() - 7)
      return date >= weekAgo
    }
    if (foundDateFilter === 'monthly') {
      return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth()
    }
    if (foundDateFilter === 'range') {
      if (!foundRangeFrom && !foundRangeTo) return false
      const from = foundRangeFrom ? new Date(foundRangeFrom + 'T00:00:00') : null
      const to   = foundRangeTo   ? new Date(foundRangeTo   + 'T23:59:59') : null
      if (from && date < from) return false
      if (to   && date > to)   return false
      return true
    }
    return true
  })

  if (filtered.length === 0) {
    const msg = (foundDateFilter === 'range' && !foundRangeFrom && !foundRangeTo)
      ? 'Selecciona un rango de fechas y presiona Aplicar'
      : 'No hay registros para el período seleccionado.'
    container.innerHTML = `<p class="empty-state">${msg}</p>`
    return
  }

  const grid = document.createElement('div')
  grid.className = 'dog-grid'
  filtered.forEach(d => {
    const card = _buildFoundCard(d.id, d)
    card.classList.add('dog-card--clickable')
    card.addEventListener('click', () => openFoundModal(d))
    grid.appendChild(card)
  })
  container.innerHTML = ''
  container.appendChild(grid)
}

function _buildFoundCard(id, data) {
  const card = document.createElement('div')
  card.className = 'dog-card'

  const date = data.created_at?.toDate
    ? data.created_at.toDate().toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
    : '—'

  const matchCount  = Array.isArray(data.matches) ? data.matches.filter(m => (m.similarity_percent ?? 0) >= 50).length : 0
  const isActive    = _isActive(data)
  const statusClass = isActive ? 'tag-active' : 'tag-inactive'
  const statusLabel = isActive ? 'Activo' : 'Inactivo'

  const details = [
    data.size  && `📏 ${data.size}`,
    data.color && `🎨 ${data.color}`,
    data.sex   && `🐾 ${data.sex}`,
  ].filter(Boolean).join('&emsp;')

  card.innerHTML = `
    ${data.found_dog_photo_url
      ? `<img class="dog-card-photo" src="${data.found_dog_photo_url}" alt="Perro encontrado" loading="lazy" />`
      : `<div class="dog-card-photo-placeholder">🐾</div>`
    }
    <div class="dog-card-body">
      <div style="display:flex; align-items:center; justify-content:space-between; gap:8px; margin-bottom:6px">
        <p class="dog-card-name">REPORTE ENCONTRADO</p>
        <span class="dog-card-tag ${statusClass}">${statusLabel}</span>
      </div>
      <p class="dog-card-meta">
        ${details ? details + '<br>' : ''}
        👤 ${data.reporter_name || '—'}<br>
        📅 ${date}
      </p>
      <span class="dog-card-tag tag-match">${matchCount} coincidencia${matchCount !== 1 ? 's' : ''}</span>
    </div>
  `
  return card
}

// ══ MODAL ════════════════════════════════════════════════════════════════════

async function openFoundModal(data) {
  currentModalReport = data
  const overlay = document.getElementById('found-modal-overlay')

  const photoEl       = document.getElementById('found-modal-photo')
  const placeholderEl = document.getElementById('found-modal-placeholder')
  if (data.found_dog_photo_url) {
    photoEl.src = data.found_dog_photo_url
    photoEl.classList.remove('hidden')
    placeholderEl.classList.add('hidden')
  } else {
    photoEl.classList.add('hidden')
    placeholderEl.classList.remove('hidden')
  }

  _refreshModalStatusUI(_isActive(data))

  const date = data.created_at?.toDate?.()
    ? data.created_at.toDate().toLocaleDateString('es-CO', { day: '2-digit', month: 'long', year: 'numeric' })
    : '—'

  const fields = [
    data.reporter_name  && { icon: '👤', label: 'Reportado por',    value: data.reporter_name },
    data.reporter_phone && { icon: '📞', label: 'Teléfono',          value: data.reporter_phone },
    data.reporter_email && { icon: '✉️',  label: 'Correo',            value: data.reporter_email },
    data.size           && { icon: '📏', label: 'Tamaño',             value: data.size },
    data.color          && { icon: '🎨', label: 'Color',              value: data.color },
    data.sex            && { icon: '🐾', label: 'Sexo',               value: data.sex },
    data.description    && { icon: '📝', label: 'Señas particulares', value: data.description },
                           { icon: '📅', label: 'Fecha del reporte',  value: date },
  ].filter(Boolean)

  document.getElementById('found-modal-fields').innerHTML = fields.map(f => `
    <div class="dog-modal-field">
      <p class="dog-modal-field-label">${f.icon} ${f.label}</p>
      <p class="dog-modal-field-value">${f.value}</p>
    </div>
  `).join('')

  const matchesContainer = document.getElementById('found-modal-matches')
  const allMatches = Array.isArray(data.matches) ? data.matches : []
  const matches = allMatches.filter(m => (m.similarity_percent ?? 0) >= 50)

  if (matches.length === 0) {
    matchesContainer.innerHTML = '<p class="empty-state" style="font-size:13px">Sin coincidencias registradas</p>'
  } else {
    matchesContainer.innerHTML = '<p class="empty-state" style="font-size:13px">Cargando coincidencias...</p>'
    try {
      const matchCards = await Promise.all(matches.map(m => _buildMatchCard(m)))
      matchesContainer.innerHTML = ''
      matchCards.forEach(el => matchesContainer.appendChild(el))
    } catch (e) {
      matchesContainer.innerHTML = '<p class="empty-state" style="font-size:13px">Error al cargar coincidencias</p>'
      console.error(e)
    }
  }

  overlay.classList.remove('hidden')
  document.body.style.overflow = 'hidden'
}

async function _buildMatchCard(match) {
  const card = document.createElement('div')
  card.className = 'found-match-card'

  let dogData = null
  try {
    const snap = await getDoc(doc(db, 'lost_dogs', match.dog_id))
    if (snap.exists()) dogData = snap.data()
  } catch (_) {}

  const similarity = match.similarity_percent ?? 0
  const colorClass = similarity >= 80 ? 'sim-high' : similarity >= 50 ? 'sim-mid' : 'sim-low'

  card.innerHTML = `
    <div class="found-match-photos">
      <div class="found-match-photo-wrap">
        ${dogData?.photo_url
          ? `<img src="${dogData.photo_url}" alt="${dogData.name || 'Perro perdido'}" class="found-match-photo" />`
          : `<div class="found-match-photo-placeholder">🐾</div>`
        }
        <span class="found-match-photo-label">Perdido</span>
      </div>
    </div>
    <div class="found-match-info">
      <div class="found-match-sim ${colorClass}">${similarity.toFixed(1)}% similitud</div>
      ${dogData ? `
        <p class="found-match-name">${dogData.name || '—'}</p>
        <div class="found-match-fields">
          ${dogData.owner_name  ? `<span>👤 ${dogData.owner_name}</span>`  : ''}
          ${dogData.owner_phone ? `<span>📞 ${dogData.owner_phone}</span>` : ''}
          ${dogData.owner_email ? `<span>✉️ ${dogData.owner_email}</span>` : ''}
          ${dogData.breed       ? `<span>🐕 ${dogData.breed}</span>`       : ''}
          ${dogData.color       ? `<span>🎨 ${dogData.color}</span>`       : ''}
          ${dogData.size        ? `<span>📏 ${dogData.size}</span>`        : ''}
          ${dogData.sex         ? `<span>🐾 ${dogData.sex}</span>`         : ''}
          ${dogData.description ? `<span>📝 ${dogData.description}</span>` : ''}
        </div>
      ` : `<p style="font-size:12px; color:var(--muted)">Datos del perro no disponibles</p>`}
    </div>
  `
  return card
}

function closeFoundModal() {
  document.getElementById('found-modal-overlay').classList.add('hidden')
  document.body.style.overflow = ''
  currentModalReport = null
}

// ── Helpers ───────────────────────────────────────────────────────────────────
function _isActive(report) {
  return !report.status || report.status === 'active' || report.status === 'activo'
}

function _refreshModalStatusUI(isActive) {
  const tag = document.getElementById('found-modal-status-tag')
  tag.textContent = isActive ? 'activo' : 'inactivo'
  tag.className   = isActive ? 'dog-modal-status-tag tag-active-modal' : 'dog-modal-status-tag tag-inactive-modal'

  const btn = document.getElementById('btn-toggle-found-status')
  btn.textContent = isActive ? 'Desactivar publicación' : 'Activar publicación'
  btn.className   = isActive ? 'btn-toggle-status btn-deactivate' : 'btn-toggle-status btn-activate'
}

function _getFilteredReports() {
  const now = new Date()
  return allFoundReports.filter(d => {
    if (foundSearchQuery) {
      const haystack = [d.color || '', d.size || '', d.sex || ''].join(' ').toLowerCase()
      if (!haystack.includes(foundSearchQuery)) return false
    }
    const active = _isActive(d)
    if (foundStatusFilter === 'active'   && !active) return false
    if (foundStatusFilter === 'inactive' && active)  return false
    if (foundDateFilter === 'all') return true
    const date = d.created_at?.toDate?.()
    if (!date) return false
    if (foundDateFilter === 'daily')   return date.toDateString() === now.toDateString()
    if (foundDateFilter === 'weekly') { const w = new Date(now); w.setDate(w.getDate() - 7); return date >= w }
    if (foundDateFilter === 'monthly') return date.getFullYear() === now.getFullYear() && date.getMonth() === now.getMonth()
    if (foundDateFilter === 'range') {
      if (!foundRangeFrom && !foundRangeTo) return false
      const from = foundRangeFrom ? new Date(foundRangeFrom + 'T00:00:00') : null
      const to   = foundRangeTo   ? new Date(foundRangeTo   + 'T23:59:59') : null
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
  let period = periodMap[foundDateFilter] || 'Todos'
  if (foundDateFilter === 'range' && (foundRangeFrom || foundRangeTo)) period = `${foundRangeFrom || '—'} al ${foundRangeTo || '—'}`
  return { period, status: statusMap[foundStatusFilter] || 'Todos', search: foundSearchQuery || '—' }
}

function _generatePDF() {
  if (!window.jspdf) { alert('La libreria PDF no esta disponible. Verifica tu conexion.'); return }
  const { jsPDF } = window.jspdf
  const reports = _getFilteredReports()
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
  doc.text('Panel Administrativo - Registro de Perros Encontrados', 14, 18)

  doc.setFontSize(8)
  doc.setTextColor(160, 160, 160)
  doc.text(`Generado: ${dateStr}`, pageW - 14, 10, { align: 'right' })
  doc.text(`${reports.length} registro(s)`, pageW - 14, 17, { align: 'right' })

  let y = 32
  doc.setFont('helvetica', 'normal')
  doc.setFontSize(9)
  doc.setTextColor(80, 80, 80)
  doc.text(`Periodo: ${period}   |   Estado: ${status}`, 14, y)
  y += 7

  const rows = reports.map(d => {
    const matchCount = Array.isArray(d.matches) ? d.matches.filter(m => (m.similarity_percent ?? 0) >= 50).length : 0
    return [
      d.color || 'N/A',
      d.size  || 'N/A',
      d.sex   || 'N/A',
      d.reporter_name  || 'N/A',
      d.reporter_phone || 'N/A',
      String(matchCount),
      (!d.status || d.status === 'active' || d.status === 'activo') ? 'Activo' : 'Inactivo',
      d.created_at?.toDate?.()
        ? d.created_at.toDate().toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
        : 'N/A',
    ]
  })

  doc.autoTable({
    startY: y,
    head: [['Color', 'Tamano', 'Sexo', 'Reportado por', 'Telefono', 'Coinc.>=50%', 'Estado', 'Fecha']],
    body: rows.length ? rows : [['Sin registros', '', '', '', '', '', '', '']],
    styles: { fontSize: 9, cellPadding: 4 },
    headStyles: { fillColor: DARK, textColor: GOLD, fontStyle: 'bold' },
    alternateRowStyles: { fillColor: LGRAY },
    columnStyles: { 5: { halign: 'center' } },
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

  doc.save(`perros-encontrados-${now.toISOString().slice(0, 10)}.pdf`)
}
