# WOOF — Aplicación para encontrar perros perdidos

WOOF es una plataforma completa que permite a usuarios reportar perros perdidos y encontrados, utilizando **inteligencia artificial** para comparar fotos y encontrar coincidencias automáticamente mediante análisis de similitud de imágenes.

---

## ¿Cómo funciona?

1. Un usuario reporta su **perro perdido** subiendo una foto y datos de contacto.
2. Otro usuario encuentra un perro y sube una **foto del perro encontrado**.
3. El sistema analiza ambas fotos con un modelo de IA y calcula el **porcentaje de similitud**.
4. Si hay coincidencia, se muestran los resultados con los datos de contacto del dueño.

---

## Estructura del proyecto

```
PROYECTO/
├── PROYECTO_APP/     → App móvil Android
├── WOOF_BACKEND/     → API REST + lógica de IA
├── WOOF_TRAINING/    → Entrenamiento del modelo de IA
└── WOOF_ADMIN/       → Panel de administración (en desarrollo)
```

---

## PROYECTO_APP — App Android

Aplicación móvil desarrollada en **Kotlin** con **Jetpack Compose** (UI declarativa, sin XML).

### Tecnologías
- **Lenguaje**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Arquitectura**: Clean Architecture (Domain / Data / UI)
- **Inyección de dependencias**: Hilt (Dagger)
- **Autenticación**: Firebase Authentication (email/password)
- **Base de datos**: Firebase Firestore (NoSQL en la nube)
- **Almacenamiento**: Firebase Storage (fotos)
- **HTTP Client**: Retrofit 2 (comunicación con el backend)
- **Min SDK**: 26 (Android 8.0) / **Target SDK**: 36
- **Tema visual**: Fondo oscuro `#0A0A0A`, acento dorado `#D4AF37`, estilo glassmorphism

### Arquitectura de carpetas

```
feature/
  auth/
    domain/model/User.kt              → Modelo: uid, name, email, role, isBlocked
    domain/repository/AuthRepository.kt
    data/repository/AuthRepositoryImpl.kt
  dogs/
    domain/model/Dog.kt               → Modelo: id, name, description, ownerName,
    |                                    ownerPhone, ownerEmail, photoUrl, status, uid
    domain/model/DogMatch.kt          → Modelo: dogId, name, ownerPhone, ownerEmail,
    |                                    similarityPercent, photoUrl
    domain/repository/DogsRepository.kt
    data/repository/DogsRepositoryImpl.kt
    data/remote/WoofApiService.kt     → Interfaz Retrofit con los endpoints del backend
  user/
    domain/repository/UserRepository.kt
    data/repository/UserRepositoryImpl.kt  → Lee/actualiza campo "phone" en Firestore
di/
  AppModule.kt                        → Provee Firebase, Retrofit y Repositories con Hilt
core/result/
  UiState.kt                          → Sealed class: Idle / Loading / Success<T> / Error
ui/
  auth/     → AuthViewModel, LoginScreen, RegisterScreen, AuthComponents
  dogs/     → DogsViewModel, ReportLostDogScreen, ReportFoundDogScreen,
  |            MyReportsScreen, MatchResultsScreen
  home/     → HomeScreen
  profile/  → ProfileViewModel, ProfileScreen
  splash/   → SplashScreen
  navigation/ → Routes.kt
  theme/    → Color.kt, Theme.kt, Type.kt
```

### Navegación

```
SPLASH
  ├── LOGIN → REGISTER
  └── HOME (BottomNav)
        ├── My Reports
        ├── Match Results
        └── Profile

  report_lost / report_found  (sin BottomNav, acceso desde HOME)
```

### Colecciones en Firestore

| Colección | Campos |
|---|---|
| `users/{uid}` | uid, name, email, role (USER/ADMIN), isBlocked, phone |
| `lost_dogs/{id}` | name, description, owner_name, owner_phone, owner_email, photo_url, embedding, registered_by_uid, created_at, status |
| `found_reports/{id}` | found_by_uid, matches[ {dog_id, similarity_percent} ] |

