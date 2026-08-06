import asyncio
import re
import uuid
import numpy as np
from typing import Annotated, List, Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile, status
from sklearn.metrics.pairwise import cosine_similarity

from app.dependencies import verify_firebase_token
from app.schemas.dog import DogMatch, FinderMatchItem, FinderMatchesResponse, MatchFoundDogResponse, MyFoundReportItem, MyFoundReportsResponse, OwnerMatchesResponse, RegisterLostDogResponse, ValidatePhotoResponse
from app.services import firebase_service, model_service
from app.limiter import limiter

# Validación de archivos subidos
ALLOWED_MIME_TYPES = {"image/jpeg", "image/png", "image/webp"}
MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024  # 10 MB

# Límites de longitud para campos de texto
MAX_NAME_LEN = 100
MAX_DESCRIPTION_LEN = 500
MAX_PHONE_LEN = 20
MAX_EMAIL_LEN = 254
MAX_SHORT_FIELD_LEN = 60

PHONE_RE = re.compile(r"^\+?[\d\s\-\(\)]{7,20}$")
EMAIL_RE = re.compile(r"^[^@\s]+@[^@\s]+\.[^@\s]+$")


def _validate_image_upload(photo: UploadFile, index: int = 1) -> None:
    """Valida MIME type y tamaño de un archivo subido. Lanza HTTPException si falla."""
    if photo.content_type not in ALLOWED_MIME_TYPES:
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail=f"Foto {index}: tipo de archivo no permitido ({photo.content_type}). Solo se aceptan JPEG, PNG o WebP.",
        )


async def _validate_and_read_photo(photo: UploadFile, index: int = 1) -> bytes:
    """Lee los bytes de una foto y valida su tamaño máximo."""
    image_bytes = await photo.read()
    if len(image_bytes) > MAX_FILE_SIZE_BYTES:
        raise HTTPException(
            status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
            detail=f"Foto {index}: el archivo supera el tamaño máximo permitido de 10 MB.",
        )
    return image_bytes


router = APIRouter()

SIMILARITY_THRESHOLD = 50.0
NOTIFICATION_THRESHOLD = 80.0
TOP_K = 5
DUPLICATE_THRESHOLD = 90.0
MAX_PHOTOS = 3
WEEKLY_REPORT_LIMIT = 3


def _get_embeddings(doc: dict) -> list:
    """Lee embeddings numerados: embedding_1, embedding_2, embedding_3.
    Fallback a embedding único para documentos antiguos."""
    result = []
    for i in range(1, 4):
        emb = doc.get(f"embedding_{i}")
        if emb and isinstance(emb, list):
            result.append(emb)
    if result:
        return result
    single = doc.get("embedding")
    if single and isinstance(single, list):
        return [single]
    return []


def _max_similarity(query_embeddings: list, stored_embeddings: list) -> float:
    """Similitud máxima entre todos los pares de embeddings (query × stored)."""
    if not query_embeddings or not stored_embeddings:
        return 0.0
    max_sim = 0.0
    for qe in query_embeddings:
        qe_arr = np.array(qe).reshape(1, -1)
        for se in stored_embeddings:
            se_arr = np.array(se).reshape(1, -1)
            sim = float(cosine_similarity(qe_arr, se_arr)[0][0]) * 100
            if sim > max_sim:
                max_sim = sim
    return max_sim


@router.post("/validate-photo", response_model=ValidatePhotoResponse, summary="Validar si la foto muestra un perro")
@limiter.limit("60/minute")
async def validate_photo(
    request: Request,
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    photo: UploadFile = File(...),
):
    _validate_image_upload(photo, 1)
    image_bytes = await _validate_and_read_photo(photo, 1)
    try:
        dog_detected, confidence, _ = await asyncio.to_thread(model_service.analyze_image, image_bytes)
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))

    if dog_detected:
        message = "Foto válida."
    elif confidence < 0.15:
        message = "La imagen tiene muy baja calidad o está borrosa. Intenta con una foto más clara y bien iluminada."
    else:
        message = "La foto no muestra un perro. Por favor seleccione otra foto."

    return ValidatePhotoResponse(is_dog=dog_detected, confidence=round(confidence, 4), message=message)


