import uuid
import numpy as np
from typing import Annotated, Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, UploadFile, status
from sklearn.metrics.pairwise import cosine_similarity

from app.dependencies import verify_firebase_token
from app.schemas.dog import DogMatch, MatchFoundDogResponse, RegisterLostDogResponse, ValidatePhotoResponse
from app.services import firebase_service, model_service

router = APIRouter()

SIMILARITY_THRESHOLD = 20.0  # percent
TOP_K = 10


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


@router.post("/register-lost-dog", response_model=RegisterLostDogResponse, summary="Registrar perro perdido")
async def register_lost_dog(
    current_user: Annotated[dict, Depends(verify_firebase_token)],
    dog_name: str = Form(...),
    description: str = Form(...),
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

    filename = f"{uuid.uuid4().hex}.jpg"
    photo_url = firebase_service.upload_photo(image_bytes, filename)

    dog_data = {
        "name": dog_name,
        "description": description,
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
):
    image_bytes = await photo.read()

    dog_detected, confidence = model_service.is_dog(image_bytes)
    if not dog_detected:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail=f"La foto no muestra un perro (confianza: {confidence:.2%}).",
        )

    embedding_found = model_service.get_embedding(image_bytes)
    emb_found_arr = np.array(embedding_found).reshape(1, -1)

    lost_dogs = firebase_service.get_active_lost_dogs()

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

    # Notificar a los dueños de los perros que coincidieron
    for sim_pct, dog in top_matches:
        owner_uid = dog.get("registered_by_uid")
        if owner_uid:
            firebase_service.send_match_notification(
                owner_uid=owner_uid,
                dog_name=dog.get("name", "tu perro"),
                similarity_percent=sim_pct,
            )

    report_data = {
        "found_by_uid": current_user.get("uid"),
        "matches": [
            {
                "dog_id": m.dog_id,
                "similarity_percent": m.similarity_percent,
            }
            for m in matches
        ],
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
