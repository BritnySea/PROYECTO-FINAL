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
  } catch {
    await signOut(auth)
    window.location.href = 'index.html'
  }
})

document.getElementById('btn-logout').addEventListener('click', async () => {
  await signOut(auth)
  window.location.href = 'index.html'
})

// ── Inicializar navegación y cargar sección inicial ───────────────────────────
initNav()
navigateTo('dashboard')
