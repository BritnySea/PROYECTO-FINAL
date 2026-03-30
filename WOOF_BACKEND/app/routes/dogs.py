import uuid
import numpy as np
from typing import Annotated, Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from sklearn.metrics.pairwise import cosine_similarity

from app.dependencies import verify_firebase_token
from app.schemas.dog import DogMatch, MatchFoundDogResponse, MyFoundReportItem, MyFoundReportsResponse, OwnerMatchesResponse, RegisterLostDogResponse, ValidatePhotoResponse
from app.services import firebase_service, model_service

router = APIRouter()

SIMILARITY_THRESHOLD = 20.0  # percent para mostrar coincidencias
NOTIFICATION_THRESHOLD = 80.0  # percent para enviar notificación
TOP_K = 5
DUPLICATE_THRESHOLD = 90.0  # percent para considerar foto duplicada


@router.post("/validate-photo", response_model=ValidatePhotoResponse, summary="Validar si la foto muestra un perro")
async def validate_photo(
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    photo: UploadFile = File(...),
):
    image_bytes = await photo.read()
    dog_detected, confidence = model_service.is_dog(image_bytes)

    if dog_detected:
        message = "Foto válida."
    elif confidence < 0.15:
        message = "La imagen tiene muy baja calidad o está borrosa. Intenta con una foto más clara y bien iluminada."
    else:
        message = "La foto no muestra un perro. Por favor seleccione otra foto."

    return ValidatePhotoResponse(is_dog=dog_detected, confidence=round(confidence, 4), message=message)


@router.post("/validate-found-photo", response_model=ValidatePhotoResponse, summary="Validar foto de perro encontrado y verificar duplicado")
async def validate_found_photo(
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    photo: UploadFile = File(...),
):
    image_bytes = await photo.read()
    dog_detected, confidence = model_service.is_dog(image_bytes)

    if not dog_detected:
        if confidence < 0.15:
            message = "La imagen tiene muy baja calidad o está borrosa. Intenta con una foto más clara y bien iluminada."
        else:
            message = "La foto no muestra un perro. Por favor seleccione otra foto."
        return ValidatePhotoResponse(is_dog=False, confidence=round(confidence, 4), message=message)

    embedding = model_service.get_embedding(image_bytes)
    emb_arr = np.array(embedding).reshape(1, -1)

    current_uid = current_user.get("uid")
    lost_dogs = firebase_service.get_active_lost_dogs()
    for dog in lost_dogs:
        if dog.get("registered_by_uid") != current_uid:
            continue
        stored_emb = dog.get("embedding")
        if not stored_emb:
            continue
        sim = float(cosine_similarity(emb_arr, np.array(stored_emb).reshape(1, -1))[0][0]) * 100
        if sim >= DUPLICATE_THRESHOLD:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Esta foto corresponde a un perro que tú mismo reportaste como perdido. No puedes reportarlo como encontrado.",
            )

    return ValidatePhotoResponse(is_dog=True, confidence=round(confidence, 4), message="Foto válida.")


@router.post("/validate-lost-photo", response_model=ValidatePhotoResponse, summary="Validar foto de perro perdido y verificar duplicado")
async def validate_lost_photo(
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    photo: UploadFile = File(...),
):
    image_bytes = await photo.read()
    dog_detected, confidence = model_service.is_dog(image_bytes)

    if not dog_detected:
        if confidence < 0.15:
            message = "La imagen tiene muy baja calidad o está borrosa. Intenta con una foto más clara y bien iluminada."
        else:
            message = "La foto no muestra un perro. Por favor seleccione otra foto."
        return ValidatePhotoResponse(is_dog=False, confidence=round(confidence, 4), message=message)

    embedding = model_service.get_embedding(image_bytes)
    emb_arr = np.array(embedding).reshape(1, -1)

    existing_lost_dogs = firebase_service.get_active_lost_dogs()
    for existing_dog in existing_lost_dogs:
        stored_emb = existing_dog.get("embedding")
        if not stored_emb:
            continue
        sim = float(cosine_similarity(emb_arr, np.array(stored_emb).reshape(1, -1))[0][0]) * 100
        if sim >= DUPLICATE_THRESHOLD:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Ya existe un reporte activo de este perro. No es posible registrarlo nuevamente.",
            )

    return ValidatePhotoResponse(is_dog=True, confidence=round(confidence, 4), message="Foto válida.")


