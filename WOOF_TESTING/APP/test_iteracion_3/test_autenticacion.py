"""
PF-APP-IT3-001 — Módulo de Autenticación
Prueba el rechazo de tokens inválidos/ausentes en el backend (capa de seguridad
que protege todas las rutas que la app móvil consume).
"""
import io
import pytest
import requests

pytestmark = pytest.mark.sin_auth   # los TC de rechazo corren sin credenciales


ENDPOINTS_PROTEGIDOS = [
    ("POST", "/api/v1/validate-photo"),
    ("POST", "/api/v1/validate-lost-photo"),
    ("POST", "/api/v1/validate-found-photo"),
    ("POST", "/api/v1/register-lost-dog"),
    ("POST", "/api/v1/match-found-dog"),
    ("GET",  "/api/v1/my-found-reports"),
    ("GET",  "/api/v1/my-dog-matches"),
]

IMAGEN_DUMMY = b"\xff\xd8\xff\xe0" + b"\x00" * 100 + b"\xff\xd9"


def _llamar(api_url, method, path, headers=None):
    url = f"{api_url}{path}"
    files = {"photo": ("t.jpg", io.BytesIO(IMAGEN_DUMMY), "image/jpeg")} if method == "POST" else None
    params = {"dog_id": "no-existe"} if "my-dog-matches" in path else None
    r = requests.request(method, url, headers=headers or {}, files=files,
                         params=params, timeout=15)
    return r


# ── TC-001 · Sin token → 401 ───────────────────────────────────────────────
@pytest.mark.parametrize("method,path", ENDPOINTS_PROTEGIDOS)
def test_PF_APP_IT3_001__sin_token_retorna_401(api_url, method, path):
    """PF-APP-IT3-001 · TC-001: ningún endpoint responde sin Authorization."""
    r = _llamar(api_url, method, path)
    assert r.status_code == 401, (
        f"{method} {path} debería retornar 401 sin token, obtuvo {r.status_code}"
    )


# ── TC-002 · Token falso → 401 ─────────────────────────────────────────────
@pytest.mark.parametrize("method,path", ENDPOINTS_PROTEGIDOS)
def test_PF_APP_IT3_001__token_falso_retorna_401(api_url, method, path):
    """PF-APP-IT3-001 · TC-002: token Bearer inventado es rechazado con 401."""
    headers = {"Authorization": "Bearer token_completamente_falso_xyz123"}
    r = _llamar(api_url, method, path, headers=headers)
    assert r.status_code == 401, (
        f"{method} {path} debería retornar 401 con token falso, obtuvo {r.status_code}"
    )


# ── TC-003 · Login correcto devuelve idToken (Firebase REST) ──────────────
@pytest.mark.con_auth
def test_PF_APP_IT3_001__login_correcto_recibe_token(user_token):
    """PF-APP-IT3-001 · TC-003: credenciales válidas producen un ID token Firebase."""
    assert isinstance(user_token, str) and len(user_token) > 100, \
        "El token Firebase debería ser un JWT largo"


# ── TC-004 · Token válido → endpoint responde (no 401) ────────────────────
@pytest.mark.con_auth
def test_PF_APP_IT3_001__token_valido_pasa_autenticacion(api_url, user_headers):
    """PF-APP-IT3-001 · TC-004: token válido supera la capa de autenticación."""
    r = requests.get(f"{api_url}/api/v1/my-found-reports",
                     headers=user_headers, timeout=15)
    assert r.status_code != 401, \
        f"Token válido no debería retornar 401, obtuvo {r.status_code}"
