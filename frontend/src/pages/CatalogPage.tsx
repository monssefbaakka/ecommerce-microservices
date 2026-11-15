import { useEffect, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router";
import { catalogApi } from "../api/endpoints";
import type { Category, Product } from "../api/types";
import { Pagination } from "../components/Pagination";
import { StockLabel } from "../components/StockLabel";
import { ErrorMessage, Loading } from "../components/Status";
import { formatPrice } from "../format";
import { useApi } from "../hooks/useApi";

const PAGE_SIZE = 12;

// Values of ?sort= for product-service: any field of the Product entity, then the direction
const sortOptions = [
  { value: "", label: "Pertinence" },
  { value: "createdAt,desc", label: "Nouveautés" },
  { value: "price,asc", label: "Prix croissant" },
  { value: "price,desc", label: "Prix décroissant" },
  { value: "name,asc", label: "Nom (A → Z)" },
];

// Search, category, sort and page live in the URL (?q=phone&category=2&page=1): a reload, the back
// button or a shared link show the same results
export function CatalogPage() {
  const [params, setParams] = useSearchParams();
  const name = params.get("q") ?? "";
  const categoryId = toNumber(params.get("category"));
  const sort = params.get("sort") ?? "";
  const page = toNumber(params.get("page")) ?? 0;

  // What is typed, sent with the form only: no request for each key
  const [search, setSearch] = useState(name);
  useEffect(() => setSearch(name), [name]);

  const categories = useApi(() => catalogApi.categories(), []);
  const products = useApi(
    () => catalogApi.products({ name: name || undefined, categoryId, sort: sort || undefined, page, size: PAGE_SIZE }),
    [name, categoryId, sort, page],
  );

  // A new search, category or sort starts again at the first page
  const change = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) {
      next.set(key, value);
    } else {
      next.delete(key);
    }
    if (key !== "page") {
      next.delete("page");
    }
    setParams(next);
  };

  const onSearch = (event: FormEvent) => {
    event.preventDefault();
    change("q", search.trim());
  };

  const categoryNames = new Map((categories.data ?? []).map((category) => [category.id, category.name]));

  return (
    <section>
      <h1>Catalogue</h1>
      <form className="toolbar" role="search" onSubmit={onSearch}>
        <input
          type="search"
          placeholder="Rechercher un produit"
          aria-label="Rechercher un produit"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <button type="submit" className="button button-primary">
          Rechercher
        </button>
        <select
          aria-label="Catégorie"
          value={categoryId ?? ""}
          onChange={(event) => change("category", event.target.value)}
        >
          <option value="">Toutes les catégories</option>
          {categories.data?.map((category: Category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
        <select aria-label="Trier par" value={sort} onChange={(event) => change("sort", event.target.value)}>
          {sortOptions.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </form>

      <ErrorMessage message={products.error} onRetry={products.reload} />
      {products.loading && !products.data && <Loading />}
      {products.data && (
        <>
          <p className="muted">
            {products.data.page.totalElements === 0
              ? "Aucun produit ne correspond à cette recherche."
              : `${products.data.page.totalElements} produit(s)`}
          </p>
          <div className="grid" aria-busy={products.loading}>
            {products.data.content.map((product) => (
              <ProductCard key={product.id} product={product} category={categoryNames.get(product.categoryId ?? -1)} />
            ))}
          </div>
          <Pagination
            page={products.data.page.number}
            totalPages={products.data.page.totalPages}
            onChange={(next) => change("page", next > 0 ? String(next) : "")}
          />
        </>
      )}
    </section>
  );
}

function ProductCard({ product, category }: { product: Product; category?: string }) {
  return (
    <Link to={`/products/${product.id}`} className="card product-card">
      {category && <span className="muted">{category}</span>}
      <h3>{product.name}</h3>
      {product.description && <span className="muted line-clamp">{product.description}</span>}
      <span className="price">{formatPrice(product.price)}</span>
      <StockLabel stock={product.stock} />
    </Link>
  );
}

// undefined for a missing or broken value (?page=abc): the default applies, no 400 from the service
function toNumber(value: string | null): number | undefined {
  if (value === null || value === "") {
    return undefined;
  }
  const number = Number(value);
  return Number.isInteger(number) && number >= 0 ? number : undefined;
}
