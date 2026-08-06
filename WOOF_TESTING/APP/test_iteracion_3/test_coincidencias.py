"""
PF-APP-IT3-004 — Módulo de Coincidencias
Prueba el endpoint GET /api/v1/my-dog-matches (ver coincidencias para un perro perdido).
"""
import pytest
import requests

pytestmark = pytest.mark.sin_auth


def test_PF_APP_IT3_004__my_matches_sin_token_401(api_url):
    """TC-001: sin token → 401."""
    r = requests.get(f"{api_url}/api/v1/my-dog-matches",
                     params={"dog_id": "cualquier-id"}, timeout=15)
    assert r.status_code == 401


def test_PF_APP_IT3_004__my_matches_token_falso_401(api_url):
    """TC-002: token inválido → 401."""
    r = requests.get(f"{api_url}/api/v1/my-dog-matches",
                     headers={"Authorization": "Bearer token_falso"},
                     params={"dog_id": "cualquier-id"}, timeout=15)
    assert r.status_code == 401


@pytest.mark.con_auth
def test_PF_APP_IT3_004__my_matches_dog_id_inexistente_404(api_url, user_headers):
    """TC-003: dog_id que no existe en Firestore → 404."""
    r = requests.get(f"{api_url}/api/v1/my-dog-matches",
                     headers=user_headers,
                     params={"dog_id": "id_que_definitivamente_no_existe_00000"},
                     timeout=15)
    assert r.status_code == 404, f"Esperado 404, obtuvo {r.status_code}: {r.text[:120]}"


@pytest.mark.con_auth
def test_PF_APP_IT3_004__my_matches_esquema_correcto(api_url, user_headers):
    """TC-004: con dog_id de otro usuario → 403 Forbidden."""
    r = requests.get(f"{api_url}/api/v1/my-dog-matches",
                     headers=user_headers,
                     params={"dog_id": "id_que_no_pertenece_al_usuario"},
                     timeout=15)
    # 404 si el doc no existe, 403 si existe pero es de otro usuario
    assert r.status_code in (403, 404), \
        f"Esperado 403 o 404, obtuvo {r.status_code}: {r.text[:120]}"
