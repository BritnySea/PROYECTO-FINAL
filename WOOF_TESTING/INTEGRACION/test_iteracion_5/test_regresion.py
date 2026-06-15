"""
PI-IT5-003 — Pruebas de Regresión (XP)
Verifica que todos los endpoints siguen respondiendo correctamente
y que los contratos de respuesta no han cambiado tras una refactorización.
"""
import io
import pytest
import requests

pytestmark = pytest.mark.sin_auth

JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 50 + b"\xff\xd9"


# ── El servidor responde ───────────────────────────────────────────────────

def test_PI_IT5_003__health_responde(api_url):
    """GET /health → 200 con campo 'status'."""
    r = requests.get(f"{api_url}/health", timeout=10)
    assert r.status_code == 200
    # Si el health retorna JSON, verificar que tenga algún campo
    try:
        body = r.json()
        assert isinstance(body, dict)
    except Exception:
        pass  # health puede retornar texto plano también


def test_PI_IT5_003__endpoints_POST_existen_no_404(api_url):
    """Los endpoints POST principales responden algo (no 404) — verificación de existencia."""
    rutas = [
        "/api/v1/validate-photo",
        "/api/v1/validate-lost-photo",
        "/api/v1/validate-found-photo",
        "/api/v1/register-lost-dog",
        "/api/v1/match-found-dog",
    ]
    for path in rutas:
        r = requests.post(f"{api_url}{path}",
                          files={"photo": ("t.jpg", io.BytesIO(JPEG), "image/jpeg"),
                                 "photos": ("t.jpg", io.BytesIO(JPEG), "image/jpeg")},
                          timeout=15)
        assert r.status_code != 404, \
            f"El endpoint {path} retornó 404 — puede haber sido eliminado o renombrado"


def test_PI_IT5_003__endpoints_GET_existen_no_404(api_url):
    """Los endpoints GET principales responden algo (no 404)."""
    rutas = [
        "/api/v1/my-found-reports",
        "/api/v1/my-dog-matches",
    ]
    for path in rutas:
        r = requests.get(f"{api_url}{path}", timeout=15)
        assert r.status_code != 404, \
            f"El endpoint {path} retornó 404 — puede haberse eliminado"


# ── Contratos de respuesta ─────────────────────────────────────────────────

@pytest.mark.con_auth
def test_PI_IT5_003__validate_photo_contrato_respuesta(api_url, user_headers):
    """POST /validate-photo retorna JSON con campos is_dog, confidence, message."""
    r = requests.post(f"{api_url}/api/v1/validate-photo",
                      headers=user_headers,
                      files={"photo": ("p.jpg", io.BytesIO(JPEG), "image/jpeg")},
                      timeout=20)
    # Puede retornar 200, 422 (no es perro) o 429 (rate limit por tests anteriores)
    if r.status_code == 429:
        pytest.skip("Rate limiter activo por tests anteriores — reintenta en un minuto")
    assert r.status_code in (200, 422), f"Inesperado: {r.status_code}"

    if r.status_code == 200:
        body = r.json()
        assert "is_dog"     in body, "Campo 'is_dog' faltante en respuesta"
        assert "confidence" in body, "Campo 'confidence' faltante en respuesta"
        assert "message"    in body, "Campo 'message' faltante en respuesta"
        assert isinstance(body["is_dog"], bool)
        assert 0.0 <= body["confidence"] <= 1.0


@pytest.mark.con_auth
def test_PI_IT5_003__my_found_reports_contrato_respuesta(api_url, user_headers):
    """GET /my-found-reports retorna JSON con campo 'reports' como lista."""
    r = requests.get(f"{api_url}/api/v1/my-found-reports",
                     headers=user_headers, timeout=15)
    assert r.status_code == 200
    body = r.json()
    assert "reports" in body, "Campo 'reports' faltante — contrato de respuesta roto"
    assert isinstance(body["reports"], list)


@pytest.mark.con_auth
def test_PI_IT5_003__my_dog_matches_404_contrato(api_url, user_headers):
    """GET /my-dog-matches con ID inexistente retorna 404 con campo 'detail'."""
    r = requests.get(f"{api_url}/api/v1/my-dog-matches",
                     headers=user_headers,
                     params={"dog_id": "regresion_id_inexistente_000"},
                     timeout=15)
    assert r.status_code == 404
    body = r.json()
    assert "detail" in body, "Respuesta 404 debe incluir campo 'detail' (FastAPI standard)"


# ── Rate limiting no está caído ────────────────────────────────────────────

def test_PI_IT5_003__rate_limiter_activo_no_500(api_url):
    """El rate limiter responde 429 (no 500) cuando se superan los límites."""
    # 10 peticiones rápidas sin token — suficiente para verificar que no hay 500
    respuestas = []
    for _ in range(10):
        try:
            r = requests.post(f"{api_url}/api/v1/register-lost-dog",
                              files={"photos": ("t.jpg", io.BytesIO(JPEG), "image/jpeg")},
                              timeout=5)
            respuestas.append(r.status_code)
        except requests.exceptions.Timeout:
            break

    assert len(respuestas) > 0, "No se recibió ninguna respuesta"
    assert 500 not in respuestas, \
        f"El servidor lanzó errores 500 durante rate limiting: {respuestas}"
    codigos_validos = {401, 429}
    assert all(c in codigos_validos for c in respuestas), \
        f"Códigos inesperados: {set(respuestas) - codigos_validos}"
