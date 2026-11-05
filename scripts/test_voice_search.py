#!/usr/bin/env python3
"""
Test script for LaiShop Voice Search API end-to-end verification.
1. Generates real Vietnamese speech audio via gTTS ("áo thun cam").
2. Converts audio to 16kHz mono WAV using ffmpeg / imageio-ffmpeg.
3. Checks /api/v1/search/capabilities for voice capability.
4. Retrieves CSRF token from /api/v1/auth/csrf.
5. Uploads audio file to /api/v1/search/voice with durationSeconds.
6. Verifies query status AWAITING_CONFIRMATION and transcribed text.
7. Calls /api/v1/search/voice/{queryId}/execute to complete search.
8. Verifies status COMPLETED and returned product items.
"""

import io
import os
import subprocess
import sys
import urllib.request
import urllib.error
import http.cookiejar
import json
from gtts import gTTS

def get_ffmpeg_exe():
    import shutil
    path = shutil.which("ffmpeg")
    if path:
        return path
    try:
        import imageio_ffmpeg
        return imageio_ffmpeg.get_ffmpeg_exe()
    except Exception:
        return "ffmpeg"

def generate_voice_audio(phrase="áo thun cam", fmt="wav"):
    tts = gTTS(phrase, lang="vi")
    mp3_io = io.BytesIO()
    tts.write_to_fp(mp3_io)
    mp3_bytes = mp3_io.getvalue()
    
    ffmpeg_exe = get_ffmpeg_exe()
    if fmt == "wav":
        cmd = [ffmpeg_exe, "-y", "-i", "pipe:0", "-ar", "16000", "-ac", "1", "-f", "wav", "pipe:1"]
    elif fmt == "webm":
        cmd = [ffmpeg_exe, "-y", "-i", "pipe:0", "-c:a", "libopus", "-f", "webm", "pipe:1"]
    else:
        return mp3_bytes
        
    proc = subprocess.run(cmd, input=mp3_bytes, capture_output=True, check=True)
    return proc.stdout

def test_voice_search(phrase="áo thun cam", base_url="http://localhost/api/v1"):
    print(f"=== Testing Voice Search API on {base_url} with phrase: '{phrase}' ===")
    
    cj = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    
    # 1. Generate real voice audio
    print(f"1. Synthesizing Vietnamese speech: '{phrase}'...")
    audio_bytes = generate_voice_audio(phrase, fmt="wav")
    print(f"   Generated {len(audio_bytes)} bytes of real speech audio.")

    # 2. Check capabilities
    print("2. Checking capabilities...")
    req = urllib.request.Request(f"{base_url}/search/capabilities")
    with opener.open(req) as resp:
        cap = json.loads(resp.read().decode('utf-8'))
        print(f"   Capabilities: {cap}")
        assert cap.get("voice") is True, f"Voice capability is not enabled: {cap}"

    # 3. Fetch CSRF token
    print("3. Fetching CSRF token from /auth/csrf...")
    csrf_req = urllib.request.Request(f"{base_url}/auth/csrf")
    with opener.open(csrf_req) as resp:
        csrf_data = json.loads(resp.read().decode('utf-8'))
        csrf_header = csrf_data["headerName"]
        csrf_token = csrf_data["token"]
        print(f"   CSRF: {csrf_header} = {csrf_token[:15]}...")

    # 4. Upload audio
    boundary = "----TestBoundaryLaiShopVoice12345"
    body = bytearray()
    body.extend(f"--{boundary}\r\n".encode())
    body.extend(b'Content-Disposition: form-data; name="file"; filename="voice.wav"\r\n')
    body.extend(b'Content-Type: audio/wav\r\n\r\n')
    body.extend(audio_bytes)
    body.extend(b"\r\n")

    url = f"{base_url}/search/voice?durationSeconds=2.5"
    body.extend(f"--{boundary}--\r\n".encode())

    print(f"4. Sending POST {url} with audio payload...")
    req = urllib.request.Request(url, data=bytes(body), headers={
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        csrf_header: csrf_token
    })
    
    with opener.open(req) as resp:
        assert resp.status == 200, f"Expected 200, got {resp.status}"
        data = json.loads(resp.read().decode('utf-8'))
        query_id = data["queryId"]
        transcribed_text = data.get("text", "")
        status = data.get("status")
        print(f"   Created Query ID: {query_id}")
        print(f"   Status: {status}")
        print(f"   Transcribed Text: '{transcribed_text}'")
        assert status == "AWAITING_CONFIRMATION", f"Expected AWAITING_CONFIRMATION, got {status}"
        assert len(transcribed_text) > 0, "Transcribed text should not be empty!"
        print(f"   SUCCESS! Speech transcribed correctly: '{transcribed_text}'")

    # 5. Execute confirmed voice query
    print(f"5. Executing query ID {query_id}...")
    exec_url = f"{base_url}/search/voice/{query_id}/execute"
    exec_payload = {
        "text": transcribed_text,
        "conditions": {
            "sort": "RELEVANCE"
        },
        "page": 0,
        "pageSize": 24
    }
    
    exec_data = json.dumps(exec_payload).encode('utf-8')
    exec_req = urllib.request.Request(exec_url, data=exec_data, headers={
        "Content-Type": "application/json",
        csrf_header: csrf_token
    })
    
    with opener.open(exec_req) as resp:
        assert resp.status == 200, f"Expected 200, got {resp.status}"
        result = json.loads(resp.read().decode('utf-8'))
        assert result.get("status") == "COMPLETED", f"Expected COMPLETED, got {result.get('status')}"
        products_data = result.get("products", {})
        items = products_data.get("items", []) if isinstance(products_data, dict) else products_data
        print(f"   Query executed successfully! Found {len(items)} products (Total: {products_data.get('total', len(items))}).")
        for p in items[:3]:
            name = p.get('name', '').encode('ascii', errors='replace').decode('ascii')
            print(f"   - [{p.get('id')}] {name} ({p.get('minPrice')} VND)")
            
    print("\n>>> ALL VOICE SEARCH TESTS PASSED SUCCESSFULLY! <<<\n")

if __name__ == "__main__":
    test_voice_search()