@router.post("/validate-found-photo", response_model=ValidatePhotoResponse, summary="Validar foto de perro encontrado y verificar duplicado")
@limiter.limit("60/minute")
async def validate_found_photo(
    request: Request,
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    photo: UploadFile = File(...),
):
    _validate_image_upload(photo, 1)
    image_bytes = await _validate_and_read_photo(photo, 1)
    try:
        dog_detected, confidence, embedding = await asyncio.to_thread(model_service.analyze_image, image_bytes)
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))

    if not dog_detected:
        message = (
            "La imagen tiene muy baja calidad o está borrosa. Intenta con una foto más clara y bien iluminada."
            if confidence < 0.15
            else "La foto no muestra un perro. Por favor seleccione otra foto."
        )
        return ValidatePhotoResponse(is_dog=False, confidence=round(confidence, 4), message=message)

    query_embs = [embedding]
    current_uid = current_user.get("uid")

    lost_dogs, found_reports = await asyncio.gather(
        asyncio.to_thread(firebase_service.get_active_lost_dogs),
        asyncio.to_thread(firebase_service.get_active_found_reports),
    )

    for dog in lost_dogs:
        if dog.get("registered_by_uid") != current_uid:
            continue
        stored_embs = _get_embeddings(dog)
        if not stored_embs:
            continue
        sim = _max_similarity(query_embs, stored_embs)
        if sim >= DUPLICATE_THRESHOLD:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Esta foto corresponde a un perro que tú mismo reportaste como perdido. No puedes reportarlo como encontrado.",
            )

    for report in found_reports:
        stored_embs = _get_embeddings(report)
        if not stored_embs:
            continue
        sim = _max_similarity(query_embs, stored_embs)
        if sim >= DUPLICATE_THRESHOLD:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Este perro ya fue reportado como encontrado anteriormente. Evita registrarlo dos veces.",
            )

    return ValidatePhotoResponse(is_dog=True, confidence=round(confidence, 4), message="Foto válida.")


@router.post("/validate-lost-photo", response_model=ValidatePhotoResponse, summary="Validar foto de perro perdido y verificar duplicado")
@limiter.limit("60/minute")
async def validate_lost_photo(
    request: Request,
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    photo: UploadFile = File(...),
):
    _validate_image_upload(photo, 1)
    image_bytes = await _validate_and_read_photo(photo, 1)
    try:
        dog_detected, confidence, embedding = await asyncio.to_thread(model_service.analyze_image, image_bytes)
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))

    if not dog_detected:
        message = (
            "La imagen tiene muy baja calidad o está borrosa. Intenta con una foto más clara y bien iluminada."
            if confidence < 0.15
            else "La foto no muestra un perro. Por favor seleccione otra foto."
        )
        return ValidatePhotoResponse(is_dog=False, confidence=round(confidence, 4), message=message)

    query_embs = [embedding]
    existing_lost_dogs = await asyncio.to_thread(firebase_service.get_active_lost_dogs)

    for existing_dog in existing_lost_dogs:
        stored_embs = _get_embeddings(existing_dog)
        if not stored_embs:
            continue
        sim = _max_similarity(query_embs, stored_embs)
        if sim >= DUPLICATE_THRESHOLD:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Ya existe un reporte activo de este perro. No es posible registrarlo nuevamente.",
            )

    return ValidatePhotoResponse(is_dog=True, confidence=round(confidence, 4), message="Foto válida.")


