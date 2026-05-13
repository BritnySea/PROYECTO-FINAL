# INTEGRACIÓN — Pruebas Automatizadas (Iteración 5)

Estas pruebas verifican el sistema WOOF completo de forma integrada:
seguridad, rendimiento, contratos de respuesta y flujo end-to-end con el modelo IA.

---

## Archivos y qué prueban

### `test_seguridad.py`
Verifica la triada **CIA** (Confidencialidad, Integridad, Disponibilidad) en todos los endpoints.

**Confidencialidad — sin auth → 401:**
```
pytest llama los 5 endpoints POST sin token  →  todos deben retornar 401
pytest los llama con token JWT inventado      →  todos deben retornar 401
```

| Tests | Qué verifican | Necesita credenciales |
|-------|--------------|----------------------|
| `confidencialidad_sin_auth_401` (×5 endpoints) | Sin token → 401 en todos | No |
| `confidencialidad_token_invalido_401` (×5) | Token falso → 401 en todos | No |
| `integridad_mime_invalido_415` (×3 endpoints) | Archivo .txt → 415 | Sí (usuario) |
| `integridad_archivo_grande_413` | 11 MB → 413 | Sí (usuario) |
| `integridad_campo_invalido_422` (×5 campos) | Campos de texto inválidos → 422 | Sí (usuario) |
| `disponibilidad_health_200` | GET /health → 200 (servidor vivo) | No |

**Parametrización automática** — un solo test corre contra múltiples endpoints:
```python
@pytest.mark.parametrize("path", ["/api/v1/validate-photo", "/api/v1/register-lost-dog", ...])
def test_confidencialidad_sin_auth_401(api_url, path):
    # Este test se corre 5 veces, una por cada endpoint
```

---

### `test_rendimiento.py`
Mide tiempos de respuesta reales contra el backend.

| Test | Mide | Límite | Necesita credenciales |
|------|------|--------|----------------------|
| `health_bajo_2s` | Promedio de 3 llamadas a /health | < 2 000 ms | No |
| `validate_photo_bajo_15s` | Promedio de 2 llamadas a validate-photo | < 15 000 ms | Sí (usuario) |
| `validate_photo_p95_bajo_20s` | Percentil 95 de 5 llamadas | < 20 000 ms | Sí (usuario) |
| `my_reports_bajo_5s` | Promedio de 3 llamadas a my-found-reports | < 5 000 ms | Sí (usuario) |

**Cómo funciona el cálculo del P95:**
```python
tiempos = [980, 1100, 1050, 1200, 15000]  # 5 mediciones en ms
tiempos.sort()  → [980, 1050, 1100, 1200, 15000]
p95 = tiempos[int(5 * 0.95)]  → tiempos[4] = 15000 ms  → FALLA el test
```
Si el P95 > 20 000 ms significa que en el 5% de las llamadas el modelo tarda mucho.

---

### `test_regresion.py`
Verifica que el sistema siga funcionando igual después de cualquier cambio de código (XP).

| Test | Qué verifica | Necesita credenciales |
|------|-------------|----------------------|
| `health_responde` | GET /health → 200 y retorna JSON | No |
| `endpoints_POST_existen_no_404` | Los 5 endpoints POST existen (no fueron renombrados) | No |
| `endpoints_GET_existen_no_404` | Los endpoints GET existen | No |
| `validate_photo_contrato_respuesta` | Respuesta tiene campos: is_dog, confidence, message | Sí (usuario) |
| `my_found_reports_contrato_respuesta` | Respuesta tiene campo: reports (lista) | Sí (usuario) |
| `my_dog_matches_404_contrato` | Error 404 incluye campo "detail" (estándar FastAPI) | Sí (usuario) |
| `rate_limiter_activo_no_500` | 22 requests rápidas → 429, nunca 500 | No |

**Para qué sirven las pruebas de regresión:**
Cada vez que refactorizas o actualizas el backend, corre `pytest INTEGRACION/test_iteracion_5/test_regresion.py`.
Si algo falla, sabes exactamente qué rompiste.

---

### `test_end_to_end.py`
Prueba el flujo completo con el modelo EfficientNetB4 real.
**Requiere**: token válido + foto real de un perro + foto de un no-perro.

| Test | Qué verifica | Necesita credenciales |
|------|-------------|----------------------|
| `validate_foto_perro_is_dog_true` | Foto real de perro → `is_dog=True`, confidence > 0.5 | Sí + imagen perro |
| `validate_foto_nodog_is_dog_false` | Foto no-perro → `is_dog=False` | Sí + imagen no-perro |
| `match_retorna_estructura_correcta` | match-found-dog retorna {matches, is_dog, message} | Sí + imagen perro |
| `matches_ordenados_por_similitud` | Coincidencias ordenadas de mayor a menor % | Sí + imagen perro |
| `validate_lost_retorna_is_dog_true` | validate-lost-photo con perro real → is_dog=True (o 409 si duplicado) | Sí + imagen perro |

---

## Cómo correr cada grupo

```bash
# Desde WOOF_TESTING/

# Todo el módulo de integración
pytest INTEGRACION/ -v

# Solo seguridad (corre sin credenciales)
pytest INTEGRACION/test_iteracion_5/test_seguridad.py -v

# Solo rendimiento
pytest INTEGRACION/test_iteracion_5/test_rendimiento.py -v

# Solo regresión (correr después de cada cambio de código)
pytest INTEGRACION/test_iteracion_5/test_regresion.py -v

# Solo end-to-end (necesita imágenes reales)
pytest INTEGRACION/test_iteracion_5/test_end_to_end.py -v

# Solo los que NO necesitan credenciales
pytest INTEGRACION/ -m sin_auth -v

# Ver tiempos reales de cada test
pytest INTEGRACION/test_iteracion_5/test_rendimiento.py -v -s
```

---

## Cómo se ve la salida de pytest al correr todo

```
INTEGRACION/test_iteracion_5/test_seguridad.py::test_..._sin_auth_401[/api/v1/validate-photo]  PASSED
INTEGRACION/test_iteracion_5/test_seguridad.py::test_..._sin_auth_401[/api/v1/register-lost-dog]  PASSED
INTEGRACION/test_iteracion_5/test_seguridad.py::test_..._token_invalido_401[/api/v1/validate-photo]  PASSED
INTEGRACION/test_iteracion_5/test_rendimiento.py::test_..._health_bajo_2s  PASSED
  /health: 45 / 38 / 42 ms  (promedio=41 ms)
INTEGRACION/test_iteracion_5/test_rendimiento.py::test_..._validate_photo_bajo_15s  PASSED
  /validate-photo: 3240 / 3100 ms  (promedio=3170 ms)
INTEGRACION/test_iteracion_5/test_regresion.py::test_..._health_responde  PASSED
INTEGRACION/test_iteracion_5/test_end_to_end.py::test_..._validate_foto_perro_is_dog_true  PASSED
...
======= 42 passed, 8 skipped in 18.4s =======
```

Los **skipped** son normales — son los tests que necesitan imágenes reales que no se configuraron.
