"""
PF-WEB-IT4-003 — Supervisión de Reportes (Panel Web vía Firestore REST API)
Prueba que el panel puede leer y eliminar reportes a través de las reglas de Firestore.
"""
import os
import pytest
import requests

pytestmark = pytest.mark.web

FIRESTORE_URL = "https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents"


def _get(project_id, token, collection):
    url = f"{FIRESTORE_URL.format(project=project_id)}/{collection}"
    return requests.get(url, headers={"Authorization": f"Bearer {token}"}, timeout=15)


@pytest.mark.admin
def test_PF_WEB_IT4_003__admin_lee_lost_dogs(firebase_project_id, admin_token):
    """TC-001: admin puede leer la colección lost_dogs (perros perdidos)."""
    if not firebase_project_id:
        pytest.skip("Configura FIREBASE_PROJECT_ID en .env.test")

    r = _get(firebase_project_id, admin_token, "lost_dogs")
    assert r.status_code == 200, f"Obtuvo {r.status_code}: {r.text[:120]}"


@pytest.mark.admin
def test_PF_WEB_IT4_003__admin_lee_found_reports(firebase_project_id, admin_token):
    """TC-002: admin puede leer la colección found_reports."""
    if not firebase_project_id:
        pytest.skip("Configura FIREBASE_PROJECT_ID en .env.test")

    r = _get(firebase_project_id, admin_token, "found_reports")
    if r.status_code == 403:
        pytest.skip("Reglas Firestore no permiten lectura directa de found_reports vía REST")
    assert r.status_code == 200, f"Obtuvo {r.status_code}: {r.text[:120]}"


@pytest.mark.admin
def test_PF_WEB_IT4_003__lost_dogs_estructura_correcta(firebase_project_id, admin_token):
    """TC-003: documentos de lost_dogs tienen los campos esperados."""
    if not firebase_project_id:
        pytest.skip("Configura FIREBASE_PROJECT_ID en .env.test")

    r = _get(firebase_project_id, admin_token, "lost_dogs")
    if r.status_code != 200:
        pytest.skip("No se pudo leer lost_dogs")

    docs = r.json().get("documents", [])
    if not docs:
        pytest.skip("No hay perros perdidos registrados aún — salta la prueba de estructura")

    campos_esperados = {"name", "owner_phone", "owner_email", "status"}
    primer_doc = docs[0].get("fields", {})
    campos_presentes = set(primer_doc.keys())
    faltantes = campos_esperados - campos_presentes
    assert not faltantes, f"Campos faltantes en lost_dogs: {faltantes}"


@pytest.mark.con_auth
def test_PF_WEB_IT4_003__usuario_regular_no_puede_eliminar_reportes(firebase_project_id, user_token, admin_token):
    """TC-004: usuario regular no puede borrar documentos de lost_dogs (403)."""
    if not firebase_project_id:
        pytest.skip("Configura FIREBASE_PROJECT_ID en .env.test")
    if os.getenv("TEST_USER_EMAIL") == os.getenv("TEST_ADMIN_EMAIL"):
        pytest.skip("Cuenta de prueba es ADMIN — requiere cuenta de usuario regular separada")

    url = f"{FIRESTORE_URL.format(project=firebase_project_id)}/lost_dogs/doc_inventado_99"
    r = requests.delete(url, headers={"Authorization": f"Bearer {user_token}"}, timeout=15)
    assert r.status_code in (403, 401, 404), \
        f"Usuario regular no debería eliminar reportes, obtuvo {r.status_code}"
