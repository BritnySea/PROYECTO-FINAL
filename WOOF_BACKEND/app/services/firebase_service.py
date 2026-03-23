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


def upload_photo(image_bytes: bytes, filename: str | None = None, folder: str = "dog_photos") -> str:
    public_id = filename.replace(".jpg", "") if filename else uuid.uuid4().hex

    result = cloudinary.uploader.upload(
        image_bytes,
        public_id=f"{folder}/{public_id}",
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

        results.append({
            "report_id": doc.id,
            "found_dog_photo_url": data.get("found_dog_photo_url", ""),
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


def save_found_report(report_data: dict) -> str:
    if _db is None:
        raise RuntimeError("Firebase no inicializado. Llama initialize_firebase() primero.")

    doc_ref = _db.collection("found_dog_reports").document()
    report_data["created_at"] = datetime.now(timezone.utc)
    doc_ref.set(report_data)
    return doc_ref.id