@router.post("/register-lost-dog", response_model=RegisterLostDogResponse, summary="Registrar perro perdido")
async def register_lost_dog(
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    dog_name: str = Form(...),
    description: Optional[str] = Form(None),
    owner_name: str = Form(...),
    owner_phone: str = Form(...),
    owner_email: str = Form(...),
    photo: UploadFile = File(...),
    size: Optional[str] = Form(None),
    color: Optional[str] = Form(None),
    breed: Optional[str] = Form(None),
    lost_location: Optional[str] = Form(None),
):
    image_bytes = await photo.read()

    dog_detected, confidence = model_service.is_dog(image_bytes)
    if not dog_detected:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"La foto no muestra un perro (confianza: {confidence:.2%}).",
        )

    embedding = model_service.get_embedding(image_bytes)

    # Validacion 1: evitar registrar el mismo perro perdido dos veces (cualquier usuario)
    emb_new_arr = np.array(embedding).reshape(1, -1)
    existing_lost_dogs = firebase_service.get_active_lost_dogs()
    for existing_dog in existing_lost_dogs:
        stored_emb = existing_dog.get("embedding")
        if not stored_emb:
            continue
        sim = float(cosine_similarity(emb_new_arr, np.array(stored_emb).reshape(1, -1))[0][0]) * 100
        if sim >= DUPLICATE_THRESHOLD:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Ya existe un reporte activo de este perro. No es posible registrarlo nuevamente.",
            )

    filename = f"{uuid.uuid4().hex}.jpg"
    photo_url = firebase_service.upload_photo(image_bytes, filename)

    dog_data = {
        "name": dog_name,
        "description": description or "",
        "owner_name": owner_name,
        "owner_phone": owner_phone,
        "owner_email": owner_email,
        "photo_url": photo_url,
        "embedding": embedding,
        "registered_by_uid": current_user.get("uid"),
        "size": size,
        "color": color,
        "breed": breed,
        "lost_location": lost_location,
    }

    dog_id = firebase_service.save_lost_dog(dog_data)

    return RegisterLostDogResponse(
        dog_id=dog_id,
        message=f"Perro registrado exitosamente con ID {dog_id}.",
    )


@router.post("/match-found-dog", response_model=MatchFoundDogResponse, summary="Buscar coincidencias con perro encontrado")
async def match_found_dog(
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    photo: UploadFile = File(...),
    size: Optional[str] = Form(None),
    color: Optional[str] = Form(None),
    sex: Optional[str] = Form(None),
    description: Optional[str] = Form(None),
    reporter_name: Optional[str] = Form(None),
    reporter_phone: Optional[str] = Form(None),
    reporter_email: Optional[str] = Form(None),
):
    image_bytes = await photo.read()

    dog_detected, confidence = model_service.is_dog(image_bytes)
    if not dog_detected:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"La foto no muestra un perro (confianza: {confidence:.2%}).",
        )

    # Subir foto del perro encontrado a Cloudinary
    found_filename = f"found_{uuid.uuid4().hex}.jpg"
    found_photo_url = firebase_service.upload_photo(image_bytes, found_filename, folder="found_dog_photos")

    embedding_found = model_service.get_embedding(image_bytes)
    emb_found_arr = np.array(embedding_found).reshape(1, -1)

    # Validacion 2: evitar que el dueno reporte su propio perro como encontrado
    current_uid = current_user.get("uid")
    lost_dogs = firebase_service.get_active_lost_dogs()
    for dog in lost_dogs:
        if dog.get("registered_by_uid") != current_uid:
            continue
        stored_emb = dog.get("embedding")
        if not stored_emb:
            continue
        sim = float(cosine_similarity(emb_found_arr, np.array(stored_emb).reshape(1, -1))[0][0]) * 100
        if sim >= DUPLICATE_THRESHOLD:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Esta foto corresponde a un perro que tú mismo reportaste como perdido. No puedes reportarlo como encontrado.",
            )

    scored = []
    for dog in lost_dogs:
        stored_embedding = dog.get("embedding")
        if not stored_embedding:
            continue

        emb_lost_arr = np.array(stored_embedding).reshape(1, -1)
        similarity = float(cosine_similarity(emb_found_arr, emb_lost_arr)[0][0])
        similarity_percent = round(similarity * 100, 1)

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
            photo_url=dog.get("photo_url", ""),
        )
        for sim_pct, dog in top_matches
    ]

    # Notificar solo si similitud >= 80%
    for sim_pct, dog in top_matches:
        if sim_pct >= NOTIFICATION_THRESHOLD:
            owner_uid = dog.get("registered_by_uid")
            if owner_uid:
                firebase_service.send_match_notification(
                    owner_uid=owner_uid,
                    dog_name=dog.get("name", "tu perro"),
                    similarity_percent=sim_pct,
                )

    report_data = {
        "found_by_uid": current_user.get("uid"),
        "found_dog_photo_url": found_photo_url,
        "matched_dog_ids": [m.dog_id for m in matches],
        "matches": [
            {
                "dog_id": m.dog_id,
                "similarity_percent": m.similarity_percent,
            }
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
    firebase_service.save_found_report(report_data)

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
async def get_my_dog_matches(
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    dog_id: str,
):
    raw_matches = firebase_service.get_matches_for_dog(dog_id)
    from app.schemas.dog import OwnerMatchItem
    items = [OwnerMatchItem(**m) for m in raw_matches]
    return OwnerMatchesResponse(matches=items)


@router.get("/my-found-reports", response_model=MyFoundReportsResponse, summary="Obtener reportes de perros encontrados del usuario")
async def get_my_found_reports(
    current_user: Annotated[dict, Depends(verify_firebase_token)],
):
    uid = current_user.get("uid")
    raw = firebase_service.get_my_found_reports(uid)
    items = [MyFoundReportItem(**r) for r in raw]
    return MyFoundReportsResponse(reports=items)


@router.patch("/found-report-status", summary="Activar o desactivar un reporte de perro encontrado")
async def update_found_report_status(
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    report_id: str,
    active: bool,
):
    firebase_service.update_found_report_status(report_id, active)
    return {"message": "Estado actualizado correctamente"}
