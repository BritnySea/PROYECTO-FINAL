import uuid
from datetime import datetime, timezone

import cloudinary
import cloudinary.uploader
import firebase_admin
from firebase_admin import credentials, firestore, messaging

from app.config import get_settings

_db = None


def initialize_firebase() -> None:
    global _db

    settings = get_settings()

    if not firebase_admin._apps:
        cred = credentials.Certificate(settings.firebase_credentials_path)
        firebase_admin.initialize_app(cred)

    _db = firestore.client()

    cloudinary.config(
        cloud_name=settings.cloudinary_cloud_name,
        api_key=settings.cloudinary_api_key,
        api_secret=settings.cloudinary_api_secret,
    )

    print("[firebase_service] Firebase y Cloudinary inicializados.")


def upload_photo(image_bytes: bytes, filename: str | None = None) -> str:
    public_id = filename.replace(".jpg", "") if filename else uuid.uuid4().hex

    result = cloudinary.uploader.upload(
        image_bytes,
        public_id=f"dog_photos/{public_id}",
        resource_type="image",
    )
    return result["secure_url"]


def save_lost_dog(dog_data: dict) -> str:
    if _db is None:
        raise RuntimeError("Firebase no inicializado. Llama initialize_firebase() primero.")

    doc_ref = _db.collection("lost_dogs").document()
    dog_data["created_at"] = datetime.now(timezone.utc)
    dog_data["status"] = "active"
    doc_ref.set(dog_data)
    return doc_ref.id


def get_active_lost_dogs() -> list[dict]:
    if _db is None:
        raise RuntimeError("Firebase no inicializado. Llama initialize_firebase() primero.")

    docs = (
        _db.collection("lost_dogs")
        .where("status", "==", "active")
        .stream()
    )

    results = []
    for doc in docs:
        data = doc.to_dict()
        data["doc_id"] = doc.id
        results.append(data)

    return results


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
        print(f"[FCM] Notificacion enviada al dueño {owner_uid} por coincidencia de '{dog_name}'")
    except Exception as e:
        print(f"[FCM] Error enviando notificacion a {owner_uid}: {e}")


def save_found_report(report_data: dict) -> str:
    if _db is None:
        raise RuntimeError("Firebase no inicializado. Llama initialize_firebase() primero.")

    doc_ref = _db.collection("found_dog_reports").document()
    report_data["created_at"] = datetime.now(timezone.utc)
    doc_ref.set(report_data)
    return doc_ref.id
