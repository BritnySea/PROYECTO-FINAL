# APP — Pruebas Automatizadas (Iteración 3)

Estas pruebas verifican automáticamente los endpoints del backend que consume la app Android.
No prueban la UI de Compose directamente — prueban la lógica de negocio que la app usa.

---

## Archivos y qué prueban

### `test_autenticacion.py`
Verifica que la capa de autenticación Firebase rechaza correctamente accesos no autorizados.

| Test | Qué verifica | Necesita credenciales |
|------|-------------|----------------------|
| `test_..._sin_token_retorna_401` | Cada endpoint protegido da 401 sin header Authorization | No |
| `test_..._token_falso_retorna_401` | Un JWT inventado es rechazado con 401 | No |
| `test_..._login_correcto_recibe_token` | Credenciales válidas producen un idToken Firebase largo | Sí (usuario) |
| `test_..._token_valido_pasa_autenticacion` | Token real supera la capa de auth (no da 401) | Sí (usuario) |

**Cómo funciona internamente:**
```
pytest llama al endpoint  →  backend verifica el JWT con Firebase Admin SDK
     sin token                        ↓
     o token falso          retorna HTTP 401 Unauthorized
```

---

### `test_reporte_perdido.py`
Verifica validaciones de `/api/v1/validate-lost-photo` y `/api/v1/register-lost-dog`.

| Test | Qué verifica | Necesita credenciales |
|------|-------------|----------------------|
| `validate_lost_sin_token_401` | Sin auth → 401 | No |
| `validate_lost_tipo_invalido_415` | Archivo .txt → 415 | Sí (usuario) |
| `validate_lost_archivo_grande_413` | Imagen > 10 MB → 413 | Sí (usuario) |
| `register_sin_nombre_422` | Campo `dog_name` vacío → 422 | Sí (usuario) |
| `register_telefono_invalido_422` | Teléfono con letras → 422 | Sí (usuario) |
| `register_email_invalido_422` | Email sin @ → 422 | Sí (usuario) |
| `register_nombre_muy_largo_422` | Nombre > 100 chars → 422 | Sí (usuario) |
| `register_descripcion_larga_422` | Descripción > 500 chars → 422 | Sí (usuario) |
| `register_foto_tipo_invalido_415` | Foto .txt en register → 415 | Sí (usuario) |

**Cómo funciona internamente:**
```
pytest genera datos de prueba en memoria (sin archivos reales)
     ↓
POST /api/v1/register-lost-dog con datos inválidos
     ↓
backend valida con regex/len → retorna 422 Unprocessable Entity
pytest verifica el código HTTP retornado
```

---

### `test_reporte_encontrado.py`
Verifica validaciones de `/api/v1/validate-found-photo` y `/api/v1/match-found-dog`.

| Test | Qué verifica | Necesita credenciales |
|------|-------------|----------------------|
| `validate_found_sin_token_401` | Sin auth → 401 | No |
| `validate_found_tipo_invalido_415` | .txt → 415 | Sí (usuario) |
| `validate_found_archivo_grande_413` | > 10 MB → 413 | Sí (usuario) |
| `match_sin_token_401` | match-found-dog sin auth → 401 | No |
| `match_tipo_invalido_415` | .txt en match → 415 | Sí (usuario) |
| `match_archivo_grande_413` | > 10 MB en match → 413 | Sí (usuario) |
| `match_con_foto_perro_retorna_200` | Foto real de perro → 200 + lista matches | Sí + imagen perro |
| `match_con_no_perro_retorna_422` | Foto de no-perro → 422 (modelo rechaza) | Sí + imagen no-perro |

---

### `test_coincidencias.py`
Verifica el endpoint `GET /api/v1/my-dog-matches`.

| Test | Qué verifica | Necesita credenciales |
|------|-------------|----------------------|
| `my_matches_sin_token_401` | Sin auth → 401 | No |
| `my_matches_token_falso_401` | Token falso → 401 | No |
| `my_matches_dog_id_inexistente_404` | ID inventado → 404 | Sí (usuario) |
| `my_matches_esquema_correcto` | ID de otro usuario → 403 o 404 | Sí (usuario) |

---

### `test_mis_reportes.py`
Verifica `GET /api/v1/my-found-reports` y `PATCH /api/v1/found-report-status`.

| Test | Qué verifica | Necesita credenciales |
|------|-------------|----------------------|
| `my_reports_sin_token_401` | Sin auth → 401 | No |
| `my_reports_token_falso_401` | Token falso → 401 | No |
| `my_reports_con_token_200` | Token válido → 200 con campo `reports` (lista) | Sí (usuario) |
| `status_sin_token_401` | PATCH status sin auth → 401 | No |
| `status_report_inexistente_404` | report_id inventado → 404 | Sí (usuario) |

---

## Cómo correr solo estas pruebas

```bash
# Desde WOOF_TESTING/

# Todos los tests de la app
pytest APP/

# Solo un archivo
pytest APP/test_iteracion_3/test_autenticacion.py -v

# Solo los que NO necesitan credenciales (siempre corren)
pytest APP/ -m sin_auth -v

# Solo los que sí necesitan credenciales
pytest APP/ -m con_auth -v

# Solo tests del modelo IA (necesitan imagen de perro)
pytest APP/ -m modelo -v

# Ver qué tests hay sin correrlos
pytest APP/ --collect-only
```

---

## Qué genera al correr

```
pytest APP/ -v

APP/test_iteracion_3/test_autenticacion.py::test_..._sin_token_retorna_401[POST /api/v1/validate-photo]  PASSED
APP/test_iteracion_3/test_autenticacion.py::test_..._sin_token_retorna_401[POST /api/v1/register-lost-dog]  PASSED
APP/test_iteracion_3/test_reporte_perdido.py::test_..._register_sin_nombre_422  PASSED
...
```

Después corre `python generar_graficos.py` para los PNG del documento.
