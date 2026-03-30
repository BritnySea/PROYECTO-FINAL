// ═══════════════════════════════════════════════════════════
//  CONFIGURACIÓN DE FIREBASE
//  Reemplaza los valores con los de tu proyecto.
//
//  Dónde encontrarlos:
//  Firebase Console → Configuración del proyecto
//  → Tus apps → Agregar app web → SDK de configuración
// ═══════════════════════════════════════════════════════════

import { initializeApp }  from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-app.js'
import { getAuth }        from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-auth.js'
import { getFirestore }   from 'https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js'

const firebaseConfig = {
  apiKey:            "TU_API_KEY",
  authDomain:        "proyectov1-15.firebaseapp.com",
  projectId:         "proyectov1-15",
  storageBucket:     "proyectov1-15.appspot.com",
  messagingSenderId: "TU_MESSAGING_SENDER_ID",
  appId:             "TU_APP_ID",
}

const app = initializeApp(firebaseConfig)

export const auth = getAuth(app)
export const db   = getFirestore(app)
