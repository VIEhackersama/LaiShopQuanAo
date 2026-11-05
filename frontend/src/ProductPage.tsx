import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, ShoppingBag } from '@phosphor-icons/react';
import { api, cartSchema, productSchema, money } from './api';
import { useAuth, Loading, ErrorMessage } from './shared';

export function ProductPage() {
  const { id } = useParams(); const navigate = useNavigate(); const client = useQueryClient(); const { customer } = useAuth();
  const product = useQuery({ queryKey: ['product', id], queryFn: () => api(`/products/${id}`, productSchema, {}) });
  const [size, setSize] = useState(''); const [color, setColor] = useState(''); const [quantity, setQuantity] = useState(1); const [image, setImage] = useState(0);
  const [message, setMessage] = useState(''); const [reconciling, setReconciling] = useState(false);
  const variant = product.data?.variants.find(v => v.size === size && v.color === color);
  const add = useMutation({ mutationFn: async () => {
    if (!variant) throw new Error('Vui lòng chọn size và màu.');
    try { return await api('/cart/items', cartSchema, { method: 'POST', body: JSON.stringify({ variantId: variant.id, quantity }) }); }
    catch (error) {
      if (error instanceof TypeError) { setReconciling(true); await api('/cart', cartSchema, {}).then(cart => client.setQueryData(['cart'], cart)); setMessage('Kết nối bị gián đoạn. Đã tải lại giỏ; hãy kiểm tra trước khi thêm lần nữa.'); }
      throw error;
    }
  }, onSuccess: cart => { client.setQueryData(['cart'], cart); setMessage('Đã thêm vào giỏ hàng.'); } });
  if (product.isPending) return <main><Loading /></main>;
  if (product.isError) return <main><ErrorMessage error={product.error} /></main>;
  const p = product.data;
  return <main className="detail-page"><Link className="back-link" to="/"><ArrowLeft /> Bộ sưu tập</Link><div className="product-detail"><div><div className="detail-image">{p.images[image] && <img src={p.images[image].imageUrl} alt={p.images[image].altText} />}</div>{p.images.length > 1 && <div className="thumbnails">{p.images.map((img, i) => <button key={img.imageUrl} onClick={() => setImage(i)} aria-label={`Xem ảnh ${i + 1}`}><img src={img.imageUrl} alt={img.altText} /></button>)}</div>}</div><section className="product-info"><p className="eyebrow">LAI EVERYDAY / {p.category}</p><h1>{p.name}</h1><p className="detail-price" data-testid="variant-price">{variant ? money(variant.price) : 'Chọn size và màu để xem giá'}</p><p className="description">{p.description}</p><fieldset><legend>Kích cỡ {size && `— ${size}`}</legend><div className="chips">{[...new Set(p.variants.map(v => v.size))].map(s => <button data-testid={`size-${s}`} key={s} className={size === s ? 'selected' : ''} aria-pressed={size === s} onClick={() => setSize(s)}>{s}</button>)}</div></fieldset><fieldset><legend>Màu sắc {color && `— ${color}`}</legend><div className="chips">{[...new Set(p.variants.map(v => v.color))].map(c => <button data-testid={`color-${c === 'Kem' ? 'cream' : c === 'Đen' ? 'black' : c}`} key={c} className={color === c ? 'selected' : ''} aria-pressed={color === c} onClick={() => setColor(c)}>{c}</button>)}</div></fieldset><label className="quantity-label">Số lượng<input data-testid="product-quantity" type="number" min="1" max={variant?.stockQuantity ?? 10000} value={quantity} onChange={e => setQuantity(Number(e.target.value))} /></label><p className="availability">{variant ? variant.stockQuantity > 0 ? `Còn ${variant.stockQuantity} sản phẩm · ${variant.sku}` : 'Biến thể này đã hết hàng' : size && color ? 'Không có biến thể size/màu này' : 'Hãy chọn size và màu bạn thích'}</p><button data-testid="add-to-cart" className="button full" disabled={!variant || quantity < 1 || !Number.isInteger(quantity) || quantity > variant.stockQuantity || add.isPending || reconciling} onClick={() => customer ? add.mutate() : navigate(`/login?next=${encodeURIComponent(`/products/${id}`)}`)}><ShoppingBag size={20} />{add.isPending ? 'Đang thêm…' : 'Thêm vào giỏ hàng'}</button><ErrorMessage error={add.error} />{message && <p className="notice" role="status" data-testid="cart-notice">{message} <Link to="/cart">Xem giỏ hàng</Link>{reconciling && <button onClick={() => setReconciling(false)}>Tôi đã kiểm tra giỏ</button>}</p>}<div className="detail-note"><p>Giá hiển thị theo đúng size và màu đã chọn.</p><p>Tồn kho được kiểm tra lại khi bạn xác nhận đơn hàng.</p></div></section></div></main>;
}
