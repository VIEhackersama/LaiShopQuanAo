import io
from typing import Optional
from fastapi import FastAPI, File, UploadFile, Form, HTTPException
from PIL import Image
import torch
from transformers import CLIPImageProcessor, CLIPVisionModelWithProjection

app = FastAPI(title="LaiShop CLIP Vision Embedding Service")

MODEL_ID = "openai/clip-vit-base-patch32"
print(f"Loading CLIP vision model: {MODEL_ID}...")
processor = CLIPImageProcessor.from_pretrained(MODEL_ID)
model = CLIPVisionModelWithProjection.from_pretrained(MODEL_ID)
model.eval()
print("CLIP vision model loaded and ready!")

@app.get("/health")
def health():
    return {"status": "ok", "model": MODEL_ID}

@app.post("/embed")
async def embed(
    file: UploadFile = File(...),
    x: Optional[float] = Form(None),
    y: Optional[float] = Form(None),
    width: Optional[float] = Form(None),
    height: Optional[float] = Form(None)
):
    try:
        contents = await file.read()
        image = Image.open(io.BytesIO(contents)).convert("RGB")
        img_w, img_h = image.size

        # If region provided, crop it
        if x is not None and y is not None and width is not None and height is not None:
            left = max(0, int(x * img_w))
            top = max(0, int(y * img_h))
            right = min(img_w, int((x + width) * img_w))
            bottom = min(img_h, int((y + height) * img_h))
            if right > left and bottom > top:
                image = image.crop((left, top, right, bottom))

        inputs = processor(images=image, return_tensors="pt")
        with torch.no_grad():
            outputs = model(**inputs)
            embeds = outputs.image_embeds
            embeds = embeds / embeds.norm(p=2, dim=-1, keepdim=True)
            vector = embeds[0].tolist()

        return {"embedding": vector}
    except Exception as e:
        print(f"Error embedding image: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8001)
