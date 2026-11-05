import { Link, NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient, useMutation } from '@tanstack/react-query';
import { ShoppingBag, UserCircle, ArrowUpRight } from '@phosphor-icons/react';
import { z } from 'zod';
import { api, cartSchema } from './api';
import { AuthProvider, useAuth, Protected, ErrorMessage } from './shared';
import { CatalogPage } from './CatalogPage';
import { ProductPage } from './ProductPage';
import { AuthPage, CartPage, CheckoutPage, OrdersPage, OrderPage } from './PurchasePages';

function Header() {
  const { customer } = useAuth(); const client = useQueryClient(); const navigate = useNavigate();
  const cart = useQuery({ queryKey: ['cart'], queryFn: () => api('/cart', cartSchema, {}), enabled: !!customer });
  const logout = useMutation({ mutationFn: () => api('/auth/logout', z.object({ loggedOut: z.boolean() }), { method: 'POST' }), onSuccess: async () => { client.clear(); navigate('/'); await client.invalidateQueries(); } });
  return <><div className="announcement">Mặc thoải mái. Sống theo cách của bạn.</div><header className="header"><Link className="brand" to="/" aria-label="Lai Shop trang chủ">lai<span>®</span></Link><nav aria-label="Điều hướng chính"><NavLink to="/">Bộ sưu tập</NavLink><Link to="/?categoryId=1">Áo</Link><Link to="/?categoryId=2">Quần</Link><Link to="/?categoryId=3">Váy</Link></nav><div className="header-actions">{customer ? <><Link data-testid="orders-link" to="/orders">Đơn của tôi</Link><button className="text-button" data-testid="logout" disabled={logout.isPending} onClick={() => logout.mutate()}>Đăng xuất</button></> : <Link data-testid="login-link" className="icon-link" to="/login"><UserCircle size={23} /><span>Đăng nhập</span></Link>}<Link data-testid="cart-link" className="icon-link" to="/cart" aria-label="Giỏ hàng"><ShoppingBag size={23} /><span className="cart-count">{cart.data?.totalQuantity ?? 0}</span></Link></div></header><ErrorMessage error={logout.error} /></>;
}
export function App() {
  return <AuthProvider><Header /><Routes><Route path="/" element={<CatalogPage />} /><Route path="/search" element={<CatalogPage />} /><Route path="/products/:id" element={<ProductPage />} /><Route path="/login" element={<AuthPage key="login" mode="login" />} /><Route path="/register" element={<AuthPage key="register" mode="register" />} /><Route path="/cart" element={<Protected><CartPage /></Protected>} /><Route path="/checkout" element={<Protected><CheckoutPage /></Protected>} /><Route path="/orders" element={<Protected><OrdersPage /></Protected>} /><Route path="/orders/:id" element={<Protected><OrderPage /></Protected>} /><Route path="*" element={<main><h1>Trang không tồn tại</h1><Link to="/">Về trang chủ</Link></main>} /></Routes><footer><Link className="brand" to="/">lai<span>®</span></Link><p>Những món đồ bạn muốn mặc mỗi ngày.</p><Link to="/">Tìm phong cách của bạn <ArrowUpRight /></Link><small>LAI SHOP · Đồ án website một shop · Giá niêm yết theo biến thể</small></footer></AuthProvider>;
}
