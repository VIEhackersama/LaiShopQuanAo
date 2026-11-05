# LaiShopQuanAo

Website một shop: React + TypeScript, Spring Boot 3.5.16 / Java 21, PostgreSQL 17. MVC bốn tầng được ánh xạ View = React, Controller = Controller/Service, DAO = repository/JDBC, Model = 15 entity JPA.

## Chạy bằng Docker Compose

1. Sao chép `.env.example` thành `.env`, đặt `DATABASE_PASSWORD` riêng.
2. Điền `BANK_NAME`, `BANK_ACCOUNT`, `BANK_HOLDER` nếu muốn bật chuyển khoản. Bỏ trống sẽ chỉ cho đặt COD, không sinh thông tin ngân hàng giả.
3. Chạy `docker compose up --build -d` tại thư mục này.
4. Mở `https://localhost`. Caddy cấp chứng chỉ local; import root CA của Caddy vào trust store khi chạy trên máy phát triển. Với domain thật, đặt `SITE_HOST` và cấu hình DNS về máy triển khai.

Compose giữ PostgreSQL, media và trạng thái TLS trong volume. Không dùng `docker compose down -v` khi cần giữ dữ liệu. Chưa tự động triển khai lên Internet.

## Chạy phát triển

Cần Java 21, Node 24 và PostgreSQL 17. Tạo database rỗng cho ứng dụng và một database riêng kết thúc bằng `_test` cho kiểm thử. Tài khoản database cần tạo extension `unaccent` và `pg_trgm` trong database của ứng dụng.

Backend, PowerShell:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk-21'
$env:DATABASE_URL='jdbc:postgresql://127.0.0.1:5432/laishop'
$env:DATABASE_USER='laishop'
$env:DATABASE_PASSWORD='<your-local-password>'
$env:COOKIE_SECURE='false' # Chỉ cho HTTP local; Compose bật Secure
cd backend
.\mvnw.cmd spring-boot:run
```

Frontend, terminal khác:

```powershell
cd frontend
npm ci
npm run dev
```

Mở `http://127.0.0.1:5173`. Vite proxy `/api` sang backend cổng 8080. Tự đăng ký tài khoản; không có tài khoản/mật khẩu demo cài sẵn. Seed gồm 24 sản phẩm, biến thể có giá riêng và ảnh stock Pexels làm mẫu. Nguồn ảnh và giấy phép nằm trong `frontend/public/catalog/photos/`.

## Hợp đồng và dữ liệu

- Schema triển khai: `backend/src/main/resources/db/migration/`; không dùng DDL cũ trong gói bàn giao tháng 10 để tạo database ứng dụng.
- `ProductVariant.price` là nguồn giá duy nhất. Product không có cột price. Cart lấy giá hiện tại; OrderItem giữ snapshot.
- `/api/openapi` và `/api/docs` mô tả API từ backend; bản xuất được lưu ở `backend/openapi.json`.
- Sinh lại frontend types: xuất `/api/openapi` vào file trên rồi chạy `npm run generate:api` trong frontend. Zod kiểm tra response tại runtime; kiểu request lấy từ OpenAPI.
- API có CSRF; gọi `/api/v1/auth/csrf`, gửi token bằng header được trả về cùng cookie session cho các mutation. Danh tính khách lấy từ session, không nhận customerId từ client.
- Checkout cần `Idempotency-Key`. Cùng khóa/nội dung trả lại đơn đã tạo; khác nội dung trả 409. Payment PENDING không có nghĩa đã nhận tiền.
- Ngừng bán thay vì xóa sản phẩm đã được tham chiếu. Không cung cấp API khách hàng để đổi trạng thái tiền, giao vận hay hủy đơn.

## Chatbot tìm kiếm AI

Tab **Trợ lý tìm kiếm AI** hỗ trợ hội thoại nhiều lượt: loại đồ, từ khóa, màu, size, khoảng giá và sort. Ollama/Qwen3 tạo yêu cầu công cụ theo JSON Schema; backend xác thực typed records và Bean Validation, rồi gọi DAO có tham số để đọc catalog. Không để model chạy SQL, tạo sản phẩm, sửa giỏ hoặc đặt hàng. Câu xác nhận số kết quả được tạo từ dữ liệu truy vấn thật; đây là chatbot lập điều kiện tìm kiếm, chưa phải vector search hoặc stylist sinh câu trả lời tự do.

Chạy `ollama serve`, bảo đảm `ollama list` có `qwen3:8b` (nếu chưa có: `ollama pull qwen3:8b`). Trước khi chạy backend local, đặt:

```powershell
$env:AI_ENABLED='true'
$env:AI_OLLAMA_URL='http://127.0.0.1:11434'
$env:AI_MODEL='qwen3:8b'
$env:AI_TIMEOUT_SECONDS='60'
```

Compose đọc các biến tương ứng trong `.env`; địa chỉ host Ollama dùng `host.docker.internal` trên Docker Desktop. Ollama phải cho phép kết nối từ container nếu dùng cách này; bản local native đã được kiểm thử, cấu hình Docker/Ollama cần xác minh trên máy triển khai. Không chuyển ngầm sang API bên ngoài khi Ollama lỗi. Tắt AI bằng `AI_ENABLED=false`; tìm kiếm thường vẫn dùng endpoint riêng.

