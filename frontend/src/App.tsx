import { createBrowserRouter, RouterProvider } from "react-router";
import { AuthProvider } from "./auth/AuthContext";
import { CartProvider } from "./cart/CartContext";
import { Layout } from "./components/Layout";
import { NotFoundPage } from "./pages/NotFoundPage";

// Every page under the same header. The Vite dev server and the nginx of the image answer index.html for
// any path that is not a file, so a reload or a shared link to /orders/12 opens the app on that page
const router = createBrowserRouter([
  {
    element: <Layout />,
    children: [
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
