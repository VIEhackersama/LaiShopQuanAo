import { createContext, useContext, type ReactNode } from 'react';
import { Navigate, useLocation, Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { ApiError, api, customerSchema } from './api';
import type { z } from 'zod';
export type CustomerData = z.infer<typeof customerSchema>;
export const AuthContext = createContext<{ customer: CustomerData | null; loading: boolean }>({ customer: null, loading: true });
export const useAuth = () => useContext(AuthContext);
export function AuthProvider({ children }: { children: ReactNode }) {
  const query = useQuery({ queryKey: ['customer'], queryFn: async () => {
    try { return await api('/auth/me', customerSchema, {}); }
    catch (error) { if (error instanceof ApiError && error.status === 401) return null; throw error; }
  } });
  if (query.isError) return <main><ErrorMessage error={query.error} /></main>;
  return <AuthContext.Provider value={{ customer: query.data ?? null, loading: query.isPending }}>{children}</AuthContext.Provider>;
}
export function Protected({ children }: { children: ReactNode }) {
  const auth = useAuth(); const location = useLocation();
  if (auth.loading) return <Loading />;
  return auth.customer ? children : <Navigate to={`/login?next=${encodeURIComponent(location.pathname + location.search)}`} replace />;
}
export function Loading() { return <p className="loading" role="status">Đang tải…</p>; }
export function ErrorMessage({ error }: { error: Error | null }) {
  if (!error) return null;
  return <div className="error" role="alert" data-testid="api-error"><p>{error.message}</p>{error instanceof ApiError && error.fields.map(field => <p key={field.field}>{field.field}: {field.message}</p>)}</div>;
}
export function Empty({ title, children }: { title: string; children: ReactNode }) { return <section className="empty"><p className="eyebrow">LAI SHOP</p><h2>{title}</h2>{children}<Link className="button" to="/">Khám phá sản phẩm</Link></section>; }
