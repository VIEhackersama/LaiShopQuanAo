-- Replace only the original seed illustrations; existing orders and variant data stay intact.
UPDATE product_images SET image_url='/catalog/photos/tee-white.jpg',alt_text='Ảnh mẫu áo thun trắng — Skylar Kang / Pexels' WHERE image_url='/catalog/product-1.svg';
UPDATE product_images SET image_url='/catalog/photos/shirt-linen.jpg',alt_text='Ảnh mẫu sơ mi linen trên giá treo — Darina Belonogova / Pexels' WHERE image_url='/catalog/product-2.svg';
UPDATE product_images SET image_url='/catalog/photos/trousers-wide.jpg',alt_text='Ảnh mẫu phối đồ quần ống rộng — Marvellous Adu / Pexels' WHERE image_url='/catalog/product-3.svg';
UPDATE product_images SET image_url='/catalog/photos/dress-white.jpg',alt_text='Ảnh mẫu váy trắng trên giá treo — Maghfira Deandra / Pexels' WHERE image_url='/catalog/product-4.svg';
UPDATE product_images SET image_url='/catalog/photos/knit-beige.jpg',alt_text='Ảnh mẫu chi tiết áo dệt kim — Polina Tankilevitch / Pexels' WHERE image_url='/catalog/product-5.svg';
UPDATE product_images SET image_url='/catalog/photos/shorts-beige.jpg',alt_text='Ảnh mẫu phối quần short màu be — Pavel Danilyuk / Pexels' WHERE image_url='/catalog/product-6.svg';
INSERT INTO product_images(product_id,image_url,alt_text,display_order) VALUES
(1,'/catalog/photos/tee-folded.jpg','Ảnh mẫu áo thun trắng gấp gọn — dayong tien / Pexels',1),
(2,'/catalog/photos/shirt-beige.jpg','Ảnh mẫu sơ mi màu be — Marina Podrez / Pexels',1),
(4,'/catalog/photos/dress-satin.jpg','Ảnh mẫu váy satin trắng — Alexander Mass / Pexels',1);
