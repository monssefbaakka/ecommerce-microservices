import { useState } from "react";
import { Link } from "react-router";
import { adminApi, catalogApi } from "../api/endpoints";
import type { Category, Product } from "../api/types";
import { Pagination } from "../components/Pagination";
import { ErrorMessage, Loading, Notice } from "../components/Status";
import { formatPrice } from "../format";
import { errorMessage, useApi } from "../hooks/useApi";
import { ProductForm } from "./ProductForm";

const PAGE_SIZE = 10;

// "new" while creating a product, the product being edited, or null for no form
type Editing = "new" | Product | null;

export function ProductsAdmin({ categories }: { categories: Category[] }) {
  const [page, setPage] = useState(0);
  const products = useApi(() => catalogApi.products({ page, size: PAGE_SIZE, sort: "id,desc" }), [page]);
  const [editing, setEditing] = useState<Editing>(null);
  const [saved, setSaved] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const categoryNames = new Map(categories.map((category) => [category.id, category.name]));

  const onSaved = (product: Product) => {
    setSaved(`« ${product.name} » enregistré.`);
    setEditing(null);
    products.reload();
  };

  const onDelete = async (product: Product) => {
    if (!window.confirm(`Supprimer « ${product.name} » du catalogue ?`)) {
      return;
    }
    setError(null);
    setSaved(null);
    try {
      // The orders already placed are not affected: their lines hold a copy of the name and price
      await adminApi.deleteProduct(product.id);
      products.reload();
    } catch (cause) {
      setError(errorMessage(cause));
    }
  };

  return (
    <div className="card">
      <div className="summary card-header">
        <h2>Produits</h2>
        {editing === null && (
          <button
            type="button"
            className="button button-primary"
            disabled={categories.length === 0}
            title={categories.length === 0 ? "Crée d'abord une catégorie" : undefined}
            onClick={() => {
              setSaved(null);
              setEditing("new");
            }}
          >
            Nouveau produit
          </button>
        )}
      </div>
      {saved && <Notice tone="success">{saved}</Notice>}
      <ErrorMessage message={error ?? products.error} />
      {editing !== null && (
        <ProductForm
          // A new form for each product: its fields start from that product
          key={editing === "new" ? "new" : editing.id}
          categories={categories}
          product={editing === "new" ? null : editing}
          onSaved={onSaved}
          onCancel={() => setEditing(null)}
        />
      )}
      {products.loading && !products.data && <Loading />}
      {products.data &&
        (products.data.content.length === 0 ? (
          <p className="muted">Le catalogue est vide.</p>
        ) : (
          <>
            <div className="table-wrapper">
              <table aria-busy={products.loading}>
                <thead>
                  <tr>
                    <th>Produit</th>
                    <th>Catégorie</th>
                    <th className="number">Prix</th>
                    <th className="number">Stock</th>
                    <th>
                      <span className="visually-hidden">Actions</span>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {products.data.content.map((product) => (
                    <tr key={product.id}>
                      <td>
                        <Link to={`/products/${product.id}`}>{product.name}</Link>
                      </td>
                      <td>{categoryNames.get(product.categoryId ?? -1) ?? "—"}</td>
                      <td className="number">{formatPrice(product.price)}</td>
                      <td className="number">{product.stock}</td>
                      <td>
                        <div className="actions">
                          <button
                            type="button"
                            className="button button-small"
                            onClick={() => {
                              setSaved(null);
                              setEditing(product);
                            }}
                          >
                            Modifier
                          </button>
                          <button
                            type="button"
                            className="button button-small button-danger"
                            onClick={() => onDelete(product)}
                          >
                            Supprimer
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <Pagination page={products.data.page.number} totalPages={products.data.page.totalPages} onChange={setPage} />
          </>
        ))}
    </div>
  );
}
