import { Field } from '../../shared/Field';
import { useEffect, useMemo, useState } from 'react';
import type { Bridge } from '../../app/bridge';
import { workspaceApi, type Entry } from '../../api/client';
import { readLocation, saveLocation } from '../../app/location';
import { bounds, validMonth, money, dateLabel } from '../../shared/format';
import { PageHeading, QueryState } from '../../shared/components';
import { useResource } from '../../shared/useResource';
type Filters = {
  month: string;
  search: string;
  type: string;
  status: string;
  accountId: string;
  page: number;
};
export function Entries({ bridge }: { bridge: Bridge }) {
  const [filters, setFilters] = useState<Filters>(() => {
    const { month, params } = readLocation(bridge.month),
      page = Number(params.get('p') || 0);
    return {
      month,
      search: (params.get('search') || '').slice(0, 255),
      type: ['INCOME', 'EXPENSE'].includes(params.get('type') || '')
        ? params.get('type')!
        : '',
      status: ['PENDING', 'PARTIAL', 'PAID', 'CANCELLED'].includes(
        params.get('status') || '',
      )
        ? params.get('status')!
        : '',
      accountId: params.get('accountId') || '',
      page: Number.isSafeInteger(page) && page >= 0 ? page : 0,
    };
  });
  const [draft, setDraft] = useState(filters);
  const client = useMemo(() => workspaceApi(bridge.request), [bridge]);
  const query = useResource(
    'entries:' + JSON.stringify(filters),
    async (signal) => {
      const [accounts, entries] = await Promise.all([
        client.accounts(signal),
        client.entries(
          {
            ...bounds(filters.month),
            search: filters.search,
            type: filters.type,
            status: filters.status,
            accountId: filters.accountId,
            page: filters.page,
            size: 20,
          },
          signal,
        ),
      ]);
      return { accounts, entries };
    },
  );
  const { data } = query;
  function apply(next: Filters, replace = false) {
    saveLocation(
      'entries',
      { ...next, page: undefined, p: next.page },
      !replace,
    );
    setFilters(next);
    setDraft(next);
  }
  useEffect(() => {
    saveLocation(
      'entries',
      { ...filters, page: undefined, p: filters.page },
      false,
    );
    bridge.sync({ month: filters.month });
  }, [filters, bridge]);
  useEffect(() => {
    if (data) {
      bridge.sync(data);
      if (filters.page > 0 && filters.page >= data.entries.totalPages)
        apply(
          { ...filters, page: Math.max(0, data.entries.totalPages - 1) },
          true,
        );
    }
  }, [data, bridge]);
  const names = new Map(data?.accounts.map((a) => [a.id, a.name]));
  function status(row: Entry) {
    if (row.seriesPaused) return 'Pausado';
    return {
      PENDING: 'Pendente',
      PARTIAL: 'Parcial',
      PAID: row.type === 'INCOME' ? 'Recebido' : 'Pago',
      CANCELLED: 'Cancelado',
    }[row.status];
  }
  return (
    <>
      <PageHeading
        title="Cada movimento, no lugar."
        description="Consulte valores, acompanhe baixas e mantenha o histórico por perto."
      >
        {bridge.canWrite && (
          <>
            <button
              className="btn secondary"
              onClick={() => bridge.navigate('imports')}
            >
              Importar CSV
            </button>
            <button className="btn" onClick={() => bridge.action('entry')}>
              ＋ Novo lançamento
            </button>
          </>
        )}
      </PageHeading>
      <section className="workspace-panel workspace-entry-panel">
        <form
          className="workspace-entry-filters"
          onSubmit={(e) => {
            e.preventDefault();
            apply({ ...draft, page: 0 });
          }}
        >
          <Field className="field" label={<>Buscar lançamento</>}>
            <input
              type="search"
              maxLength={255}
              placeholder="Descrição do movimento"
              value={draft.search}
              onChange={(e) => setDraft({ ...draft, search: e.target.value })}
            />
          </Field>
          <Field className="field" label={<>Mês de vencimento</>}>
            <input
              type="month"
              min="2000-01"
              max="2100-12"
              required
              value={draft.month}
              onChange={(e) => {
                if (validMonth(e.target.value))
                  setDraft({ ...draft, month: e.target.value });
              }}
            />
          </Field>
          <Field className="field" label={<>Tipo</>}>
            <select
              value={draft.type}
              onChange={(e) => setDraft({ ...draft, type: e.target.value })}
            >
              <option value="">Receitas e despesas</option>
              <option value="INCOME">Receitas</option>
              <option value="EXPENSE">Despesas</option>
            </select>
          </Field>
          <Field className="field" label={<>Situação</>}>
            <select
              value={draft.status}
              onChange={(e) => setDraft({ ...draft, status: e.target.value })}
            >
              <option value="">Todas as situações</option>
              <option value="PENDING">Pendentes</option>
              <option value="PARTIAL">Parciais</option>
              <option value="PAID">Realizados</option>
              <option value="CANCELLED">Cancelados</option>
            </select>
          </Field>
          <Field className="field" label={<>Conta</>}>
            <select
              value={draft.accountId}
              onChange={(e) =>
                setDraft({ ...draft, accountId: e.target.value })
              }
            >
              <option value="">Todas as contas</option>
              {data?.accounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                </option>
              ))}
            </select>
          </Field>
          <div className="workspace-filter-actions">
            <button className="btn" type="submit">
              Aplicar filtros
            </button>
            <button
              className="btn text"
              type="button"
              onClick={() =>
                apply({
                  ...filters,
                  search: '',
                  type: '',
                  status: '',
                  accountId: '',
                  page: 0,
                })
              }
            >
              Limpar filtros
            </button>
          </div>
        </form>
        <QueryState {...query} />
        {data && (
          <>
            <div className="workspace-list-meta">
              <span>
                {data.entries.totalElements} lançamento(s) encontrados
              </span>
              <span>Organizados pelo vencimento</span>
            </div>
            {data.entries.content.length ? (
              <div
                className="workspace-entry-table"
                role="region"
                aria-label="Lançamentos"
                tabIndex={0}
              >
                <table>
                  <thead>
                    <tr>
                      <th scope="col">Movimento</th>
                      <th scope="col">Vencimento</th>
                      <th scope="col">Conta</th>
                      <th scope="col">Valor</th>
                      <th scope="col">Situação</th>
                      <th scope="col">Ações</th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.entries.content.map((row) => (
                      <tr key={row.id}>
                        <td data-label="Movimento">
                          <button
                            className="workspace-row-name"
                            onClick={() => bridge.details(row.id)}
                          >
                            {row.description}
                          </button>
                          <small>
                            {row.category || 'Sem categoria'}
                            {row.contactName ? ' · ' + row.contactName : ''}
                          </small>
                          {row.seriesId && (
                            <span className="workspace-origin">
                              {row.seriesKind === 'INSTALLMENT'
                                ? 'Parcela'
                                : 'Recorrência'}{' '}
                              {row.sequenceNumber}/{row.seriesCount}
                            </span>
                          )}
                          {row.saleId && (
                            <span className="workspace-origin">
                              Venda vinculada
                            </span>
                          )}
                          {row.transferId && (
                            <span className="workspace-origin">
                              Transferência
                            </span>
                          )}
                        </td>
                        <td data-label="Vencimento">
                          {dateLabel(row.dueDate)}
                        </td>
                        <td data-label="Conta">
                          {names.get(row.accountId) || 'Conta'}
                        </td>
                        <td data-label="Valor">
                          <strong
                            className={
                              row.type === 'INCOME'
                                ? 'workspace-income'
                                : 'workspace-expense'
                            }
                          >
                            {row.type === 'INCOME' ? '+' : '−'}{' '}
                            {money(row.amount)}
                          </strong>
                          {row.receivedAmount > 0 && (
                            <small>
                              Baixado {money(row.receivedAmount)}
                              <br />
                              Restante {money(row.remainingAmount)}
                            </small>
                          )}
                        </td>
                        <td data-label="Situação">
                          <span
                            className={`workspace-status ${row.status.toLowerCase()}`}
                          >
                            {status(row)}
                          </span>
                        </td>
                        <td data-label="Ações">
                          <div className="workspace-row-actions">
                            <button
                              className="btn secondary small"
                              onClick={() => bridge.details(row.id)}
                              aria-label={'Detalhes de ' + row.description}
                            >
                              Detalhes
                            </button>
                            {bridge.canWrite &&
                              ['PENDING', 'PARTIAL'].includes(row.status) && (
                                <>
                                  <button
                                    className="btn soft small"
                                    onClick={() => bridge.action('pay', row)}
                                  >
                                    {row.type === 'INCOME'
                                      ? 'Receber'
                                      : 'Pagar'}
                                  </button>
                                  {row.status === 'PENDING' &&
                                    !row.seriesId &&
                                    !row.saleId && (
                                      <button
                                        className="btn secondary small"
                                        aria-label={'Editar ' + row.description}
                                        onClick={() =>
                                          bridge.action('edit-entry', row)
                                        }
                                      >
                                        Editar
                                      </button>
                                    )}
                                </>
                              )}
                            {bridge.canWrite &&
                              row.status !== 'CANCELLED' &&
                              !row.transferId &&
                              !row.saleId && (
                                <button
                                  className="btn text small"
                                  onClick={() => bridge.action('cancel', row)}
                                >
                                  Cancelar
                                </button>
                              )}
                          </div>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="workspace-empty">
                <h2>Nenhum movimento nesta consulta</h2>
                <p>
                  Ajuste o período ou limpe os filtros para explorar outros
                  lançamentos.
                </p>
              </div>
            )}
            <nav
              className="workspace-pagination"
              aria-label="Paginação dos lançamentos"
            >
              <span>
                Página {filters.page + 1} de{' '}
                {Math.max(1, data.entries.totalPages)}
              </span>
              <div>
                <button
                  className="btn secondary small"
                  disabled={!filters.page}
                  onClick={() => apply({ ...filters, page: filters.page - 1 })}
                >
                  Anterior
                </button>
                <button
                  className="btn secondary small"
                  disabled={filters.page + 1 >= data.entries.totalPages}
                  onClick={() => apply({ ...filters, page: filters.page + 1 })}
                >
                  Próxima
                </button>
              </div>
            </nav>
          </>
        )}
      </section>
    </>
  );
}