@router.post("/register-lost-dog", response_model=RegisterLostDogResponse, summary="Registrar perro perdido con 1 a 3 fotos")
@limiter.limit("20/minute")
async def register_lost_dog(
    request: Request,
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    dog_name: str = Form(...),
    description: Optional[str] = Form(None),
    owner_name: str = Form(...),
    owner_phone: str = Form(...),
    owner_email: str = Form(...),
    photos: List[UploadFile] = File(...),
    size: Optional[str] = Form(None),
    color: Optional[str] = Form(None),
    breed: Optional[str] = Form(None),
    lost_location: Optional[str] = Form(None),
    sex: Optional[str] = Form(None),
):
    uid = current_user.get("uid")
    role = await asyncio.to_thread(firebase_service.get_user_role, uid)
    if role != "ADMIN":
        allowed = await asyncio.to_thread(firebase_service.atomic_weekly_limit_check, uid, WEEKLY_REPORT_LIMIT)
        if not allowed:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail=f"Has alcanzado el límite de {WEEKLY_REPORT_LIMIT} reportes por semana. Podrás reportar nuevamente la próxima semana.",
            )

    # Validar campos de texto
    if not dog_name.strip() or len(dog_name) > MAX_NAME_LEN:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"El nombre del perro es requerido y debe tener máximo {MAX_NAME_LEN} caracteres.")
    if not owner_name.strip() or len(owner_name) > MAX_NAME_LEN:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"El nombre del dueño es requerido y debe tener máximo {MAX_NAME_LEN} caracteres.")
    if not PHONE_RE.match(owner_phone.strip()):
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="El teléfono del dueño tiene un formato inválido.")
    if not EMAIL_RE.match(owner_email.strip()) or len(owner_email) > MAX_EMAIL_LEN:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="El email del dueño tiene un formato inválido.")
    if description and len(description) > MAX_DESCRIPTION_LEN:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"La descripción supera los {MAX_DESCRIPTION_LEN} caracteres permitidos.")

    if not photos or len(photos) == 0:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Se requiere al menos una foto.")
    if len(photos) > MAX_PHOTOS:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"Máximo {MAX_PHOTOS} fotos permitidas.")

    for i, photo in enumerate(photos, start=1):
        _validate_image_upload(photo, i)

    # Leer bytes de todas las fotos
    images_bytes = [await _validate_and_read_photo(photo, i) for i, photo in enumerate(photos, start=1)]

    try:
        analysis_results = await asyncio.gather(*[
            asyncio.to_thread(model_service.analyze_image, img_bytes)
            for img_bytes in images_bytes
        ])
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))

    embeddings_list = []
    for i, (dog_detected, confidence, embedding) in enumerate(analysis_results):
        if not dog_detected:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"La foto {i + 1} no muestra un perro (confianza: {confidence:.2%}).",
            )
        embeddings_list.append(embedding)

    # Verificar duplicados: cualquiera de las nuevas fotos vs perros existentes
    existing_lost_dogs = await asyncio.to_thread(firebase_service.get_active_lost_dogs)
    for existing_dog in existing_lost_dogs:
        stored_embs = _get_embeddings(existing_dog)
        if not stored_embs:
            continue
        sim = _max_similarity(embeddings_list, stored_embs)
        if sim >= DUPLICATE_THRESHOLD:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Ya existe un reporte activo de este perro. No es posible registrarlo nuevamente.",
            )

    # Subir todas las fotos en paralelo
    upload_tasks = [
        asyncio.to_thread(firebase_service.upload_photo, img_bytes, f"{uuid.uuid4().hex}.jpg")
        for img_bytes in images_bytes
    ]
    upload_results = list(await asyncio.gather(*upload_tasks))
    photo_urls  = [r[0] for r in upload_results]
    public_ids  = [r[1] for r in upload_results]

    dog_data = {
        "name": dog_name,
        "description": description or "",
        "owner_name": owner_name,
        "owner_phone": owner_phone,
        "owner_email": owner_email,
        "registered_by_uid": current_user.get("uid"),
        "size": size,
        "color": color,
        "breed": breed,
        "lost_location": lost_location,
        "sex": sex or "",
    }
    for i, (url, emb) in enumerate(zip(photo_urls, embeddings_list), start=1):
        dog_data[f"photo_url_{i}"] = url
        dog_data[f"embedding_{i}"] = emb

    try:
        dog_id = firebase_service.save_lost_dog(dog_data)
    except Exception:
        await asyncio.to_thread(firebase_service.cleanup_cloudinary_photos, public_ids)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Error al guardar el reporte. Las fotos fueron eliminadas.")

    return RegisterLostDogResponse(
        dog_id=dog_id,
        message=f"Perro registrado exitosamente con ID {dog_id}.",
    )


