import { Link, NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { ShoppingBag, UserCircle, Sparkle } from '@phosphor-icons/react';
import { z } from 'zod';
import { api, cartSchema } from './api';
import { AuthProvider, useAuth, Protected, ErrorMessage } from './shared';
import { CatalogPage } from './CatalogPage';
import { ProductPage } from './ProductPage';
import { AssistantPage } from './AssistantPage';
import { Footer } from './Footer';
import { AuthPage, CartPage, CheckoutPage, OrdersPage, OrderPage } from './PurchasePages';

function Header() {
  const { customer } = useAuth(); const client = useQueryClient(); const navigate = useNavigate();
  const cart = useQuery({ queryKey: ['cart'], queryFn: () => api('/cart', cartSchema, {}), enabled: !!customer });
  const logout = useMutation({ mutationFn: () => api('/auth/logout', z.object({ loggedOut: z.boolean() }), { method: 'POST' }), onSuccess: async () => { client.clear(); navigate('/'); await client.invalidateQueries(); } });
  return <><div className="announcement">Mặc thoải mái. Sống theo cách của bạn.</div><header className="header"><Link className="brand" to="/" aria-label="LaiShopQuanAo trang chủ">LaiShopQuanAo</Link><nav aria-label="Điều hướng chính"><NavLink to="/" end>Bộ sưu tập</NavLink><Link className="category-nav" to="/?categoryId=1">Áo</Link><Link className="category-nav" to="/?categoryId=2">Quần</Link><Link className="category-nav" to="/?categoryId=3">Váy</Link><NavLink className="assistant-nav" data-testid="ai-tab" to="/assistant"><Sparkle size={16} />Trợ lý AI</NavLink></nav><div className="header-actions">{customer ? <><Link data-testid="orders-link" to="/orders">Đơn của tôi</Link><button className="text-button" data-testid="logout" disabled={logout.isPending} onClick={() => logout.mutate()}>Đăng xuất</button></> : <Link data-testid="login-link" className="icon-link" to="/login"><UserCircle size={23} /><span>Đăng nhập</span></Link>}<Link data-testid="cart-link" className="icon-link" to="/cart" aria-label="Giỏ hàng"><ShoppingBag size={23} /><span className="cart-count">{cart.data?.totalQuantity ?? 0}</span></Link></div></header><ErrorMessage error={logout.error} /></>;
}
export function App() {
  return <AuthProvider><Header /><Routes><Route path="/" element={<CatalogPage />} /><Route path="/search" element={<CatalogPage />} /><Route path="/assistant" element={<AssistantPage />} /><Route path="/products/:id" element={<ProductPage />} /><Route path="/login" element={<AuthPage key="login" mode="login" />} /><Route path="/register" element={<AuthPage key="register" mode="register" />} /><Route path="/cart" element={<Protected><CartPage /></Protected>} /><Route path="/checkout" element={<Protected><CheckoutPage /></Protected>} /><Route path="/orders" element={<Protected><OrdersPage /></Protected>} /><Route path="/orders/:id" element={<Protected><OrderPage /></Protected>} /><Route path="*" element={<main><h1>Trang không tồn tại</h1><Link to="/">Về trang chủ</Link></main>} /></Routes><Footer /></AuthProvider>;
}
