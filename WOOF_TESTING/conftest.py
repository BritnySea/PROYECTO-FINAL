"""
Configuración compartida para toda la suite de pruebas WOOF.
Lee variables desde .env.test y provee fixtures a todos los tests.
"""
import io
import os
import pytest
import requests
from pathlib import Path
from dotenv import load_dotenv

load_dotenv(Path(__file__).parent / ".env.test", override=True)

FIREBASE_SIGN_IN = "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword"
FIRESTORE_BASE   = "https://firestore.googleapis.com/v1/projects/{project}/databases/(default)/documents"


# ── Marcadores personalizados ──────────────────────────────────────────────
def pytest_configure(config):
    config.addinivalue_line("markers", "sin_auth: corre sin necesidad de token Firebase")
    config.addinivalue_line("markers", "con_auth: requiere token de usuario en .env.test")
    config.addinivalue_line("markers", "admin:    requiere token de admin en .env.test")
    config.addinivalue_line("markers", "modelo:   requiere imágenes de prueba + token")
    config.addinivalue_line("markers", "web:      prueba el panel web vía Firestore REST")


# ── Helpers internos ───────────────────────────────────────────────────────
def _firebase_login(api_key: str, email: str, password: str) -> str | None:
    if not all([api_key, email, password]):
        return None
    try:
        r = requests.post(
            f"{FIREBASE_SIGN_IN}?key={api_key}",
            json={"email": email, "password": password, "returnSecureToken": True},
            timeout=10,
        )
        return r.json().get("idToken") if r.status_code == 200 else None
    except Exception:
        return None


# ── Fixtures de URL y proyecto ─────────────────────────────────────────────
@pytest.fixture(scope="session")
def api_url() -> str:
    return os.getenv("WOOF_API_URL", "http://localhost:8000").rstrip("/")


@pytest.fixture(scope="session")
def firebase_api_key() -> str:
    return os.getenv("FIREBASE_API_KEY", "")


@pytest.fixture(scope="session")
def firebase_project_id() -> str:
    return os.getenv("FIREBASE_PROJECT_ID", "")


# ── Fixtures de tokens ─────────────────────────────────────────────────────
@pytest.fixture(scope="session")
def user_token(firebase_api_key) -> str:
    token = _firebase_login(
        firebase_api_key,
        os.getenv("TEST_USER_EMAIL", ""),
        os.getenv("TEST_USER_PASSWORD", ""),
    )
    if not token:
        pytest.skip("Token de usuario no disponible — configura .env.test")
    return token


@pytest.fixture(scope="session")
def admin_token(firebase_api_key) -> str:
    token = _firebase_login(
        firebase_api_key,
        os.getenv("TEST_ADMIN_EMAIL", ""),
        os.getenv("TEST_ADMIN_PASSWORD", ""),
    )
    if not token:
        pytest.skip("Token de admin no disponible — configura .env.test")
    return token


@pytest.fixture(scope="session")
def user_headers(user_token) -> dict:
    return {"Authorization": f"Bearer {user_token}"}


@pytest.fixture(scope="session")
def admin_headers(admin_token) -> dict:
    return {"Authorization": f"Bearer {admin_token}"}


# ── Fixtures de imágenes ───────────────────────────────────────────────────
@pytest.fixture(scope="session")
def img_perro_bytes() -> bytes | None:
    p = os.getenv("TEST_IMG_PERRO", "")
    return Path(p).read_bytes() if p and Path(p).exists() else None


@pytest.fixture(scope="session")
def img_nodog_bytes() -> bytes | None:
    p = os.getenv("TEST_IMG_NODOG", "")
    return Path(p).read_bytes() if p and Path(p).exists() else None


# ── Imágenes sintéticas para tests de validación ───────────────────────────
@pytest.fixture
def jpeg_minimo() -> bytes:
    """JPEG mínimo válido (~200 bytes, no es un perro real)."""
    return b"\xff\xd8\xff\xe0\x00\x10JFIF\x00\x01\x01\x00\x00\x01\x00\x01\x00\x00" + b"\x00" * 80 + b"\xff\xd9"


@pytest.fixture
def archivo_txt() -> bytes:
    return b"esto no es una imagen"


@pytest.fixture
def jpeg_11mb() -> bytes:
    """JPEG de 11 MB para probar el límite de 10 MB."""
    return b"\xff\xd8\xff\xe0" + b"\x00" * (11 * 1024 * 1024) + b"\xff\xd9"
