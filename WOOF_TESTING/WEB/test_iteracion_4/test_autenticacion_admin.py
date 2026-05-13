"""
PF-WEB-IT4-001 — Autenticación del Panel Admin
Prueba el control de acceso a nivel de autenticación Firebase
(lo que el panel verifica antes de dar acceso al dashboard).
"""
import os
import pytest
import requests

FIREBASE_SIGN_IN = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword"

pytestmark = pytest.mark.web


def _login(api_key, email, password):
    if not api_key:
        return None, None
    r = requests.post(
        f"{FIREBASE_SIGN_IN}?key={api_key}",
        json={"email": email, "password": password, "returnSecureToken": True},
        timeout=10,
    )
    return r.status_code, r.json()


def test_PF_WEB_IT4_001__login_admin_correcto(firebase_api_key):
    """TC-001: credenciales de admin → recibe idToken válido."""
    email = os.getenv("TEST_ADMIN_EMAIL", "")
    pwd   = os.getenv("TEST_ADMIN_PASSWORD", "")
    if not firebase_api_key or not email:
        pytest.skip("Configura FIREBASE_API_KEY, TEST_ADMIN_EMAIL y TEST_ADMIN_PASSWORD en .env.test")

    status, body = _login(firebase_api_key, email, pwd)
    assert status == 200, f"Login de admin falló: {body}"
    assert "idToken" in body, "La respuesta debe incluir idToken"
    assert len(body["idToken"]) > 100


def test_PF_WEB_IT4_001__login_clave_incorrecta_falla(firebase_api_key):
    """TC-002: contraseña incorrecta → Firebase retorna error (no 200)."""
    email = os.getenv("TEST_ADMIN_EMAIL", "")
    if not firebase_api_key or not email:
        pytest.skip("Configura FIREBASE_API_KEY y TEST_ADMIN_EMAIL en .env.test")

    status, body = _login(firebase_api_key, email, "ClaveIncorrecta_9999")
    assert status != 200, "Login con clave incorrecta NO debe retornar 200"
    assert "error" in body, "Firebase debe retornar objeto 'error'"


def test_PF_WEB_IT4_001__login_email_inexistente_falla(firebase_api_key):
    """TC-003: email no registrado → Firebase retorna error."""
    if not firebase_api_key:
        pytest.skip("Configura FIREBASE_API_KEY en .env.test")

    status, body = _login(firebase_api_key, "noexiste_9999@woof.test", "ClaveAny123")
    assert status != 200
    assert "error" in body


@pytest.mark.con_auth
@pytest.mark.admin
def test_PF_WEB_IT4_001__token_admin_acepta_backend(api_url, admin_headers):
    """TC-004: token de admin es aceptado por el backend sin 401."""
    r = requests.get(f"{api_url}/api/v1/my-found-reports",
                     headers=admin_headers, timeout=15)
    assert r.status_code != 401, \
        f"Token de admin no debe dar 401, obtuvo {r.status_code}"
