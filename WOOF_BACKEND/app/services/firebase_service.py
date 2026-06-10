import json
import os
import tempfile
import uuid
from datetime import datetime, timedelta, timezone

import cloudinary
import cloudinary.uploader
import firebase_admin
from firebase_admin import credentials, firestore, messaging
from google.cloud.firestore_v1 import transactional

from app.config import get_settings

_db = None


def initialize_firebase() -> None:
    global _db

    settings = get_settings()

    if not firebase_admin._apps:
        if settings.firebase_credentials_json:
            # Railway: credenciales como JSON string en variable de entorno
            creds_dict = json.loads(settings.firebase_credentials_json)
            tmp = tempfile.NamedTemporaryFile(mode="w", suffix=".json", delete=False)
            json.dump(creds_dict, tmp)
            tmp.close()
            cred = credentials.Certificate(tmp.name)
        else:
            cred = credentials.Certificate(settings.firebase_credentials_path)
        firebase_admin.initialize_app(cred)

    _db = firestore.client()

    cloudinary.config(
        cloud_name=settings.cloudinary_cloud_name,
        api_key=settings.cloudinary_api_key,
        api_secret=settings.cloudinary_api_secret,
    )


def upload_photo(image_bytes: bytes, filename: str | None = None, folder: str = "dog_photos") -> tuple[str, str]:
    """Sube una foto a Cloudinary. Devuelve (secure_url, public_id)."""
    public_id = filename.replace(".jpg", "") if filename else uuid.uuid4().hex
    full_public_id = f"{folder}/{public_id}"

    result = cloudinary.uploader.upload(
        image_bytes,
        public_id=full_public_id,
        resource_type="image",
        allowed_formats=["jpg", "jpeg", "png", "webp"],
    )
    return result["secure_url"], full_public_id


def cleanup_cloudinary_photos(public_ids: list[str]) -> None:
    """Elimina fotos de Cloudinary por su public_id. Silencia errores para no enmascarar el error original."""
    for pid in public_ids:
        try:
            cloudinary.uploader.destroy(pid)
        except Exception:
            pass


def save_lost_dog(dog_data: dict) -> str:
    if _db is None:
        raise RuntimeError("Firebase no inicializado. Llama initialize_firebase() primero.")

    doc_ref = _db.collection("lost_dogs").document()
    dog_data["created_at"] = datetime.now(timezone.utc)
    dog_data["status"] = "active"
    doc_ref.set(dog_data)
    return doc_ref.id


def get_active_found_reports() -> list[dict]:
    """Devuelve reportes de perros encontrados activos que tengan embedding guardado.
    Documentos sin campo 'status' se consideran activos (reportes anteriores a la migración).
    Soporta tanto formato antiguo (embedding único) como nuevo (embeddings lista).
    """
    if _db is None:
        raise RuntimeError("Firebase no inicializado. Llama initialize_firebase() primero.")

    docs = _db.collection("found_dog_reports").stream()

    results = []
    for doc in docs:
        data = doc.to_dict()
        if data.get("status", "active") != "active":
            continue
        has_embedding = bool(data.get("embedding")) or bool(data.get("embedding_1"))
        if has_embedding:
            data["doc_id"] = doc.id
            results.append(data)

    return results


def get_active_lost_dogs() -> list[dict]:
    if _db is None:
        raise RuntimeError("Firebase no inicializado. Llama initialize_firebase() primero.")

    docs = _db.collection("lost_dogs").stream()

    results = []
    for doc in docs:
        data = doc.to_dict()
        # Sin campo status → reporte antiguo → se trata como activo
        if data.get("status", "active") != "active":
            continue
        data["doc_id"] = doc.id
        results.append(data)

    return results


def get_user_role(uid: str) -> str:
    """Devuelve el rol del usuario ('USER' o 'ADMIN'). Por defecto 'USER'."""
    if _db is None:
        return "USER"
    doc = _db.collection("users").document(uid).get()
    if doc.exists:
        return doc.to_dict().get("role", "USER")
    return "USER"


FOUND_REPORT_COOLDOWN_SECONDS = 30


