import { useEffect, useMemo, useRef, useState } from 'react';
import type { Bridge } from '../../app/bridge';
import type { Account, Category, Entry } from '../../api/client';
import { query } from '../../api/client';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import { Dialog } from '../../shared/Dialog';
import { Field } from '../../shared/Field';
import { money, dateLabel } from '../../shared/format';
import { useResource } from '../../shared/useResource';
import { Rules } from './Rules';
import type { Movement, MovementPage, Candidate, OfxPreview } from './types';
type Operation = {
  kind: 'LINK' | 'CREATE' | 'IGNORE' | 'REOPEN' | 'GROUP' | 'IMPORT';
  row?: Movement;
};
const labels = {
  OPEN: 'Em revisão',
  LINKED: 'Conciliados',
  IGNORED: 'Ignorados',
};
export function Reconciliation({ bridge }: { bridge: Bridge }) {
  const initial = useMemo(() => new URLSearchParams(location.search), []);
  const [account, setAccount] = useState(initial.get('accountId') || ''),
    [status, setStatus] = useState(
      ['OPEN', 'LINKED', 'IGNORED'].includes(initial.get('status') || '')
        ? initial.get('status')!
        : 'OPEN',
    ),
    [page, setPage] = useState(0),
    [selected, setSelected] = useState<string[]>([]),
    [operation, setOperation] = useState<Operation>(),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [notice, setNotice] = useState(''),
    [upload, setUpload] = useState(false),
    [ofx, setOfx] = useState(''),
    [filename, setFilename] = useState(''),
    [preview, setPreview] = useState<OfxPreview>(),
    [rows, setRows] = useState<number[]>([]),
    [origin, setOrigin] = useState(false),
    [search, setSearch] = useState(''),
    [term, setTerm] = useState('');
  const lock = useRef(false),
    generation = useRef(0);
  const accounts = useResource('reconciliation-accounts', (signal) =>
    bridge.request<Account[]>('/finance/accounts', { signal }),
  );
  const categories = useResource('reconciliation-categories', (signal) =>
    bridge.request<Category[]>('/categories', { signal }),
  );
  useEffect(() => {
    if (accounts.data) {
      bridge.sync({ accounts: accounts.data });
      if (!accounts.data.some((a) => a.id === account))
        setAccount(accounts.data[0]?.id || '');
    }
  }, [accounts.data]);
  const data = useResource(
    'reconciliation:' + account + status + page,
    (signal) =>
      account
        ? bridge.request<MovementPage>(
            '/finance/reconciliation?' +
              query({ accountId: account, status, page, size: 25 }),
            { signal },
          )
        : Promise.resolve({
            content: [],
            number: 0,
            totalPages: 0,
            totalElements: 0,
            open: 0,
            linked: 0,
            ignored: 0,
          }),
  );
  useEffect(() => {
    const u = new URL(location.href);
    u.search = new URLSearchParams({
      page: 'reconciliation',
      accountId: account,
      status,
    }).toString();
    window.history.replaceState({}, '', u);
    setSelected([]);
  }, [account, status, page]);
  useEffect(() => {
    const t = setTimeout(() => setTerm(search), 250);
    return () => clearTimeout(t);
  }, [search]);
  const choices = useResource(
    'reconciliation-choices:' +
      operation?.kind +
      operation?.row?.id +
      term +
      selected.join(','),
    async (signal) => {
      if (operation?.kind === 'LINK')
        return bridge.request<Candidate[]>(
          '/finance/reconciliation/' +
            operation.row!.id +
            '/candidates?' +
            query({ search: term }),
          { signal },
        );
      if (operation?.kind === 'GROUP') {
        const entries = await bridge.request<Entry[]>(
          '/finance/reconciliation/group/candidates',
          {
            method: 'POST',
            body: JSON.stringify({ accountId: account, movementIds: selected }),
            signal,
          },
        );
        return entries.map((entry) => ({
          entry,
          explanation: 'Saldo restante suficiente para o grupo selecionado.',
          score: 0,
          reasons: [],
          rules: [],
        }));
      }
      return [];
    },
  );
  function refresh() {
    data.retry();
    accounts.retry();
  }
  async function perform(task: () => Promise<void>) {
    if (lock.current) return;
    lock.current = true;
    setBusy(true);
    setError('');
    try {
      await task();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  function changeScope(id: string) {
    generation.current++;
    setAccount(id);
    setPage(0);
    setPreview(undefined);
    setRows([]);
    setOrigin(false);
    setSelected([]);
    setError('');
    setNotice('');
    setOperation(undefined);
  }
  async function fileChanged(file?: File) {
    if (!file) return;
    const current = ++generation.current;
    setPreview(undefined);
    setOrigin(false);
    setFilename(file.name);
    setError('');
    setOfx('');
    try {
      if (file.size > 1048576 || !/\.ofx$/i.test(file.name))
        throw Error('Escolha um arquivo OFX de até 1 MB.');
      const buffer = await file.arrayBuffer();
      let text;
      try {
        text = new TextDecoder('utf-8', { fatal: true }).decode(buffer);
      } catch {
        text = new TextDecoder('windows-1252', { fatal: true }).decode(buffer);
      }
      if (!text.trim()) throw Error('O arquivo está vazio.');
      if (current === generation.current) setOfx(text);
    } catch (e) {
      if (current === generation.current) setError((e as Error).message);
    }
  }
  async function confirm(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (!operation || !bridge.canWrite) return;
    const input = Object.fromEntries(new FormData(e.currentTarget));
    await perform(async () => {
      if (operation.kind === 'IMPORT') {
        await bridge.request('/finance/reconciliation/import', {
          method: 'POST',
          body: JSON.stringify({ accountId: account, ofx, selectedRows: rows }),
        });
        setPreview(undefined);
        setUpload(false);
        setOfx('');
        setRows([]);
        setStatus('OPEN');
        setPage(0);
        setNotice(
          'Extrato adicionado à revisão. Confira e confirme cada correspondência.',
        );
      } else if (operation.kind === 'GROUP') {
        await bridge.request('/finance/reconciliation/group', {
          method: 'POST',
          body: JSON.stringify({
            accountId: account,
            movementIds: selected,
            entryId: input.entryId,
            reason: input.reason,
          }),
        });
        setSelected([]);
        setNotice(
          'Grupo conciliado com o histórico de cada movimento preservado.',
        );
      } else {
        await bridge.request(
          '/finance/reconciliation/' + operation.row!.id + '/resolve',
          {
            method: 'POST',
            body: JSON.stringify({
              revision: operation.row!.revision,
              action: operation.kind,
              entryId: input.entryId || null,
              category: input.category || null,
              categoryId: input.categoryId || null,
              reason: input.reason || null,
            }),
          },
        );
        setNotice(
          'Decisão registrada. Consulte o histórico para conferir a alteração.',
        );
      }
      setOperation(undefined);
      refresh();
    });
  }
  function open(kind: Operation['kind'], row?: Movement) {
    setOperation({ kind, row });
    setError('');
    setSearch('');
    setTerm('');
  }
  const amount = (row: Movement) => money(row.amount);
  return (
    <>
      <PageHeading
        title="Encontre o vínculo. Confira os motivos."
        description="Revise seu extrato com sugestões explicadas e confirme cada decisão."
      >
        {bridge.canWrite && (
          <button className="btn" onClick={() => setUpload((u) => !u)}>
            Importar OFX
          </button>
        )}
      </PageHeading>
      <QueryState {...accounts} />
      <div className="workspace-rec-scope">
        <Field label="Conta do Fintry">
          <select
            value={account}
            onChange={(e) => changeScope(e.target.value)}
            disabled={busy}
          >
            {accounts.data?.map((a) => (
              <option key={a.id} value={a.id}>
                {a.name}
              </option>
            ))}
          </select>
        </Field>
        <p>
          Um vínculo com lançamento pendente registra a baixa na data do banco.
          Um realizado na mesma data preserva o saldo.
        </p>
      </div>
      {notice && (
        <p className="workspace-scenario-note" role="status">
          {notice}
        </p>
      )}
      {error && !operation && (
        <p role="alert" className="workspace-field-error">
          {error}
        </p>
      )}
      {upload && bridge.canWrite && (
        <Panel title="Primeiro confira. Depois importe." eyebrow="EXTRATO OFX">
          <div className="workspace-rec-upload">
            <Field label="Arquivo OFX">
              <input
                type="file"
                accept=".ofx"
                disabled={busy}
                onChange={(e) => void fileChanged(e.target.files?.[0])}
              />
            </Field>
            <p>
              {filename || 'UTF-8 ou Windows-1252 · até 1 MB e 500 movimentos.'}
            </p>
            <a href="/api/v1/finance/reconciliation/example" download>
              Baixar OFX fictício
            </a>
            <button
              className="btn secondary"
              disabled={busy || !account || !ofx}
              onClick={() =>
                void perform(async () => {
                  const p = await bridge.request<OfxPreview>(
                    '/finance/reconciliation/preview',
                    {
                      method: 'POST',
                      body: JSON.stringify({ accountId: account, ofx }),
                    },
                  );
                  setPreview(p);
                  setRows(
                    p.rows
                      .filter((r) => r.state === 'READY')
                      .map((r) => r.number),
                  );
                  setOrigin(false);
                })
              }
            >
              Gerar prévia OFX
            </button>
            {preview && (
              <>
                <div className="workspace-form-context">
                  <strong>{preview.accountName}</strong>
                  <span>
                    Banco {preview.bank} · final {preview.accountHint}
                  </span>
                  <span>
                    {preview.ready} novos · {preview.duplicates} repetidos ·{' '}
                    {preview.invalid} com erro
                  </span>
                </div>
                <div className="workspace-rec-preview">
                  {preview.rows.map((r) => (
                    <label key={r.number}>
                      <input
                        type="checkbox"
                        disabled={r.state !== 'READY' || busy}
                        checked={rows.includes(r.number)}
                        onChange={(e) =>
                          setRows((old) =>
                            e.target.checked
                              ? [...old, r.number]
                              : old.filter((n) => n !== r.number),
                          )
                        }
                      />
                      <span>
                        <strong>{r.description}</strong>
                        <small>
                          {r.date ? dateLabel(r.date) : 'Data inválida'} ·{' '}
                          {r.message}
                        </small>
                      </span>
                      <b>{r.amount ? money(r.amount) : '—'}</b>
                    </label>
                  ))}
                </div>
                <label className="check">
                  <input
                    type="checkbox"
                    checked={origin}
                    onChange={(e) => setOrigin(e.target.checked)}
                  />
                  Conferi o banco e o final da conta de destino.
                </label>
                <button
                  className="btn"
                  disabled={!origin || !rows.length || busy}
                  onClick={() => open('IMPORT')}
                >
                  Adicionar selecionados à revisão
                </button>
              </>
            )}
          </div>
        </Panel>
      )}
      <div
        className="workspace-rec-tabs"
        role="group"
        aria-label="Situação da conciliação"
      >
        {(['OPEN', 'LINKED', 'IGNORED'] as const).map((s) => (
          <button
            key={s}
            className={status === s ? 'active' : ''}
            aria-pressed={status === s}
            onClick={() => {
              setStatus(s);
              setPage(0);
            }}
          >
            <span>{labels[s]}</span>
            <strong>
              {data.data?.[
                s === 'OPEN' ? 'open' : s === 'LINKED' ? 'linked' : 'ignored'
              ] || 0}
            </strong>
          </button>
        ))}
      </div>
      <div className="workspace-saved-toolbar">
        <span>{data.data?.totalElements || 0} movimento(s)</span>
        <button
          className="btn secondary small"
          disabled={busy}
          onClick={refresh}
        >
          Atualizar revisão
        </button>
        {selected.length >= 2 && bridge.canWrite && (
          <button className="btn" disabled={busy} onClick={() => open('GROUP')}>
            Conciliar {selected.length} movimentos em grupo
          </button>
        )}
      </div>
      <QueryState {...data} />
      <div className="workspace-rec-list">
        {data.data?.content.map((r) => (
          <article
            className="workspace-panel workspace-rec-movement"
            key={r.id}
          >
            <header>
              <div>
                <span className="eyebrow">
                  {r.amount.startsWith('-') ? 'SAÍDA' : 'ENTRADA'} ·{' '}
                  {dateLabel(r.date)}
                </span>
                <h2>{r.description}</h2>
                <small>Identificador: {r.fitId}</small>
              </div>
              <strong
                className={
                  r.amount.startsWith('-')
                    ? 'workspace-expense'
                    : 'workspace-income'
                }
              >
                {amount(r)}
              </strong>
            </header>
            {r.status === 'OPEN' ? (
              <>
                <div className="workspace-rec-suggestions">
                  <strong>
                    {r.suggestions.length
                      ? 'Correspondências para revisar'
                      : 'Nenhuma sugestão próxima'}
                  </strong>
                  {r.suggestions.map((c) => (
                    <div key={c.entry.id}>
                      <span>{c.entry.description}</span>
                      <small>{c.score} pontos de ordenação</small>
                      <ul>
                        {c.reasons.map((reason, i) => (
                          <li key={i}>{reason}</li>
                        ))}
                      </ul>
                    </div>
                  ))}
                  <p>
                    Pontos organizam sugestões; não representam probabilidade ou
                    aprovação automática. A busca examina até 100 lançamentos
                    compatíveis.
                  </p>
                </div>
                <div className="workspace-saved-toolbar">
                  {bridge.canWrite && (
                    <label className="check">
                      <input
                        type="checkbox"
                        checked={selected.includes(r.id)}
                        onChange={(e) =>
                          setSelected((old) =>
                            e.target.checked
                              ? [...old, r.id]
                              : old.filter((id) => id !== r.id),
                          )
                        }
                      />
                      Agrupar
                    </label>
                  )}
                  <button
                    className="btn secondary"
                    onClick={() => open('LINK', r)}
                  >
                    {bridge.canWrite
                      ? 'Revisar correspondências'
                      : 'Consultar correspondências'}
                  </button>
                  {bridge.canWrite && (
                    <>
                      <button
                        className="btn secondary"
                        onClick={() => open('CREATE', r)}
                      >
                        Criar lançamento
                      </button>
                      <button
                        className="btn text"
                        onClick={() => open('IGNORE', r)}
                      >
                        Ignorar movimento
                      </button>
                    </>
                  )}
                </div>
              </>
            ) : (
              <>
                <p>
                  {r.batchId
                    ? 'Grupo conciliado · ' + r.batchMovements + ' movimentos'
                    : r.entry
                      ? 'Vinculado a ' + r.entry.description
                      : 'Movimento ignorado'}
                  {r.reason ? ' · ' + r.reason : ''}
                </p>
                {r.evidence && (
                  <details className="workspace-rec-suggestions">
                    <summary>Justificativas no momento da confirmação</summary>
                    <p>{r.evidence.score} pontos de ordenação</p>
                    <ul>
                      {r.evidence.reasons.map((reason, i) => (
                        <li key={i}>{reason}</li>
                      ))}
                    </ul>
                    {r.evidence.rules.map((rule) => (
                      <small key={rule.id}>
                        {rule.name} · revisão {rule.revision + 1}
                      </small>
                    ))}
                  </details>
                )}
                <div className="workspace-saved-toolbar">
                  {r.entryId && (
                    <button
                      className="btn secondary"
                      onClick={() => bridge.details(r.entryId!)}
                    >
                      Ver lançamento
                    </button>
                  )}
                  {bridge.canWrite && !r.batchId && (
                    <button
                      className="btn text"
                      onClick={() => open('REOPEN', r)}
                    >
                      Reabrir revisão
                    </button>
                  )}
                </div>
              </>
            )}
          </article>
        ))}
      </div>
      {data.data && !data.data.content.length && (
        <div className="workspace-message">
          <h2>Nenhum movimento neste filtro.</h2>
          <p>Consulte outra situação ou importe um extrato OFX.</p>
        </div>
      )}
      {data.data && data.data.totalPages > 1 && (
        <nav className="workspace-pagination" aria-label="Movimentos">
          <button
            className="btn secondary"
            disabled={!page || busy}
            onClick={() => setPage((p) => p - 1)}
          >
            Anterior
          </button>
          <span>
            {page + 1}/{data.data.totalPages}
          </span>
          <button
            className="btn secondary"
            disabled={page + 1 >= data.data.totalPages || busy}
            onClick={() => setPage((p) => p + 1)}
          >
            Próxima
          </button>
        </nav>
      )}
      {account && (
        <Rules
          key={account}
          bridge={bridge}
          accountId={account}
          refresh={refresh}
        />
      )}
      {operation && (
        <Dialog
          title={
            {
              LINK: 'Revisar correspondências',
              CREATE: 'Criar lançamento do extrato',
              IGNORE: 'Ignorar movimento',
              REOPEN: 'Reabrir revisão',
              GROUP: 'Conciliar movimentos em grupo',
              IMPORT: 'Adicionar extrato à revisão',
            }[operation.kind]
          }
          description="Confira os dados e a consequência antes de confirmar."
          busy={busy}
          close={() => setOperation(undefined)}
        >
          <form className="workspace-finance-form" onSubmit={confirm}>
            {operation.row && (
              <div className="workspace-form-context">
                <strong>{operation.row.description}</strong>
                <span>
                  {dateLabel(operation.row.date)} · {amount(operation.row)}
                </span>
              </div>
            )}
            {(operation.kind === 'LINK' || operation.kind === 'GROUP') && (
              <>
                {operation.kind === 'LINK' && (
                  <Field label="Buscar descrição compatível">
                    <input
                      type="search"
                      maxLength={120}
                      value={search}
                      onChange={(e) => setSearch(e.target.value)}
                    />
                  </Field>
                )}
                <QueryState {...choices} />
                <div className="workspace-rec-options">
                  {choices.data?.map((c) => (
                    <label key={c.entry.id}>
                      <input
                        type="radio"
                        name="entryId"
                        value={c.entry.id}
                        required
                      />
                      <span>
                        <strong>{c.entry.description}</strong>
                        <small>
                          {money(c.entry.amount)} · {dateLabel(c.entry.dueDate)}
                          {c.score ? ' · ' + c.score + ' pontos' : ''}
                        </small>
                        <span>{c.explanation}</span>
                      </span>
                    </label>
                  ))}
                </div>
                {choices.data && !choices.data.length && (
                  <p>
                    Nenhuma correspondência disponível. Refine a busca ou
                    confira o financeiro antes de criar um lançamento.
                  </p>
                )}
                <p className="workspace-scenario-note">
                  Confirmar um pendente registra a baixa na data do extrato. Em
                  grupo, cada movimento mantém sua própria data.
                </p>
              </>
            )}
            {operation.kind === 'CREATE' && (
              <>
                <Field label="Categoria cadastrada">
                  <select name="categoryId">
                    <option value="">Usar categoria avulsa</option>
                    {categories.data
                      ?.filter(
                        (c) =>
                          c.active &&
                          (c.kind ===
                            (operation.row!.amount.startsWith('-')
                              ? 'EXPENSE'
                              : 'INCOME') ||
                            c.kind === 'BOTH'),
                      )
                      .map((c) => (
                        <option key={c.id} value={c.id}>
                          {c.name}
                        </option>
                      ))}
                  </select>
                </Field>
                <Field label="Categoria avulsa">
                  <input
                    name="category"
                    maxLength={80}
                    defaultValue="Movimentos bancários"
                  />
                </Field>
                <p className="workspace-scenario-note">
                  Será criado um lançamento realizado na data do banco,
                  alterando o saldo. Confira as correspondências para evitar
                  duplicidades.
                </p>
                <label className="check">
                  <input type="checkbox" required />
                  Conferi o financeiro e quero criar um novo lançamento.
                </label>
              </>
            )}
            {['CREATE', 'IGNORE', 'REOPEN', 'GROUP'].includes(
              operation.kind,
            ) && (
              <Field
                label={
                  operation.kind === 'CREATE'
                    ? 'Motivo, se houver lançamento com o mesmo valor'
                    : 'Motivo da decisão'
                }
              >
                <input
                  name="reason"
                  maxLength={255}
                  required={operation.kind !== 'CREATE'}
                />
              </Field>
            )}
            {operation.kind === 'REOPEN' && (
              <p className="workspace-scenario-note">
                Reabrir mantém a baixa já registrada. Confira o financeiro antes
                de corrigir os valores.
              </p>
            )}
            {operation.kind === 'IMPORT' && (
              <p>
                {rows.length} movimento(s) do banco {preview?.bank}, conta{' '}
                {preview?.accountHint}, para {preview?.accountName}. A
                importação adiciona registros à revisão.
              </p>
            )}
            {error && (
              <p role="alert" className="workspace-field-error">
                {error}
              </p>
            )}
            <footer className="workspace-form-actions">
              <button
                type="button"
                className="btn secondary"
                disabled={busy}
                onClick={() => setOperation(undefined)}
              >
                Voltar
              </button>
              {bridge.canWrite && (
                <button
                  className="btn"
                  disabled={
                    busy ||
                    ((operation.kind === 'LINK' ||
                      operation.kind === 'GROUP') &&
                      !choices.data?.length)
                  }
                >
                  {busy ? 'Registrando…' : 'Confirmar decisão'}
                </button>
              )}
            </footer>
          </form>
        </Dialog>
      )}
    </>
  );
}
