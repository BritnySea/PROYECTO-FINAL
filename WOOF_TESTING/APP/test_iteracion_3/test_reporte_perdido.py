"""
PF-APP-IT3-002 — Módulo Reportar Perro Perdido
Prueba los endpoints /api/v1/validate-lost-photo y /api/v1/register-lost-dog.
"""
import io
import pytest
import requests

pytestmark = pytest.mark.sin_auth


# ═══════════════════════════════════════════════════════════════════
#  /api/v1/validate-lost-photo
# ═══════════════════════════════════════════════════════════════════

def test_PF_APP_IT3_002__validate_lost_sin_token_401(api_url, jpeg_minimo):
    """TC-001: validate-lost-photo sin token → 401."""
    r = requests.post(f"{api_url}/api/v1/validate-lost-photo",
                      files={"photo": ("p.jpg", io.BytesIO(jpeg_minimo), "image/jpeg")},
                      timeout=15)
    assert r.status_code == 401


def test_PF_APP_IT3_002__validate_lost_tipo_invalido_415(api_url, user_headers, archivo_txt):
    """TC-002: archivo .txt → 415 Unsupported Media Type."""
    pytest.importorskip("requests")  # siempre disponible
    r = requests.post(f"{api_url}/api/v1/validate-lost-photo",
                      headers=user_headers,
                      files={"photo": ("doc.txt", io.BytesIO(archivo_txt), "text/plain")},
                      timeout=15)
    assert r.status_code == 415, f"Esperado 415, obtuvo {r.status_code}: {r.text[:120]}"


test_PF_APP_IT3_002__validate_lost_tipo_invalido_415 = pytest.mark.con_auth(
    test_PF_APP_IT3_002__validate_lost_tipo_invalido_415
)


def test_PF_APP_IT3_002__validate_lost_archivo_grande_413(api_url, user_headers, jpeg_11mb):
    """TC-003: imagen > 10 MB → 413 Request Entity Too Large."""
    r = requests.post(f"{api_url}/api/v1/validate-lost-photo",
                      headers=user_headers,
                      files={"photo": ("grande.jpg", io.BytesIO(jpeg_11mb), "image/jpeg")},
                      timeout=35)
    assert r.status_code == 413, f"Esperado 413, obtuvo {r.status_code}"


test_PF_APP_IT3_002__validate_lost_archivo_grande_413 = pytest.mark.con_auth(
    test_PF_APP_IT3_002__validate_lost_archivo_grande_413
)


# ═══════════════════════════════════════════════════════════════════
#  /api/v1/register-lost-dog — validación de campos de texto
# ═══════════════════════════════════════════════════════════════════

def _register(api_url, headers, foto, **overrides):
    defaults = {
        "dog_name":    "TestDog",
        "owner_name":  "Dueño Test",
        "owner_phone": "3001234567",
        "owner_email": "dueño@test.com",
    }
    data = {**defaults, **overrides}
    return requests.post(
        f"{api_url}/api/v1/register-lost-dog",
        headers=headers,
        files={"photos": ("perro.jpg", io.BytesIO(foto), "image/jpeg")},
        data=data,
        timeout=20,
    )


def test_PF_APP_IT3_002__register_sin_token_401(api_url, jpeg_minimo):
    """TC-004: register-lost-dog sin token → 401."""
    r = _register(api_url, {}, jpeg_minimo)
    assert r.status_code == 401


@pytest.mark.con_auth
def test_PF_APP_IT3_002__register_sin_nombre_422(api_url, user_headers, jpeg_minimo):
    """TC-005: dog_name vacío → 422 Unprocessable Entity."""
    r = _register(api_url, user_headers, jpeg_minimo, dog_name="")
    assert r.status_code == 422, f"Esperado 422, obtuvo {r.status_code}: {r.text[:120]}"


@pytest.mark.con_auth
def test_PF_APP_IT3_002__register_telefono_invalido_422(api_url, user_headers, jpeg_minimo):
    """TC-006: teléfono con letras → 422."""
    r = _register(api_url, user_headers, jpeg_minimo, owner_phone="ABCD-NO-PHONE")
    assert r.status_code == 422, f"Esperado 422, obtuvo {r.status_code}: {r.text[:120]}"


@pytest.mark.con_auth
def test_PF_APP_IT3_002__register_email_invalido_422(api_url, user_headers, jpeg_minimo):
    """TC-007: email sin @dominio → 422."""
    r = _register(api_url, user_headers, jpeg_minimo, owner_email="correo_invalido")
    assert r.status_code == 422, f"Esperado 422, obtuvo {r.status_code}: {r.text[:120]}"


@pytest.mark.con_auth
def test_PF_APP_IT3_002__register_nombre_muy_largo_422(api_url, user_headers, jpeg_minimo):
    """TC-008: nombre de perro con 101 caracteres → 422 (límite=100)."""
    nombre_largo = "X" * 101
    r = _register(api_url, user_headers, jpeg_minimo, dog_name=nombre_largo)
    assert r.status_code == 422, f"Esperado 422, obtuvo {r.status_code}: {r.text[:120]}"


@pytest.mark.con_auth
def test_PF_APP_IT3_002__register_descripcion_larga_422(api_url, user_headers, jpeg_minimo):
    """TC-009: descripción con 501 caracteres → 422 (límite=500)."""
    r = _register(api_url, user_headers, jpeg_minimo, description="D" * 501)
    assert r.status_code == 422, f"Esperado 422, obtuvo {r.status_code}: {r.text[:120]}"


@pytest.mark.con_auth
def test_PF_APP_IT3_002__register_foto_tipo_invalido_415(api_url, user_headers, archivo_txt):
    """TC-010: foto con MIME type text/plain → 415."""
    r = requests.post(
        f"{api_url}/api/v1/register-lost-dog",
        headers=user_headers,
        files={"photos": ("doc.txt", io.BytesIO(archivo_txt), "text/plain")},
        data={"dog_name": "Test", "owner_name": "Test", "owner_phone": "3001234567", "owner_email": "t@t.com"},
        timeout=20,
    )
    assert r.status_code == 415, f"Esperado 415, obtuvo {r.status_code}"