def atomic_weekly_limit_check(uid: str, limit: int) -> bool:
    """Lee e incrementa el contador semanal de forma atómica. Devuelve True si se permite, False si el límite fue alcanzado."""
    if _db is None:
        return True
    now = datetime.now(timezone.utc)
    iso = now.isocalendar()
    week_key = f"{iso.year}-W{iso.week:02d}"
    user_ref = _db.collection("users").document(uid)
    txn = _db.transaction()

    @transactional
    def _run(txn, user_ref):
        snapshot = user_ref.get(transaction=txn)
        data = snapshot.to_dict() if snapshot.exists else {}
        stored_week = data.get("weekly_reports_week", "")
        count = data.get("weekly_reports_count", 0) if stored_week == week_key else 0
        if count >= limit:
            return False
        txn.update(user_ref, {"weekly_reports_count": count + 1, "weekly_reports_week": week_key})
        return True

    return _run(txn, user_ref)


def check_and_update_found_cooldown(uid: str) -> bool:
    """Verifica cooldown de 30s entre reportes de perros encontrados del mismo usuario. Devuelve True si puede reportar."""
    if _db is None:
        return True
    now = datetime.now(timezone.utc)
    user_ref = _db.collection("users").document(uid)
    txn = _db.transaction()

    @transactional
    def _run(txn, user_ref):
        snapshot = user_ref.get(transaction=txn)
        data = snapshot.to_dict() if snapshot.exists else {}
        last_report = data.get("last_found_report_at")
        if last_report and (now - last_report).total_seconds() < FOUND_REPORT_COOLDOWN_SECONDS:
            return False
        txn.update(user_ref, {"last_found_report_at": now})
        return True

    return _run(txn, user_ref)


def is_user_blocked(uid: str) -> bool:
    """Devuelve True si el usuario tiene isBlocked=True en Firestore."""
    if _db is None:
        return False
    doc = _db.collection("users").document(uid).get()
    if doc.exists:
        return bool(doc.to_dict().get("isBlocked", False))
    return False


def count_weekly_reports(uid: str) -> int:
    """Cuenta solo los reportes de perros PERDIDOS que el usuario hizo en la semana actual (lunes–domingo UTC).
    Los reportes de perros encontrados no tienen límite semanal."""
    if _db is None:
        return 0
    now = datetime.now(timezone.utc)
    week_start = (now - timedelta(days=now.weekday())).replace(
        hour=0, minute=0, second=0, microsecond=0
    )

    lost_docs = _db.collection("lost_dogs").where("registered_by_uid", "==", uid).stream()
    return sum(
        1 for doc in lost_docs
        if (ts := doc.to_dict().get("created_at")) and ts >= week_start
    )


def get_user_fcm_token(uid: str) -> str | None:
    if _db is None:
        return None
    doc = _db.collection("users").document(uid).get()
    if doc.exists:
        return doc.to_dict().get("fcmToken")
    return None


def send_match_notification(owner_uid: str, dog_name: str, similarity_percent: float) -> None:
    token = get_user_fcm_token(owner_uid)
    if not token:
        return

    message = messaging.Message(
        notification=messaging.Notification(
            title="¡Posible coincidencia encontrada!",
            body=f"Se encontro un perro que podria ser '{dog_name}' con {similarity_percent:.0f}% de similitud. Entra a verificar.",
        ),
        android=messaging.AndroidConfig(
            priority="high",
            notification=messaging.AndroidNotification(channel_id="woof_matches"),
        ),
        token=token,
    )

    try:
        messaging.send(message)
    except Exception:
        pass


def send_match_notification_to_finder(finder_uid: str, similarity_percent: float) -> None:
    token = get_user_fcm_token(finder_uid)
    if not token:
        return

    message = messaging.Message(
        notification=messaging.Notification(
            title="¡Posible coincidencia encontrada!",
            body=f"Un perro extraviado podria coincidir con el perro que reportaste como encontrado! Similitud: {similarity_percent:.0f}%",
        ),
        android=messaging.AndroidConfig(
            priority="high",
            notification=messaging.AndroidNotification(channel_id="woof_matches"),
        ),
        token=token,
    )

    try:
        messaging.send(message)
    except Exception:
        pass


