import { useRef, useState } from 'react';
import type { Bridge } from '../../app/bridge';
import type { Rule, RulePage } from './types';
import { Dialog } from '../../shared/Dialog';
import { Field } from '../../shared/Field';
import { QueryState } from '../../shared/components';
import { useResource } from '../../shared/useResource';
export function Rules({
  bridge,
  accountId,
  refresh,
}: {
  bridge: Bridge;
  accountId: string;
  refresh: () => void;
}) {
  const [page, setPage] = useState(0),
    [editing, setEditing] = useState<Rule | null | undefined>(),
    [busy, setBusy] = useState(false),
    [error, setError] = useState('');
  const identity = useRef({ key: crypto.randomUUID(), payload: '' });
  const result = useResource('rules:' + accountId + page, (signal) =>
    bridge.request<RulePage>(
      '/finance/reconciliation/rules?' +
        new URLSearchParams({ accountId, page: String(page) }),
      { signal },
    ),
  );
  return (
    <details className="workspace-panel workspace-rules">
      <summary>
        <strong>Regras de correspondência</strong>
        <span>Priorize padrões conhecidos da sua conta</span>
      </summary>
      <div className="workspace-rules-content">
        <p className="workspace-caption">
          As regras acrescentam pontos à ordenação das sugestões. Conta,
          direção, valor e disponibilidade continuam sendo conferidos. Cada
          conciliação exige sua confirmação.
        </p>
        {bridge.canWrite && (
          <button
            className="btn secondary"
            onClick={() => {
              setEditing(null);
              setError('');
              identity.current = { key: crypto.randomUUID(), payload: '' };
            }}
          >
            Criar regra
          </button>
        )}
        <button className="btn text small" onClick={result.retry}>
          Atualizar regras
        </button>
        <QueryState {...result} />
        <div className="workspace-saved-list">
          {result.data?.content.map((r) => (
            <article key={r.id}>
              <header>
                <strong>{r.name}</strong>
                <span className="workspace-status">
                  {r.active ? 'Ativa' : 'Inativa'}
                </span>
              </header>
              <p>
                {r.direction === 'INCOME' ? 'Entrada' : 'Saída'} · até{' '}
                {r.windowDays} dia(s)
              </p>
              <dl>
                <dt>No banco contém</dt>
                <dd>{r.bankText}</dd>
                <dt>No lançamento contém</dt>
                <dd>{r.entryText}</dd>
              </dl>
              <small>
                {r.author} · revisão {r.revision + 1}
              </small>
              {bridge.canWrite && (
                <button
                  className="btn secondary small"
                  onClick={() => {
                    setEditing(r);
                    setError('');
                    identity.current = {
                      key: crypto.randomUUID(),
                      payload: '',
                    };
                  }}
                >
                  Editar regra {r.name}
                </button>
              )}
            </article>
          ))}
        </div>
        {result.data && !result.data.content.length && (
          <p className="workspace-caption">
            Nenhuma regra cadastrada para esta conta.
          </p>
        )}
        {result.data && result.data.totalPages > 1 && (
          <nav className="workspace-pagination" aria-label="Regras">
            <button
              className="btn secondary small"
              disabled={!page}
              onClick={() => setPage((p) => p - 1)}
            >
              Anterior
            </button>
            <span>
              {page + 1}/{result.data.totalPages}
            </span>
            <button
              className="btn secondary small"
              disabled={page + 1 >= result.data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Próxima
            </button>
          </nav>
        )}
        {editing !== undefined && (
          <Dialog
            title={editing ? 'Editar regra' : 'Nova regra de correspondência'}
            description="Textos são comparados sem diferenciar maiúsculas, acentos ou pontuação. Não há conciliação automática."
            busy={busy}
            close={() => setEditing(undefined)}
          >
            <form
              className="workspace-finance-form"
              onSubmit={async (e) => {
                e.preventDefault();
                if (busy) return;
                const data = Object.fromEntries(new FormData(e.currentTarget));
                const body = {
                  accountId,
                  name: String(data.name),
                  direction: String(data.direction),
                  bankText: String(data.bankText),
                  entryText: String(data.entryText),
                  windowDays: Number(data.windowDays),
                  active: data.active === 'on',
                  revision: editing?.revision,
                };
                const payload = JSON.stringify(body);
                if (
                  identity.current.payload &&
                  identity.current.payload !== payload
                )
                  identity.current.key = crypto.randomUUID();
                identity.current.payload = payload;
                setBusy(true);
                setError('');
                try {
                  await bridge.request(
                    '/finance/reconciliation/rules' +
                      (editing ? '/' + editing.id : ''),
                    {
                      method: editing ? 'PUT' : 'POST',
                      body: JSON.stringify({
                        ...body,
                        requestId: identity.current.key,
                      }),
                    },
                  );
                  setEditing(undefined);
                  result.retry();
                  refresh();
                } catch (err) {
                  setError((err as Error).message);
                } finally {
                  setBusy(false);
                }
              }}
            >
              <Field label="Nome da regra">
                <input
                  autoFocus
                  name="name"
                  required
                  maxLength={120}
                  defaultValue={editing?.name}
                />
              </Field>
              <Field label="Direção do movimento">
                <select
                  name="direction"
                  defaultValue={editing?.direction || 'EXPENSE'}
                >
                  <option value="INCOME">Entrada</option>
                  <option value="EXPENSE">Saída</option>
                </select>
              </Field>
              <Field label="Texto que aparece no banco">
                <input
                  name="bankText"
                  required
                  minLength={3}
                  maxLength={80}
                  defaultValue={editing?.bankText}
                  placeholder="Ex.: INTERNET"
                />
              </Field>
              <Field label="Texto que aparece no lançamento">
                <input
                  name="entryText"
                  required
                  minLength={3}
                  maxLength={80}
                  defaultValue={editing?.entryText}
                  placeholder="Ex.: Assinatura de internet"
                />
              </Field>
              <Field label="Distância máxima entre as datas (dias)">
                <input
                  name="windowDays"
                  type="number"
                  min={0}
                  max={30}
                  required
                  defaultValue={editing?.windowDays ?? 7}
                />
              </Field>
              <label className="check">
                <input
                  name="active"
                  type="checkbox"
                  defaultChecked={editing?.active ?? true}
                />
                Regra ativa
              </label>
              <p role="alert" className="workspace-field-error">
                {error}
              </p>
              <footer className="workspace-form-actions">
                <button
                  type="button"
                  className="btn secondary"
                  disabled={busy}
                  onClick={() => setEditing(undefined)}
                >
                  Voltar
                </button>
                <button className="btn" disabled={busy}>
                  Salvar regra
                </button>
              </footer>
            </form>
          </Dialog>
        )}
      </div>
    </details>
  );
}
