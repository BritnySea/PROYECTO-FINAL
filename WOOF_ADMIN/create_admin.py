#!/usr/bin/env python3
"""
Script de un solo uso para crear credenciales de administrador.

Ejecutar desde la raiz del proyecto:
    cd WOOF_BACKEND
    python ../WOOF_ADMIN/create_admin.py

Requisitos: tener firebase_credentials.json en WOOF_BACKEND/
"""
import sys
import os
import getpass
import re

# Ruta al directorio del backend para acceder a las credenciales de Firebase
BACKEND_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'WOOF_BACKEND')
CRED_PATH   = os.path.join(BACKEND_DIR, 'firebase_credentials.json')


def validate_email(email: str) -> bool:
    pattern = r'^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$'
    return bool(re.match(pattern, email))


def validate_password(password: str) -> list:
    errors = []
    if len(password) < 8:
        errors.append("Debe tener mínimo 8 caracteres")
    if not any(c.isupper() for c in password):
        errors.append("Debe contener al menos una mayúscula (A-Z)")
    if not any(c.islower() for c in password):
        errors.append("Debe contener al menos una minúscula (a-z)")
    if not any(c.isdigit() for c in password):
        errors.append("Debe contener al menos un número (0-9)")
    if not any(not c.isalnum() for c in password):
        errors.append("Debe contener al menos un símbolo (!@#$%...)")
    return errors


def main():
    print()
    print("=" * 55)
    print("  WOOF ADMIN — Crear credenciales de administrador")
    print("=" * 55)
    print()

    # Verificar que existen las credenciales de Firebase
    if not os.path.exists(CRED_PATH):
        print(f"ERROR: No se encontró el archivo de credenciales en:")
        print(f"   {CRED_PATH}")
        print()
        print("Asegúrate de tener 'firebase_credentials.json' en WOOF_BACKEND/")
        sys.exit(1)

    try:
        import firebase_admin
        from firebase_admin import credentials, auth, firestore
    except ImportError:
        print("ERROR: firebase-admin no está instalado.")
        print("Instálalo con: pip install firebase-admin")
        sys.exit(1)

    # Inicializar Firebase Admin SDK
    cred = credentials.Certificate(CRED_PATH)
    firebase_admin.initialize_app(cred)
    db = firestore.client()

    print("Conexion a Firebase establecida\n")

    # ── Pedir nombre ─────────────────────────────────────────
    name = input("Nombre del administrador: ").strip()
    if not name:
        print("ERROR: El nombre no puede estar vacío.")
        sys.exit(1)

    # ── Pedir email ──────────────────────────────────────────
    email = input("Correo electrónico:       ").strip()
    if not email:
        print("ERROR: El correo no puede estar vacío.")
        sys.exit(1)
    if not validate_email(email):
        print("ERROR: Formato de correo no válido.")
        sys.exit(1)

    # ── Pedir contraseña con validaciones ────────────────────
    print()
    print("La contraseña debe tener:")
    print("  • Mínimo 8 caracteres")
    print("  • Al menos una mayúscula (A-Z)")
    print("  • Al menos una minúscula (a-z)")
    print("  • Al menos un número (0-9)")
    print("  • Al menos un símbolo (!@#$%...)")
    print()

    while True:
        password = getpass.getpass("Contraseña:         ")
        errors = validate_password(password)
        if errors:
            print("Contraseña no válida:")
            for e in errors:
                print(f"  • {e}")
            print()
            continue

        confirm = getpass.getpass("Confirmar contraseña: ")
        if password != confirm:
            print("ERROR: Las contraseñas no coinciden.\n")
            continue
        break

    # ── Crear o actualizar usuario ───────────────────────────
    print("\nCreando administrador en Firebase...")

    try:
        existing = auth.get_user_by_email(email)
        print(f"Aviso: El correo ya existe en Firebase Auth (UID: {existing.uid})")
        choice = input("¿Actualizar su rol a ADMIN en Firestore? (s/N): ").strip().lower()
        if choice != 's':
            print("Operación cancelada.")
            sys.exit(0)
        uid = existing.uid
    except auth.UserNotFoundError:
        user_record = auth.create_user(
            email=email,
            password=password,
            display_name=name,
            email_verified=True,   # Admin no necesita verificar email manualmente
        )
        uid = user_record.uid
        print(f"Usuario creado en Firebase Auth (UID: {uid})")

    # ── Guardar en Firestore con role=ADMIN ──────────────────
    db.collection("users").document(uid).set(
        {
            "uid": uid,
            "name": name,
            "email": email,
            "role": "ADMIN",
            "isBlocked": False,
        },
        merge=True,
    )

    print(f"Documento guardado en Firestore con role='ADMIN'")
    print()
    print("=" * 55)
    print("  Administrador creado exitosamente")
    print(f"  Email:  {email}")
    print(f"  Nombre: {name}")
    print(f"  UID:    {uid}")
    print("=" * 55)
    print()
    print("Ahora puedes iniciar sesion en el panel admin con estas credenciales.")
    print()


if __name__ == "__main__":
    main()
