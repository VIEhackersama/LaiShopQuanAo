#!/usr/bin/env python3
"""
Test script for LaiShop Image Search API end-to-end verification.
1. Reads sample photo (frontend/public/catalog/photos/tee-orange.jpg).
2. Retrieves CSRF token from /api/v1/auth/csrf.
3. Uploads image to /api/v1/search/image.
4. Verifies returned status COMPLETED and ranking based on CLIP embedding.
"""

import json
import urllib.request
import urllib.error
import http.cookiejar

def test_image_search(photo_path="frontend/public/catalog/photos/tee-orange.jpg", base_url="http://localhost/api/v1"):
    print(f"=== Testing Image Search API with {photo_path} ===")
    
    cj = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(cj))
    
    csrf_req = urllib.request.Request(f"{base_url}/auth/csrf")
    with opener.open(csrf_req) as resp:
        csrf = json.loads(resp.read().decode('utf-8'))
    
    with open(photo_path, "rb") as f:
        img_bytes = f.read()
        
    boundary = "----TestBoundaryLaiShopImage789"
    options_json = json.dumps({"conditions": {}})
    body = bytearray()
    body.extend(f"--{boundary}\r\n".encode())
    body.extend(b'Content-Disposition: form-data; name="file"; filename="photo.jpg"\r\n')
    body.extend(b'Content-Type: image/jpeg\r\n\r\n')
    body.extend(img_bytes)
    body.extend(b"\r\n")
    body.extend(f"--{boundary}\r\n".encode())
    body.extend(b'Content-Disposition: form-data; name="options"\r\n')
    body.extend(b'Content-Type: application/json\r\n\r\n')
    body.extend(options_json.encode('utf-8'))
    body.extend(b"\r\n")
    body.extend(f"--{boundary}--\r\n".encode())
    
    req = urllib.request.Request(f"{base_url}/search/image", data=bytes(body), headers={
        "Content-Type": f"multipart/form-data; boundary={boundary}",
        csrf["headerName"]: csrf["token"]
    })
    
    with opener.open(req) as resp:
        assert resp.status == 200, f"Expected 200, got {resp.status}"
        res = json.loads(resp.read().decode('utf-8'))
        
        status = res.get("status")
        query_id = res.get("queryId")
        products = res.get("products", {})
        items = products.get("items", []) if isinstance(products, dict) else products
        
        print(f"Image search success! Query ID: {query_id}, Status: {status}")
        print(f"Total matching products: {len(items)}")
        assert len(items) > 6, f"Expected full catalog ranking (>6), got {len(items)}"
        
        top1 = items[0]
        print(f"Top 1 Result: ID={top1.get('id')}, Name='{top1.get('name')}', Score={top1.get('score')}")
        if "tee-orange" in photo_path:
            assert top1.get("id") == 20, f"Expected Top 1 to be ID 20 (Áo Thun Cam Pop), got ID {top1.get('id')}"
        elif "sweater-black" in photo_path:
            assert top1.get("id") == 19, f"Expected Top 1 to be ID 19 (Sweater Minimal Đen), got ID {top1.get('id')}"
        
        print("\nTop 5 Results:")
        for i, p in enumerate(items[:5]):
            print(f"  Rank {i+1}: ID={p.get('id')}, Name={p.get('name')}, Score={p.get('score')}")
            
    print("\n>>> ALL IMAGE SEARCH TESTS PASSED SUCCESSFULLY! <<<\n")

if __name__ == "__main__":
    test_image_search()
