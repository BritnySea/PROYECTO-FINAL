"""
PF-APP-IT3-005 — Módulo Mis Reportes
Prueba GET /api/v1/my-found-reports y PATCH /api/v1/found-report-status.
"""
import pytest
import requests

pytestmark = pytest.mark.sin_auth


# ═══════════════════════════════════════════════════════════════════
#  GET /api/v1/my-found-reports
# ═══════════════════════════════════════════════════════════════════

def test_PF_APP_IT3_005__my_reports_sin_token_401(api_url):
    """TC-001: sin token → 401."""
    r = requests.get(f"{api_url}/api/v1/my-found-reports", timeout=15)
    assert r.status_code == 401


def test_PF_APP_IT3_005__my_reports_token_falso_401(api_url):
    """TC-002: token falso → 401."""
    r = requests.get(f"{api_url}/api/v1/my-found-reports",
                     headers={"Authorization": "Bearer token_falso"}, timeout=15)
    assert r.status_code == 401


@pytest.mark.con_auth
def test_PF_APP_IT3_005__my_reports_con_token_200(api_url, user_headers):
    """TC-003: token válido → 200 con lista de reportes (puede estar vacía)."""
    r = requests.get(f"{api_url}/api/v1/my-found-reports",
                     headers=user_headers, timeout=15)
    assert r.status_code == 200, f"Esperado 200, obtuvo {r.status_code}: {r.text[:120]}"
    body = r.json()
    assert "reports" in body, "La respuesta debe tener el campo 'reports'"
    assert isinstance(body["reports"], list)


# ═══════════════════════════════════════════════════════════════════
#  PATCH /api/v1/found-report-status
# ═══════════════════════════════════════════════════════════════════

def test_PF_APP_IT3_005__status_sin_token_401(api_url):
    """TC-004: PATCH status sin token → 401."""
    r = requests.patch(f"{api_url}/api/v1/found-report-status",
                       params={"report_id": "cualquier-id", "active": "true"},
                       timeout=15)
    assert r.status_code == 401


@pytest.mark.con_auth
def test_PF_APP_IT3_005__status_report_inexistente_404(api_url, user_headers):
    """TC-005: report_id que no existe → 404."""
    r = requests.patch(f"{api_url}/api/v1/found-report-status",
                       headers=user_headers,
                       params={"report_id": "reporte_que_no_existe_00000", "active": "true"},
                       timeout=15)
    assert r.status_code == 404, f"Esperado 404, obtuvo {r.status_code}: {r.text[:120]}"
