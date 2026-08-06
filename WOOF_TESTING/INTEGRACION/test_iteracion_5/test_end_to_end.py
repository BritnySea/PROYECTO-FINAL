"""
PI-IT5-001 — Pruebas End-to-End
Flujo completo: registro de perro perdido → búsqueda de coincidencias.
Requiere token válido e imágenes de prueba reales.
"""
import io
import pytest
import requests

pytestmark = pytest.mark.modelo

JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 50 + b"\xff\xd9"


def test_PI_IT5_001__validate_foto_perro_is_dog_true(api_url, user_headers, img_perro_bytes):
    """Foto real de un perro → is_dog=True en validate-photo."""
    if img_perro_bytes is None:
        pytest.skip("Configura TEST_IMG_PERRO en .env.test")

    r = requests.post(f"{api_url}/api/v1/validate-photo",
                      headers=user_headers,
                      files={"photo": ("perro.jpg", io.BytesIO(img_perro_bytes), "image/jpeg")},
                      timeout=30)
    assert r.status_code == 200, f"Esperado 200, obtuvo {r.status_code}: {r.text[:150]}"

    body = r.json()
    assert body["is_dog"] is True, \
        f"El modelo no detectó al perro — confidence={body.get('confidence')}, msg={body.get('message')}"
    assert body["confidence"] > 0.5, \
        f"Confianza baja: {body['confidence']:.2%} — el modelo no está seguro"


def test_PI_IT5_001__validate_foto_nodog_is_dog_false(api_url, user_headers, img_nodog_bytes):
    """Foto de no-perro → is_dog=False en validate-photo."""
    if img_nodog_bytes is None:
        pytest.skip("Configura TEST_IMG_NODOG en .env.test")

    r = requests.post(f"{api_url}/api/v1/validate-photo",
                      headers=user_headers,
                      files={"photo": ("nodog.jpg", io.BytesIO(img_nodog_bytes), "image/jpeg")},
                      timeout=30)
    assert r.status_code == 200, f"Esperado 200, obtuvo {r.status_code}: {r.text[:150]}"

    body = r.json()
    assert body["is_dog"] is False, \
        f"El modelo identificó erróneamente un no-perro — confidence={body.get('confidence')}"


def test_PI_IT5_001__match_retorna_estructura_correcta(api_url, user_headers, img_perro_bytes):
    """match-found-dog retorna la estructura esperada {matches, is_dog, message}."""
    if img_perro_bytes is None:
        pytest.skip("Configura TEST_IMG_PERRO en .env.test")

    r = requests.post(f"{api_url}/api/v1/match-found-dog",
                      headers=user_headers,
                      files={"photos": ("perro.jpg", io.BytesIO(img_perro_bytes), "image/jpeg")},
                      timeout=35)
    assert r.status_code == 200, f"Esperado 200, obtuvo {r.status_code}: {r.text[:150]}"

    body = r.json()
    assert "matches"  in body
    assert "is_dog"   in body
    assert "message"  in body
    assert body["is_dog"] is True

    for match in body["matches"]:
        assert "dog_id"            in match, "Campo dog_id faltante en match"
        assert "similarity_percent" in match, "Campo similarity_percent faltante"
        assert match["similarity_percent"] >= 50.0, \
            f"Match retornado con similitud {match['similarity_percent']}% < umbral 50%"


def test_PI_IT5_001__matches_ordenados_por_similitud(api_url, user_headers, img_perro_bytes):
    """Las coincidencias retornadas están ordenadas de mayor a menor similitud."""
    if img_perro_bytes is None:
        pytest.skip("Configura TEST_IMG_PERRO en .env.test")

    r = requests.post(f"{api_url}/api/v1/match-found-dog",
                      headers=user_headers,
                      files={"photos": ("perro.jpg", io.BytesIO(img_perro_bytes), "image/jpeg")},
                      timeout=35)

    if r.status_code != 200:
        pytest.skip(f"match-found-dog retornó {r.status_code}")

    matches = r.json().get("matches", [])
    if len(matches) < 2:
        pytest.skip("Menos de 2 coincidencias para verificar orden")

    similitudes = [m["similarity_percent"] for m in matches]
    assert similitudes == sorted(similitudes, reverse=True), \
        f"Matches no están ordenados por similitud: {similitudes}"


def test_PI_IT5_001__validate_lost_retorna_is_dog_true(api_url, user_headers, img_perro_bytes):
    """validate-lost-photo con foto real de perro → is_dog=True."""
    if img_perro_bytes is None:
        pytest.skip("Configura TEST_IMG_PERRO en .env.test")

    r = requests.post(f"{api_url}/api/v1/validate-lost-photo",
                      headers=user_headers,
                      files={"photo": ("perro.jpg", io.BytesIO(img_perro_bytes), "image/jpeg")},
                      timeout=30)
    # Puede ser 200 (válido) o 409 (duplicado ya existe)
    assert r.status_code in (200, 409), f"Obtuvo {r.status_code}: {r.text[:150]}"

    if r.status_code == 200:
        body = r.json()
        assert body["is_dog"] is True