@router.post("/match-found-dog", response_model=MatchFoundDogResponse, summary="Buscar coincidencias con perro encontrado (1 a 3 fotos)")
@limiter.limit("10/minute")
async def match_found_dog(
    request: Request,
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    photos: List[UploadFile] = File(...),
    size: Optional[str] = Form(None),
    color: Optional[str] = Form(None),
    sex: Optional[str] = Form(None),
    description: Optional[str] = Form(None),
    reporter_name: Optional[str] = Form(None),
    reporter_phone: Optional[str] = Form(None),
    reporter_email: Optional[str] = Form(None),
):
    uid = current_user.get("uid")

    # Cooldown atómico: evita que el mismo usuario duplique un reporte dentro de 30s
    can_report = await asyncio.to_thread(firebase_service.check_and_update_found_cooldown, uid)
    if not can_report:
        raise HTTPException(
            status_code=status.HTTP_429_TOO_MANY_REQUESTS,
            detail="Enviaste un reporte hace menos de 30 segundos. Espera un momento antes de volver a intentarlo.",
        )

    if not photos or len(photos) == 0:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Se requiere al menos una foto.")
    if len(photos) > MAX_PHOTOS:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"Máximo {MAX_PHOTOS} fotos permitidas.")

    for i, photo in enumerate(photos, start=1):
        _validate_image_upload(photo, i)

    images_bytes = [await _validate_and_read_photo(photo, i) for i, photo in enumerate(photos, start=1)]

    try:
        analysis_results = await asyncio.gather(*[
            asyncio.to_thread(model_service.analyze_image, img_bytes)
            for img_bytes in images_bytes
        ])
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(e))

    found_embeddings = []
    for i, (dog_detected, confidence, embedding) in enumerate(analysis_results):
        if not dog_detected:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"La foto {i + 1} no muestra un perro (confianza: {confidence:.2%}).",
            )
        found_embeddings.append(embedding)

    current_uid = current_user.get("uid")

    # Subir fotos y consultar Firestore en paralelo
    upload_tasks = [
        asyncio.to_thread(firebase_service.upload_photo, images_bytes[i], f"found_{uuid.uuid4().hex}.jpg",
                          "found_dog_photos")
        for i in range(len(images_bytes))
    ]

    upload_results_and_db = await asyncio.gather(
        asyncio.gather(*upload_tasks),
        asyncio.to_thread(firebase_service.get_active_lost_dogs),
        asyncio.to_thread(firebase_service.get_active_found_reports),
    )
    raw_upload_results = list(upload_results_and_db[0])
    found_photo_urls   = [r[0] for r in raw_upload_results]
    found_public_ids   = [r[1] for r in raw_upload_results]
    lost_dogs          = upload_results_and_db[1]
    found_reports      = upload_results_and_db[2]

    # Verificar duplicado en reportes encontrados existentes
    for report in found_reports:
        stored_embs = _get_embeddings(report)
        if not stored_embs:
            continue
        sim = _max_similarity(found_embeddings, stored_embs)
        if sim >= DUPLICATE_THRESHOLD:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Este perro ya fue reportado como encontrado anteriormente. Evita registrarlo dos veces.",
            )

    # Verificar que el dueño no reporte su propio perro como encontrado
    for dog in lost_dogs:
        if dog.get("registered_by_uid") != current_uid:
            continue
        stored_embs = _get_embeddings(dog)
        if not stored_embs:
            continue
        sim = _max_similarity(found_embeddings, stored_embs)
        if sim >= DUPLICATE_THRESHOLD:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Esta foto corresponde a un perro que tú mismo reportaste como perdido. No puedes reportarlo como encontrado.",
            )

    # Buscar coincidencias: solo contra perros de OTROS usuarios
    scored = []
    for dog in lost_dogs:
        if dog.get("registered_by_uid") == current_uid:
            continue
        stored_embs = _get_embeddings(dog)
        if not stored_embs:
            continue
        similarity_percent = round(_max_similarity(found_embeddings, stored_embs), 1)
        if similarity_percent >= SIMILARITY_THRESHOLD:
            scored.append((similarity_percent, dog))

    scored.sort(key=lambda x: x[0], reverse=True)
    top_matches = scored[:TOP_K]

    matches = [
        DogMatch(
            dog_id=dog["doc_id"],
            name=dog.get("name", ""),
            owner_phone=dog.get("owner_phone", ""),
            owner_email=dog.get("owner_email", ""),
            similarity_percent=sim_pct,
            photo_url=dog.get("photo_url_1", dog.get("photo_url", "")),
        )
        for sim_pct, dog in top_matches
    ]

    # Notificar si similitud >= 80%
    for sim_pct, dog in top_matches:
        if sim_pct >= NOTIFICATION_THRESHOLD:
            owner_uid = dog.get("registered_by_uid")
            if owner_uid:
                firebase_service.send_match_notification(
                    owner_uid=owner_uid,
                    dog_name=dog.get("name", "tu perro"),
                    similarity_percent=sim_pct,
                )
            firebase_service.send_match_notification_to_finder(
                finder_uid=uid,
                similarity_percent=sim_pct,
            )

    report_data = {
        "found_by_uid": current_user.get("uid"),
        "matched_dog_ids": [m.dog_id for m in matches],
        "matches": [
            {"dog_id": m.dog_id, "similarity_percent": m.similarity_percent}
            for m in matches
        ],
        "size": size or "",
        "color": color or "",
        "sex": sex or "",
        "description": description or "",
        "reporter_name": reporter_name or "",
        "reporter_phone": reporter_phone or "",
        "reporter_email": reporter_email or "",
    }
    for i, (url, emb) in enumerate(zip(found_photo_urls, found_embeddings), start=1):
        report_data[f"found_dog_photo_url_{i}"] = url
        report_data[f"embedding_{i}"] = emb

    try:
        firebase_service.save_found_report(report_data)
    except Exception:
        await asyncio.to_thread(firebase_service.cleanup_cloudinary_photos, found_public_ids)
        raise HTTPException(status_code=status.HTTP_500_INTERNAL_SERVER_ERROR, detail="Error al guardar el reporte. Las fotos fueron eliminadas.")

    message = (
        f"Se encontraron {len(matches)} perro(s) que podrían coincidir."
        if matches
        else "No se encontraron coincidencias con perros perdidos registrados."
    )

    return MatchFoundDogResponse(
        matches=matches,
        is_dog=True,
        message=message,
    )


