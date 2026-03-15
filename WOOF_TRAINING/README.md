# REFUGIO WOOF — Sistema de Identificación de Perros Extraviados

> **Título académico:**
> *Sistema web y móvil de identificación de perros extraviados aplicando redes neuronales convolucionales EfficientNet y el algoritmo Cosine Similarity*

---

## ¿Qué es este proyecto?

Un sistema completo para ayudar a reunir perros perdidos con sus dueños.
Cuando alguien pierde un perro, lo registra con **una foto**. Cuando alguien encuentra un perro, sube **una foto** y el sistema **compara automáticamente** contra todos los perros perdidos registrados usando inteligencia artificial, mostrando los más similares con un porcentaje de coincidencia para que el usuario pueda contactar al dueño por su cuenta.

> **Restricciones del sistema:**
> - Cada reporte acepta **únicamente 1 foto** (perdido o encontrado)
> - **No hay mensajería dentro de la app** — el sistema muestra los datos de contacto del dueño (teléfono / correo) y el usuario se comunica por fuera
> - No hay módulo de adopciones

---

## ¿Cómo funciona la IA?

El sistema usa **dos tecnologías combinadas**:

### 1. EfficientNetB4 — Red Neuronal Convolucional
Analiza la foto del perro y extrae un **vector de características** (embedding) — una lista de 512 números que representan matemáticamente cómo se ve ese perro: su color, forma, textura, rasgos del hocico, orejas, etc.

```
1 foto del perro
      ↓
[EfficientNetB4]     ← red neuronal entrenada con 93.4% de precisión
      ↓
Vector embedding     ← ej: [0.23, -0.87, 0.14, 0.95, ...]  (512 números)
```

### 2. Cosine Similarity — Algoritmo de Comparación
Compara el embedding del perro encontrado contra todos los embeddings de perros perdidos en la base de datos. El resultado es un porcentaje que indica qué tan similares son dos perros visualmente.

```
Perro encontrado  →  embedding A  ─┐
                                    ├→ coseno similitud → 87% similar
Perro perdido #1  →  embedding B  ─┘

Perro encontrado  →  embedding A  ─┐
                                    ├→ coseno similitud → 12% similar
Perro perdido #2  →  embedding C  ─┘
```

**¿Por qué coseno de similitud?**
Porque mide el ángulo entre dos vectores en el espacio matemático — dos fotos del mismo perro producirán vectores muy similares (ángulo pequeño = alta similitud), aunque la foto haya sido tomada desde otro ángulo, con diferente iluminación o en distinto momento.

---

## Arquitectura del sistema (4 componentes)

```
┌─────────────────────────────────────────────────────────────────┐
│                        APP ANDROID (móvil)                       │
│                                                                   │
│  • Reportar perro perdido → sube 1 foto + nombre + contacto      │
│  • Reportar perro encontrado → sube 1 foto                       │
│  • Ver resultados con % de similitud y datos de contacto         │
│    del dueño (el usuario contacta por fuera de la app)           │
│                                                                   │
│  SIN mensajería interna  |  SIN adopciones  |  1 foto máx        │
└──────────────────────────┬──────────────────────────────────────┘
                           │  HTTP (foto + token Firebase Auth)
                           ↓
┌─────────────────────────────────────────────────────────────────┐
│              BACKEND PYTHON — FastAPI (servidor)                  │
│              Desplegado en Railway                                │
│                                                                   │
│  POST /api/v1/register-lost-dog                                   │
│    1. Verifica token Firebase (autenticación)                     │
│    2. Recibe foto + datos del dueño                               │
│    3. Verifica que la foto sea un perro (EfficientNetB4)          │
│    4. Extrae embedding (vector 512 números)                       │
│    5. Sube la foto a Cloudinary → obtiene URL pública             │
│    6. Guarda URL + datos + embedding en Firestore                 │
│                                                                   │
│  POST /api/v1/match-found-dog                                     │
│    1. Verifica token Firebase (autenticación)                     │
│    2. Recibe foto del perro encontrado                            │
│    3. Verifica que la foto sea un perro (EfficientNetB4)          │
│    4. Extrae embedding del perro encontrado                       │
│    5. Calcula coseno similitud vs todos los perros perdidos       │
│    6. Devuelve lista ordenada por % de similitud (top 10)        │
│                                                                   │
│  GET /health  ← sin auth, para monitoreo del servidor            │
│                                                                   │
│  El modelo ML vive aquí → se actualiza sin publicar nuevo APK    │
└────────────┬─────────────────────────┬───────────────────────────┘
             │                         │
             ↓                         ↓
┌────────────────────────┐  ┌─────────────────────────────────────┐
│  CLOUDINARY (fotos)    │  │  FIREBASE (base de datos + auth)     │
│                        │  │                                       │
│  • Almacena las fotos  │  │  Firestore:                          │
│    de perros perdidos  │  │  • lost_dogs (embedding, URL, datos) │
│  • 25 GB gratis        │  │  • found_dog_reports (matches)       │
│  • Devuelve URL        │  │  • Usuarios y roles                  │
│    pública de la foto  │  │                                       │
│                        │  │  Authentication:                      │
└────────────────────────┘  │  • Login con email/contraseña        │
                            │  • Token Bearer en cada petición     │
                            └──────────────────┬────────────────────┘
                                               │
                                               ↓
                            ┌─────────────────────────────────────┐
                            │         PANEL ADMIN (web)            │
                            │         Desplegado en Firebase       │
                            │         Hosting o Vercel             │
                            │                                       │
                            │  • Ver perros perdidos registrados   │
                            │  • Ver reportes con sus matches      │
                            │  • Gestionar usuarios                │
                            │  • Agregar perros del refugio        │
                            └─────────────────────────────────────┘
```

