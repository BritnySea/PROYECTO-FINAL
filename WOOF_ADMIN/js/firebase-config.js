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
  apiKey:            "AIzaSyBPB4oF3u2P5EXdH3JE9iDmA3563jjpYNo",
  authDomain:        "proyectov1-15.firebaseapp.com",
  projectId:         "proyectov1-15",
  storageBucket:     "proyectov1-15.firebasestorage.app",
  messagingSenderId: "939257384265",
  appId:             "1:939257384265:web:e4ae9b913fbaf474101e98",
}

const app = initializeApp(firebaseConfig)

export const auth = getAuth(app)
export const db   = getFirestore(app)
