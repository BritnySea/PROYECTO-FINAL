import { db } from '../firebase-config.js'
import {
  collection, getDocs,
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js'
import { buildDogCard } from './utils.js'

export async function loadDashboard() {
  try {
    const [lostSnap, foundSnap] = await Promise.all([
      getDocs(collection(db, 'lost_dogs')),
      getDocs(collection(db, 'found_reports')),
    ])
    document.getElementById('stat-lost').textContent  = lostSnap.size
    document.getElementById('stat-found').textContent = foundSnap.size

    const container = document.getElementById('dashboard-recent')
    if (lostSnap.empty) {
      container.innerHTML = '<p class="empty-state">No hay perros registrados aún.</p>'
      return
    }
    const docs = []
    lostSnap.forEach(d => docs.push({ id: d.id, ...d.data() }))
    docs.sort((a, b) => {
      const ta = a.created_at?.toDate?.() ?? new Date(0)
      const tb = b.created_at?.toDate?.() ?? new Date(0)
      return tb - ta
    })
    container.innerHTML = ''
    docs.slice(0, 6).forEach(d => container.appendChild(buildDogCard(d.id, d)))
  } catch (err) {
    console.error('Dashboard error:', err)
  }
}