@router.get("/my-dog-matches", response_model=OwnerMatchesResponse, summary="Obtener coincidencias para un perro perdido del usuario")
@limiter.limit("60/minute")
async def get_my_dog_matches(
    request: Request,
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    dog_id: str,
):
    uid = current_user.get("uid")
    dog_doc = await asyncio.to_thread(firebase_service.get_lost_dog_by_id, dog_id)
    if dog_doc is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Reporte no encontrado.")
    if dog_doc.get("registered_by_uid") != uid:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="No tienes permiso para ver las coincidencias de este reporte.")
    from app.schemas.dog import OwnerMatchItem
    raw_matches = firebase_service.get_matches_for_dog(dog_id)
    items = [OwnerMatchItem(**m) for m in raw_matches]
    return OwnerMatchesResponse(matches=items)


@router.get("/my-found-reports", response_model=MyFoundReportsResponse, summary="Obtener reportes de perros encontrados del usuario")
@limiter.limit("60/minute")
async def get_my_found_reports(
    request: Request,
    current_user: Annotated[dict, Depends(verify_firebase_token)],
):
    uid = current_user.get("uid")
    raw = firebase_service.get_my_found_reports(uid)
    items = [MyFoundReportItem(**r) for r in raw]
    return MyFoundReportsResponse(reports=items)


