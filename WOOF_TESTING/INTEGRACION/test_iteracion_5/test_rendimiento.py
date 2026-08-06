"""
PI-IT5 — Pruebas de Rendimiento y Latencia
Mide tiempos de respuesta de los endpoints y verifica que estén
dentro de los umbrales aceptables para la experiencia del usuario.
"""
import io
import time
import statistics
import pytest
import requests

pytestmark = pytest.mark.sin_auth

JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 50 + b"\xff\xd9"
REINTENTOS = 3


def _medir(url, method="GET", **kwargs):
    t0 = time.perf_counter()
    r  = requests.request(method, url, timeout=35, **kwargs)
    return r, (time.perf_counter() - t0) * 1000


# ── /health ────────────────────────────────────────────────────────────────

def test_PI_IT5__rendimiento_health_bajo_3s(api_url):
    """GET /health responde en promedio < 3 000 ms en 3 intentos."""
    tiempos = []
    for _ in range(REINTENTOS):
        r, ms = _medir(f"{api_url}/health")
        assert r.status_code == 200, f"Health falló: {r.status_code}"
        tiempos.append(ms)

    promedio = statistics.mean(tiempos)
    print(f"\n  /health: {tiempos[0]:.0f} / {tiempos[1]:.0f} / {tiempos[2]:.0f} ms  "
          f"(promedio={promedio:.0f} ms)")
    assert promedio < 3_000, f"/health tardó {promedio:.0f} ms en promedio (límite: 3 000 ms)"


# ── /api/v1/validate-photo ────────────────────────────────────────────────

@pytest.mark.con_auth
def test_PI_IT5__rendimiento_validate_photo_bajo_15s(api_url, user_headers):
    """POST /validate-photo responde en promedio < 15 000 ms en 2 intentos."""
    tiempos = []
    for _ in range(2):
        r, ms = _medir(f"{api_url}/api/v1/validate-photo", method="POST",
                       headers=user_headers,
                       files={"photo": ("p.jpg", io.BytesIO(JPEG), "image/jpeg")})
        assert r.status_code in (200, 422), \
            f"Esperado 200 o 422, obtuvo {r.status_code}: {r.text[:80]}"
        tiempos.append(ms)

    promedio = statistics.mean(tiempos)
    print(f"\n  /validate-photo: {tiempos[0]:.0f} / {tiempos[1]:.0f} ms  "
          f"(promedio={promedio:.0f} ms)")
    assert promedio < 15_000, f"validate-photo tardó {promedio:.0f} ms (límite: 15 000 ms)"


@pytest.mark.con_auth
def test_PI_IT5__rendimiento_validate_photo_p95_bajo_20s(api_url, user_headers):
    """Percentil 95 de /validate-photo en 5 intentos < 20 000 ms."""
    tiempos = []
    for _ in range(5):
        r, ms = _medir(f"{api_url}/api/v1/validate-photo", method="POST",
                       headers=user_headers,
                       files={"photo": ("p.jpg", io.BytesIO(JPEG), "image/jpeg")})
        tiempos.append(ms)

    tiempos.sort()
    p95 = tiempos[int(len(tiempos) * 0.95)]
    print(f"\n  validate-photo tiempos (ms): {[f'{t:.0f}' for t in tiempos]}  p95={p95:.0f}")
    assert p95 < 20_000, f"P95 de validate-photo fue {p95:.0f} ms (límite: 20 000 ms)"


# ── my-found-reports (consulta liviana) ───────────────────────────────────

@pytest.mark.con_auth
def test_PI_IT5__rendimiento_my_reports_bajo_5s(api_url, user_headers):
    """GET /my-found-reports (consulta Firestore) responde < 5 000 ms."""
    tiempos = []
    for _ in range(REINTENTOS):
        r, ms = _medir(f"{api_url}/api/v1/my-found-reports", headers=user_headers)
        assert r.status_code == 200
        tiempos.append(ms)

    promedio = statistics.mean(tiempos)
    print(f"\n  /my-found-reports: {[f'{t:.0f}' for t in tiempos]} ms  "
          f"promedio={promedio:.0f}")
    assert promedio < 5_000, f"my-found-reports tardó {promedio:.0f} ms (límite: 5 000 ms)"
