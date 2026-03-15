from pydantic import BaseModel
from typing import List


class RegisterLostDogResponse(BaseModel):
    dog_id: str
    message: str


class DogMatch(BaseModel):
    dog_id: str
    name: str
    owner_phone: str
    owner_email: str
    similarity_percent: float
    photo_url: str


class MatchFoundDogResponse(BaseModel):
    matches: List[DogMatch]
    is_dog: bool
    message: str


class ValidatePhotoResponse(BaseModel):
    is_dog: bool
    confidence: float
    message: str
