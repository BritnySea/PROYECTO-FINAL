# WEB — Pruebas Automatizadas (Iteración 4)

Estas pruebas verifican el panel web administrativo WOOF usando la **Firestore REST API**
y la **Firebase Authentication REST API** directamente, sin necesidad de abrir un navegador.

---

## Cómo funciona la conexión

```
pytest → Firebase Auth REST API → obtiene idToken del admin
pytest → Firestore REST API (con idToken) → verifica permisos y datos

URL Firebase Auth:  https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword
URL Firestore REST: https://firestore.googleapis.com/v1/projects/{project_id}/databases/(default)/documents
```

Esto simula exactamente lo que hace el panel web: el panel usa el Firebase JS SDK que
internamente hace las mismas llamadas REST.

---

## Archivos y qué prueban

### `test_autenticacion_admin.py`
Verifica el control de acceso a nivel de autenticación Firebase.

| Test | Qué verifica | Necesita credenciales |
|------|-------------|----------------------|
| `login_admin_correcto` | Credenciales de admin → recibe idToken válido de Firebase | Sí (admin) |
| `login_clave_incorrecta_falla` | Contraseña incorrecta → Firebase retorna error (no 200) | Sí (admin email) |
| `login_email_inexistente_falla` | Email no registrado → Firebase retorna error | Solo API Key |
| `token_admin_acepta_backend` | Token de admin es aceptado por el backend WOOF (no 401) | Sí (admin) |

**Cómo funciona internamente:**
```python
# El test hace exactamente esto:
requests.post(
    "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=TU_API_KEY",
    json={"email": "admin@woof.com", "password": "...", "returnSecureToken": True}
)
# Verifica que la respuesta tenga "idToken"
```

---

### `test_gestion_usuarios.py`
Verifica que las reglas de seguridad de Firestore protejan correctamente la colección `users`.

| Test | Qué verifica | Necesita credenciales |
|------|-------------|----------------------|
| `admin_puede_listar_usuarios` | Token admin puede leer `users/` en Firestore | Sí (admin) |
| `usuario_regular_no_puede_listar_usuarios` | Token usuario normal → Firestore retorna 403 | Sí (usuario) |
| `admin_puede_leer_perros_perdidos` | Token admin puede leer `lost_dogs/` | Sí (admin) |
| `usuario_regular_no_puede_bloquear` | Usuario normal no puede modificar `isBlocked` de otro | Sí (usuario) |

**Por qué estos tests son importantes:**
Verifican que las **reglas de seguridad de Firestore** funcionan correctamente.
Si un test falla (por ejemplo el usuario regular SÍ puede leer usuarios), significa
que las reglas de Firestore tienen una brecha de seguridad.

---

### `test_supervision_reportes.py`
Verifica que el admin puede leer y que los usuarios no pueden borrar reportes.

| Test | Qué verifica | Necesita credenciales |
|------|-------------|----------------------|
| `admin_lee_lost_dogs` | Admin lee colección `lost_dogs` → 200 | Sí (admin) |
| `admin_lee_found_reports` | Admin lee colección `found_reports` → 200 | Sí (admin) |
| `lost_dogs_estructura_correcta` | Documentos tienen campos: name, owner_phone, owner_email, status | Sí (admin) |
| `usuario_regular_no_puede_eliminar_reportes` | DELETE en `lost_dogs` con token usuario → 403 | Sí (usuario) |

---

## Cómo correr solo estas pruebas

```bash
# Desde WOOF_TESTING/

# Todos los tests del panel web
pytest WEB/ -v

# Solo autenticación del panel
pytest WEB/test_iteracion_4/test_autenticacion_admin.py -v

# Solo tests que necesitan admin
pytest WEB/ -m admin -v

# Solo tests de seguridad del panel (sin credenciales)
pytest WEB/ -m sin_auth -v

# Ver qué tests hay sin correr
pytest WEB/ --collect-only
```

---

## Ejemplo de salida al correr

```
WEB/test_iteracion_4/test_autenticacion_admin.py::test_..._login_admin_correcto  PASSED
WEB/test_iteracion_4/test_autenticacion_admin.py::test_..._login_clave_incorrecta_falla  PASSED
WEB/test_iteracion_4/test_gestion_usuarios.py::test_..._admin_puede_listar_usuarios  PASSED
WEB/test_iteracion_4/test_gestion_usuarios.py::test_..._usuario_regular_no_puede_listar_usuarios  PASSED
```
