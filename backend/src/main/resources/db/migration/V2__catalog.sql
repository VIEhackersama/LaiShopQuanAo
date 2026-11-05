INSERT INTO categories(name,description) VALUES ('Áo','Những thiết kế mặc hằng ngày'),('Quần','Phom dáng thoải mái'),('Váy','Nhẹ nhàng và tự nhiên');
INSERT INTO products(category_id,name,description) VALUES
(1,'Áo thun Everyday','Cotton mềm, phom suông thoải mái. Một lựa chọn đơn giản cho mỗi ngày.'),
(1,'Sơ mi Linen','Chất liệu linen thoáng nhẹ, cổ bẻ và đường may tinh gọn.'),
(2,'Quần Wide Leg','Ống rộng vừa phải, cạp cao. Dễ kết hợp cùng áo thun hoặc sơ mi.'),
(3,'Váy Midi Flow','Dáng midi mềm mại, phù hợp những ngày dạo phố.'),
(1,'Áo Knit Essential','Dệt kim nhẹ, bề mặt mềm, phom vừa vặn.'),
(2,'Quần Short Weekend','Thiết kế tối giản với túi tiện dụng cho cuối tuần.');
INSERT INTO product_variants(product_id,sku,size,color,price,stock_quantity)
SELECT p.id,'LAI-'||p.id||'-'||s.size||'-'||c.code,s.size,c.color,
CASE p.id WHEN 1 THEN 189000 WHEN 2 THEN 389000 WHEN 3 THEN 429000 WHEN 4 THEN 549000 WHEN 5 THEN 329000 ELSE 259000 END + CASE WHEN s.size='L' THEN 20000 ELSE 0 END,
CASE WHEN p.id=5 AND s.size='L' THEN 0 ELSE 12 END
FROM products p CROSS JOIN (VALUES ('S'),('M'),('L')) s(size) CROSS JOIN (VALUES ('Kem','CRM'),('Đen','BLK')) c(color,code);
INSERT INTO product_images(product_id,image_url,alt_text,display_order)
SELECT id,'/catalog/product-'||id||'.svg',name,0 FROM products;
