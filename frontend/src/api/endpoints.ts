import { request } from "./client";
import type {
  AuthResponse,
  Cart,
  Category,
  Credentials,
  Order,
  Page,
  Payment,
  Product,
  User,
} from "./types";

// One function per endpoint of the gateway used by the app (same routes as docs/requests.http)

export const authApi = {
  login: (credentials: Credentials) => request<AuthResponse>("POST", "/api/auth/login", credentials),
  register: (credentials: Credentials) => request<User>("POST", "/api/auth/register", credentials),
};

// A type, not an interface: only a type alias fits the Record that toQueryString takes
export type ProductQuery = {
  name?: string;
  categoryId?: number;
  page?: number;
  size?: number;
  sort?: string;
};

export const catalogApi = {
  products: (query: ProductQuery = {}) => request<Page<Product>>("GET", `/api/products${toQueryString(query)}`),
  product: (id: number) => request<Product>("GET", `/api/products/${id}`),
  categories: () => request<Category[]>("GET", "/api/categories"),
};

export const cartApi = {
  get: () => request<Cart>("GET", "/api/cart"),
  add: (productId: number, quantity: number) =>
    request<Cart>("POST", "/api/cart/items", { productId, quantity }),
  update: (productId: number, quantity: number) =>
    request<Cart>("PUT", `/api/cart/items/${productId}`, { quantity }),
  remove: (productId: number) => request<Cart>("DELETE", `/api/cart/items/${productId}`),
  clear: () => request<void>("DELETE", "/api/cart"),
};

export const orderApi = {
  // Turns the cart into a PENDING order and empties it
  place: () => request<Order>("POST", "/api/orders"),
  list: (page = 0, size = 10) => request<Page<Order>>("GET", `/api/orders${toQueryString({ page, size })}`),
  get: (id: number) => request<Order>("GET", `/api/orders/${id}`),
};

export const paymentApi = {
  pay: (orderId: number, cardNumber: string) => request<Payment>("POST", "/api/payments", { orderId, cardNumber }),
  list: (page = 0, size = 10) =>
    request<Page<Payment>>("GET", `/api/payments${toQueryString({ page, size, sort: "createdAt,desc" })}`),
};

// ADMIN token only: product-service answers 403 to any other account
export interface CategoryInput {
  name: string;
  description: string | null;
}

export interface ProductInput {
  name: string;
  description: string | null;
  price: number;
  categoryId: number;
  stock: number;
}

export const adminApi = {
  createCategory: (category: CategoryInput) => request<Category>("POST", "/api/categories", category),
  updateCategory: (id: number, category: CategoryInput) => request<Category>("PUT", `/api/categories/${id}`, category),
  // 409 while a product still uses it
  deleteCategory: (id: number) => request<void>("DELETE", `/api/categories/${id}`),
  createProduct: (product: ProductInput) => request<Product>("POST", "/api/products", product),
  updateProduct: (id: number, product: ProductInput) => request<Product>("PUT", `/api/products/${id}`, product),
  deleteProduct: (id: number) => request<void>("DELETE", `/api/products/${id}`),
};

// Leaves out undefined and empty values: "?name=" would search for an empty name
export function toQueryString(params: Record<string, string | number | undefined>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") {
      search.set(key, String(value));
    }
  }
  const query = search.toString();
  return query ? `?${query}` : "";
}
