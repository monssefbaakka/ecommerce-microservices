// The JSON of the services behind the gateway, field for field (their DTO classes).
// BigDecimal amounts arrive as JSON numbers, Instant dates as ISO-8601 strings.

export interface ErrorResponse {
  status: number;
  error: string;
  message: string;
  path: string;
  errors: { field: string; message: string }[];
}

// A Spring Data page, serialized VIA_DTO (@EnableSpringDataWebSupport in each service)
export interface Page<T> {
  content: T[];
  page: {
    size: number;
    number: number;
    totalElements: number;
    totalPages: number;
  };
}

// auth-service
export interface AuthResponse {
  token: string;
  tokenType: "Bearer";
  expiresIn: number;
}

export interface Credentials {
  email: string;
  password: string;
}

export type Role = "USER" | "ADMIN";

export interface User {
  id: number;
  email: string;
  role: Role;
  createdAt: string;
}

// product-service
export interface Category {
  id: number;
  name: string;
  description: string | null;
}

export interface Product {
  id: number;
  name: string;
  description: string | null;
  price: number;
  categoryId: number | null;
  stock: number;
  createdAt: string;
}

// order-service. With product-service unavailable, the cart comes with the last known names and prices
// ("productsUpToDate": false), and null for a line never seen before
export interface CartItem {
  productId: number;
  productName: string | null;
  unitPrice: number | null;
  quantity: number;
  subtotal: number | null;
}

export interface Cart {
  items: CartItem[];
  totalQuantity: number;
  total: number | null;
  productsUpToDate: boolean;
}

export type OrderStatus = "PENDING" | "PAID" | "CANCELLED";

export interface OrderItem {
  productId: number | null;
  productName: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}

export interface Order {
  id: number;
  status: OrderStatus;
  items: OrderItem[];
  totalQuantity: number;
  total: number;
  createdAt: string;
}

// payment-service
export type PaymentStatus = "SUCCEEDED" | "DECLINED";

export interface Payment {
  id: number;
  orderId: number;
  amount: number;
  status: PaymentStatus;
  cardLast4: string;
  createdAt: string;
}
