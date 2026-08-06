import io
import os
import urllib.request

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter
import tensorflow as tf

from app.config import get_settings

# Singleton references
_full_model      = None
_embedding_model = None

NOT_A_DOG_INDEX = 83
CONFIDENCE_THRESHOLD = 0.20
IMAGE_SIZE = (380, 380)


def _download_model_if_needed(model_path: str, download_url: str) -> None:
    """Descarga el modelo desde download_url si no existe en model_path."""
    if os.path.exists(model_path):
        return
    if not download_url:
        raise FileNotFoundError(
            f"Modelo no encontrado en '{model_path}' y MODEL_DOWNLOAD_URL no está configurada."
        )
    os.makedirs(os.path.dirname(model_path), exist_ok=True)
    print(f"Descargando modelo desde Firebase Storage ({model_path})...")
    urllib.request.urlretrieve(download_url, model_path)
    print("Modelo descargado correctamente.")


def load_model() -> None:
    global _full_model, _embedding_model

    settings = get_settings()
    model_path = settings.model_path

    _download_model_if_needed(model_path, settings.model_download_url)

    _full_model = tf.keras.models.load_model(model_path, compile=False)

    # Embedding model: salida de la penúltima capa (antes del Dense final)
    _embedding_model = tf.keras.Model(
        inputs=_full_model.input,
        outputs=_full_model.layers[-2].output,
    )


def is_model_loaded() -> bool:
    return _full_model is not None


def _enhance_image(image: Image.Image) -> Image.Image:
    # Brillo: sube ligeramente si la imagen es oscura
    avg_brightness = np.array(image).mean()
    if avg_brightness < 100:
        image = ImageEnhance.Brightness(image).enhance(1.4)
    elif avg_brightness < 140:
        image = ImageEnhance.Brightness(image).enhance(1.15)

    # Contraste
    image = ImageEnhance.Contrast(image).enhance(1.3)

    # Nitidez (sharpening) para fotos borrosas
    image = ImageEnhance.Sharpness(image).enhance(2.0)
    image = image.filter(ImageFilter.UnsharpMask(radius=1, percent=120, threshold=3))

    return image


def _preprocess_image(image_bytes: bytes) -> np.ndarray:
    image = Image.open(io.BytesIO(image_bytes)).convert("RGB")

    # Mejorar calidad antes de pasar al modelo
    image = _enhance_image(image)

    # Escalar manteniendo aspect ratio hasta que el lado más largo sea IMAGE_SIZE
    w, h = image.size
    scale = min(IMAGE_SIZE[0] / w, IMAGE_SIZE[1] / h)
    new_w, new_h = int(w * scale), int(h * scale)
    image = image.resize((new_w, new_h), Image.LANCZOS)

    # Letterbox: centrar en canvas negro 380×380 (sin recortar nada)
    canvas = Image.new("RGB", IMAGE_SIZE, (0, 0, 0))
    offset_x = (IMAGE_SIZE[0] - new_w) // 2
    offset_y = (IMAGE_SIZE[1] - new_h) // 2
    canvas.paste(image, (offset_x, offset_y))

    array = np.array(canvas, dtype=np.float32)
    array = tf.keras.applications.efficientnet.preprocess_input(array)
    return np.expand_dims(array, axis=0)


def analyze_image(image_bytes: bytes) -> tuple[bool, float, list[float]]:
    """
    Preprocesa la imagen UNA sola vez y la pasa a ambos modelos,
    evitando procesar los bytes dos veces.

    Returns:
        dog_detected (bool), confidence (float), embedding (list[float])
    """
    if _full_model is None or _embedding_model is None:
        raise RuntimeError("Model is not loaded. Call load_model() first.")

    try:
        preprocessed = _preprocess_image(image_bytes)
    except Exception:
        raise ValueError("No se pudo procesar la imagen. Verifica que el archivo no esté dañado.")

    # Clasificación
    predictions  = _full_model.predict(preprocessed, verbose=0)
    probs        = predictions[0]
    max_class    = int(np.argmax(probs))
    confidence   = float(probs[max_class])
    dog_detected = (max_class != NOT_A_DOG_INDEX) and (confidence >= CONFIDENCE_THRESHOLD)

    # Embedding (reutiliza el mismo tensor ya preprocesado)
    embedding = _embedding_model.predict(preprocessed, verbose=0)

    return dog_detected, confidence, embedding[0].tolist()
