from fastapi import APIRouter
from app.services import model_service

router = APIRouter()


@router.get("/health", summary="Estado del servidor")
async def health_check():
    return {
        "status": "ok",
        "model_loaded": model_service.is_model_loaded(),
    }
