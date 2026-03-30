import { auth, db } from './firebase-config.js'
import {
  signInWithEmailAndPassword,
  sendPasswordResetEmail,
  onAuthStateChanged,
  signOut,
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js'
import { doc, getDoc } from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js'

// ── Elementos del DOM ────────────────────────────────────────────────────────
const emailInput    = document.getElementById('email')
const passwordInput = document.getElementById('password')
const emailWrapper  = document.getElementById('email-wrapper')
const passwordWrapper = document.getElementById('password-wrapper')
const emailError    = document.getElementById('email-error')
const loginBtn      = document.getElementById('btn-login')
const togglePwdBtn  = document.getElementById('toggle-password')
const forgotBtn     = document.getElementById('forgot-btn')

// Modal
const modal         = document.getElementById('reset-modal')
const resetEmailInput = document.getElementById('reset-email')
const resetEmailWrapper = document.getElementById('reset-email-wrapper')
const cancelBtn     = document.getElementById('btn-cancel')
const sendBtn       = document.getElementById('btn-send')

// Toast
const toast         = document.getElementById('toast')
let toastTimer      = null

// ── Utilidades ───────────────────────────────────────────────────────────────

function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
}

function showToast(message, isError = true) {
  clearTimeout(toastTimer)
  toast.textContent = message
  toast.className = 'toast ' + (isError ? 'error' : 'success')
  toastTimer = setTimeout(() => { toast.className = 'toast hidden' }, 5000)
}

function setLoading(active) {
  loginBtn.disabled = active
  loginBtn.innerHTML = active
    ? '<div class="spinner"></div>'
    : 'Iniciar Sesión'
}

function setSendLoading(active) {
  sendBtn.disabled = active
  sendBtn.innerHTML = active
    ? '<div class="spinner"></div>'
    : 'Enviar'
}

// ── Ver/ocultar contraseña ───────────────────────────────────────────────────
togglePwdBtn.addEventListener('click', () => {
  const isText = passwordInput.type === 'text'
  passwordInput.type = isText ? 'password' : 'text'
  togglePwdBtn.innerHTML = isText ? iconEyeOff() : iconEyeOn()
})

function iconEyeOn() {
  return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
    <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/>
    <circle cx="12" cy="12" r="3"/>
  </svg>`
}
function iconEyeOff() {
  return `<svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round">
    <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/>
    <path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/>
    <line x1="1" y1="1" x2="23" y2="23"/>
  </svg>`
}

// ── Validación de email en tiempo real ──────────────────────────────────────
let emailTouched = false

emailInput.addEventListener('blur', () => {
  emailTouched = true
  validateEmail()
})

emailInput.addEventListener('input', () => {
  if (emailTouched) validateEmail()
})

function validateEmail() {
  const val = emailInput.value.trim()
  if (val && !isValidEmail(val)) {
    emailWrapper.classList.add('error')
    emailError.textContent = 'Formato de correo no válido'
  } else {
    emailWrapper.classList.remove('error')
    emailError.textContent = ''
  }
}

// ── Login ────────────────────────────────────────────────────────────────────
loginBtn.addEventListener('click', handleLogin)

document.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') handleLogin()
})

async function handleLogin() {
  const email    = emailInput.value.trim()
  const password = passwordInput.value

  // Validaciones del lado del cliente (igual que la app móvil)
  if (!email) {
    showToast('⚠️ El correo no puede estar vacío')
    return
  }
  if (!isValidEmail(email)) {
    showToast('⚠️ Formato de correo no válido')
    return
  }
  if (!password) {
    showToast('⚠️ La contraseña no puede estar vacía')
    return
  }

  setLoading(true)
  try {
    const credential = await signInWithEmailAndPassword(auth, email, password)

    // Verificar que el usuario tiene rol ADMIN en Firestore
    const snap = await getDoc(doc(db, 'users', credential.user.uid))

    if (!snap.exists() || snap.data().role !== 'ADMIN') {
      await signOut(auth)
      showToast('🚫 Acceso denegado. Solo cuentas de administradores pueden ingresar.')
      return
    }

    // Login correcto → redirigir al dashboard
    window.location.href = 'dashboard.html'

  } catch (err) {
    const code = err.code
    let msg

    if (code === 'auth/invalid-credential' || code === 'auth/wrong-password' || code === 'auth/user-not-found') {
      msg = '🔴 Correo o contraseña incorrectos'
    } else if (code === 'auth/too-many-requests') {
      msg = '⚠️ Demasiados intentos fallidos. Intenta más tarde o restablece tu contraseña.'
    } else if (code === 'auth/network-request-failed') {
      msg = '❌ Sin conexión a internet. Verifica tu red.'
    } else if (code === 'auth/invalid-email') {
      msg = '⚠️ El correo no tiene un formato válido'
    } else {
      msg = '❌ Error al iniciar sesión. Intenta nuevamente.'
    }

    showToast(msg)
  } finally {
    setLoading(false)
  }
}

// ── Modal recuperar contraseña ───────────────────────────────────────────────
forgotBtn.addEventListener('click', () => {
  resetEmailInput.value = emailInput.value  // prellenar con el correo ingresado
  modal.classList.remove('hidden')
})

cancelBtn.addEventListener('click', () => {
  modal.classList.add('hidden')
})

// Cerrar modal al hacer clic fuera
modal.addEventListener('click', (e) => {
  if (e.target === modal) modal.classList.add('hidden')
})

sendBtn.addEventListener('click', handleResetPassword)

resetEmailInput.addEventListener('keydown', (e) => {
  if (e.key === 'Enter') handleResetPassword()
})

async function handleResetPassword() {
  const email = resetEmailInput.value.trim()

  if (!email) {
    showToast('⚠️ Ingresa tu correo electrónico')
    return
  }
  if (!isValidEmail(email)) {
    showToast('⚠️ El correo no tiene un formato válido')
    return
  }

  setSendLoading(true)
  try {
    await sendPasswordResetEmail(auth, email)
    modal.classList.add('hidden')
    showToast('📧 Se envió un correo para restablecer tu contraseña. Si no lo ves, revisa tu carpeta de spam.', false)
  } catch (err) {
    const msg = (err.code === 'auth/user-not-found' || err.code === 'auth/invalid-email')
      ? '🔴 No existe una cuenta con ese correo'
      : '❌ Error al enviar el correo. Intenta nuevamente.'
    showToast(msg)
  } finally {
    setSendLoading(false)
  }
}

// ── Si ya hay sesión activa de admin → ir directo al dashboard ───────────────
onAuthStateChanged(auth, async (user) => {
  if (!user) return
  try {
    const snap = await getDoc(doc(db, 'users', user.uid))
    if (snap.exists() && snap.data().role === 'ADMIN') {
      window.location.href = 'dashboard.html'
    } else {
      await signOut(auth)
    }
  } catch {
    await signOut(auth)
  }
})
