import asyncio
import uuid
import numpy as np
from typing import Annotated, List, Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from sklearn.metrics.pairwise import cosine_similarity

from app.dependencies import verify_firebase_token
from app.schemas.dog import DogMatch, MatchFoundDogResponse, MyFoundReportItem, MyFoundReportsResponse, OwnerMatchesResponse, RegisterLostDogResponse, ValidatePhotoResponse
from app.services import firebase_service, model_service

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
async def validate_photo(
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    photo: UploadFile = File(...),
):
    image_bytes = await photo.read()
    dog_detected, confidence, _ = await asyncio.to_thread(model_service.analyze_image, image_bytes)

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
    dog_detected, confidence, embedding = await asyncio.to_thread(model_service.analyze_image, image_bytes)

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

    print(f"[validate_found_photo] revisando {len(found_reports)} reportes encontrados con embedding")
    for report in found_reports:
        stored_embs = _get_embeddings(report)
        if not stored_embs:
            continue
        sim = _max_similarity(query_embs, stored_embs)
        print(f"[validate_found_photo] similitud con reporte {report.get('doc_id')}: {sim:.1f}%")
        if sim >= DUPLICATE_THRESHOLD:
            raise HTTPException(
                status_code=status.HTTP_409_CONFLICT,
                detail="Este perro ya fue reportado como encontrado anteriormente. Evita registrarlo dos veces.",
            )

    return ValidatePhotoResponse(is_dog=True, confidence=round(confidence, 4), message="Foto válida.")


@router.post("/validate-lost-photo", response_model=ValidatePhotoResponse, summary="Validar foto de perro perdido y verificar duplicado")
async def validate_lost_photo(
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    photo: UploadFile = File(...),
):
    image_bytes = await photo.read()
    dog_detected, confidence, embedding = await asyncio.to_thread(model_service.analyze_image, image_bytes)

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
async def register_lost_dog(
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
        weekly_count = await asyncio.to_thread(firebase_service.count_weekly_reports, uid)
        if weekly_count >= WEEKLY_REPORT_LIMIT:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail=f"Has alcanzado el límite de {WEEKLY_REPORT_LIMIT} reportes por semana. Podrás reportar nuevamente la próxima semana.",
            )

    if not photos or len(photos) == 0:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Se requiere al menos una foto.")
    if len(photos) > MAX_PHOTOS:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"Máximo {MAX_PHOTOS} fotos permitidas.")

    # Leer bytes de todas las fotos
    images_bytes = [await photo.read() for photo in photos]

    # Analizar todas las fotos en paralelo
    analysis_results = await asyncio.gather(*[
        asyncio.to_thread(model_service.analyze_image, img_bytes)
        for img_bytes in images_bytes
    ])

    embeddings_list = []
    for i, (dog_detected, confidence, embedding) in enumerate(analysis_results):
        if not dog_detected:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"La foto {i + 1} no muestra un perro (confianza: {confidence:.2%}).",
            )
        embeddings_list.append(embedding)

    # Verificar duplicados: cualquiera de las nuevas fotos vs perros existentes
    emb_new_arr = np.array(embeddings_list[0]).reshape(1, -1)
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
    photo_urls = list(await asyncio.gather(*upload_tasks))

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

    dog_id = firebase_service.save_lost_dog(dog_data)

    return RegisterLostDogResponse(
        dog_id=dog_id,
        message=f"Perro registrado exitosamente con ID {dog_id}.",
    )


@router.post("/match-found-dog", response_model=MatchFoundDogResponse, summary="Buscar coincidencias con perro encontrado (1 a 3 fotos)")
async def match_found_dog(
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

    if not photos or len(photos) == 0:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail="Se requiere al menos una foto.")
    if len(photos) > MAX_PHOTOS:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=f"Máximo {MAX_PHOTOS} fotos permitidas.")

    images_bytes = [await photo.read() for photo in photos]

    analysis_results = await asyncio.gather(*[
        asyncio.to_thread(model_service.analyze_image, img_bytes)
        for img_bytes in images_bytes
    ])

    found_embeddings = []
    for i, (dog_detected, confidence, embedding) in enumerate(analysis_results):
        if not dog_detected:
            raise HTTPException(
                status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
                detail=f"La foto {i + 1} no muestra un perro (confianza: {confidence:.2%}).",
            )
        found_embeddings.append(embedding)

    current_uid = current_user.get("uid")

    # Subir primera foto y consultar Firestore en paralelo (primera foto para referencia)
    found_filename = f"found_{uuid.uuid4().hex}.jpg"
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
    found_photo_urls = list(upload_results_and_db[0])
    lost_dogs = upload_results_and_db[1]
    found_reports = upload_results_and_db[2]

    # Verificar duplicado en reportes encontrados existentes
    for report in found_reports:
        stored_embs = _get_embeddings(report)
        if not stored_embs:
            continue
        sim = _max_similarity(found_embeddings, stored_embs)
        print(f"[match_found_dog] similitud con reporte encontrado existente: {sim:.1f}%")
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