def get_matches_for_dog(dog_id: str, top_k: int = 5) -> list[dict]:
    if _db is None:
        raise RuntimeError("Firebase no inicializado.")

    docs = (
        _db.collection("found_dog_reports")
        .where("matched_dog_ids", "array_contains", dog_id)
        .stream()
    )

    results = []
    for doc in docs:
        data = doc.to_dict()
        similarity = 0.0
        for m in data.get("matches", []):
            if m.get("dog_id") == dog_id:
                similarity = m.get("similarity_percent", 0.0)
                break

        created_at = data.get("created_at")
        reported_at = created_at.strftime("%Y-%m-%d %H:%M") if created_at else ""

        found_photo_urls = []
        for i in range(1, 4):
            url = data.get(f"found_dog_photo_url_{i}")
            if url:
                found_photo_urls.append(url)
        if not found_photo_urls:
            fallback = data.get("found_dog_photo_url", "")
            if fallback:
                found_photo_urls.append(fallback)

        results.append({
            "report_id": doc.id,
            "found_dog_photo_url": found_photo_urls[0] if found_photo_urls else "",
            "found_dog_photo_urls": found_photo_urls,
            "similarity_percent": similarity,
            "reporter_name": data.get("reporter_name", ""),
            "reporter_phone": data.get("reporter_phone", ""),
            "reporter_email": data.get("reporter_email", ""),
            "found_dog_size": data.get("size", ""),
            "found_dog_color": data.get("color", ""),
            "found_dog_sex": data.get("sex", ""),
            "found_dog_description": data.get("description", ""),
            "reported_at": reported_at,
        })

    results.sort(key=lambda x: x["similarity_percent"], reverse=True)
    return results[:top_k]


def get_my_found_reports(uid: str) -> list[dict]:
    if _db is None:
        raise RuntimeError("Firebase no inicializado.")

    docs = (
        _db.collection("found_dog_reports")
        .where("found_by_uid", "==", uid)
        .stream()
    )

    results = []
    for doc in docs:
        data = doc.to_dict()
        created_at = data.get("created_at")
        created_at_str = created_at.strftime("%Y-%m-%dT%H:%M:%S") if created_at else ""
        photo_urls = []
        for i in range(1, 4):
            url = data.get(f"found_dog_photo_url_{i}")
            if url:
                photo_urls.append(url)
        if not photo_urls:
            url = data.get("found_dog_photo_url", "")
            if url:
                photo_urls.append(url)

        results.append({
            "report_id": doc.id,
            "photo_url": photo_urls[0] if photo_urls else "",
            "photo_urls": photo_urls,
            "status": data.get("status", "active"),
            "created_at": created_at_str,
            "size": data.get("size", ""),
            "color": data.get("color", ""),
            "sex": data.get("sex", ""),
            "description": data.get("description", ""),
        })
    return results


def get_lost_dog_by_id(dog_id: str) -> dict | None:
    """Devuelve el documento de un perro perdido por su ID, o None si no existe."""
    if _db is None:
        raise RuntimeError("Firebase no inicializado.")
    doc = _db.collection("lost_dogs").document(dog_id).get()
    if not doc.exists:
        return None
    data = doc.to_dict()
    data["doc_id"] = doc.id
    return data


def get_found_report_by_id(report_id: str) -> dict | None:
    """Devuelve el documento de un reporte de perro encontrado por su ID, o None si no existe."""
    if _db is None:
        raise RuntimeError("Firebase no inicializado.")
    doc = _db.collection("found_dog_reports").document(report_id).get()
    if not doc.exists:
        return None
    data = doc.to_dict()
    data["doc_id"] = doc.id
    return data


def update_found_report_status(report_id: str, active: bool, deactivation_reason: str | None = None) -> None:
    if _db is None:
        raise RuntimeError("Firebase no inicializado.")
    status = "active" if active else "inactive"
    data: dict = {"status": status}
    if active:
        data["deactivation_reason"] = firestore.DELETE_FIELD
    elif deactivation_reason:
        data["deactivation_reason"] = deactivation_reason
    _db.collection("found_dog_reports").document(report_id).update(data)


def save_found_report(report_data: dict) -> str:
    if _db is None:
        raise RuntimeError("Firebase no inicializado. Llama initialize_firebase() primero.")

    doc_ref = _db.collection("found_dog_reports").document()
    report_data["created_at"] = datetime.now(timezone.utc)
    report_data["status"] = "active"
    doc_ref.set(report_data)
    return doc_ref.id
