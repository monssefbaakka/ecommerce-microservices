interface Props {
  // 0-based, like Spring Data
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}

// Nothing to show for a single page
export function Pagination({ page, totalPages, onChange }: Props) {
  if (totalPages <= 1) {
    return null;
  }
  return (
    <nav className="pagination" aria-label="Pagination">
      <button type="button" className="button" disabled={page <= 0} onClick={() => onChange(page - 1)}>
        ← Précédent
      </button>
      <span>
        Page {page + 1} sur {totalPages}
      </span>
      <button type="button" className="button" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>
        Suivant →
      </button>
    </nav>
  );
}
