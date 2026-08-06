import { db } from '../firebase-config.js'
import {
  collection, getDocs, doc, updateDoc, query, where,
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js'
import { showToast, escapeHtml } from './utils.js'

let allUsers  = []
let searchQuery = ''

// ═════════════════════════════════════════════════════════════════════════════
//  INIT
// ═════════════════════════════════════════════════════════════════════════════
export function initUsers() {
  searchQuery = ''
  const searchInput = document.getElementById('users-search')
  searchInput.value = ''
  searchInput.addEventListener('input', () => {
    searchQuery = searchInput.value.trim().toLowerCase()
    renderUsers()
  })

  document.getElementById('btn-refresh-users').addEventListener('click', loadUsers)

  // Cerrar modal usuario
  document.getElementById('user-modal-close').addEventListener('click', closeUserModal)
  document.getElementById('user-modal-overlay').addEventListener('click', e => {
    if (e.target === document.getElementById('user-modal-overlay')) closeUserModal()
  })

  // Modal de confirmación bloqueo: cancelar
  document.getElementById('user-block-cancel').addEventListener('click', () => {
    document.getElementById('user-block-confirm-overlay').classList.add('hidden')
  })
}

// ═════════════════════════════════════════════════════════════════════════════
//  CARGA
// ═════════════════════════════════════════════════════════════════════════════
export async function loadUsers() {
  const container = document.getElementById('users-list-content')
  container.innerHTML = '<p class="empty-state">Cargando...</p>'
  try {
    const snap = await getDocs(query(collection(db, 'users'), where('role', '==', 'USER')))
    allUsers = []
    snap.forEach(d => allUsers.push({ uid: d.id, ...d.data() }))
    allUsers.sort((a, b) => (a.name || '').localeCompare(b.name || '', 'es'))
    renderUsers()
  } catch (err) {
    container.innerHTML = '<p class="empty-state">Error al cargar los usuarios.</p>'
    console.error('[users] error:', err.code, err.message)
  }
}

function renderUsers() {
  const container = document.getElementById('users-list-content')
  const filtered = allUsers.filter(u =>
    !searchQuery || (u.name || '').toLowerCase().includes(searchQuery) || (u.email || '').toLowerCase().includes(searchQuery)
  )

  if (filtered.length === 0) {
    container.innerHTML = `<p class="empty-state">${searchQuery ? 'Sin resultados para tu búsqueda.' : 'No hay usuarios registrados.'}</p>`
    return
  }

  container.innerHTML = ''
  filtered.forEach(u => {
    const row = _buildUserRow(u)
    row.addEventListener('click', () => openUserModal(u))
    container.appendChild(row)
  })
}

function _buildUserRow(u) {
  const row = document.createElement('div')
  row.className = 'user-row'

  const initials = (u.name || u.email || '?').slice(0, 2).toUpperCase()
  const blocked  = u.isBlocked === true
  const statusClass = blocked ? 'user-badge user-badge--blocked' : 'user-badge user-badge--active'
  const statusLabel = blocked ? 'Bloqueado' : 'Activo'

  row.innerHTML = `
    <div class="user-row-avatar">${initials}</div>
    <div class="user-row-info">
      <p class="user-row-name">${escapeHtml(u.name) || '—'}</p>
      <p class="user-row-email">${escapeHtml(u.email) || '—'}</p>
    </div>
    <span class="${statusClass}">${statusLabel}</span>
    <svg class="user-row-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" stroke-width="2" stroke-linecap="round">
      <polyline points="9 18 15 12 9 6"/>
    </svg>
  `
  return row
}

// ═════════════════════════════════════════════════════════════════════════════
//  MODAL USUARIO
// ═════════════════════════════════════════════════════════════════════════════
let _currentUser = null

async function openUserModal(u) {
  _currentUser = u

  // Info básica
  const initials = (u.name || u.email || '?').slice(0, 2).toUpperCase()
  document.getElementById('user-modal-avatar').textContent   = initials
  document.getElementById('user-modal-name').textContent     = u.name  || '—'
  document.getElementById('user-modal-email').textContent    = u.email || '—'
  document.getElementById('user-modal-phone').textContent    = u.phone ? `📞 ${u.phone}` : ''

  _refreshBlockBtn(u.isBlocked === true)

  // Limpiar reportes
  document.getElementById('user-modal-lost').innerHTML    = '<p class="empty-state" style="font-size:13px">Cargando...</p>'
  document.getElementById('user-modal-found').innerHTML   = '<p class="empty-state" style="font-size:13px">Cargando...</p>'

  document.getElementById('user-modal-overlay').classList.remove('hidden')
  document.body.style.overflow = 'hidden'

  // Cargar reportes en paralelo
  const [lostSnap, foundSnap] = await Promise.all([
    getDocs(query(collection(db, 'lost_dogs'),        where('registered_by_uid', '==', u.uid))),
    getDocs(query(collection(db, 'found_dog_reports'), where('found_by_uid',      '==', u.uid))),
  ])

  // Perros extraviados
  const lostContainer = document.getElementById('user-modal-lost')
  if (lostSnap.empty) {
    lostContainer.innerHTML = '<p class="empty-state" style="font-size:13px">Sin reportes de perros extraviados.</p>'
  } else {
    lostContainer.innerHTML = ''
    const dateFormat = d => d?.toDate?.().toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' }) ?? '—'
    lostSnap.forEach(d => {
      const data = d.data()
      lostContainer.appendChild(_buildMiniCard({
        photoUrl : data.photo_url_1 || data.photo_url,
        title    : data.name || 'Sin nombre',
        lines    : [
          data.breed       ? `🐕 ${data.breed}` : null,
          data.color       ? `🎨 ${data.color}` : null,
          data.size        ? `📏 ${data.size}`  : null,
          `📅 ${dateFormat(data.created_at)}`,
        ],
        status   : data.status,
        label    : 'Extraviado',
        labelClass: 'tag-lost',
      }))
    })
  }

  // Perros encontrados
  const foundContainer = document.getElementById('user-modal-found')
  if (foundSnap.empty) {
    foundContainer.innerHTML = '<p class="empty-state" style="font-size:13px">Sin reportes de perros encontrados.</p>'
  } else {
    foundContainer.innerHTML = ''
    const dateFormat = d => d?.toDate?.().toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' }) ?? '—'
    foundSnap.forEach(d => {
      const data = d.data()
      const matchCount = Array.isArray(data.matches) ? data.matches.length : 0
      foundContainer.appendChild(_buildMiniCard({
        photoUrl  : data.found_dog_photo_url_1 || data.found_dog_photo_url,
        title     : 'Perro encontrado',
        lines     : [
          data.color       ? `🎨 ${data.color}`  : null,
          data.size        ? `📏 ${data.size}`   : null,
          data.sex         ? `🐾 ${data.sex}`    : null,
          `🔍 ${matchCount} coincidencia${matchCount !== 1 ? 's' : ''}`,
          `📅 ${dateFormat(data.created_at)}`,
        ],
        status    : data.status,
        label     : 'Encontrado',
        labelClass: 'tag-found',
      }))
    })
  }
}

function _buildMiniCard({ photoUrl, title, lines, status, label, labelClass }) {
  const el = document.createElement('div')
  el.className = 'user-report-card'
  const active  = status === 'active' || status === 'activo'
  const stClass = active ? 'tag-active' : 'tag-inactive'
  const stLabel = active ? 'activo' : 'inactivo'

  el.innerHTML = `
    ${photoUrl
      ? `<img class="user-report-photo" src="${photoUrl}" alt="${title}" />`
      : `<div class="user-report-photo user-report-photo--empty">🐾</div>`
    }
    <div class="user-report-info">
      <div style="display:flex; gap:6px; align-items:center; flex-wrap:wrap; margin-bottom:4px">
        <span class="dog-card-tag ${labelClass}" style="font-size:10px">${label}</span>
        <span class="dog-card-tag ${stClass}"    style="font-size:10px">${stLabel}</span>
      </div>
      <p class="user-report-title">${title}</p>
      <div class="user-report-lines">
        ${lines.filter(Boolean).map(l => `<span>${l}</span>`).join('')}
      </div>
    </div>
  `
  return el
}

function closeUserModal() {
  document.getElementById('user-modal-overlay').classList.add('hidden')
  document.body.style.overflow = ''
  _currentUser = null
}

// ── Bloquear / Desbloquear ────────────────────────────────────────────────────
function _refreshBlockBtn(isBlocked) {
  const btn = document.getElementById('user-modal-block-btn')
  btn.textContent = isBlocked ? 'Desbloquear cuenta' : 'Bloquear cuenta'
  btn.className   = isBlocked ? 'btn-user-block btn-user-unblock' : 'btn-user-block btn-user-block--danger'

  btn.onclick = () => {
    if (!_currentUser) return
    _openBlockConfirm(isBlocked)
  }
}

function _openBlockConfirm(isBlocked) {
  const overlay    = document.getElementById('user-block-confirm-overlay')
  const titleEl    = document.getElementById('user-block-confirm-title')
  const descEl     = document.getElementById('user-block-confirm-desc')
  const confirmBtn = document.getElementById('user-block-confirm')

  if (isBlocked) {
    titleEl.textContent  = '¿Desbloquear esta cuenta?'
    descEl.textContent   = 'El usuario podrá volver a iniciar sesión y usar la aplicación con normalidad.'
    confirmBtn.textContent = 'Sí, desbloquear'
    confirmBtn.className   = 'btn-confirm-yes btn-confirm-activate'
  } else {
    titleEl.textContent  = '¿Bloquear esta cuenta?'
    descEl.textContent   = 'El usuario no podrá iniciar sesión mientras su cuenta esté bloqueada.'
    confirmBtn.textContent = 'Sí, bloquear'
    confirmBtn.className   = 'btn-confirm-yes btn-confirm-deactivate'
  }

  // Reemplazar el listener para que cada apertura tenga la acción correcta
  const newBtn = confirmBtn.cloneNode(true)
  confirmBtn.parentNode.replaceChild(newBtn, confirmBtn)

  newBtn.addEventListener('click', async () => {
    if (!_currentUser) return
    const newBlocked = !isBlocked
    newBtn.disabled = true
    newBtn.innerHTML = '<div class="spinner white" style="width:16px;height:16px;border-width:2px"></div>'

    try {
      await updateDoc(doc(db, 'users', _currentUser.uid), { isBlocked: newBlocked })
      _currentUser.isBlocked = newBlocked

      const idx = allUsers.findIndex(u => u.uid === _currentUser.uid)
      if (idx !== -1) allUsers[idx].isBlocked = newBlocked

      overlay.classList.add('hidden')
      _refreshBlockBtn(newBlocked)
      renderUsers()
      showToast(newBlocked ? '🔒 Cuenta bloqueada' : '🔓 Cuenta desbloqueada', false)
    } catch (err) {
      showToast('❌ Error al actualizar. Revisa los permisos de Firestore.')
      console.error('[users] block error:', err?.code, err?.message)
    } finally {
      newBtn.disabled = false
    }
  })

  overlay.classList.remove('hidden')
}
