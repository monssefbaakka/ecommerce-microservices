import { useState, type FormEvent } from "react";
import { ApiError } from "../api/client";
import { adminApi } from "../api/endpoints";
import type { Category } from "../api/types";
import { Field } from "../components/Field";
import { ErrorMessage } from "../components/Status";
import { errorMessage } from "../hooks/useApi";

interface Props {
  categories: Category[];
  // The product form and the catalog filter use the same list: reloaded after each change
  onChange: () => void;
}

export function CategoriesAdmin({ categories, onChange }: Props) {
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onCreate = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    setFieldErrors({});
    try {
      await adminApi.createCategory({ name: name.trim(), description: description.trim() || null });
      setName("");
      setDescription("");
      onChange();
    } catch (cause) {
      // 400 per field, 409 for a name already used (case ignored)
      if (cause instanceof ApiError && Object.keys(cause.fieldErrors).length > 0) {
        setFieldErrors(cause.fieldErrors);
      } else {
        setError(errorMessage(cause));
      }
    } finally {
      setBusy(false);
    }
  };

  const onDelete = async (category: Category) => {
    if (!window.confirm(`Supprimer la catégorie « ${category.name} » ?`)) {
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await adminApi.deleteCategory(category.id);
      onChange();
    } catch (cause) {
      // 409 while a product still uses it
      setError(errorMessage(cause));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="card">
      <h2>Catégories</h2>
      <ErrorMessage message={error} />
      {categories.length === 0 ? (
        <p className="muted">Aucune catégorie : crée la première ci-dessous.</p>
      ) : (
        <ul className="list">
          {categories.map((category) => (
            <li key={category.id}>
              <div>
                <strong>{category.name}</strong>
                {category.description && <div className="muted">{category.description}</div>}
              </div>
              <button
                type="button"
                className="button button-small button-danger"
                disabled={busy}
                onClick={() => onDelete(category)}
              >
                Supprimer
              </button>
            </li>
          ))}
        </ul>
      )}
      <form className="form" onSubmit={onCreate} noValidate>
        <Field
          label="Nom"
          required
          maxLength={100}
          value={name}
          error={fieldErrors.name}
          onChange={(event) => setName(event.target.value)}
        />
        <Field
          label="Description"
          maxLength={500}
          value={description}
          error={fieldErrors.description}
          onChange={(event) => setDescription(event.target.value)}
        />
        <button type="submit" className="button button-primary" disabled={busy}>
          Ajouter la catégorie
        </button>
      </form>
    </div>
  );
}
