# MANUAL DE INSTALACIÓN — SISTEMA WOOF

---

**Universidad del Valle (UNIVALLE) — Sede La Paz, Bolivia**
**Carrera:** Ingeniería en Sistemas Informáticos
**Proyecto de Grado:** WOOF — Sistema de Reporte y Reunificación de Perros Extraviados mediante Reconocimiento Visual con IA
**Docente evaluador:** [NOMBRE DEL DOCENTE]
**Autora:** Britny Miroslava Sea Herrera
**Gestión:** 2025 – 2026

---

## ÍNDICE

1. [Introducción y Descripción del Sistema](#1-introducción-y-descripción-del-sistema)
2. [Requisitos del Sistema](#2-requisitos-del-sistema)
3. [Contenido del Paquete](#3-contenido-del-paquete)
4. [Instalación Paso a Paso](#4-instalación-paso-a-paso)
   - 4.1 [Inicio Rápido](#41-inicio-rápido-opción-recomendada--12-minutos)
   - 4.2 [Backend FastAPI — Instalación Local](#42-backend-fastapi--instalación-local)
   - 4.3 [App Móvil Android](#43-app-móvil-android)
   - 4.4 [Panel Web Admin](#44-panel-web-admin)
5. [Despliegue en Servicios en la Nube](#5-despliegue-en-servicios-en-la-nube)
6. [Solución de Problemas Comunes](#6-solución-de-problemas-comunes)
7. [Anexos](#7-anexos)

---

## 1. Introducción y Descripción del Sistema

**WOOF** es un sistema de reporte y reunificación de perros extraviados que utiliza inteligencia artificial para identificar y comparar visualmente perros mediante fotografías.

### Problema que resuelve

Cuando un perro se pierde, los dueños no tienen una forma eficiente de buscar y encontrar a su mascota. WOOF centraliza los reportes de perros perdidos y encontrados, y usa visión artificial para comparar automáticamente las fotografías y sugerir coincidencias probables.

### Los 3 componentes del sistema

| Componente | Descripción | Acceso |
|---|---|---|
| **App Móvil Android** | Los usuarios reportan perros perdidos o encontrados mediante fotos | APK incluido en el paquete |
| **Backend FastAPI** | Procesa imágenes con IA, gestiona reportes, conecta con Firebase y Cloudinary | Local (este manual) o Railway (ya desplegado) |
| **Panel Web Admin** | El administrador gestiona usuarios, reportes y estadísticas | Navegador web |

### Arquitectura general

```
App Android  ──→  FastAPI (Railway / local)  ──→  Firebase Firestore
                                              ──→  Cloudinary (imágenes)
Panel Web    ──→  Firebase Firestore (conexión directa via SDK web)
FastAPI      ──→  Modelo EfficientNet-B4 (woof_model_v1.h5)
             ──→  Comparación Cosine Similarity (embeddings 512-dim)
```

### Métricas del modelo de IA

- Modelo: **EfficientNet-B4** fine-tuned
- Clases: **122 razas** de perros + clase "no es un perro"
- **Top-1 accuracy: 93.4%**
- **Top-5 accuracy: 99.59%**
- Embeddings de **512 dimensiones** para comparación visual

---

## 2. Requisitos del Sistema

### 2.1 Hardware mínimo

| Recurso | Mínimo recomendado |
|---|---|
| RAM | **8 GB** (el modelo de IA requiere ~4 GB al cargar) |
| Almacenamiento libre | **5 GB** (Python + TensorFlow + modelo + proyecto) |
| Para probar la app | Dispositivo Android 8.0+ o emulador |

### 2.2 Software requerido

| Software | Versión | Enlace de descarga |
|---|---|---|
| **Python** | **3.11.x** (se recomienda 3.11.8) | https://python.org/downloads/release/python-3118/ |
| **Git** | Cualquier versión reciente | https://git-scm.com |
| **Android Studio** | Cualquier versión reciente | https://developer.android.com/studio |
| | *(solo si compila desde fuente; NO necesario si usa el APK incluido)* | |

> **Nota:** El panel web es HTML/JS estático. **No requiere Node.js** ni ningún servidor especial.

### 2.3 Servicios cloud (ya configurados — no requiere crear cuentas)

El paquete incluye credenciales listas para usar. No es necesario registrarse en ningún servicio.

| Servicio | Uso |
|---|---|
| **Firebase Firestore** | Base de datos de reportes y usuarios |
| **Firebase Auth** | Autenticación de usuarios |
| **Firebase Hosting** | Panel web en producción |
| **Cloudinary** | Almacenamiento de imágenes de perros |
| **Railway** | Hosting del backend FastAPI (también puede correrse local) |

---

## 3. Contenido del Paquete

```
WOOF_EntregaDocente/
├── backend/                 → Código fuente FastAPI + credenciales
│   ├── app/                 → Módulos de la API (rutas, servicios, schemas)
│   ├── ml/
│   │   └── woof_model_v1.h5 → Modelo de IA (213 MB)
│   ├── main.py              → Punto de entrada de la API
│   ├── requirements.txt     → Dependencias con versiones exactas
│   ├── .env                 → Variables de entorno con credenciales reales
│   ├── .env.example         → Estructura sin valores (referencia)
│   └── firebase_credentials.json → Credenciales de Firebase (cuenta de servicio)
├── panel-web/               → Código fuente del Panel Admin (HTML/JS)
│   ├── index.html           → Login del panel
│   ├── dashboard.html       → Panel principal
│   ├── js/                  → Lógica JavaScript
│   └── css/                 → Estilos
├── app-android/             → Código fuente Android (sin google-services.json)
├── apk/
│   └── app-debug.apk        → APK listo para instalar
├── MANUAL_INSTALACION.md    → Este documento
└── README.md                → Guía de inicio rápido
```

---

## 4. Instalación Paso a Paso

### 4.1 Inicio Rápido (opción recomendada — ~12 minutos)

| Paso | Acción | Sección |
|---|---|---|
| 1 | Instalar el APK en el dispositivo | [4.3 Opción A](#opción-a--instalar-apk-precompilado-recomendado) |
| 2 | Correr el backend local | [4.2](#42-backend-fastapi--instalación-local) |
| 3 | Abrir el panel web | [4.4](#44-panel-web-admin) |

> Si solo quiere ver la app en funcionamiento sin el backend local, el backend **ya está desplegado en Railway**: `https://refugiowoof.up.railway.app/`. La app Android y el panel ya apuntan a esa URL por defecto.

---

### 4.2 Backend FastAPI — Instalación Local

#### Requisito previo: Python 3.11

Verificar que Python 3.11 está instalado:
```
python --version
# Debe mostrar: Python 3.11.x
```

#### Instalación

**Windows:**
```bat
cd backend
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
```

**Linux / macOS:**
```bash
cd backend
python3.11 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```

> El archivo `.env` ya está incluido en el paquete con todas las credenciales configuradas. **No es necesario modificar nada.**

> El modelo `ml/woof_model_v1.h5` ya está incluido en el paquete. **No requiere descarga adicional.**

#### Iniciar el backend

```bat
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

#### Verificación

Abrir en el navegador: **http://localhost:8000/docs**

Debe mostrar la documentación interactiva de la API (Swagger UI) con todos los endpoints disponibles.

Al iniciar, el servidor cargará el modelo de IA. Los primeros mensajes en consola serán:
```
INFO: Iniciando WOOF API...
INFO: Modelo cargado correctamente.
INFO: Uvicorn running on http://0.0.0.0:8000
```

> **Nota:** La primera carga del modelo tarda entre 15 y 60 segundos dependiendo del hardware.

---

### 4.3 App Móvil Android

#### Opción A — Instalar APK precompilado (recomendado)

1. Conectar un dispositivo Android (versión 8.0 o superior) o abrir un emulador en Android Studio
2. Habilitar instalación desde fuentes desconocidas:
   - **Android 8 o superior:** Ajustes → Apps → Instalar apps desconocidas → seleccionar el explorador de archivos → Permitir
   - **Android 7 o inferior:** Ajustes → Seguridad → Fuentes desconocidas → Activar
3. Transferir `apk/app-debug.apk` al dispositivo (por cable USB, Google Drive, etc.)
4. Abrir el archivo desde el administrador de archivos del dispositivo e instalar
5. Abrir la app **WOOF**

#### Opción B — Compilar desde código fuente (requiere Android Studio)

1. Abrir Android Studio → File → Open → seleccionar la carpeta `app-android/`
2. Copiar `google-services.json` a `app-android/app/`
   (el archivo está incluido en el paquete en la raíz de `backend/`)
3. Esperar que Gradle sincronice las dependencias (puede tardar varios minutos)
4. Run → Run 'app' (▶)

#### Credenciales de acceso a la app

| Campo | Valor |
|---|---|
| Email | obazanalarcon@gmail.com |
| Contraseña | [COMPLETAR — usar la contraseña de la cuenta] |

> Esta es la cuenta de la autora del proyecto con datos reales de prueba en Firebase.

#### Nota sobre conectividad con el backend local

La app ya apunta al backend en Railway (`https://refugiowoof.up.railway.app/`). Si desea probar contra el backend local:

- En el emulador Android, "localhost" no apunta a su PC. Use `http://10.0.2.2:8000/` para el emulador.
- En un dispositivo físico, use la IP local de su PC (ejecutar `ipconfig` en Windows → buscar la IP de la red WiFi, p. ej. `192.168.1.100`) → `http://192.168.1.100:8000/`

---

### 4.4 Panel Web Admin

El panel es un sitio **HTML/JS estático**. No requiere ningún servidor especial ni instalación.

#### Opción A — Abrir directamente (más simple)

Abrir el archivo `panel-web/index.html` directamente en el navegador.

> **Nota:** Algunos navegadores restringen módulos ES6 (`import/export`) en archivos locales (`file://`). Si el panel no carga, usar la Opción B.

#### Opción B — Servidor local (recomendado)

```bat
cd panel-web
python -m http.server 3000
```
Abrir en el navegador: **http://localhost:3000**

#### URL de producción ya desplegada

El panel también está disponible en: **https://refugiowoof.web.app**

#### Credenciales de acceso al panel admin

| Campo | Valor |
|---|---|
| Email | obazanalarcon@gmail.com |
| Contraseña | [COMPLETAR — usar la contraseña de la cuenta admin] |

> Solo las cuentas con `role: "ADMIN"` en Firestore pueden acceder al panel.

---

## 5. Despliegue en Servicios en la Nube

### 5.1 Backend en Railway

- **Plataforma:** railway.app
- **URL de producción:** `https://refugiowoof.up.railway.app/`
- **Documentación API:** `https://refugiowoof.up.railway.app/docs`
- **Comando de inicio:** `uvicorn main:app --host 0.0.0.0 --port $PORT`
- **Health check:** `https://refugiowoof.up.railway.app/health`

Variables de entorno configuradas en Railway Dashboard:

| Variable | Descripción |
|---|---|
| `FIREBASE_CREDENTIALS_JSON` | JSON completo de cuenta de servicio Firebase (como string) |
| `CLOUDINARY_CLOUD_NAME` | Nombre del cloud de Cloudinary |
| `CLOUDINARY_API_KEY` | Clave de API de Cloudinary |
| `CLOUDINARY_API_SECRET` | Secreto de API de Cloudinary |
| `MODEL_PATH` | Ruta al modelo `.h5` en el servidor |
| `MODEL_DOWNLOAD_URL` | URL pública de descarga del modelo (si aplica) |

> En Railway se usa `FIREBASE_CREDENTIALS_JSON` (JSON como string) en lugar de un archivo físico.
> En el paquete local se usa el archivo `firebase_credentials.json` vía `FIREBASE_CREDENTIALS_PATH`.

### 5.2 Panel Web en Firebase Hosting

- **Proyecto Firebase:** `proyectov1-15`
- **URL de producción:** `https://refugiowoof.web.app`
- **Comando de despliegue:** `firebase deploy`

### 5.3 Base de Datos — Firebase Firestore

- **Tipo:** NoSQL documental
- **Proyecto:** `proyectov1-15`

**Colecciones principales:**

| Colección | Descripción | Campos principales |
|---|---|---|
| `users` | Usuarios registrados | `uid`, `name`, `email`, `role` (USER/ADMIN), `isBlocked`, `phone` |
| `lost_dogs` | Reportes de perros perdidos | `name`, `description`, `owner_name`, `owner_phone`, `owner_email`, `photo_url`, `embedding`, `registered_by_uid`, `created_at`, `status` |
| `found_dog_reports` | Reportes de perros encontrados | `found_by_uid`, `photo_url`, `matches[{dog_id, similarity_percent}]`, `created_at`, `status` |

### 5.4 Almacenamiento de Imágenes — Cloudinary

- Las fotos de perros se suben a Cloudinary en la carpeta `dog_photos/`
- El backend gestiona la subida y recuperación vía API de Cloudinary
- **No se usa Firebase Storage** para las imágenes de perros

### 5.5 Generar APK de release (producción)

En Android Studio: **Build → Generate Signed APK**

---

## 6. Solución de Problemas Comunes

---

**ERROR: `No module named 'X'`**

```
ModuleNotFoundError: No module named 'fastapi'
```
→ El entorno virtual no está activado. Verificar que el prompt muestra `(venv)` al inicio.

Windows:
```bat
venv\Scripts\activate
```
Linux/macOS:
```bash
source venv/bin/activate
```

---

**ERROR: `woof_model_v1.h5 not found`**

```
FileNotFoundError: Modelo no encontrado en 'ml/woof_model_v1.h5'
```
→ Verificar que el archivo `ml/woof_model_v1.h5` existe en la carpeta `backend/ml/`.
→ Verificar que la variable `MODEL_PATH` en `.env` dice `ml/woof_model_v1.h5`.
→ Ejecutar `uvicorn` **desde dentro de la carpeta `backend/`**, no desde otra ubicación.

---

**ERROR: `firebase_credentials.json not found`**

→ Verificar que el archivo `firebase_credentials.json` existe en la carpeta `backend/` (mismo nivel que `main.py`).
→ Verificar que `FIREBASE_CREDENTIALS_PATH=firebase_credentials.json` está en `.env`.

---

**ERROR: Puerto 8000 ya está en uso**

```
ERROR: [Errno 10048] error while attempting to bind on address ('0.0.0.0', 8000)
```
→ Usar otro puerto:
```bat
uvicorn main:app --reload --port 8001
```

---

**App Android no conecta con el backend local**

→ En el emulador, `localhost` y `127.0.0.1` apuntan al emulador mismo, no a la PC.
→ Para el emulador: usar `http://10.0.2.2:8000/`
→ Para dispositivo físico en la misma red WiFi: usar la IP local de la PC
   (ejecutar `ipconfig` en Windows → buscar IPv4 en "Adaptador de LAN inalámbrica WiFi")

---

**ERROR de CORS en el panel web**

```
Access to fetch at 'http://localhost:8000/...' has been blocked by CORS policy
```
→ El backend ya permite los orígenes `http://localhost`, `http://127.0.0.1`, `http://localhost:5500`.
→ Si el panel está en un puerto diferente, agregar al `.env`:
```
ALLOWED_ORIGINS=http://localhost:3000
```
→ Reiniciar el backend después de modificar `.env`.

---

**ERROR: Cloudinary upload fails / Error 401**

→ Verificar las 3 variables en `.env`:
```
CLOUDINARY_CLOUD_NAME=...
CLOUDINARY_API_KEY=...
CLOUDINARY_API_SECRET=...
```

---

**El panel web no carga (archivo local en Chrome/Edge)**

→ Chrome y Edge bloquean `import` de módulos ES6 desde `file://`.
→ Solución: usar el servidor local de Python:
```bat
cd panel-web
python -m http.server 3000
```
→ Abrir: http://localhost:3000

---

## 7. Anexos

### 7.1 Inicio Rápido — Comandos Resumidos

```bat
cd backend
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
uvicorn main:app --reload --host 0.0.0.0 --port 8000
```
→ Verificar: http://localhost:8000/docs

### 7.2 Tecnologías y Versiones Utilizadas

**Backend:**

| Tecnología | Versión |
|---|---|
| Python | 3.11.8 |
| FastAPI | 0.136.1 |
| Uvicorn | 0.46.0 |
| TensorFlow / Keras | 2.17.1 / 3.14.1 |
| firebase-admin | 7.4.0 |
| cloudinary | 1.44.2 |
| scikit-learn | 1.8.0 |
| numpy | 1.26.4 |
| Pillow | 12.2.0 |

**Servicios Cloud:**

| Servicio | Uso |
|---|---|
| Firebase Firestore | Base de datos NoSQL |
| Firebase Auth | Autenticación |
| Firebase Hosting | Hosting del panel web |
| Cloudinary | Almacenamiento de imágenes |
| Railway | Hosting del backend |

**App Móvil:**

| Tecnología | Versión |
|---|---|
| Android (compileSdk) | 36 |
| Android (minSdk) | 26 (Android 8.0) |
| Android (targetSdk) | 36 |
| Kotlin | Jetpack Compose |
| Firebase BOM | 34.8.0 |
| Retrofit | 2.11.0 |
| Hilt | 2.52 |

**Panel Web:**

| Tecnología | Detalle |
|---|---|
| HTML5 / CSS3 / JavaScript ES6 | Sin framework, sin Node.js |
| Firebase JS SDK | 10.12.0 (cargado desde CDN) |

### 7.3 Estructura de Firestore

**Colección `users`**
```
users/{uid}
  ├── uid: string
  ├── name: string
  ├── email: string
  ├── role: string         → "USER" | "ADMIN"
  ├── isBlocked: boolean
  └── phone: string
```

**Colección `lost_dogs`**
```
lost_dogs/{dogId}
  ├── name: string
  ├── description: string
  ├── owner_name: string
  ├── owner_phone: string
  ├── owner_email: string
  ├── photo_url: string    → URL de Cloudinary
  ├── embedding: array     → 512 floats (EfficientNet-B4)
  ├── registered_by_uid: string
  ├── created_at: timestamp
  └── status: string       → "active" | "inactive"
```

**Colección `found_dog_reports`**
```
found_dog_reports/{reportId}
  ├── found_by_uid: string
  ├── photo_url: string
  ├── matches: array[{dog_id, similarity_percent}]
  ├── created_at: timestamp
  └── status: string
```

### 7.4 Endpoints del API

Documentación interactiva completa (Swagger UI):
- **Local:** http://localhost:8000/docs
- **Producción:** https://refugiowoof.up.railway.app/docs

**Endpoints principales:**

| Método | Endpoint | Descripción |
|---|---|---|
| `GET` | `/health` | Estado del servidor y del modelo |
| `POST` | `/api/v1/validate-lost-photo` | Valida si una foto contiene un perro |
| `POST` | `/api/v1/validate-found-photo` | Valida foto de perro encontrado |
| `POST` | `/api/v1/register-lost-dog` | Registra un perro perdido con embedding |
| `POST` | `/api/v1/match-found-dog` | Compara foto con perros perdidos (cosine similarity) |
| `GET` | `/api/v1/my-dog-matches` | Obtiene coincidencias para un perro perdido específico |
| `GET` | `/api/v1/my-found-reports` | Lista reportes de perros encontrados del usuario |
| `PATCH` | `/api/v1/found-report-status` | Activa/desactiva un reporte de perro encontrado |
| `GET` | `/api/v1/my-found-report-matches` | Obtiene coincidencias para un reporte de encontrado |

Todos los endpoints (excepto `/health`) requieren token Bearer de Firebase Auth en el header `Authorization`.
