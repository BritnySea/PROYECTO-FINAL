import requests
import json

BASE = "http://127.0.0.1:8000/api/v1"
IMG_PERRO = r"E:\PROYECTO\PROYECTO_APP\app\src\main\res\drawable\perrito.png"
IMG_REF   = r"E:\PROYECTO\PROYECTO_APP\app\src\main\res\drawable\dog_photo_reference.png"
IMG_LOGO  = r"E:\PROYECTO\PROYECTO_APP\app\src\main\res\drawable\logo_refugio.png"

# Obtener token
login = requests.post(
    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=AIzaSyDzXuDuMKoLnl9c79yBtKk9QU1S6rLLrw0",
    json={"email": "britny2003sea@gmail.com", "password": "Sea151103.", "returnSecureToken": True}
)
token = login.json()["idToken"]
headers = {"Authorization": f"Bearer {token}"}
print("TOKEN OK:", token[:50], "...\n")

# =====================================================
# PRUEBA 1: validate-photo con imagen de perro
# =====================================================
print("=" * 55)
print("PRUEBA 1: POST /validate-photo (foto de perro)")
print("=" * 55)
with open(IMG_PERRO, "rb") as f:
    r = requests.post(f"{BASE}/validate-photo", headers=headers,
                      files={"photo": ("perrito.png", f, "image/png")})
print("STATUS:", r.status_code)
print(json.dumps(r.json(), indent=2, ensure_ascii=False))
print()

# =====================================================
# PRUEBA 2: register-lost-dog con 2 fotos
# =====================================================
print("=" * 55)
print("PRUEBA 2: POST /register-lost-dog (2 fotos)")
print("=" * 55)
with open(IMG_PERRO, "rb") as f1, open(IMG_REF, "rb") as f2:
    r = requests.post(f"{BASE}/register-lost-dog", headers=headers,
        data={
            "dog_name": "Rex",
            "owner_name": "Britny Sea",
            "owner_phone": "+591 12345678",
            "owner_email": "britny2003sea@gmail.com",
            "size": "grande",
            "color": "marron",
            "breed": "Labrador",
            "sex": "macho",
            "description": "Perro de prueba para documentacion"
        },
        files=[
            ("photos", ("perrito.png", f1, "image/png")),
            ("photos", ("dog_photo_reference.png", f2, "image/png"))
        ]
    )
print("STATUS:", r.status_code)
print(json.dumps(r.json(), indent=2, ensure_ascii=False))
print()

# =====================================================
# PRUEBA 3: validate-photo SIN TOKEN (debe dar 401)
# =====================================================
print("=" * 55)
print("PRUEBA 3: POST /validate-photo SIN TOKEN (401)")
print("=" * 55)
with open(IMG_PERRO, "rb") as f:
    r = requests.post(f"{BASE}/validate-photo",
                      files={"photo": ("perrito.png", f, "image/png")})
print("STATUS:", r.status_code)
print(json.dumps(r.json(), indent=2, ensure_ascii=False))
print()

# =====================================================
# PRUEBA 4: validate-photo con imagen NO es perro
# =====================================================
print("=" * 55)
print("PRUEBA 4: POST /validate-photo imagen NO es perro")
print("=" * 55)
with open(IMG_LOGO, "rb") as f:
    r = requests.post(f"{BASE}/validate-photo", headers=headers,
                      files={"photo": ("logo_refugio.png", f, "image/png")})
print("STATUS:", r.status_code)
print(json.dumps(r.json(), indent=2, ensure_ascii=False))
print()

# =====================================================
# PRUEBA 5: match-found-dog proteccion perro propio (409)
# =====================================================
print("=" * 55)
print("PRUEBA 5: POST /match-found-dog proteccion 409")
print("=" * 55)
with open(IMG_PERRO, "rb") as f:
    r = requests.post(f"{BASE}/match-found-dog", headers=headers,
        data={
            "reporter_name": "Maria Lopez",
            "reporter_phone": "+591 87654321",
            "reporter_email": "maria@test.com"
        },
        files=[("photos", ("perrito.png", f, "image/png"))]
    )
print("STATUS:", r.status_code)
print(json.dumps(r.json(), indent=2, ensure_ascii=False))
print()

# =====================================================
# PRUEBA 6: GET /health
# =====================================================
print("=" * 55)
print("PRUEBA 6: GET /health (estado del servidor)")
print("=" * 55)
r = requests.get("http://127.0.0.1:8000/health")
print("STATUS:", r.status_code)
print(json.dumps(r.json(), indent=2, ensure_ascii=False))
print()

print("FIN DE PRUEBAS")
