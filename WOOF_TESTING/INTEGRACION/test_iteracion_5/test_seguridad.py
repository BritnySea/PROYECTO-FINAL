"""
PI-IT5 — Pruebas de Seguridad (Integración)
Verifica la triada CIA: Confidencialidad, Integridad y Disponibilidad
a nivel de todos los endpoints de la API.
"""
import io
import pytest
import requests

pytestmark = pytest.mark.sin_auth

TODOS_LOS_ENDPOINTS_POST = [
    "/api/v1/validate-photo",
    "/api/v1/validate-lost-photo",
    "/api/v1/validate-found-photo",
    "/api/v1/register-lost-dog",
    "/api/v1/match-found-dog",
]

JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 50 + b"\xff\xd9"


# ── Confidencialidad: auth requerida en todos los endpoints ───────────────

@pytest.mark.parametrize("path", TODOS_LOS_ENDPOINTS_POST)
def test_PI_IT5__confidencialidad_sin_auth_401(api_url, path):
    """Cada endpoint protegido retorna 401 sin Authorization header."""
    r = requests.post(f"{api_url}{path}",
                      files={"photo": ("t.jpg", io.BytesIO(JPEG), "image/jpeg"),
                             "photos": ("t.jpg", io.BytesIO(JPEG), "image/jpeg")},
                      timeout=15)
    assert r.status_code == 401, f"{path} → esperado 401, obtuvo {r.status_code}"


@pytest.mark.parametrize("path", TODOS_LOS_ENDPOINTS_POST)
def test_PI_IT5__confidencialidad_token_invalido_401(api_url, path):
    """Token Bearer inventado es rechazado con 401 en todos los endpoints."""
    hdrs = {"Authorization": "Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.INVALIDO.INVALIDO"}
    r = requests.post(f"{api_url}{path}",
                      headers=hdrs,
                      files={"photo": ("t.jpg", io.BytesIO(JPEG), "image/jpeg"),
                             "photos": ("t.jpg", io.BytesIO(JPEG), "image/jpeg")},
                      timeout=15)
    assert r.status_code == 401, f"{path} → esperado 401 con token falso, obtuvo {r.status_code}"


# ── Integridad: validación de archivos ─────────────────────────────────────

@pytest.mark.con_auth
@pytest.mark.parametrize("path", [
    "/api/v1/validate-photo",
    "/api/v1/validate-lost-photo",
    "/api/v1/validate-found-photo",
])
def test_PI_IT5__integridad_mime_invalido_415(api_url, user_headers, path):
    """Archivos con MIME type text/plain son rechazados con 415."""
    r = requests.post(f"{api_url}{path}",
                      headers=user_headers,
                      files={"photo": ("doc.txt", io.BytesIO(b"texto plano"), "text/plain")},
                      timeout=15)
    assert r.status_code == 415, f"{path} → esperado 415, obtuvo {r.status_code}: {r.text[:80]}"


@pytest.mark.con_auth
def test_PI_IT5__integridad_archivo_grande_413(api_url, user_headers):
    """Imagen de 11 MB es rechazada con 413."""
    grande = io.BytesIO(b"\xff\xd8\xff\xe0" + b"\x00" * (11 * 1024 * 1024) + b"\xff\xd9")
    r = requests.post(f"{api_url}/api/v1/validate-photo",
                      headers=user_headers,
                      files={"photo": ("grande.jpg", grande, "image/jpeg")},
                      timeout=35)
    assert r.status_code == 413, f"Esperado 413, obtuvo {r.status_code}"


# ── Integridad: validación de campos de texto ──────────────────────────────

@pytest.mark.con_auth
@pytest.mark.parametrize("campo,valor,descripcion", [
    ("dog_name",    "",                           "nombre vacío"),
    ("dog_name",    "X" * 101,                    "nombre >100 chars"),
    ("owner_phone", "TELEFONO_INVALIDO",           "teléfono con letras"),
    ("owner_email", "correo_sin_arroba",           "email sin @"),
    ("description", "D" * 501,                    "descripción >500 chars"),
])
def test_PI_IT5__integridad_campo_invalido_422(api_url, user_headers, campo, valor, descripcion):
    """Campos de texto inválidos en register-lost-dog → 422."""
    defaults = {
        "dog_name":    "TestDog",
        "owner_name":  "Dueño Test",
        "owner_phone": "3001234567",
        "owner_email": "dueño@test.com",
    }
    defaults[campo] = valor
    r = requests.post(
        f"{api_url}/api/v1/register-lost-dog",
        headers=user_headers,
        files={"photos": ("p.jpg", io.BytesIO(JPEG), "image/jpeg")},
        data=defaults,
        timeout=20,
    )
    assert r.status_code == 422, \
        f"Campo '{descripcion}' debería dar 422, obtuvo {r.status_code}: {r.text[:120]}"


# ── Disponibilidad: el servidor está levantado ─────────────────────────────

def test_PI_IT5__disponibilidad_health_200(api_url):
    """GET /health → 200 OK (servidor disponible)."""
    r = requests.get(f"{api_url}/health", timeout=10)
    assert r.status_code == 200, f"Backend no disponible, obtuvo {r.status_code}"