API: `GET /api/v1/search/ai/capabilities`, `POST /api/v1/search/ai/chat`, `GET /api/v1/search/ai/conversations/{id}`. POST nhận `message` (1–500 ký tự), `conversationId` và `expectedVersion` (cùng null ở lượt đầu). Server giữ lịch sử/điều kiện; phiên tìm kiếm hoặc Customer quyết định quyền truy cập. Xung đột phiên bản trả 409, provider không có trả 503, JSON model sai trả 502. Không lưu lượt chat thành công khi model lỗi. Context gồm tối đa 12 tin gần nhất; hội thoại dọn sau 7 ngày. Chat UI giữ hội thoại trong lần mở panel hiện tại; tải lại trang có thể bắt đầu hội thoại mới. Giới hạn 12 lượt chat/phút/IP trên một backend.

Thử “Tìm áo màu đen size M dưới 300 nghìn”, sau đó “Giữ loại áo, màu và size, chỉ lấy dưới 200 nghìn và xếp giá rẻ trước”. Bộ lọc được hiển thị để khách kiểm tra AI hiểu đúng. Khách có thể sửa yêu cầu hoặc dùng bộ lọc thường khi AI hiểu sai; backend không tự nới lỏng điều kiện.

Catalog mẫu có 24 sản phẩm và 28 ảnh stock local. Nguồn/tác giả/giấy phép lưu trong `frontend/public/catalog/photos/sources.json` và `expanded-sources.json`. Ảnh minh họa phong cách, chưa phải ảnh inventory thực của shop. Size, màu, chất liệu và giá là dữ liệu demo khai báo, không suy ra tự động từ ảnh.

Chạy test AI thật bằng `$env:RUN_AI_TESTS='true'` trước `mvnw test`; cần Ollama hoạt động và database riêng `_test`. Không dùng mock AI. Các bài AI kiểm tra nhiều lượt, lọc biến thể thực, quyền sở hữu, xung đột phiên bản, kết quả rỗng và câu hỏi làm rõ. Playwright AI cũng gọi model thật; backend demo cần bật AI.

## Voice / Image — điểm bàn giao

Hai interface trong package `search` là `SpeechRecognitionPort` và `ImageSimilarityPort`. Đăng ký đúng một Spring bean cho mỗi port cần bật. Không có bean thì capability tương ứng là false và endpoint upload trả 503 `SEARCH_PROVIDER_NOT_CONFIGURED`; không tạo query thành công.

Port nhận dữ liệu media đã kiểm tra kích thước/chữ ký file; adapter phải giải mã file thật, kiểm tra nội dung, chống ảnh vượt giới hạn tài nguyên, đo thời lượng âm thanh và từ chối dữ liệu hỏng. Speech trả phiên âm tiếng Việt và thời lượng thực 1–30 giây. Image nhận vùng crop chuẩn hóa [0,1], trả productId duy nhất và điểm theo thứ tự tương đồng, đã áp dụng ngưỡng do người phụ trách chọn. Backend kiểm tra sản phẩm đủ điều kiện và áp dụng bộ lọc/sort.

Adapter phải cấu hình connect/read timeout hữu hạn; ném `ProviderUnavailableException` cho lỗi tạm thời để backend retry tối đa hai lần, ghi cảnh báo và trả lỗi. Lỗi input dùng `ApiException` 400/422, không retry. Không gọi provider trong database transaction. Không triển khai model, API trả phí, pgvector hoặc embedding giả trong bản nền này.

Media lưu volume riêng, không phục vụ như file public. `/api/v1/search/queries/{id}/media` kiểm tra quyền sở hữu. Query/media không còn tham chiếu được dọn sau 7 ngày. Query của khách vãng lai gắn session tìm kiếm; không tự tạo Customer giả.

## Kiểm thử

```powershell
$env:TEST_DATABASE_URL='jdbc:postgresql://127.0.0.1:5432/laishop_test'
$env:TEST_DATABASE_USER='laishop'
$env:TEST_DATABASE_PASSWORD='<test-db-password>'
cd backend
.\mvnw.cmd test
```

Test chạy trên PostgreSQL thật và xóa dữ liệu khách của database test trước mỗi ca; có kiểm tra tên database `_test`. Không trỏ vào database ứng dụng. Benchmark 10.000 sản phẩm nằm trong transaction được rollback.

Khi frontend/backend phát triển đang chạy, `npm run test:e2e` trong frontend chạy desktop và mobile emulation trên Edge. Có thể đặt `PLAYWRIGHT_CHANNEL=chromium` và cài browser bằng `npx playwright install chromium` để chạy trong CI. E2E tạo tài khoản/đơn thật trong database đang kết nối; chỉ chạy trên môi trường thử nghiệm. Để kiểm tra chuyển khoản, cấu hình thông tin ngân hàng demo rõ ràng trước khi khởi động backend.

## Sao lưu và vận hành

Service `backup` dùng pg_dump mỗi 24 giờ, giữ dump 7 ngày trong `backups/`. Theo dõi `docker compose logs backup`; lỗi backup làm job kết thúc và được khởi động lại. Media cần sao lưu volume riêng nếu adapter được bật. Kiểm tra khôi phục vào database mới trước khi thay thế dữ liệu vận hành; không restore đè database đang chạy.

Rate limit hiện tại lưu trong bộ nhớ một backend (20 request auth/phút/IP, 120 search/phút/IP), phù hợp một instance trong Compose. Nếu chạy nhiều instance cần rate limiting chung ở proxy. Log có traceId; không ghi mật khẩu, cookie hoặc nội dung media. Chỉ tin forwarded headers từ reverse proxy nội bộ; backend không mở port public trong Compose.

Đổi trả, đánh giá, admin/seller, vận chuyển thật và xác nhận thanh toán tự động ngoài phạm vi. Uptime 99% cần đo trên môi trường vận hành; các kiểm thử local không chứng minh SLA.
