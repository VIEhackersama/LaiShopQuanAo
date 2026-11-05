import io
import os
import shutil
import subprocess
import tempfile
from typing import Optional

from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from PIL import Image
try:
    import speech_recognition as sr
    recognizer = sr.Recognizer()
except ImportError:
    sr = None
    recognizer = None

def get_ffmpeg_binary() -> str:
    path = shutil.which("ffmpeg")
    if path:
        return path
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"

def normalize_language(lang: Optional[str]) -> str:
    if not lang:
        return "vi-VN"
    lang = lang.strip()
    if lang.lower() in ("vi", "vi_vn", "vi-vn"):
        return "vi-VN"
    if lang.lower() in ("en", "en_us", "en-us"):
        return "en-US"
    return lang

import torch
from transformers import CLIPImageProcessor, CLIPVisionModelWithProjection

app = FastAPI(title="LaiShop Vision and Speech Service (CLIP + Speech-to-Text)")

MODEL_ID = "openai/clip-vit-base-patch32"
print(f"Loading CLIP vision model: {MODEL_ID}...")
processor = CLIPImageProcessor.from_pretrained(MODEL_ID)
model = CLIPVisionModelWithProjection.from_pretrained(MODEL_ID)
model.eval()
print("CLIP vision model loaded and ready!")

@app.get("/health")
def health():
    return {
        "status": "ok",
        "model": MODEL_ID,
        "stt": "Google Web Speech API (vi-VN)"
    }

@app.post("/embed")
async def embed(
    file: Optional[UploadFile] = File(None),
    image: Optional[UploadFile] = File(None),
    x: Optional[float] = Form(None),
    y: Optional[float] = Form(None),
    width: Optional[float] = Form(None),
    height: Optional[float] = Form(None)
):
    actual_file = file or image
    if not actual_file:
        raise HTTPException(status_code=422, detail="Vui lòng cung cấp tệp ảnh (field 'file' hoặc 'image').")

    try:
        contents = await actual_file.read()
        pil_image = Image.open(io.BytesIO(contents)).convert("RGB")
        img_w, img_h = pil_image.size

        # If region provided, crop it
        if x is not None and y is not None and width is not None and height is not None:
            left = max(0, int(x * img_w))
            top = max(0, int(y * img_h))
            right = min(img_w, int((x + width) * img_w))
            bottom = min(img_h, int((y + height) * img_h))
            if right > left and bottom > top:
                pil_image = pil_image.crop((left, top, right, bottom))

        inputs = processor(images=pil_image, return_tensors="pt")
        with torch.no_grad():
            outputs = model(**inputs)
            embeds = outputs.image_embeds
            embeds = embeds / embeds.norm(p=2, dim=-1, keepdim=True)
            vector = embeds[0].tolist()

        return {"embedding": vector}
    except HTTPException:
        raise
    except Exception as e:
        print(f"Error embedding image: {e}")
        raise HTTPException(status_code=500, detail=str(e))

async def handle_transcription(file: UploadFile, language: Optional[str] = None):
    try:
        contents = await file.read()
        if not contents:
            raise HTTPException(status_code=400, detail="Tệp âm thanh trống.")

        # Determine extension if available
        ext = ".webm"
        if file.filename and "." in file.filename:
            ext = "." + file.filename.rsplit(".", 1)[-1].lower()
        elif file.content_type:
            if "wav" in file.content_type:
                ext = ".wav"
            elif "mp4" in file.content_type:
                ext = ".mp4"
            elif "ogg" in file.content_type:
                ext = ".ogg"
            elif "webm" in file.content_type:
                ext = ".webm"

        # Save to temp file so ffmpeg can seek container headers if necessary
        with tempfile.NamedTemporaryFile(suffix=ext, delete=False) as tmp:
            tmp.write(contents)
            tmp_path = tmp.name

        try:
            ffmpeg_exe = get_ffmpeg_binary()
            # Convert to 16kHz mono WAV for SpeechRecognition
            cmd = [
                ffmpeg_exe,
                "-y",
                "-i", tmp_path,
                "-ar", "16000",
                "-ac", "1",
                "-f", "wav",
                "pipe:1"
            ]
            proc = subprocess.run(cmd, capture_output=True, check=True)
            wav_bytes = proc.stdout
        finally:
            if os.path.exists(tmp_path):
                try:
                    os.remove(tmp_path)
                except Exception:
                    pass

        target_lang = normalize_language(language)

        try:
            with sr.AudioFile(io.BytesIO(wav_bytes)) as source:
                audio_data = recognizer.record(source)
            text = recognizer.recognize_google(audio_data, language=target_lang)
            return {"text": text}
        except sr.UnknownValueError:
            # Unintelligible or silence
            return {"text": ""}
        except sr.RequestError as e:
            raise HTTPException(status_code=502, detail=f"Lỗi kết nối dịch vụ nhận dạng giọng nói: {e}")

    except HTTPException:
        raise
    except subprocess.CalledProcessError as e:
        err = e.stderr.decode("utf-8", errors="ignore") if e.stderr else str(e)
        raise HTTPException(status_code=400, detail=f"Không thể giải mã tệp âm thanh: {err}")
    except Exception as e:
        print(f"Error transcribing audio: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/transcribe")
async def transcribe(
    file: Optional[UploadFile] = File(None),
    audio: Optional[UploadFile] = File(None),
    language: Optional[str] = Form(None)
):
    actual_file = file or audio
    if not actual_file:
        raise HTTPException(status_code=422, detail="Vui lòng cung cấp tệp âm thanh (field 'file' hoặc 'audio').")
    return await handle_transcription(actual_file, language)

# OpenAI-compatible transcription endpoints
@app.post("/audio/transcriptions")
@app.post("/v1/audio/transcriptions")
async def transcribe_openai_compatible(
    file: Optional[UploadFile] = File(None),
    audio: Optional[UploadFile] = File(None),
    model: Optional[str] = Form(None),
    language: Optional[str] = Form(None),
    prompt: Optional[str] = Form(None),
    response_format: Optional[str] = Form(None)
):
    actual_file = file or audio
    if not actual_file:
        raise HTTPException(status_code=422, detail="Vui lòng cung cấp tệp âm thanh (field 'file' hoặc 'audio').")
    return await handle_transcription(actual_file, language)

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
