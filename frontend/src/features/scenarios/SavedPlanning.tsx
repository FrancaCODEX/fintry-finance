import { Field } from '../../shared/Field';
import { useEffect, useRef, useState } from 'react';
import type { Bridge } from '../../app/bridge';
import type {
  ScenarioInput,
  ScenarioPreview,
  SavedScenario,
  SavedScenarioPage,
  ScenarioRun,
  ScenarioHistory,
  ScenarioComparison,
} from '../../api/client';
import { QueryState } from '../../shared/components';
import { useResource } from '../../shared/useResource';
import { money } from '../../shared/format';
export function SavedPlanning({
  bridge,
  input,
  load,
}: {
  bridge: Bridge;
  input: ScenarioInput;
  load: (input: ScenarioInput, result?: ScenarioPreview) => void;
}) {
  const [page, setPage] = useState(0),
    [archived, setArchived] = useState(false),
    [selected, setSelected] = useState<SavedScenario>(),
    [name, setName] = useState(''),
    [busy, setBusy] = useState(false),
    [message, setMessage] = useState(''),
    [error, setError] = useState(''),
    [historyPage, setHistoryPage] = useState(0),
    [left, setLeft] = useState(''),
    [right, setRight] = useState(''),
    [comparison, setComparison] = useState<ScenarioComparison>();
  const intent = useRef({ payload: '', key: crypto.randomUUID() }),
    lock = useRef(false);
  const list = useResource('saved-scenarios:' + page + archived, (signal) =>
    bridge.request<SavedScenarioPage>(
      '/scenarios/saved?' +
        new URLSearchParams({ page: String(page), archived: String(archived) }),
      { signal },
    ),
  );
  const historyQuery = useResource(
    'scenario-history:' + selected?.id + historyPage + selected?.latest?.id,
    (signal) =>
      selected
        ? bridge.request<ScenarioHistory>(
            '/scenarios/saved/' + selected.id + '/runs?page=' + historyPage,
            { signal },
          )
        : Promise.resolve({
            content: [],
            totalElements: 0,
            totalPages: 0,
            number: 0,
          }),
  );
  async function perform(task: () => Promise<void>) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError('');
    setMessage('');
    try {
      await task();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  async function open(id: string) {
    const s = await bridge.request<SavedScenario>('/scenarios/saved/' + id);
    setSelected(s);
    setName(s.name);
    setHistoryPage(0);
    setComparison(undefined);
    setLeft('');
    setRight('');
    const url = new URL(location.href);
    url.searchParams.set('scenario', s.id);
    window.history.replaceState({}, '', url);
    if (s.latest) {
      const run = await bridge.request<ScenarioRun>(
        '/scenarios/saved/' + id + '/runs/' + s.latest.id,
      );
      load(s.input, run.result);
    } else load(s.input);
  }
  const initial = useRef(new URLSearchParams(location.search).get('scenario'));
  useEffect(() => {
    if (initial.current) {
      const id = initial.current;
      initial.current = null;
      void perform(() => open(id));
    }
  }, []);
  function key(payload: string) {
    if (intent.current.payload && intent.current.payload !== payload)
      intent.current.key = crypto.randomUUID();
    intent.current.payload = payload;
    return intent.current.key;
  }
  async function save(asNew: boolean) {
    const body = {
      name: asNew && selected ? name.slice(0, 110) + ' — cópia' : name,
      revision: !asNew ? selected?.revision : undefined,
      input,
    };
    const payload = JSON.stringify(body);
    const s = await bridge.request<SavedScenario>(
      '/scenarios/saved' + (!asNew && selected ? '/' + selected.id : ''),
      {
        method: !asNew && selected ? 'PUT' : 'POST',
        body: JSON.stringify({ ...body, requestId: key(payload) }),
      },
    );
    list.retry();
    await open(s.id);
    setMessage(
      'Cenário e cálculo salvos. Disponíveis para a equipe desta empresa.',
    );
  }
  async function recalculate() {
    if (!selected) return;
    const payload = JSON.stringify({
      id: selected.id,
      revision: selected.revision,
      operation: 'recalculate',
    });
    await bridge.request('/scenarios/saved/' + selected.id + '/calculate', {
      method: 'POST',
      body: JSON.stringify({
        revision: selected.revision,
        requestId: key(payload),
      }),
    });
    intent.current = { payload: '', key: crypto.randomUUID() };
    list.retry();
    await open(selected.id);
    setMessage(
      'Novo cálculo salvo. Os resultados anteriores foram preservados.',
    );
  }
  return (
    <details
      className="workspace-panel workspace-saved-planning"
      open={!!selected || undefined}
    >
      <summary>
        <span className="eyebrow">PLANEJAMENTO DA EQUIPE</span>
        <strong>Cenários salvos</strong>
        <span>Retome premissas e compare o histórico</span>
      </summary>
      <div className="workspace-saved-content">
        <p className="workspace-caption">
          Salvar registra as premissas e um retrato do cálculo. Os lançamentos
          reais continuam separados. A equipe desta empresa pode consultar os
          cenários.
        </p>
        {error && (
          <p role="alert" className="workspace-field-error">
            {error}
          </p>
        )}
        <p role="status">{message}</p>
        <div className="workspace-saved-toolbar">
          <label className="check">
            <input
              type="checkbox"
              checked={archived}
              onChange={(e) => {
                setArchived(e.target.checked);
                setPage(0);
              }}
            />
            Mostrar arquivados
          </label>
          {selected && (
            <button
              className="btn secondary small"
              disabled={busy}
              onClick={() => void perform(() => open(selected.id))}
            >
              Atualizar versão
            </button>
          )}
        </div>
        <QueryState {...list} />
        <div className="workspace-saved-list">
          {list.data?.content.map((s) => (
            <button
              key={s.id}
              className={selected?.id === s.id ? 'selected' : ''}
              disabled={busy}
              onClick={() => void perform(() => open(s.id))}
            >
              <strong>{s.name}</strong>
              <small>
                {s.author} · revisão {s.revision + 1}
              </small>
              <span>
                {s.stale ? 'Dados alterados · recalcule' : 'Cálculo atualizado'}
                {s.archived ? ' · arquivado' : ''}
              </span>
            </button>
          ))}
        </div>
        {list.data && !list.data.content.length && (
          <p className="workspace-caption">
            Nenhum cenário nesta lista. Prepare as premissas abaixo e salve seu
            primeiro planejamento.
          </p>
        )}
        {list.data && list.data.totalPages > 1 && (
          <nav className="workspace-pagination" aria-label="Cenários salvos">
            <button
              className="btn secondary small"
              disabled={!page || busy}
              onClick={() => setPage((p) => p - 1)}
            >
              Anteriores
            </button>
            <span>
              {page + 1}/{list.data.totalPages}
            </span>
            <button
              className="btn secondary small"
              disabled={page + 1 >= list.data.totalPages || busy}
              onClick={() => setPage((p) => p + 1)}
            >
              Próximos
            </button>
          </nav>
        )}
        {selected && (
          <div className="workspace-scenario-note">
            <strong>{selected.name}</strong>
            <p>
              Revisão {selected.revision + 1} · {selected.author}
              {selected.stale
                ? ' · Este retrato usa dados anteriores. Recalcule para consultar a posição atual.'
                : ''}
              {selected.archived ? ' · Arquivado: restaure para editar.' : ''}
            </p>
          </div>
        )}
        {bridge.canWrite && (
          <>
            <Field className="field" label={<>Nome do cenário</>}>
              <input
                maxLength={120}
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Ex.: Contratação no próximo trimestre"
              />
            </Field>
            <div className="workspace-saved-toolbar">
              <button
                className="btn"
                disabled={busy || !name.trim() || selected?.archived}
                onClick={() => void perform(() => save(false))}
              >
                {selected
                  ? 'Salvar alterações e cálculo'
                  : 'Salvar cenário e cálculo'}
              </button>
              {selected && (
                <>
                  <button
                    className="btn secondary"
                    disabled={busy || !name.trim()}
                    onClick={() => void perform(() => save(true))}
                  >
                    Duplicar cenário
                  </button>
                  <button
                    className="btn secondary"
                    disabled={busy || selected.archived}
                    onClick={() => void perform(recalculate)}
                  >
                    Recalcular e registrar
                  </button>
                  <button
                    className="btn secondary"
                    disabled={busy}
                    onClick={() =>
                      void perform(async () => {
                        await bridge.request(
                          '/scenarios/saved/' + selected.id + '/archive',
                          {
                            method: 'PUT',
                            body: JSON.stringify({
                              revision: selected.revision,
                              archived: !selected.archived,
                            }),
                          },
                        );
                        list.retry();
                        await open(selected.id);
                      })
                    }
                  >
                    {selected.archived
                      ? 'Restaurar cenário'
                      : 'Arquivar cenário'}
                  </button>
                </>
              )}
              {selected && (
                <button
                  className="btn text"
                  disabled={busy}
                  onClick={() => {
                    setSelected(undefined);
                    setName('');
                    setComparison(undefined);
                    const u = new URL(location.href);
                    u.searchParams.delete('scenario');
                    window.history.replaceState({}, '', u);
                  }}
                >
                  Salvar como novo planejamento
                </button>
              )}
            </div>
          </>
        )}
        {selected && (
          <section className="workspace-run-history">
            <h3>Histórico de cálculos</h3>
            <QueryState {...historyQuery} />
            <div className="workspace-saved-list">
              {historyQuery.data?.content.map((run) => (
                <button
                  key={run.id}
                  disabled={busy}
                  onClick={() =>
                    void perform(async () => {
                      const r = await bridge.request<ScenarioRun>(
                        '/scenarios/saved/' + selected.id + '/runs/' + run.id,
                      );
                      load(r.input, r.result);
                      setMessage(
                        'Exibindo retrato de ' +
                          new Date(run.calculatedAt).toLocaleString('pt-BR') +
                          '. Use Salvar alterações para registrar novas premissas.',
                      );
                    })
                  }
                >
                  <strong>{money(run.closingBalance)}</strong>
                  <small>
                    {new Date(run.calculatedAt).toLocaleString('pt-BR')} ·
                    revisão {run.revision + 1}
                  </small>
                  <span>
                    {run.author} · efeito {money(run.delta)}
                  </span>
                </button>
              ))}
            </div>
            {historyQuery.data && historyQuery.data.totalPages > 1 && (
              <div className="workspace-saved-toolbar">
                <button
                  className="btn secondary small"
                  disabled={!historyPage}
                  onClick={() => setHistoryPage((p) => p - 1)}
                >
                  Cálculos anteriores
                </button>
                <button
                  className="btn secondary small"
                  disabled={historyPage + 1 >= historyQuery.data.totalPages}
                  onClick={() => setHistoryPage((p) => p + 1)}
                >
                  Mais cálculos
                </button>
              </div>
            )}
            {!!historyQuery.data?.content.length && (
              <>
                <div className="workspace-form-grid">
                  {[
                    ['Cálculo de referência', left, setLeft],
                    ['Cálculo para comparar', right, setRight],
                  ].map(([label, value, setter]) => (
                    <Field
                      className="field"
                      key={String(label)}
                      label={<>{String(label)}</>}
                    >
                      <select
                        value={String(value)}
                        onChange={(e) =>
                          (setter as (v: string) => void)(e.target.value)
                        }
                      >
                        <option value="">
                          Escolha um cálculo desta página
                        </option>
                        {historyQuery.data!.content.map((r) => (
                          <option key={r.id} value={r.id}>
                            {new Date(r.calculatedAt).toLocaleString('pt-BR')} ·{' '}
                            {money(r.closingBalance)}
                          </option>
                        ))}
                      </select>
                    </Field>
                  ))}
                </div>
                <button
                  className="btn secondary"
                  disabled={!left || !right || left === right || busy}
                  onClick={() =>
                    void perform(async () => {
                      setComparison(
                        await bridge.request<ScenarioComparison>(
                          '/scenarios/saved/' +
                            selected.id +
                            '/compare?' +
                            new URLSearchParams({ left, right }),
                        ),
                      );
                    })
                  }
                >
                  Comparar cálculos salvos
                </button>
              </>
            )}
            {comparison && (
              <div className="workspace-form-context" role="status">
                <span>
                  Referência {money(comparison.left.summary.closingBalance)} →
                  comparação {money(comparison.right.summary.closingBalance)}
                </span>
                <strong>
                  Variação no saldo final: {money(comparison.closingChange)}
                </strong>
              </div>
            )}
          </section>
        )}
      </div>
    </details>
  );
}
