import { loadDashboard }                       from './stats.js'
import { initLostDogs, loadLostDogs }           from './lost-dogs.js'
import { initFoundReports, loadFoundReports }   from './found-reports.js'
import { initReportFound }                      from './report-found.js'
import { initProfile, loadProfile }             from './profile.js'
import { initUsers, loadUsers }                 from './users.js'
import { initMyReports, loadMyReports }         from './my-reports.js'

// Rastrear qué secciones ya fueron inicializadas (para no duplicar listeners)
const _initialized = new Set()

export function navigateTo(target) {
  // Ocultar todas las secciones
  document.querySelectorAll('.section-content').forEach(s => s.classList.add('hidden'))

  // Mostrar la sección destino
  const section = document.getElementById(`section-${target}`)
  if (section) section.classList.remove('hidden')

  switch (target) {
    case 'dashboard':
      loadDashboard()
      break

    case 'list-lost':
      if (!_initialized.has('list-lost')) {
        try { initLostDogs() } catch (e) { console.error('[nav] initLostDogs:', e) }
        _initialized.add('list-lost')
      }
      loadLostDogs()
      break

    case 'list-found':
      if (!_initialized.has('list-found')) {
        try { initFoundReports() } catch (e) { console.error('[nav] initFoundReports:', e) }
        _initialized.add('list-found')
      }
      loadFoundReports()
      break

    case 'report-found':
      if (!_initialized.has('report-found')) {
        try { initReportFound() } catch (e) { console.error('[nav] initReportFound:', e) }
        _initialized.add('report-found')
      }
      break

    case 'profile':
      if (!_initialized.has('profile')) {
        try { initProfile() } catch (e) { console.error('[nav] initProfile:', e) }
        _initialized.add('profile')
      }
      loadProfile()
      break

    case 'users':
      if (!_initialized.has('users')) {
        try { initUsers() } catch (e) { console.error('[nav] initUsers:', e) }
        _initialized.add('users')
      }
      loadUsers()
      break

    case 'my-reports':
      if (!_initialized.has('my-reports')) {
        try { initMyReports() } catch (e) { console.error('[nav] initMyReports:', e) }
        _initialized.add('my-reports')
      }
      loadMyReports()
      break
  }
}

export function initNav() {
  const navItems = document.querySelectorAll('.nav-item')
  navItems.forEach(btn => {
    btn.addEventListener('click', () => {
      const target = btn.dataset.section
      navItems.forEach(b => b.classList.remove('active'))
      btn.classList.add('active')
      navigateTo(target)
    })
  })
}
