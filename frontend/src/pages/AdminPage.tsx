import { CategoriesAdmin } from "../admin/CategoriesAdmin";
import { ProductsAdmin } from "../admin/ProductsAdmin";
import { catalogApi } from "../api/endpoints";
import { ErrorMessage, Loading } from "../components/Status";
import { useApi } from "../hooks/useApi";

// The catalog of product-service, for an ADMIN account (RequireAuth role="ADMIN" in App.tsx).
// The first ADMIN comes from ADMIN_EMAIL / ADMIN_PASSWORD in infra/.env, read by auth-service at startup
export function AdminPage() {
  const categories = useApi(() => catalogApi.categories(), []);

  return (
    <section>
      <h1>Administration du catalogue</h1>
      <ErrorMessage message={categories.error} onRetry={categories.reload} />
      {categories.loading && !categories.data && <Loading />}
      {categories.data && (
        <div className="admin">
          <CategoriesAdmin categories={categories.data} onChange={categories.reload} />
          <ProductsAdmin categories={categories.data} />
        </div>
      )}
    </section>
  );
}
