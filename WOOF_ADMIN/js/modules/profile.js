import { db } from '../firebase-config.js'
import {
  doc, updateDoc,
} from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js'
import { showToast }                                           from './utils.js'
import { currentUser, currentUserData, storeAdminState, waitForAdminState } from './admin-state.js'

export function initProfile() {
  const phoneInput   = document.getElementById('profile-phone')
  const phoneWrapper = document.getElementById('profile-phone-wrapper')
  const phoneErr     = document.getElementById('profile-phone-error')
  const saveBtn      = document.getElementById('btn-save-profile')

  if (!phoneInput || !saveBtn) return

  phoneInput.setAttribute('maxlength', '10')
  phoneInput.addEventListener('input', () => {
    phoneInput.value = phoneInput.value.replace(/\D/g, '').slice(0, 10)
    phoneWrapper?.classList.remove('error')
    if (phoneErr) phoneErr.textContent = ''
  })

  saveBtn.addEventListener('click', async () => {
    const nameInput = document.getElementById('profile-name')
    const name  = nameInput ? nameInput.value.trim() : ''
    const phone = phoneInput.value.trim()

    if (!phone) {
      phoneWrapper?.classList.add('error')
      if (phoneErr) phoneErr.textContent = 'El número de celular es requerido'
      return
    }
    if (phone.length < 8) {
      phoneWrapper?.classList.add('error')
      if (phoneErr) phoneErr.textContent = 'El número debe tener al menos 8 dígitos'
      return
    }

    saveBtn.disabled = true
    saveBtn.innerHTML = '<div class="spinner"></div>'

    try {
      const user = currentUser
      if (!user) throw new Error('No hay sesión activa')

      await updateDoc(doc(db, 'users', user.uid), { name, phone })
      storeAdminState(user, { ...currentUserData, name, phone })

      const adminNameEl = document.getElementById('admin-name')
      if (adminNameEl) adminNameEl.textContent = name || user.email

      const avatar = document.getElementById('profile-avatar')
      if (avatar) {
        const initials = (name || user.email || '?')
          .split(' ').slice(0, 2).map(w => w[0]?.toUpperCase()).join('')
        avatar.textContent = initials || '?'
      }

      showToast('✅ Perfil actualizado correctamente', false)
    } catch (err) {
      showToast('❌ Error al guardar. Intenta nuevamente.')
      console.error('[profile] save error:', err?.code, err?.message)
    } finally {
      saveBtn.disabled = false
      saveBtn.innerHTML = 'Guardar cambios'
    }
  })
}

export async function loadProfile() {
  // Espera a que el auth guard haya validado al usuario y guardado el estado
  await waitForAdminState()

  const user = currentUser
  const data = currentUserData

  if (!user) return

  const nameInput  = document.getElementById('profile-name')
  const emailInput = document.getElementById('profile-email')
  const phoneInput = document.getElementById('profile-phone')
  const avatar     = document.getElementById('profile-avatar')

  if (nameInput)  nameInput.value       = data.name  || ''
  if (emailInput) emailInput.textContent = user.email || ''
  if (phoneInput) phoneInput.value       = data.phone || ''

  if (avatar) {
    const initials = (data.name || user.email || '?')
      .split(' ').slice(0, 2).map(w => w[0]?.toUpperCase()).join('')
    avatar.textContent = initials || '?'
  }
}
