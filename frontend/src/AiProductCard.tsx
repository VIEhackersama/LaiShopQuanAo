import { Link } from 'react-router-dom';
import { ArrowUpRight } from '@phosphor-icons/react';
import type { z } from 'zod';
import { money, type cardSchema } from './api';

export function AiProductCard({ product }: { product: z.infer<typeof cardSchema> }) {
  return <Link to={`/products/${product.id}`} className="ai-product-card" data-testid={`product-${product.id}`}>
    <div className="ai-product-thumbnail">{product.imageUrl ? <img src={product.imageUrl} alt={product.name} loading="lazy" /> : <span>Chưa có ảnh</span>}</div>
    <div className="ai-product-description"><h3>{product.name}</h3><p className="ai-product-colors">{product.colors.join(' · ')}</p><strong>{money(product.minPrice)}{product.maxPrice !== product.minPrice && ` – ${money(product.maxPrice)}`}</strong><span className="ai-product-stock">{product.inStock ? 'Còn hàng' : 'Hết hàng'}</span></div>
    <ArrowUpRight size={18} className="ai-product-open" />
  </Link>;
}