@router.get("/my-found-report-matches", response_model=FinderMatchesResponse, summary="Obtener coincidencias de un perro encontrado del usuario con perros perdidos de otros")
@limiter.limit("60/minute")
async def get_my_found_report_matches(
    request: Request,
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    report_id: str,
):
    uid = current_user.get("uid")
    report_doc = await asyncio.to_thread(firebase_service.get_found_report_by_id, report_id)
    if report_doc is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Reporte no encontrado.")
    if report_doc.get("found_by_uid") != uid:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="No tienes permiso para ver las coincidencias de este reporte.")

    found_photo_urls = []
    for i in range(1, 4):
        url = report_doc.get(f"found_dog_photo_url_{i}")
        if url:
            found_photo_urls.append(url)
    if not found_photo_urls:
        fallback = report_doc.get("found_dog_photo_url", "")
        if fallback:
            found_photo_urls.append(fallback)

    raw_matches = report_doc.get("matches", [])
    items = []
    for match in raw_matches:
        dog_id = match.get("dog_id")
        similarity = match.get("similarity_percent", 0.0)
        if not dog_id:
            continue
        lost_dog = await asyncio.to_thread(firebase_service.get_lost_dog_by_id, dog_id)
        if not lost_dog:
            continue
        # Excluir perros perdidos registrados por el mismo usuario
        if lost_dog.get("registered_by_uid") == uid:
            continue

        lost_photo_url = ""
        for i in range(1, 4):
            url = lost_dog.get(f"photo_url_{i}")
            if url:
                lost_photo_url = url
                break
        if not lost_photo_url:
            lost_photo_url = lost_dog.get("photo_url", "")

        created_at = lost_dog.get("created_at")
        lost_at = created_at.strftime("%Y-%m-%d %H:%M") if created_at else ""

        items.append(FinderMatchItem(
            lost_dog_id=dog_id,
            lost_dog_name=lost_dog.get("name", ""),
            lost_dog_photo_url=lost_photo_url,
            similarity_percent=similarity,
            owner_name=lost_dog.get("owner_name", ""),
            owner_phone=lost_dog.get("owner_phone", ""),
            owner_email=lost_dog.get("owner_email", ""),
            lost_dog_size=lost_dog.get("size", ""),
            lost_dog_color=lost_dog.get("color", ""),
            lost_dog_sex=lost_dog.get("sex", ""),
            lost_dog_description=lost_dog.get("description", ""),
            lost_at=lost_at,
        ))

    items.sort(key=lambda x: x.similarity_percent, reverse=True)

    return FinderMatchesResponse(
        report_id=report_id,
        found_dog_photo_url=found_photo_urls[0] if found_photo_urls else "",
        found_dog_photo_urls=found_photo_urls,
        matches=items,
    )


@router.patch("/found-report-status", summary="Activar o desactivar un reporte de perro encontrado")
@limiter.limit("60/minute")
async def update_found_report_status(
    request: Request,
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    report_id: str,
    active: bool,
    deactivation_reason: str | None = None,
):
    uid = current_user.get("uid")
    report_doc = await asyncio.to_thread(firebase_service.get_found_report_by_id, report_id)
    if report_doc is None:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Reporte no encontrado.")
    if report_doc.get("found_by_uid") != uid:
        raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="No tienes permiso para modificar este reporte.")
    firebase_service.update_found_report_status(report_id, active, deactivation_reason)
    return {"message": "Estado actualizado correctamente"}
