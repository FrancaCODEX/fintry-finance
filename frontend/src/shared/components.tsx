import type { ReactNode } from 'react';
export function QueryState({
  loading,
  error,
  retry,
}: {
  loading: boolean;
  error?: string;
  retry: () => void;
}) {
  if (error)
    return (
      <section className="workspace-message" role="alert">
        <h2>Não foi possível carregar</h2>
        <p>{error}</p>
        <button className="btn secondary" onClick={retry}>
          Tentar novamente
        </button>
      </section>
    );
  if (loading)
    return (
      <div
        className="workspace-loading"
        role="status"
        aria-label="Carregando informações"
      >
        <div />
        <div />
        <div />
        <span className="sr-only">Carregando informações…</span>
      </div>
    );
  return null;
}
export function Panel({
  title,
  eyebrow,
  children,
  action,
}: {
  title: string;
  eyebrow?: string;
  children: ReactNode;
  action?: ReactNode;
}) {
  return (
    <section className="workspace-panel">
      <header className="workspace-panel-head">
        <div>
          {eyebrow && <span className="eyebrow">{eyebrow}</span>}
          <h2>{title}</h2>
        </div>
        {action}
      </header>
      {children}
    </section>
  );
}
export function PageHeading({
  title,
  description,
  children,
}: {
  title: string;
  description: string;
  children?: ReactNode;
}) {
  return (
    <div className="page-head">
      <div>
        <span className="eyebrow">SEU ESPAÇO FINANCEIRO</span>
        <h1 tabIndex={-1}>{title}</h1>
        <p className="muted">{description}</p>
      </div>
      <div className="head-actions">{children}</div>
    </div>
  );
}
