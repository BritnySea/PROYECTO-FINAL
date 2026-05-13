from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from starlette.requests import Request as StarletteRequest
from slowapi import _rate_limit_exceeded_handler
from slowapi.middleware import SlowAPIMiddleware
from slowapi.errors import RateLimitExceeded

from app.limiter import limiter
from app.services import firebase_service, model_service
from app.routes import health, dogs
from app.config import get_settings


@asynccontextmanager
async def lifespan(app: FastAPI):
    firebase_service.initialize_firebase()
    model_service.load_model()
    yield


app = FastAPI(
    title="WOOF API",
    description="Backend para matching de perros perdidos usando EfficientNetB4.",
    version="1.0.0",
    lifespan=lifespan,
)

app.state.limiter = limiter


async def rate_limit_handler(request: StarletteRequest, exc: RateLimitExceeded):
    return JSONResponse(
        status_code=429,
        content={"detail": "Demasiadas solicitudes. Intenta de nuevo en un momento."}
    )


app.add_exception_handler(RateLimitExceeded, rate_limit_handler)
app.add_middleware(SlowAPIMiddleware)

_BASE_ORIGINS = [
    "http://localhost",
    "http://10.0.2.2",
    "http://127.0.0.1",
    "http://localhost:5500",
    "http://127.0.0.1:5500",
    "https://proyectov1-15.web.app",
    "https://proyectov1-15.firebaseapp.com",
]

def _build_origins() -> list[str]:
    extra = get_settings().allowed_origins
    if not extra:
        return _BASE_ORIGINS
    return _BASE_ORIGINS + [o.strip() for o in extra.split(",") if o.strip()]

app.add_middleware(
    CORSMiddleware,
    allow_origins=_build_origins(),
    allow_credentials=False,
    allow_methods=["GET", "POST", "PATCH"],
    allow_headers=["Authorization", "Content-Type"],
)

app.include_router(health.router)
app.include_router(dogs.router, prefix="/api/v1")
