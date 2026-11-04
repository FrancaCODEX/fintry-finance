import { useEffect, useRef, type ReactNode } from 'react';
export function Dialog({
  title,
  description,
  busy,
  children,
  close,
}: {
  title: string;
  description: string;
  busy: boolean;
  children: ReactNode;
  close: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null;
    ref.current?.showModal();
    return () => {
      ref.current?.close();
      if (opener?.isConnected) opener.focus({ preventScroll: true });
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className="workspace-form-dialog"
      aria-labelledby="finance-dialog-title"
      aria-describedby="finance-dialog-description"
      aria-busy={busy}
      onCancel={(e) => {
        e.preventDefault();
        if (!busy) close();
      }}
    >
      <header className="workspace-panel-head">
        <div>
          <span className="eyebrow">SEU FINANCEIRO</span>
          <h2 id="finance-dialog-title">{title}</h2>
          <p id="finance-dialog-description">{description}</p>
        </div>
        <button
          type="button"
          className="btn secondary small"
          disabled={busy}
          onClick={close}
          aria-label="Fechar formulário"
        >
          Fechar
        </button>
      </header>
      {children}
    </dialog>
  );
}
export function FieldError({ error, id }: { error?: string; id: string }) {
  return error ? (
    <small className="workspace-field-error" id={id}>
      {error}
    </small>
  ) : null;
}
