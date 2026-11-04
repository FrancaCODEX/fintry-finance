import type { ReactNode } from 'react';
export function EmptyState({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div className="workspace-empty-state">
      <span aria-hidden="true" className="workspace-empty-symbol">
        ↗
      </span>
      <h2>{title}</h2>
      <p>{description}</p>
      {children}
    </div>
  );
}
export function Pagination({
  page,
  pages,
  total,
  onChange,
}: {
  page: number;
  pages: number;
  total: number;
  onChange: (page: number) => void;
}) {
  return (
    <div className="workspace-pagination">
      <span role="status">
        {total} registro(s) · Página {page + 1} de {Math.max(1, pages)}
      </span>
      <div>
        <button
          className="btn secondary small"
          disabled={page === 0}
          onClick={() => onChange(page - 1)}
        >
          Anterior
        </button>
        <button
          className="btn secondary small"
          disabled={page + 1 >= pages}
          onClick={() => onChange(page + 1)}
        >
          Próxima
        </button>
      </div>
    </div>
  );
}
