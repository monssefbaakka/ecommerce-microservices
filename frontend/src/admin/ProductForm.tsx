import { useId, useState, type FormEvent } from "react";
import { ApiError } from "../api/client";
import { adminApi, type ProductInput } from "../api/endpoints";
import type { Category, Product } from "../api/types";
import { Field } from "../components/Field";
import { ErrorMessage } from "../components/Status";
import { errorMessage } from "../hooks/useApi";

interface Props {
  categories: Category[];
  // null to create a product
  product: Product | null;
  onSaved: (product: Product) => void;
  onCancel: () => void;
}

// The fields as typed: numbers are only converted on submit, an empty field is not a 0
interface Values {
  name: string;
  description: string;
  price: string;
  categoryId: string;
  stock: string;
}

function toValues(product: Product | null): Values {
  return {
    name: product?.name ?? "",
    description: product?.description ?? "",
    price: product ? String(product.price) : "",
    categoryId: product?.categoryId != null ? String(product.categoryId) : "",
    stock: product ? String(product.stock) : "0",
  };
}

// NaN for an empty or broken field: JSON sends it as null, product-service answers 400 on that field
function toInput(values: Values): ProductInput {
  return {
    name: values.name.trim(),
    description: values.description.trim() || null,
    price: values.price.trim() === "" ? Number.NaN : Number(values.price.replace(",", ".")),
    categoryId: values.categoryId === "" ? Number.NaN : Number(values.categoryId),
    stock: values.stock.trim() === "" ? Number.NaN : Number(values.stock),
  };
}

export function ProductForm({ categories, product, onSaved, onCancel }: Props) {
  const [values, setValues] = useState(() => toValues(product));
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const categoryFieldId = useId();

  const set = (key: keyof Values) => (event: { target: { value: string } }) =>
    setValues((current) => ({ ...current, [key]: event.target.value }));

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      const input = toInput(values);
      const saved = product ? await adminApi.updateProduct(product.id, input) : await adminApi.createProduct(input);
      onSaved(saved);
    } catch (cause) {
      // 400 per field; 404 for a category deleted meanwhile
      if (cause instanceof ApiError && Object.keys(cause.fieldErrors).length > 0) {
        setFieldErrors(cause.fieldErrors);
      } else {
        setError(errorMessage(cause));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="form form-wide" onSubmit={onSubmit} noValidate>
      <h3>{product ? `Modifier « ${product.name} »` : "Nouveau produit"}</h3>
      <ErrorMessage message={error} />
      <Field label="Nom" required maxLength={150} value={values.name} error={fieldErrors.name} onChange={set("name")} />
      <Field
        label="Description"
        maxLength={1000}
        value={values.description}
        error={fieldErrors.description}
        onChange={set("description")}
      />
      <div className="field">
        <label htmlFor={categoryFieldId}>Catégorie</label>
        <select
          id={categoryFieldId}
          required
          value={values.categoryId}
          aria-invalid={fieldErrors.categoryId ? true : undefined}
          onChange={set("categoryId")}
        >
          <option value="">Choisir…</option>
          {categories.map((category) => (
            <option key={category.id} value={category.id}>
              {category.name}
            </option>
          ))}
        </select>
        {fieldErrors.categoryId && <p className="field-error">{fieldErrors.categoryId}</p>}
      </div>
      <div className="two-columns">
        <Field
          label="Prix (€)"
          inputMode="decimal"
          required
          value={values.price}
          error={fieldErrors.price}
          onChange={set("price")}
        />
        <Field
          label="Stock"
          type="number"
          min={0}
          required
          value={values.stock}
          error={fieldErrors.stock}
          onChange={set("stock")}
        />
      </div>
      <div className="actions">
        <button type="submit" className="button button-primary" disabled={busy}>
          {product ? "Enregistrer" : "Créer le produit"}
        </button>
        <button type="button" className="button" disabled={busy} onClick={onCancel}>
          Annuler
        </button>
      </div>
    </form>
  );
}
