// Estado del administrador autenticado — compartido entre módulos
export let currentUser     = null
export let currentUserData = {}

// Promesa que se resuelve la primera vez que storeAdminState es llamado
let _resolveReady = null
const _readyPromise = new Promise(r => { _resolveReady = r })

export function storeAdminState(user, firestoreData) {
  currentUser     = user
  currentUserData = firestoreData || {}
  if (_resolveReady) { _resolveReady(); _resolveReady = null }
}

// Espera a que el estado del admin esté disponible
export function waitForAdminState() {
  return _readyPromise
}
