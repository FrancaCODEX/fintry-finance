import type { Bridge } from '../../app/bridge';
import { query, type Account } from '../../api/client';
import { useResource } from '../../shared/useResource';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import {
  FilterBar,
  Metrics,
  Table,
  usePageFilters,
  accountControl,
} from '../../shared/workspace';
import { Pagination } from '../../shared/Collection';
import { money, dateLabel, bounds } from '../../shared/format';
import type { Report } from './types';
export function Reports({ bridge }: { bridge: Bridge }) {
  const f = usePageFilters(
    'reports',
    { ...bounds(bridge.month), basis: 'PAYMENT', status: 'PAID' },
    [
      'start',
      'end',
      'basis',
      'status',
      'type',
      'accountId',
      'category',
      'contact',
    ],
  );
  const filters: Record<string, string> = {
    ...f.values,
    ...(f.values.basis === 'PAYMENT' ? { status: 'PAID' } : {}),
  };
  const resource = useResource('reports:' + query(filters), async (signal) => {
    const { index, ...values } = filters;
    const [data, accounts] = await Promise.all([
      bridge.request<Report>(
        '/reports?' + query({ ...values, page: f.index }),
        { signal },
      ),
      bridge.request<Account[]>('/finance/accounts', { signal }),
    ]);
    return { data, accounts };
  });
  const data = resource.data?.data;
  return (
    <>
      <PageHeading
        title="Relatórios"
        description="Compare, filtre e exporte as movimentações da empresa."
      >
        {['csv', 'xlsx', 'pdf'].map((format) => (
          <button
            key={format}
            className="btn secondary"
            onClick={() =>
              bridge.hostAction(
                'report-export',
                { format },
                { state: { reportFilters: filters } },
              )
            }
          >
            {format.toUpperCase()}
          </button>
        ))}
      </PageHeading>
      <Panel title="Período e critérios">
        <FilterBar
          draft={f.draft}
          setDraft={f.setDraft}
          submit={() =>
            f.apply({
              ...f.draft,
              index: '0',
              ...(f.draft.basis === 'PAYMENT' ? { status: 'PAID' } : {}),
            })
          }
          fields={[
            {
              name: 'start',
              label: 'Data inicial',
              type: 'date',
              required: true,
            },
            { name: 'end', label: 'Data final', type: 'date', required: true },
            {
              name: 'basis',
              label: 'Base do período',
              options: [
                ['PAYMENT', 'Data do pagamento / recebimento'],
                ['DUE', 'Data do vencimento'],
              ],
            },
            accountControl(resource.data?.accounts || []),
            {
              name: 'type',
              label: 'Tipo',
              options: [
                ['', 'Receitas e despesas'],
                ['INCOME', 'Receitas'],
                ['EXPENSE', 'Despesas'],
              ],
            },
            {
              name: 'status',
              label: 'Situação',
              disabled: f.draft.basis === 'PAYMENT',
              options: [
                ['', 'Todos os ativos'],
                ['PAID', 'Realizados'],
                ['PARTIAL', 'Parciais'],
                ['PENDING', 'Pendentes'],
                ['CANCELLED', 'Cancelados'],
              ],
            },
            {
              name: 'category',
              label: 'Categoria contém',
              type: 'search',
              maxLength: 80,
            },
            {
              name: 'contact',
              label: 'Cliente / fornecedor contém',
              type: 'search',
              maxLength: 120,
            },
          ]}
        />
      </Panel>
      <QueryState {...resource} />
      {data && (
        <>
          <Metrics
            items={[
              ['Receitas realizadas', money(data.totals.income)],
              ['Despesas realizadas', money(data.totals.expense)],
              ['A receber', money(data.totals.pendingIncome)],
              ['A pagar', money(data.totals.pendingExpense)],
            ]}
          />
          <div className="workspace-analysis">
            <Panel title="Despesas realizadas por categoria">
              {data.categories.map((c, i) => (
                <div className="workspace-category" key={i}>
                  <div>
                    <span>{c.category}</span>
                    <strong>{money(c.amount)}</strong>
                  </div>
                  <progress
                    value={c.amount}
                    max={Math.max(1, data.totals.expense)}
                    aria-label={c.category}
                  />
                </div>
              ))}
            </Panel>
            <Panel title="Resultado de caixa">
              <div className="workspace-empty">
                <strong>{money(data.totals.result)}</strong>
                <p>
                  Receitas menos despesas realizadas. Não representa lucro
                  contábil.
                </p>
              </div>
            </Panel>
          </div>
          <Panel title="Movimentações do período">
            <Table
              rows={data.content}
              keyOf={(r) => r.paymentId || r.id}
              columns={[
                {
                  label: 'Descrição',
                  render: (r) => (
                    <button
                      className="workspace-row-name"
                      onClick={() => bridge.details(r.id)}
                    >
                      {r.description}
                      <small>
                        {r.category} · {r.contactName || 'Sem vínculo'}
                      </small>
                    </button>
                  ),
                },
                { label: 'Conta', render: (r) => r.accountName },
                { label: 'Vencimento', render: (r) => dateLabel(r.dueDate) },
                {
                  label: 'Baixa',
                  render: (r) =>
                    r.paymentDate ? dateLabel(r.paymentDate) : '—',
                },
                { label: 'Valor', render: (r) => money(r.amount) },
              ]}
            />
            <Pagination
              page={f.index}
              pages={data.totalPages}
              total={data.totalElements}
              onChange={(index) =>
                f.apply({ ...f.values, index: String(index) })
              }
            />
          </Panel>
        </>
      )}
      <p className="workspace-caption">
        Exportações usam todos os filtros aplicados, até 5.000 linhas.
        Transferências e cancelados não compõem os totais.
      </p>
    </>
  );
}
