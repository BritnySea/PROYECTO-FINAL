import { auth, db } from './firebase-config.js'
import {
  onAuthStateChanged, signOut,
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js'
import {
  doc, getDoc,
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js'

import { initNav, navigateTo } from './modules/nav.js'
import { storeAdminState }     from './modules/admin-state.js'

// ── Auth guard ────────────────────────────────────────────────────────────────
let _appReady = false

onAuthStateChanged(auth, async (user) => {
  if (!user) { window.location.href = 'index.html'; return }
  try {
    const snap = await getDoc(doc(db, 'users', user.uid))
    if (!snap.exists() || snap.data().role !== 'ADMIN') {
      await signOut(auth)
      window.location.href = 'index.html'
      return
    }
    const data = snap.data()
    storeAdminState(user, data)
    document.getElementById('admin-name').textContent = data.name || user.email

    // Cargar sección inicial solo la primera vez que auth confirma al admin
    if (!_appReady) {
      _appReady = true
      navigateTo('dashboard')
      _startSessionManagement()
    }
  } catch {
    await signOut(auth)
    window.location.href = 'index.html'
  }
})

document.getElementById('btn-logout').addEventListener('click', async () => {
  localStorage.removeItem('woofActiveTab')
  sessionStorage.removeItem('woofLogoutReason')
  await signOut(auth)
  window.location.replace('index.html')
})

// ── Gestión de sesión ────────────────────────────────────────────────────────

const INACTIVITY_MS  = 60 * 1000         // 1 minuto (prueba)
const WARNING_MS     = 20 * 1000         // aviso 20 segundos antes

let _inactivityTimer   = null
let _warningTimer      = null
let _countdownInterval = null

function _ensureInactivityModal() {
  if (document.getElementById('inactivity-overlay')) return
  const el = document.createElement('div')
  el.id = 'inactivity-overlay'
  el.style.cssText = [
    'position:fixed', 'inset:0', 'background:rgba(0,0,0,0.78)', 'z-index:9999',
    'display:none', 'align-items:center', 'justify-content:center',
  ].join(';')
  el.innerHTML = `
    <div style="background:#131313;border:1px solid rgba(255,255,255,0.08);border-radius:18px;
      padding:36px 28px;max-width:360px;width:90%;text-align:center;
      box-shadow:0 0 50px rgba(212,175,55,0.14);">
      <div style="font-size:38px;margin-bottom:14px">⏱️</div>
      <p style="font-size:17px;font-weight:600;color:rgba(255,255,255,0.92);margin-bottom:8px">¿Sigues ahí?</p>
      <p style="font-size:13px;color:rgba(255,255,255,0.45);margin-bottom:18px;line-height:1.5">
        La sesión se cerrará por inactividad en
      </p>
      <p id="inactivity-countdown" style="font-size:52px;font-weight:800;color:#D4AF37;margin-bottom:8px;letter-spacing:-2px">60</p>
      <p style="font-size:12px;color:rgba(255,255,255,0.3)">Mueve el puntero para continuar</p>
    </div>`
  document.body.appendChild(el)
}

function _showWarning() {
  _ensureInactivityModal()
  const overlay = document.getElementById('inactivity-overlay')
  overlay.style.display = 'flex'

  let secs = Math.round(WARNING_MS / 1000)
  const countEl = document.getElementById('inactivity-countdown')
  if (countEl) countEl.textContent = secs

  clearInterval(_countdownInterval)
  _countdownInterval = setInterval(() => {
    secs--
    const el = document.getElementById('inactivity-countdown')
    if (el) el.textContent = secs
    if (secs <= 0) clearInterval(_countdownInterval)
  }, 1000)
}

function _hideWarning() {
  clearInterval(_countdownInterval)
  const overlay = document.getElementById('inactivity-overlay')
  if (overlay) overlay.style.display = 'none'
}

function _autoSignOut() {
  _hideWarning()
  sessionStorage.setItem('woofLogoutReason', 'inactividad')
  signOut(auth)
}

function _resetInactivityTimer() {
  _hideWarning()
  clearTimeout(_warningTimer)
  clearTimeout(_inactivityTimer)
  _warningTimer    = setTimeout(_showWarning,   INACTIVITY_MS - WARNING_MS)
  _inactivityTimer = setTimeout(_autoSignOut,   INACTIVITY_MS)
}

function _startSessionManagement() {
  // Siempre generar ID nuevo — duplicar pestaña copia sessionStorage,
  // así ambas tabs tendrían el mismo ID y no se detectaría el conflicto.
  const myId = crypto.randomUUID()
  const myTs = Date.now()
  sessionStorage.setItem('woofTabId', myId)

  const channel = new BroadcastChannel('woof_admin')

  // Anunciar que esta pestaña existe con su timestamp
  channel.postMessage({ type: 'claim', tabId: myId, ts: myTs })

  channel.onmessage = (e) => {
    if (e.data?.type !== 'claim' || e.data.tabId === myId) return
    // El tab más reciente gana; en empate el UUID mayor gana
    const otherWins = e.data.ts > myTs || (e.data.ts === myTs && e.data.tabId > myId)
    if (otherWins) {
      channel.close()
      sessionStorage.setItem('woofLogoutReason', 'otro_tab')
      signOut(auth)
    }
  }

  // Timer de inactividad
  const events = ['mousemove', 'mousedown', 'keydown', 'scroll', 'touchstart', 'click']
  events.forEach(ev => document.addEventListener(ev, _resetInactivityTimer, { passive: true }))
  _resetInactivityTimer()
}

// Detectar restauración desde bfcache (botón atrás/adelante del navegador)
window.addEventListener('pageshow', (e) => {
  if (e.persisted && !auth.currentUser) {
    window.location.replace('index.html')
  }
})

// ── Inicializar navegación (solo listeners, sin cargar datos aún) ─────────────
initNav()
