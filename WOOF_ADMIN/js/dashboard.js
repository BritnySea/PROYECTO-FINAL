import { auth, db } from './firebase-config.js'
import { onAuthStateChanged, signOut } from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js'
import { doc, getDoc }                from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js'

const logoutBtn   = document.getElementById('btn-logout')
const adminName   = document.getElementById('admin-name')

// ── Verificar sesión al cargar ────────────────────────────────────────────────
onAuthStateChanged(auth, async (user) => {
  if (!user) {
    // No hay sesión → volver al login
    window.location.href = 'index.html'
    return
  }

  try {
    const snap = await getDoc(doc(db, 'users', user.uid))

    if (!snap.exists() || snap.data().role !== 'ADMIN') {
      // Sesión válida pero no es admin → cerrar y redirigir
      await signOut(auth)
      window.location.href = 'index.html'
      return
    }

    // Mostrar nombre del admin en el header
    if (adminName) adminName.textContent = snap.data().name || user.email

  } catch {
    await signOut(auth)
    window.location.href = 'index.html'
  }
})

// ── Cerrar sesión ─────────────────────────────────────────────────────────────
logoutBtn.addEventListener('click', async () => {
  await signOut(auth)
  window.location.href = 'index.html'
})
