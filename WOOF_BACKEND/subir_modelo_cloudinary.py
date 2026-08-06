"""
Sube el modelo ML a Cloudinary como archivo raw.
Ejecutar UNA SOLA VEZ antes de desplegar en Railway.

Uso:
    (venv) python subir_modelo_cloudinary.py
"""
import cloudinary
import cloudinary.uploader
from app.config import get_settings

settings = get_settings()

cloudinary.config(
    cloud_name=settings.cloudinary_cloud_name,
    api_key=settings.cloudinary_api_key,
    api_secret=settings.cloudinary_api_secret,
)

MODEL_PATH = settings.model_path   # ml/woof_model_v1.h5

print(f"Subiendo {MODEL_PATH} a Cloudinary en fragmentos (puede tardar varios minutos)...")

result = cloudinary.uploader.upload_large(
    MODEL_PATH,
    resource_type="raw",
    public_id="woof_models/woof_model_v1",
    overwrite=True,
    chunk_size=6 * 1024 * 1024,   # fragmentos de 6 MB
)

url = result["secure_url"]
print(f"\nModelo subido exitosamente.")
print(f"URL del modelo:")
print(f"  {url}")
print(f"\nCopia esta URL y agrégala en Railway como variable de entorno:")
print(f"  MODEL_DOWNLOAD_URL = {url}")
