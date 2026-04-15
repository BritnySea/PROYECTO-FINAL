import { auth } from './firebase-config.js'
import {
  verifyPasswordResetCode,
  confirmPasswordReset,
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js'

// ── Leer oobCode de la URL ───────────────────────────────────────────────────
const params  = new URLSearchParams(window.location.search)
const oobCode = params.get('oobCode')

// ── Vistas ───────────────────────────────────────────────────────────────────
const viewInvalid  = document.getElementById('view-invalid')
const viewForm     = document.getElementById('view-form')
const viewSuccess  = document.getElementById('view-success')

function showView(name) {
  viewInvalid.classList.add('hidden')
  viewForm.classList.add('hidden')
  viewSuccess.classList.add('hidden')
  document.getElementById('view-' + name).classList.remove('hidden')
}

// ── Elementos del formulario ─────────────────────────────────────────────────
const pwInput       = document.getElementById('pw')
const confirmInput  = document.getElementById('confirm')
const pwWrapper     = document.getElementById('pw-wrapper')
const confirmWrapper= document.getElementById('confirm-wrapper')
const pwError       = document.getElementById('pw-error')
const confirmError  = document.getElementById('confirm-error')
const resetBtn      = document.getElementById('btn-reset')
const toast         = document.getElementById('toast')
let toastTimer      = null

// ── Toggle visibilidad contraseña ────────────────────────────────────────────
function bindToggle(btnId, inputEl) {
  const btn = document.getElementById(btnId)
  btn.addEventListener('click', () => {
    const isText = inputEl.type === 'text'
    inputEl.type = isText ? 'password' : 'text'
    btn.innerHTML = isText ? iconEyeOff() : iconEyeOn()
  })
}
bindToggle('toggle-pw',      pwInput)
bindToggle('toggle-confirm', confirmInput)

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

// ── Toast ────────────────────────────────────────────────────────────────────
function showToast(message, isError = true) {
  clearTimeout(toastTimer)
  toast.textContent = message
  toast.className = 'toast ' + (isError ? 'error' : 'success')
  toastTimer = setTimeout(() => { toast.className = 'toast hidden' }, 3500)
  toast.onclick = () => { clearTimeout(toastTimer); toast.className = 'toast hidden' }
}

// ── Fuerza de contraseña ─────────────────────────────────────────────────────
const STRENGTH_COLORS = ['', '#EF4444', '#F97316', '#EAB308', '#22C55E']
const STRENGTH_LABELS = ['', 'Muy débil', 'Débil', 'Buena', 'Fuerte']

function calcStrength(pw) {
  let s = 0
  if (pw.length >= 8) s++
  if (/[A-Z]/.test(pw) && /[a-z]/.test(pw)) s++
  if (/\d/.test(pw)) s++
  if (/[^a-zA-Z0-9]/.test(pw) && pw.length > 0) s++
  return s
}

function updateStrengthBar(pw) {
  const strength = calcStrength(pw)
  const color    = STRENGTH_COLORS[strength] || 'transparent'
  const label    = STRENGTH_LABELS[strength] || ''

  for (let i = 1; i <= 4; i++) {
    const seg = document.getElementById('seg' + i)
    seg.style.background = i <= strength ? color : 'rgba(255,255,255,0.08)'
  }
  document.getElementById('strength-label').textContent = label
  document.getElementById('strength-label').style.color = color
  document.getElementById('strength-row').style.display = pw.length > 0 ? 'flex' : 'none'
}

// ── Requisitos ───────────────────────────────────────────────────────────────
function updateRequirements(pw) {
  const checks = {
    'req-length': pw.length >= 8,
    'req-case':   /[A-Z]/.test(pw) && /[a-z]/.test(pw),
    'req-number': /\d/.test(pw),
    'req-symbol': /[^a-zA-Z0-9]/.test(pw) && pw.length > 0,
  }
  for (const [id, met] of Object.entries(checks)) {
    const li   = document.getElementById(id)
    const icon = li.querySelector('.req-icon')
    icon.textContent = met ? '✓' : '○'
    li.classList.toggle('met', met)
  }
}

// ── Validaciones en tiempo real ──────────────────────────────────────────────
let pwTouched      = false
let confirmTouched = false

pwInput.addEventListener('input', () => {
  updateStrengthBar(pwInput.value)
  updateRequirements(pwInput.value)
  if (pwTouched)      validatePw()
  if (confirmTouched) validateConfirm()
  updateBtnState()
})

pwInput.addEventListener('blur', () => {
  pwTouched = true
  validatePw()
})

confirmInput.addEventListener('input', () => {
  if (confirmTouched) validateConfirm()
  updateBtnState()
})

confirmInput.addEventListener('blur', () => {
  confirmTouched = true
  validateConfirm()
})

function validatePw() {
  const pw = pwInput.value
  if (pw && pw.length < 8) {
    pwWrapper.classList.add('error')
    pwError.textContent = 'Mínimo 8 caracteres requeridos'
  } else {
    pwWrapper.classList.remove('error')
    pwError.textContent = ''
  }
}

function validateConfirm() {
  const pw      = pwInput.value
  const confirm = confirmInput.value
  if (confirm && confirm !== pw) {
    confirmWrapper.classList.add('error')
    confirmError.textContent = 'Las contraseñas no coinciden'
  } else {
    confirmWrapper.classList.remove('error')
    confirmError.textContent = ''
  }
}

function isFormValid() {
  const pw = pwInput.value
  return pw.length >= 8 && pw === confirmInput.value
}

function updateBtnState() {
  const valid = isFormValid()
  resetBtn.style.background = valid
    ? 'linear-gradient(90deg, #D4AF37, #E8C547)'
    : 'rgba(255,255,255,0.12)'
  resetBtn.style.color      = valid ? '#000' : 'rgba(255,255,255,0.3)'
  resetBtn.style.boxShadow  = valid ? '0 4px 20px rgba(212,175,55,0.25)' : 'none'
}

// ── Enviar formulario ────────────────────────────────────────────────────────
resetBtn.addEventListener('click', handleReset)
document.addEventListener('keydown', (e) => {
  if (e.key === 'Enter' && !viewForm.classList.contains('hidden')) handleReset()
})

async function handleReset() {
  pwTouched      = true
  confirmTouched = true
  validatePw()
  validateConfirm()

  if (!isFormValid()) {
    showToast('⚠️ Corrige los errores antes de continuar')
    return
  }

  resetBtn.disabled = true
  resetBtn.innerHTML = '<div class="spinner"></div>'

  try {
    await confirmPasswordReset(auth, oobCode, pwInput.value)
    showView('success')
    setTimeout(() => { window.location.href = 'index.html' }, 3000)
  } catch (err) {
    const msg = err.code === 'auth/expired-action-code'
      ? '⚠️ El enlace expiró. Solicita uno nuevo.'
      : err.code === 'auth/invalid-action-code'
      ? '⚠️ El enlace es inválido o ya fue usado.'
      : err.code === 'auth/weak-password'
      ? '⚠️ La contraseña es demasiado débil.'
      : '❌ Error al cambiar la contraseña. Intenta nuevamente.'
    showToast(msg)
  } finally {
    resetBtn.disabled = false
    resetBtn.innerHTML = 'Cambiar contraseña'
  }
}

// ── Verificar oobCode al cargar ──────────────────────────────────────────────
async function init() {
  if (!oobCode) {
    showView('invalid')
    return
  }
  try {
    await verifyPasswordResetCode(auth, oobCode)
    updateStrengthBar('')
    updateRequirements('')
    updateBtnState()
    showView('form')
  } catch {
    showView('invalid')
  }
}

init()
