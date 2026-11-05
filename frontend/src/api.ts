import { z } from 'zod';
import type { components } from './generated/api';

export type Customer = components['schemas']['CustomerResponse'];
export type PlaceOrderInput = components['schemas']['PlaceOrderRequest'];
export const paymentMethodSchema = z.enum(['COD', 'BANK_TRANSFER']);
export const money = (value: string): string => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(Number(value));
const price = z.string().regex(/^\d+(\.\d+)?$/);
export const customerSchema = z.object({ id: z.number(), fullName: z.string(), email: z.string(), phone: z.string().nullable(), shippingAddress: z.string().nullable() });
export const categorySchema = z.object({ id: z.number(), name: z.string() });
export const cardSchema = z.object({ id: z.number(), name: z.string(), description: z.string(), imageUrl: z.string().nullable(), minPrice: price, maxPrice: price, colors: z.array(z.string()), inStock: z.boolean() });
export const pageSchema = z.object({ items: z.array(cardSchema), page: z.number(), pageSize: z.number(), total: z.number() });
export const variantSchema = z.object({ id: z.number(), sku: z.string(), size: z.string(), color: z.string(), price, stockQuantity: z.number(), active: z.boolean() });
export const productSchema = z.object({ id: z.number(), name: z.string(), description: z.string(), category: z.string(), variants: z.array(variantSchema), images: z.array(z.object({ imageUrl: z.string(), altText: z.string() })) });
export const cartSchema = z.object({ id: z.number().nullable(), version: z.number(), items: z.array(z.object({ id: z.number(), variantId: z.number(), productName: z.string(), imageUrl: z.string().nullable(), sku: z.string(), size: z.string(), color: z.string(), quantity: z.number(), unitPrice: price, lineTotal: price, stockQuantity: z.number(), warnings: z.array(z.string()) })), totalQuantity: z.number(), totalAmount: price });
const bankSchema = z.object({ name: z.string(), account: z.string(), holder: z.string() });
export const checkoutSchema = z.object({ cart: cartSchema, receiverName: z.string(), receiverPhone: z.string().nullable(), shippingAddress: z.string().nullable(), bank: bankSchema, bankTransferAvailable: z.boolean() });
export const orderSchema = z.object({ id: z.number(), orderNumber: z.string(), status: z.string(), createdAt: z.string(), receiverName: z.string(), receiverPhone: z.string(), shippingAddress: z.string(), totalAmount: price, paymentMethod: z.enum(['COD', 'BANK_TRANSFER']), paymentStatus: z.string(), items: z.array(z.object({ productName: z.string(), sku: z.string(), size: z.string(), color: z.string(), unitPrice: price, quantity: z.number(), lineTotal: price })), bank: bankSchema.nullable(), transferContent: z.string().nullable() });
export const ordersSchema = z.object({ items: z.array(orderSchema), page: z.number(), pageSize: z.number(), total: z.number() });
export const conditionsSchema = z.object({ categoryId: z.number().nullable(), sizes: z.array(z.string()).nullable(), colors: z.array(z.string()).nullable(), minPrice: z.number().nullable(), maxPrice: z.number().nullable(), sort: z.enum(['RELEVANCE', 'PRICE_ASC', 'PRICE_DESC', 'NEWEST', 'NAME_ASC']).nullable() });
export const searchSchema = z.object({ queryId: z.number(), type: z.string(), status: z.string(), text: z.string().nullable(), conditions: conditionsSchema, products: pageSchema });
export const capabilitiesSchema = z.object({ voice: z.boolean(), image: z.boolean() });
const problemSchema = z.object({ code: z.string(), detail: z.string(), traceId: z.string().optional(), fieldErrors: z.array(z.object({ field: z.string(), message: z.string() })).optional() });
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields: readonly { field: string; message: string }[];
  constructor(status: number, code: string, detail: string, fields: readonly { field: string; message: string }[]) { super(detail); this.status = status; this.code = code; this.fields = fields; }
}
export async function api<T>(path: string, schema: z.ZodType<T>, init: RequestInit): Promise<T> {
  const headers = new Headers(init.headers);
  if (init.body && !(init.body instanceof FormData)) headers.set('Content-Type', 'application/json');
  if (init.method && init.method !== 'GET') {
    const csrfResponse = await fetch('/api/v1/auth/csrf', { credentials: 'same-origin' });
    if (!csrfResponse.ok) throw new ApiError(csrfResponse.status, 'CSRF_FETCH_FAILED', 'Không thể xác thực yêu cầu. Hãy tải lại trang.', []);
    const csrf = z.object({ token: z.string(), headerName: z.string() }).parse(await csrfResponse.json());
    headers.set(csrf.headerName, csrf.token);
  }
  const response = await fetch(`/api/v1${path}`, { ...init, headers, credentials: 'same-origin' });
  if (!response.ok) {
    const result = problemSchema.safeParse(await response.json());
    if (!result.success) throw new ApiError(response.status, 'INVALID_ERROR_RESPONSE', `API ${path} trả lỗi HTTP ${response.status} không đúng hợp đồng.`, []);
    throw new ApiError(response.status, result.data.code, result.data.detail, result.data.fieldErrors ?? []);
  }
  return schema.parse(await response.json());
}
