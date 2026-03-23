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


class OwnerMatchItem(BaseModel):
    report_id: str
    found_dog_photo_url: str
    similarity_percent: float
    reporter_name: str
    reporter_phone: str
    reporter_email: str
    found_dog_size: str
    found_dog_color: str
    found_dog_sex: str
    found_dog_description: str
    reported_at: str


class OwnerMatchesResponse(BaseModel):
    matches: List[OwnerMatchItem]
