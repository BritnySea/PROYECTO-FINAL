"""
PF-APP-IT3-003 — Módulo Reportar Perro Encontrado
Prueba los endpoints /api/v1/validate-found-photo y /api/v1/match-found-dog.
"""
import io
import pytest
import requests

pytestmark = pytest.mark.sin_auth


# ═══════════════════════════════════════════════════════════════════
#  /api/v1/validate-found-photo
# ═══════════════════════════════════════════════════════════════════

def test_PF_APP_IT3_003__validate_found_sin_token_401(api_url, jpeg_minimo):
    """TC-001: validate-found-photo sin token → 401."""
    r = requests.post(f"{api_url}/api/v1/validate-found-photo",
                      files={"photo": ("p.jpg", io.BytesIO(jpeg_minimo), "image/jpeg")},
                      timeout=15)
    assert r.status_code == 401


@pytest.mark.con_auth
def test_PF_APP_IT3_003__validate_found_tipo_invalido_415(api_url, user_headers, archivo_txt):
    """TC-002: archivo .txt → 415."""
    r = requests.post(f"{api_url}/api/v1/validate-found-photo",
                      headers=user_headers,
                      files={"photo": ("doc.txt", io.BytesIO(archivo_txt), "text/plain")},
                      timeout=15)
    assert r.status_code == 415, f"Esperado 415, obtuvo {r.status_code}: {r.text[:120]}"


@pytest.mark.con_auth
def test_PF_APP_IT3_003__validate_found_archivo_grande_413(api_url, user_headers, jpeg_11mb):
    """TC-003: imagen > 10 MB → 413."""
    r = requests.post(f"{api_url}/api/v1/validate-found-photo",
                      headers=user_headers,
                      files={"photo": ("grande.jpg", io.BytesIO(jpeg_11mb), "image/jpeg")},
                      timeout=35)
    assert r.status_code == 413, f"Esperado 413, obtuvo {r.status_code}"


# ═══════════════════════════════════════════════════════════════════
#  /api/v1/match-found-dog
# ═══════════════════════════════════════════════════════════════════

def test_PF_APP_IT3_003__match_sin_token_401(api_url, jpeg_minimo):
    """TC-004: match-found-dog sin token → 401."""
    r = requests.post(f"{api_url}/api/v1/match-found-dog",
                      files={"photos": ("p.jpg", io.BytesIO(jpeg_minimo), "image/jpeg")},
                      timeout=15)
    assert r.status_code == 401


@pytest.mark.con_auth
def test_PF_APP_IT3_003__match_tipo_invalido_415(api_url, user_headers, archivo_txt):
    """TC-005: match con archivo .txt → 415."""
    r = requests.post(f"{api_url}/api/v1/match-found-dog",
                      headers=user_headers,
                      files={"photos": ("doc.txt", io.BytesIO(archivo_txt), "text/plain")},
                      timeout=15)
    assert r.status_code == 415, f"Esperado 415, obtuvo {r.status_code}: {r.text[:120]}"


@pytest.mark.con_auth
def test_PF_APP_IT3_003__match_archivo_grande_413(api_url, user_headers, jpeg_11mb):
    """TC-006: match con imagen > 10 MB → 413 (o 429 si rate limiter actúa primero)."""
    r = requests.post(f"{api_url}/api/v1/match-found-dog",
                      headers=user_headers,
                      files={"photos": ("grande.jpg", io.BytesIO(jpeg_11mb), "image/jpeg")},
                      timeout=35)
    assert r.status_code in (413, 429), f"Esperado 413 o 429, obtuvo {r.status_code}"


@pytest.mark.modelo
def test_PF_APP_IT3_003__match_con_foto_perro_retorna_200(api_url, user_headers, img_perro_bytes):
    """TC-007 (modelo): foto real de perro → 200 con lista de coincidencias."""
    if img_perro_bytes is None:
        pytest.skip("Configura TEST_IMG_PERRO en .env.test")
    r = requests.post(f"{api_url}/api/v1/match-found-dog",
                      headers=user_headers,
                      files={"photos": ("perro.jpg", io.BytesIO(img_perro_bytes), "image/jpeg")},
                      timeout=35)
    assert r.status_code == 200, f"Esperado 200, obtuvo {r.status_code}: {r.text[:150]}"
    body = r.json()
    assert "matches" in body, "La respuesta debe incluir el campo 'matches'"
    assert isinstance(body["matches"], list)


@pytest.mark.modelo
def test_PF_APP_IT3_003__match_con_no_perro_retorna_422(api_url, user_headers, img_nodog_bytes):
    """TC-008 (modelo): foto de no-perro → 422 (el modelo la rechaza)."""
    if img_nodog_bytes is None:
        pytest.skip("Configura TEST_IMG_NODOG en .env.test")
    r = requests.post(f"{api_url}/api/v1/match-found-dog",
                      headers=user_headers,
                      files={"photos": ("nodog.jpg", io.BytesIO(img_nodog_bytes), "image/jpeg")},
                      timeout=35)
    assert r.status_code == 422, f"Esperado 422, obtuvo {r.status_code}: {r.text[:150]}"
