"""
PF-WEB-IT4-002 — Gestión de Usuarios (Panel Web vía Firestore REST API)
Prueba que las operaciones del panel (leer/actualizar usuarios) funcionen
correctamente a nivel de datos, usando la Firestore REST API con tokens Firebase.
"""
import os
import pytest
import requests

pytestmark = pytest.mark.web

FIRESTORE_URL = "https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents"


def _firestore_get(project_id, token, collection, doc_id=None):
    base = FIRESTORE_URL.format(project=project_id)
    url  = f"{base}/{collection}" if not doc_id else f"{base}/{collection}/{doc_id}"
    return requests.get(url, headers={"Authorization": f"Bearer {token}"}, timeout=15)


def _firestore_patch(project_id, token, collection, doc_id, fields: dict):
    base = FIRESTORE_URL.format(project=project_id)
    url  = f"{base}/{collection}/{doc_id}"
    body = {"fields": {k: {"booleanValue": v} if isinstance(v, bool)
                           else {"stringValue": v} for k, v in fields.items()}}
    mask = "&".join(f"updateMask.fieldPaths={k}" for k in fields)
    return requests.patch(f"{url}?{mask}",
                          headers={"Authorization": f"Bearer {token}",
                                   "Content-Type": "application/json"},
                          json=body, timeout=15)


@pytest.mark.admin
def test_PF_WEB_IT4_002__admin_puede_listar_usuarios(firebase_project_id, admin_token):
    """TC-001: token de admin puede leer la colección 'users' de Firestore."""
    if not firebase_project_id:
        pytest.skip("Configura FIREBASE_PROJECT_ID en .env.test")

    r = _firestore_get(firebase_project_id, admin_token, "users")
    assert r.status_code == 200, \
        f"Admin debería poder leer users, obtuvo {r.status_code}: {r.text[:150]}"
    body = r.json()
    assert "documents" in body or body == {}, \
        "Firestore debe retornar 'documents' o colección vacía"


@pytest.mark.admin
def test_PF_WEB_IT4_002__usuario_regular_no_puede_listar_usuarios(firebase_project_id, user_token, admin_token):
    """TC-002: token de usuario regular → las reglas Firestore lo bloquean (403)."""
    if not firebase_project_id:
        pytest.skip("Configura FIREBASE_PROJECT_ID en .env.test")
    if os.getenv("TEST_USER_EMAIL") == os.getenv("TEST_ADMIN_EMAIL"):
        pytest.skip("Cuenta de prueba es ADMIN — requiere cuenta de usuario regular separada")

    r = _firestore_get(firebase_project_id, user_token, "users")
    assert r.status_code in (403, 401), \
        f"Usuario regular no debería poder leer users, obtuvo {r.status_code}"


@pytest.mark.admin
def test_PF_WEB_IT4_002__admin_puede_leer_perros_perdidos(firebase_project_id, admin_token):
    """TC-003: admin puede leer la colección 'lost_dogs'."""
    if not firebase_project_id:
        pytest.skip("Configura FIREBASE_PROJECT_ID en .env.test")

    r = _firestore_get(firebase_project_id, admin_token, "lost_dogs")
    assert r.status_code == 200, f"Obtuvo {r.status_code}: {r.text[:150]}"


@pytest.mark.admin
def test_PF_WEB_IT4_002__usuario_regular_no_puede_bloquear(firebase_project_id, user_token):
    """TC-004: usuario regular no puede modificar isBlocked de otro usuario."""
    if not firebase_project_id:
        pytest.skip("Configura FIREBASE_PROJECT_ID en .env.test")

    r = _firestore_patch(firebase_project_id, user_token, "users", "uid_inventado_999",
                         {"isBlocked": True})
    assert r.status_code in (403, 401, 404), \
        f"Usuario regular no debería poder modificar isBlocked, obtuvo {r.status_code}"
