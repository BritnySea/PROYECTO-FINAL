from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.services import firebase_service, model_service
from app.routes import health, dogs


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

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router)
app.include_router(dogs.router, prefix="/api/v1")
