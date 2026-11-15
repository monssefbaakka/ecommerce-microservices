import { createBrowserRouter, RouterProvider } from "react-router";
import { AuthProvider } from "./auth/AuthContext";
import { CartProvider } from "./cart/CartContext";
import { Layout } from "./components/Layout";
import { RequireAuth } from "./components/RequireAuth";
import { CartPage } from "./pages/CartPage";
import { CatalogPage } from "./pages/CatalogPage";
import { LoginPage } from "./pages/LoginPage";
import { NotFoundPage } from "./pages/NotFoundPage";
import { OrderPage } from "./pages/OrderPage";
import { OrdersPage } from "./pages/OrdersPage";
import { PaymentsPage } from "./pages/PaymentsPage";
import { ProductPage } from "./pages/ProductPage";
import { RegisterPage } from "./pages/RegisterPage";

// Every page under the same header. The Vite dev server and the nginx of the image answer index.html for
// any path that is not a file, so a reload or a shared link to /orders/12 opens the app on that page
const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
      { index: true, element: <CatalogPage /> },
      { path: "products/:id", element: <ProductPage /> },
      { path: "cart", element: <RequireAuth><CartPage /></RequireAuth> },
      { path: "orders", element: <RequireAuth><OrdersPage /></RequireAuth> },
      { path: "orders/:id", element: <RequireAuth><OrderPage /></RequireAuth> },
      { path: "payments", element: <RequireAuth><PaymentsPage /></RequireAuth> },
      { path: "login", element: <LoginPage /> },
      { path: "register", element: <RegisterPage /> },
      { path: "*", element: <NotFoundPage /> },
    ],
  },
]);

export function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <RouterProvider router={router} />
      </CartProvider>
    </AuthProvider>
  );
}
