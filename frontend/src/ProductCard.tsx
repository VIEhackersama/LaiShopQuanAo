import { Link } from 'react-router-dom';
import { ArrowUpRight } from '@phosphor-icons/react';
import type { z } from 'zod';
import { money, type cardSchema } from './api';

export function ProductCard({ product }: { product: z.infer<typeof cardSchema> }) {
  return <Link to={`/products/${product.id}`} className="product-card" data-testid={`product-${product.id}`}><div className="product-image">{product.imageUrl ? <img src={product.imageUrl} alt={product.name} loading="lazy" /> : <span>Chưa có ảnh</span>}<span className="product-arrow"><ArrowUpRight size={20} /></span>{!product.inStock && <span className="stock-badge">Hết hàng</span>}</div><div className="product-meta"><h3>{product.name}</h3><span className="colors-count">{product.colors.length} màu</span></div><p>{money(product.minPrice)}{product.maxPrice !== product.minPrice && ` – ${money(product.maxPrice)}`}</p></Link>;
}
