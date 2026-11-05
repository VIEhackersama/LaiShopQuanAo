# Catalog assets

## Ảnh mẫu đang sử dụng

28 ảnh chụp stock nằm trong `photos/`, tải từ Pexels theo [Pexels License](https://www.pexels.com/license/). `photos/sources.json` và `photos/expanded-sources.json` lưu tên tác giả, trang gốc, URL tải và giấy phép của từng ảnh. Ảnh dùng làm dữ liệu mẫu giao diện, không khẳng định các mẫu chụp hoặc màu ảnh là hàng thực tế của shop. 27 ảnh minh họa catalog và một ảnh giá treo dùng cho hero.

## Thư viện ảnh bổ sung

`library/` chứa 73 ảnh Pexels riêng biệt: 15 ảnh áo thun/sơ mi, 20 ảnh quần, 19 ảnh váy/đầm và 19 ảnh áo khoác. Mỗi ảnh rộng 1200 px, giữ tỷ lệ gốc; đã kiểm tra giải mã ảnh và SHA-256 để loại file trùng. `library/index.html` là trang xem/chọn ảnh; `library/sources.json` và `library/sources.csv` ghi mã ảnh, nhóm trang phục, kích thước, SHA-256, handle tác giả từ URL tải, trang nguồn và giấy phép. Ảnh chưa được gán vào sản phẩm/biến thể của database.

## Asset cũ

`hero-linen.png` được tạo bằng công cụ ImageGen tích hợp cho trang chủ. Prompt: “Create a single premium fashion ecommerce hero photograph, square composition, photorealistic editorial still life. A cream natural linen button down shirt with its sleeves neatly draped and a pair of dark charcoal wide leg trousers on a simple oak hanger or minimalist clothing rail, warm light grey-green plaster studio background, afternoon window light from left, realistic fabric weave and soft long shadows, restrained Vietnamese independent clothing brand feeling, beautiful and quiet. All garments fully within frame, abundant breathing room, no people, no text, no letters, no logo, no watermarks. This is a website background asset, not a UI mockup.”

`product-1.svg` đến `product-6.svg` là minh họa vector ban đầu. Migration V3 chuyển catalog seed sang ảnh thật; các file cũ được giữ để đối chiếu.
