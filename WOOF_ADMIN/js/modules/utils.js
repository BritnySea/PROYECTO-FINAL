// ── Escape HTML ───────────────────────────────────────────────────────────────
export function escapeHtml(str) {
  if (str == null) return ''
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

// ── Toast ─────────────────────────────────────────────────────────────────────
let toastTimer = null

export function showToast(msg, isError = true) {
  const toast = document.getElementById('toast')
  if (!toast) return
  clearTimeout(toastTimer)
  toast.textContent = msg
  toast.className   = 'toast ' + (isError ? 'error' : 'success')
  toastTimer = setTimeout(() => { toast.className = 'toast hidden' }, 3500)
  toast.onclick = () => { clearTimeout(toastTimer); toast.className = 'toast hidden' }
}

// ── Dog card (compartida: dashboard recientes + lista extraviados) ─────────────
export function buildDogCard(id, data) {
  const card = document.createElement('div')
  card.className = 'dog-card'

  const date = data.created_at?.toDate
    ? data.created_at.toDate().toLocaleDateString('es-CO', { day: '2-digit', month: 'short', year: 'numeric' })
    : '—'

  const isActive = data.status === 'active' || data.status === 'activo'
  const statusLabel = isActive ? 'Activo' : 'Inactivo'
  const statusClass = isActive ? 'tag-active' : 'tag-inactive'

  card.innerHTML = `
    ${(data.photo_url_1 || data.photo_url)
      ? `<img class="dog-card-photo" src="${data.photo_url_1 || data.photo_url}" alt="${escapeHtml(data.name || '')}" loading="lazy" />`
      : `<div class="dog-card-photo-placeholder">🐾</div>`
    }
    <div class="dog-card-body">
      <p class="dog-card-name">${escapeHtml(data.name || 'Sin nombre').toUpperCase()}</p>
      <p class="dog-card-meta">
        👤 ${escapeHtml(data.owner_name)  || '—'}<br>
        📞 ${escapeHtml(data.owner_phone) || '—'}<br>
        📅 ${date}
      </p>
      <span class="dog-card-tag ${statusClass}">${statusLabel}</span>
    </div>
  `
  return card
}