### Lógica destacada: Phone Gate

Antes de que un usuario pueda reportar un perro (perdido o encontrado), el sistema verifica que tenga número de teléfono registrado en Firestore (`users/{uid}.phone`). Si no lo tiene, lo redirige automáticamente a la pantalla de Perfil para completar sus datos.

---

## WOOF_BACKEND — API REST

Backend que expone los endpoints para registrar perros perdidos y encontrar coincidencias usando IA.

### Tecnologías
- **Lenguaje**: Python 3
- **Framework**: FastAPI
- **IA / ML**: TensorFlow + Keras (modelo EfficientNetB4)
- **Firebase Admin SDK**: verificación de tokens, Firestore, Storage
- **Autenticación**: Firebase ID Token (Bearer token en cada request)

### Estructura

```
WOOF_BACKEND/
├── main.py                          → Inicializa Firebase y carga el modelo al arrancar
├── app/
│   ├── routes/
│   │   └── dogs.py                  → Define los 2 endpoints principales
│   └── services/
│       ├── model_service.py         → Detección de perro (is_dog) + extracción de embedding
│       └── firebase_service.py      → Sube foto a Storage, guarda datos en Firestore
└── ml/
    └── woof_model_v1.h5             → Modelo entrenado (no versionado en Git)
```

### Endpoints

#### `POST /api/v1/register-lost-dog`
Registra un perro perdido. Recibe los datos del dueño y una foto, extrae el embedding del perro con el modelo de IA y lo guarda en Firestore.

- **Auth**: Bearer token (Firebase ID Token)
- **Body** (multipart/form-data):
  - `dog_name`, `description`, `owner_name`, `owner_phone`, `owner_email`
  - `photo` (archivo de imagen)

#### `POST /api/v1/match-found-dog`
Recibe la foto de un perro encontrado, extrae su embedding y lo compara contra todos los perros perdidos registrados usando **similitud coseno**. Devuelve los mejores 10 resultados con más del 20% de similitud.

- **Auth**: Bearer token (Firebase ID Token)
- **Body** (multipart/form-data):
  - `photo` (archivo de imagen)

### Lógica de matching

1. Se recibe la foto del perro encontrado.
2. El modelo verifica que la imagen **contiene un perro** (`is_dog`).
3. Se extrae el **embedding** (vector numérico que representa visualmente al perro).
4. Se compara ese vector contra todos los embeddings almacenados en Firestore usando **similitud coseno**.
5. Se retornan los resultados ordenados por similitud, filtrados por un umbral mínimo del 20%.

---

## WOOF_TRAINING — Entrenamiento del modelo

Notebook de Jupyter para entrenar el modelo de reconocimiento visual de perros.

### Tecnologías
- **Python**, **TensorFlow / Keras**
- **Modelo base**: EfficientNetB4 (transfer learning)
- **Técnica**: Fine-tuning sobre dataset de razas/perros

### Resultado
El modelo entrenado se exporta como `woof_model_v1.h5` y se coloca en `WOOF_BACKEND/ml/` para ser usado por la API en producción.

```
WOOF_TRAINING/
└── woof_training_v1.ipynb    → Notebook completo de entrenamiento
```

---

## WOOF_ADMIN — Panel de administración

Panel web para gestión de usuarios y reportes (en desarrollo).

---

## Configuración para desarrollo local

### Backend
```bash
cd WOOF_BACKEND
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --reload
```

### App Android
- Abrir `PROYECTO_APP/` en Android Studio
- Agregar el archivo `google-services.json` de tu proyecto Firebase en `app/`
- La URL base del backend en el emulador es `http://10.0.2.2:8000/`

> **Nota**: El archivo `google-services.json` y las credenciales de Firebase **no están incluidos** en este repositorio por seguridad. Debes configurar tu propio proyecto en [Firebase Console](https://console.firebase.google.com/).

---

## Ramas del repositorio

| Rama | Descripción |
|---|---|
| `main` | Código estable |
| `proyecto` | Rama de desarrollo activo |
