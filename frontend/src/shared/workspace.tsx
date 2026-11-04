import { useEffect, useState, useRef, type ReactNode } from 'react';
import { saveLocation } from '../app/location';
import { Field } from './Field';
import { Dialog } from './Dialog';
import { EmptyState } from './Collection';
export type Values = Record<string, string>;
export type Option = [string, string];
export interface Control {
  name: string;
  label: string;
  type?: string;
  options?: Option[];
  required?: boolean;
  disabled?: boolean;
  maxLength?: number;
  min?: string;
  max?: string;
  step?: string;
  help?: string;
  pattern?: string;
}
export function Controls({
  fields,
  values,
  setValues,
}: {
  fields: Control[];
  values: Values;
  setValues: (v: Values) => void;
}) {
  return (
    <>
      {fields.map((field) => (
        <Field key={field.name} label={field.label}>
          {field.options ? (
            <select
              name={field.name}
              disabled={field.disabled}
              required={field.required}
              value={values[field.name] || ''}
              onChange={(e) =>
                setValues({ ...values, [field.name]: e.target.value })
              }
            >
              {field.options.map(([value, label]) => (
                <option key={value} value={value}>
                  {label}
                </option>
              ))}
            </select>
          ) : field.type === 'textarea' ? (
            <textarea
              name={field.name}
              rows={3}
              disabled={field.disabled}
              required={field.required}
              maxLength={field.maxLength}
              value={values[field.name] || ''}
              onChange={(e) =>
                setValues({ ...values, [field.name]: e.target.value })
              }
            />
          ) : (
            <input
              name={field.name}
              type={field.type || 'text'}
              disabled={field.disabled}
              required={field.required}
              min={field.min}
              max={field.max}
              step={field.step}
              pattern={field.pattern}
              maxLength={field.maxLength ?? 255}
              autoComplete={
                field.type === 'password' ? 'new-password' : undefined
              }
              value={values[field.name] || ''}
              onChange={(e) =>
                setValues({ ...values, [field.name]: e.target.value })
              }
            />
          )}
          {field.help && <small>{field.help}</small>}
        </Field>
      ))}
    </>
  );
}
export function usePageFilters(
  page: string,
  defaults: Values,
  fields: string[],
) {
  const [values, setValues] = useState<Values>(() => {
    const params = new URLSearchParams(location.search);
    const result = { ...defaults };
    for (const key of fields)
      if (params.has(key)) result[key] = (params.get(key) || '').slice(0, 255);
    const p = Number(params.get('index') || 0);
    result.index =
      Number.isSafeInteger(p) && p >= 0 && p <= 100000 ? String(p) : '0';
    return result;
  });
  const [draft, setDraft] = useState(values);
  useEffect(() => {
    saveLocation(page, values, false);
  }, [page, values]);
  const apply = (next: Values) => {
    saveLocation(page, next);
    setValues(next);
    setDraft(next);
  };
  return { values, draft, setDraft, apply, index: Number(values.index || 0) };
}
export function FilterBar({
  fields,
  draft,
  setDraft,
  submit,
  children,
}: {
  fields: Control[];
  draft: Values;
  setDraft: (v: Values) => void;
  submit: () => void;
  children?: ReactNode;
}) {
  return (
    <form
      className="workspace-catalog-filters"
      onSubmit={(e) => {
        e.preventDefault();
        submit();
      }}
    >
      <Controls fields={fields} values={draft} setValues={setDraft} />
      <button type="submit" className="btn secondary">
        Aplicar filtros
      </button>
      {children}
    </form>
  );
}
export function Metrics({ items }: { items: [string, ReactNode][] }) {
  return (
    <section className="workspace-metrics" aria-label="Resumo da área">
      {items.map(([label, value]) => (
        <article key={label}>
          <span>{label}</span>
          <strong>{value}</strong>
        </article>
      ))}
    </section>
  );
}
export function Table<T>({
  rows,
  columns,
  keyOf,
  empty = 'Nenhum registro encontrado',
}: {
  rows: T[];
  columns: { label: string; render: (row: T) => ReactNode }[];
  keyOf: (row: T) => string;
  empty?: string;
}) {
  if (!rows.length)
    return (
      <EmptyState
        title={empty}
        description="Ajuste o período ou os filtros para consultar outros registros."
      />
    );
  return (
    <div className="workspace-entry-table workspace-responsive-table">
      <table>
        <thead>
          <tr>
            {columns.map((c) => (
              <th scope="col" key={c.label}>
                {c.label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={keyOf(row)}>
              {columns.map((c) => (
                <td key={c.label} data-label={c.label}>
                  {c.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
export function useCommand() {
  const running = useRef(false);
  const [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  async function run(action: () => Promise<void>) {
    if (running.current) return false;
    running.current = true;
    setBusy(true);
    setError('');
    try {
      await action();
      return true;
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Não foi possível concluir.');
      return false;
    } finally {
      running.current = false;
      setBusy(false);
    }
  }
  return { busy, error, run };
}
export function Feedback({ error }: { error: string }) {
  return error ? (
    <p className="workspace-form-feedback" role="alert">
      {error}
    </p>
  ) : null;
}
export function Confirm({
  title,
  description,
  confirm,
  close,
  label = 'Confirmar',
}: {
  title: string;
  description: string;
  confirm: () => Promise<void>;
  close: () => void;
  label?: string;
}) {
  const cmd = useCommand();
  return (
    <Dialog
      title={title}
      description={description}
      busy={cmd.busy}
      close={close}
    >
      <form
        className="workspace-finance-form"
        onSubmit={async (e) => {
          e.preventDefault();
          if (await cmd.run(confirm)) close();
        }}
      >
        <Feedback error={cmd.error} />
        <div className="workspace-form-actions">
          <button
            type="button"
            className="btn secondary"
            disabled={cmd.busy}
            onClick={close}
          >
            Voltar
          </button>
          <button type="submit" className="btn" disabled={cmd.busy}>
            {cmd.busy ? 'Confirmando…' : label}
          </button>
        </div>
      </form>
    </Dialog>
  );
}
export const timestamp = (value?: string | null) =>
  value
    ? new Intl.DateTimeFormat('pt-BR', {
        dateStyle: 'short',
        timeStyle: 'short',
        timeZone: 'America/Sao_Paulo',
      }).format(new Date(value))
    : '—';
export const today = () =>
  new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Sao_Paulo' }).format(
    new Date(),
  );
export const mutation = (method: string, data?: unknown): RequestInit => ({
  method,
  ...(data === undefined ? {} : { body: JSON.stringify(data) }),
});
export const accountControl = (
  accounts: { id: string; name: string }[],
): Control => ({
  name: 'accountId',
  label: 'Conta',
  options: [
    ['', 'Todas as contas'],
    ...accounts.map((a): Option => [a.id, a.name]),
  ],
});
