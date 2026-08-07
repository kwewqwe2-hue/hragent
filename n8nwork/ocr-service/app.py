import base64
import binascii
import io
import threading
from typing import Any

import numpy as np
from fastapi import FastAPI, HTTPException, Request
from PIL import Image, UnidentifiedImageError
from pydantic import BaseModel, Field
from rapidocr_onnxruntime import RapidOCR

MAX_IMAGE_BYTES = 10 * 1024 * 1024
ALLOWED_FORMATS = {"JPEG", "PNG"}

app = FastAPI(title="HRAgent Local OCR", docs_url=None, redoc_url=None)
ocr_engine: RapidOCR | None = None
ocr_lock = threading.Lock()


class OcrRequest(BaseModel):
    data: str = Field(min_length=1, description="Image data encoded as base64")
    file_name: str = Field(default="", max_length=255)


def get_ocr_engine() -> RapidOCR:
    if ocr_engine is None:
        raise HTTPException(status_code=503, detail="OCR model is still loading")
    return ocr_engine


def decode_image_bytes(raw: bytes) -> np.ndarray:
    if not raw or len(raw) > MAX_IMAGE_BYTES:
        raise HTTPException(status_code=413, detail="Image must be between 1 byte and 10 MB")

    try:
        with Image.open(io.BytesIO(raw)) as image:
            if image.format not in ALLOWED_FORMATS:
                raise HTTPException(status_code=400, detail="Only JPG, JPEG, and PNG images are supported")
            return np.asarray(image.convert("RGB"))[:, :, ::-1].copy()
    except UnidentifiedImageError as error:
        raise HTTPException(status_code=400, detail="Uploaded file is not a valid image") from error


def decode_image(encoded_data: str) -> np.ndarray:
    value = encoded_data.strip()
    if value.startswith("data:"):
        parts = value.split(",", 1)
        if len(parts) != 2 or ";base64" not in parts[0].lower():
            raise HTTPException(status_code=400, detail="Image data URL must be base64 encoded")
        value = parts[1]

    try:
        raw = base64.b64decode(value, validate=True)
    except (ValueError, binascii.Error) as error:
        raise HTTPException(status_code=400, detail="Image data is not valid base64") from error

    return decode_image_bytes(raw)


def recognize_image(image: np.ndarray, file_name: str = "") -> dict[str, Any]:
    with ocr_lock:
        result, _ = get_ocr_engine()(image)

    rows = result or []
    lines = []
    confidences = []
    for row in rows:
        if not isinstance(row, (list, tuple)) or len(row) < 3:
            continue
        text = str(row[1]).strip()
        if not text:
            continue
        lines.append(text)
        try:
            confidences.append(float(row[2]))
        except (TypeError, ValueError):
            pass

    return {
        "success": True,
        "fileName": file_name,
        "text": "\n".join(lines),
        "lineCount": len(lines),
        "averageConfidence": round(sum(confidences) / len(confidences), 4) if confidences else None,
    }


@app.on_event("startup")
def load_model() -> None:
    global ocr_engine
    ocr_engine = RapidOCR()


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok" if ocr_engine is not None else "starting"}


@app.post("/ocr")
def ocr_image(request: OcrRequest) -> dict[str, Any]:
    return recognize_image(decode_image(request.data), request.file_name)


@app.post("/ocr-file")
async def ocr_file(request: Request) -> dict[str, Any]:
    return recognize_image(decode_image_bytes(await request.body()))
