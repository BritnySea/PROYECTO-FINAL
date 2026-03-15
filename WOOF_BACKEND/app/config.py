from functools import lru_cache
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    firebase_credentials_path: str = "firebase_credentials.json"
    model_path: str = "ml/woof_model_v1.h5"
    cloudinary_cloud_name: str = ""
    cloudinary_api_key: str = ""
    cloudinary_api_secret: str = ""

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


@lru_cache()
def get_settings() -> Settings:
    return Settings()
