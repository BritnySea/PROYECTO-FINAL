import numpy as np
from PIL import Image, ImageEnhance, ImageFilter
import io
import tensorflow as tf
from tensorflow.keras.applications.efficientnet import preprocess_input

from app.config import get_settings

# Singleton references
_full_model = None
_embedding_model = None

NOT_A_DOG_INDEX = 83
CONFIDENCE_THRESHOLD = 0.20
IMAGE_SIZE = (380, 380)


def load_model() -> None:
    global _full_model, _embedding_model

    settings = get_settings()
    model_path = settings.model_path

    _full_model = tf.keras.models.load_model(model_path)

    # Embedding model: outputs from the penultimate layer (before the final Dense)
    _embedding_model = tf.keras.Model(
        inputs=_full_model.input,
        outputs=_full_model.layers[-2].output,
    )

    print(f"[model_service] Model loaded from '{model_path}'")
    print(f"[model_service] Embedding size: {_embedding_model.output_shape}")


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
    array = preprocess_input(array)
    return np.expand_dims(array, axis=0)


def is_dog(image_bytes: bytes) -> tuple[bool, float]:
    if _full_model is None:
        raise RuntimeError("Model is not loaded. Call load_model() first.")

    preprocessed = _preprocess_image(image_bytes)
    predictions = _full_model.predict(preprocessed, verbose=0)
    probs = predictions[0]

    max_class = int(np.argmax(probs))
    confidence = float(probs[max_class])

    dog_detected = (max_class != NOT_A_DOG_INDEX) and (confidence >= CONFIDENCE_THRESHOLD)
    return dog_detected, confidence


def get_embedding(image_bytes: bytes) -> list[float]:
    if _embedding_model is None:
        raise RuntimeError("Model is not loaded. Call load_model() first.")

    preprocessed = _preprocess_image(image_bytes)
    embedding = _embedding_model.predict(preprocessed, verbose=0)
    return embedding[0].tolist()
