import { auth } from './firebase-config.js'
import { applyActionCode } from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js'

const params  = new URLSearchParams(window.location.search)
const mode    = params.get('mode')
const oobCode = params.get('oobCode')

function show(id) {
  ['view-loading', 'view-verified', 'view-invalid'].forEach(v => {
    document.getElementById(v).classList.toggle('hidden', v !== id)
  })
}

async function init() {
  if (mode === 'resetPassword') {
    // Redirigir a la página de cambio de contraseña con todos los parámetros
    window.location.replace('reset-password.html' + window.location.search)
    return
  }

  if (mode === 'verifyEmail') {
    document.getElementById('left-title').textContent = 'Verificar correo'
    document.getElementById('left-sub').textContent   = 'Activando tu cuenta...'

    if (!oobCode) { show('view-invalid'); return }

    try {
      await applyActionCode(auth, oobCode)
      show('view-verified')

      const isMobile = /Android|iPhone|iPad|iPod|Mobile/i.test(navigator.userAgent)
      if (isMobile) {
        document.getElementById('verified-msg').textContent =
          'Tu cuenta está activa. Vuelve a la aplicación WOOF para iniciar sesión.'
      } else {
        document.getElementById('verified-msg').textContent =
          'Tu cuenta está activa. Redirigiendo al inicio de sesión...'
        document.getElementById('verified-spinner').style.display = 'block'
        setTimeout(() => { window.location.href = 'index.html' }, 3000)
      }
    } catch {
      show('view-invalid')
    }
    return
  }

  if (mode === 'recoverEmail') {
    document.getElementById('left-title').textContent = 'Recuperar correo'
    if (!oobCode) { show('view-invalid'); return }
    try {
      await applyActionCode(auth, oobCode)
      show('view-verified')
      document.getElementById('verified-msg').textContent =
        'Tu correo anterior ha sido restaurado.'
      document.getElementById('verified-link').classList.remove('hidden')
    } catch {
      show('view-invalid')
    }
    return
  }

  // mode desconocido
  show('view-invalid')
}

init()
