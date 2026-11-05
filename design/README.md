# Mô hình hiện hành

`analysis.puml` là sơ đồ phân tích 15 lớp của ứng dụng. Schema thực thi nằm trong Flyway; entity JPA được Hibernate validate với schema lúc khởi động.

Giá nằm tại ProductVariant; minPrice/maxPrice của Product là thuộc tính suy ra. SearchQuery có Customer tùy chọn và mã phiên tìm kiếm; Cart có version để kiểm tra checkout. Customer có passwordHash, không trả hash qua DTO.

Các tệp VPP/ảnh/ZIP trong thư mục bàn giao ngoài repository là bản chụp lịch sử trước khi chốt giá biến thể và đăng nhập thật. Không dùng chúng làm schema triển khai và không ghi đè gói đã bàn giao. Khi xuất bản nộp mới, dùng sơ đồ hiện hành và đối chiếu Flyway thay vì bản giá chung Product cũ.
