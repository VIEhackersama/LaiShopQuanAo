import io
import os
import subprocess
import tempfile
import pytest
from fastapi.testclient import TestClient
from gtts import gTTS
import imageio_ffmpeg
from app import app

client = TestClient(app)

def generate_audio(text: str, fmt: str) -> bytes:
    ffmpeg_exe = imageio_ffmpeg.get_ffmpeg_exe()
    tts = gTTS(text, lang="vi")
    mp3_io = io.BytesIO()
    tts.write_to_fp(mp3_io)
    mp3_bytes = mp3_io.getvalue()

    if fmt == "webm":
        proc = subprocess.run(
            [ffmpeg_exe, "-y", "-i", "pipe:0", "-c:a", "libopus", "-f", "webm", "pipe:1"],
            input=mp3_bytes, capture_output=True, check=True
        )
        return proc.stdout
    elif fmt == "mp4":
        with tempfile.NamedTemporaryFile(suffix=".mp4", delete=False) as f:
            tmp_path = f.name
        try:
            subprocess.run(
                [ffmpeg_exe, "-y", "-i", "pipe:0", "-c:a", "aac", tmp_path],
                input=mp3_bytes, capture_output=True, check=True
            )
            with open(tmp_path, "rb") as f:
                return f.read()
        finally:
            if os.path.exists(tmp_path):
                os.remove(tmp_path)
    elif fmt == "wav":
        proc = subprocess.run(
            [ffmpeg_exe, "-y", "-i", "pipe:0", "-ar", "16000", "-ac", "1", "-f", "wav", "pipe:1"],
            input=mp3_bytes, capture_output=True, check=True
        )
        return proc.stdout
    return mp3_bytes

def test_transcribe_webm():
    audio = generate_audio("áo sơ mi trắng", "webm")
    response = client.post(
        "/transcribe",
        files={"file": ("recording.webm", audio, "audio/webm")},
        data={"language": "vi"}
    )
    assert response.status_code == 200
    assert "text" in response.json()
    assert len(response.json()["text"]) > 0

def test_transcribe_mp4():
    audio = generate_audio("quần short", "mp4")
    response = client.post(
        "/transcribe",
        files={"file": ("recording.mp4", audio, "audio/mp4")},
        data={"language": "vi"}
    )
    assert response.status_code == 200
    assert "text" in response.json()
    assert len(response.json()["text"]) > 0

def test_transcribe_wav():
    audio = generate_audio("váy nữ", "wav")
    response = client.post(
        "/transcribe",
        files={"file": ("recording.wav", audio, "audio/wav")}
    )
    assert response.status_code == 200
    assert "text" in response.json()
    assert len(response.json()["text"]) > 0

def test_transcribe_openai_format():
    audio = generate_audio("quần jean", "webm")
    response = client.post(
        "/audio/transcriptions",
        files={"file": ("audio.webm", audio, "audio/webm")},
        data={"model": "gpt-transcribe", "language": "vi"}
    )
    assert response.status_code == 200
    assert "text" in response.json()
    assert len(response.json()["text"]) > 0

if __name__ == "__main__":
    test_transcribe_webm()
    test_transcribe_mp4()
    test_transcribe_wav()
    test_transcribe_openai_format()
    print("All transcribe tests passed successfully!")