---

## Casos de uso principales

### Caso A — Usuario perdió su perro
```
1. Abre la app → "Mi perro se perdió"
2. Inicia sesión (Firebase Auth)
3. Sube 1 foto del perro + nombre + descripción + datos de contacto
4. El backend verifica que sea un perro (rechaza si no lo es)
5. Extrae el embedding → sube foto a Cloudinary → guarda todo en Firestore
6. El perro queda registrado y disponible para comparaciones futuras
```

### Caso B — Usuario encontró un perro
```
1. Abre la app → "Encontré un perro"
2. Inicia sesión (Firebase Auth)
3. Sube 1 foto del perro encontrado
4. El backend verifica que sea un perro (rechaza si no lo es)
5. Extrae el embedding → calcula similitud coseno contra todos los perros perdidos
6. La app muestra los resultados más similares:

   ┌──────────────────────────────────────┐
   │  Rex — Golden Retriever              │
   │  Similitud: 87%                      │
   │  Contacto: +57 311 234 5678          │
   │  Correo: juan@email.com              │
   └──────────────────────────────────────┘

   El usuario llama o escribe al dueño por fuera de la app.
   No hay chat ni mensajería interna.
```

### Caso C — Admin gestiona el sistema
```
• Ve todos los perros perdidos registrados (fotos, embeddings, dueños)
• Ve todos los reportes de perros encontrados con sus matches y %
• Bloquea usuarios que hagan mal uso del sistema
• Agrega perros del refugio para que aparezcan en las comparaciones
```

---

## Validaciones del sistema

| Situación | Respuesta del sistema |
|---|---|
| Usuario sube foto de un perro | ✅ Procesa: extrae embedding y guarda/compara |
| Usuario sube foto de un gato, persona u objeto | ❌ Rechaza con mensaje claro |
| Usuario no ha iniciado sesión | ❌ 401 Unauthorized |
| Perro perdido registrado | Aparece automáticamente en futuras comparaciones |
| Se actualiza el modelo ML | Sin publicar nuevo APK — solo se actualiza el servidor |

---

## Stack tecnológico

| Componente | Tecnología |
|---|---|
| App móvil | Android (Kotlin) |
| Backend ML | Python — FastAPI + Uvicorn |
| Hosting backend | Railway |
| Panel admin | Web (por definir: React / Vue / HTML) |
| Hosting panel admin | Firebase Hosting o Vercel |
| Base de datos | Firebase Firestore |
| Autenticación | Firebase Authentication |
| Almacenamiento fotos | Cloudinary (25 GB gratis) |
| Red neuronal | EfficientNetB4 (TensorFlow / Keras) |
| Algoritmo matching | Cosine Similarity (scikit-learn) |
| Entrenamiento | Kaggle (GPU T4) |

---

## Estructura de carpetas del proyecto

```
PROYECTO/
├── WOOF_TRAINING/          ← entrenamiento del modelo (este directorio)
│   ├── woof_training_v1.ipynb
│   ├── README.md
│   └── output/
│       ├── woof_model_v1.h5         ← modelo oficial (93.4% val_accuracy)
│       ├── woof_model_v1.tflite
│       └── woof_model_v1_metadata.json
│
├── WOOF_BACKEND/           ← backend Python/FastAPI
│   ├── main.py
│   ├── requirements.txt
│   ├── .env                ← credenciales (no subir a git)
│   ├── firebase_credentials.json   ← (no subir a git)
│   ├── app/
│   │   ├── config.py
│   │   ├── dependencies.py
│   │   ├── routes/
│   │   │   ├── health.py
│   │   │   └── dogs.py
│   │   ├── services/
│   │   │   ├── model_service.py
│   │   │   └── firebase_service.py
│   │   └── schemas/
│   │       └── dog.py
│   └── ml/
│       └── woof_model_v1.h5        ← copia del modelo
│
├── PROYECTO_APP/           ← app Android (Kotlin)
└── WOOF_ADMIN/             ← panel de administración (web)
```

---

## Módulo de entrenamiento (este directorio)

Este directorio contiene los notebooks de entrenamiento del modelo.
El modelo se entrena en **Kaggle** (GPU gratis) y el archivo resultante se despliega en el backend Python.

### El modelo — EfficientNetB4

| Versión | Arquitectura | Dataset | Precisión | Estado |
|---|---|---|---|---|
| v1 | EfficientNetB4 | Stanford Dogs + Animals10 | 93.4% val_accuracy | ✅ Completado |

### Decisiones de diseño del entrenamiento

| Decisión | Razón |
|---|---|
| EfficientNetB4 desde ImageNet | Mejor base que MobileNetV2, alcanza 90%+ |
| Sin CIFAR-10 como negativos | Imágenes de 32px estiradas a 380px — inútiles para el modelo |
| Animals10 como negativos | 9 clases de animales reales de alta calidad |
| `preprocess_input` en lugar de `/255` | Normalización correcta para EfficientNet |
| Entrenamiento en 3 fases | Estabiliza los gradientes antes de ajustar la base |
| Label smoothing 0.1 | Evita sobreconfianza en razas visualmente muy similares |
| Umbral de confianza 40% | Con EfficientNetB4 bien entrenado el modelo es mucho más seguro |

### Datasets utilizados

| Dataset | Uso | Fuente (Kaggle) |
|---|---|---|
| Stanford Dogs | 120 razas, ~20k imágenes de perros | jessicali9530 |
| Perros vs Gatos (dogs/) | Imágenes adicionales de perros | sergiodelcarpio |
| Animals10 (cane/) | Imágenes adicionales de perros | alessiocorrado99 |
| Animals10 (otras clases) | Negativos de alta calidad: caballos, ovejas, gallinas, etc. | alessiocorrado99 |
| Perros vs Gatos (cats/) | Negativos: gatos reales | sergiodelcarpio |

### Estrategia de entrenamiento — 3 fases

```
FASE 1 (10 épocas, LR=1e-3)
  Base EfficientNetB4 completamente congelada
  Solo se entrena la cabeza clasificadora
  → Estabiliza gradientes sin tocar los pesos de ImageNet

FASE 2 (20 épocas, LR=5e-5)
  Se descongela el 70% superior de la base
  → La red aprende características específicas de perros

FASE 3 (15 épocas, LR=5e-6)
  Se descongela todo el modelo
  → Ajuste fino completo con LR muy bajo para no perder lo aprendido
```

---

## Cómo correr el entrenamiento en Kaggle

### 1. Agregar los 3 datasets
Panel derecho → **+ Add Input** → agregar:
- `stanford-dogs-dataset` — jessicali9530
- `perros-vs-gatos` — sergiodelcarpio
- `animals10` — alessiocorrado99

### 2. Activar GPU
`Settings → Accelerator → GPU T4 x2`

### 3. Subir y ejecutar
**File → Import Notebook** → subir `woof_training_v1.ipynb`
Luego **Run All** — duración estimada: 4-6 horas

### 4. Descargar outputs
Al terminar, descargar de `/kaggle/working/`:
```
output/
├── woof_model_v1.h5                ← modelo completo para el backend Python
├── woof_model_v1.tflite            ← versión compacta (opcional)
└── woof_model_v1_metadata.json     ← umbral, clases, preprocessing info
```

---

## Cómo correr el backend localmente

```bash
cd WOOF_BACKEND
pip install -r requirements.txt
# copiar woof_model_v1.h5 a ml/
# crear .env desde .env.example con tus valores reales
uvicorn main:app --reload
# abrir http://localhost:8000/docs
```

Variables requeridas en `.env`:
```
FIREBASE_CREDENTIALS_PATH=firebase_credentials.json
MODEL_PATH=ml/woof_model_v1.h5
CLOUDINARY_CLOUD_NAME=tu-cloud-name
CLOUDINARY_API_KEY=tu-api-key
CLOUDINARY_API_SECRET=tu-api-secret
```

---

## Nota importante — Preprocessing en el backend

Al usar el modelo en el backend Python la imagen debe procesarse así:

```python
from tensorflow.keras.applications.efficientnet import preprocess_input
import numpy as np

# img_array debe estar en rango [0, 255] — NO dividir entre 255
img_processed = preprocess_input(img_array.astype(np.float32))
img_processed = np.expand_dims(img_processed, axis=0)

# Para clasificación (¿es un perro?)
prediccion = model.predict(img_processed)

# Para coseno de similitud — extraer embedding de la capa antes del clasificador
embedding = embedding_model.predict(img_processed)  # vector 512 números
```

> EfficientNetB4 normaliza internamente a `[-1, 1]` mediante `preprocess_input`.
> Usar `img / 255.0` produce resultados incorrectos con esta arquitectura.
