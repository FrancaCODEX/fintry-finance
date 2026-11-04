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
import { money, dateLabel } from '../../shared/format';
import type { Pending as Result } from './types';
const groups: Record<string, string> = {
  ALL: 'Todas as pendências',
  OVERDUE: 'Vencidas',
  TODAY: 'Vencem hoje',
  NEXT7: 'Próximos 7 dias',
  NEXT30: 'De 8 a 30 dias',
  UPCOMING: 'Hoje e próximos 7 dias',
};
export function Pending({ bridge }: { bridge: Bridge }) {
  const f = usePageFilters('pending', { group: 'ALL', source: 'ALL' }, [
    'group',
    'source',
    'search',
    'type',
    'accountId',
  ]);
  const resource = useResource('pending:' + query(f.values), async (signal) => {
    const { index, ...filters } = f.values;
    const [data, accounts] = await Promise.all([
      bridge.request<Result>(
        '/finance/pending?' + query({ ...filters, page: f.index }),
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
        title="Pendências"
        description="Priorize vencimentos e registre pagamentos e recebimentos."
      >
        {bridge.canWrite && (
          <button className="btn" onClick={() => bridge.action('entry')}>
            Novo lançamento
          </button>
        )}
      </PageHeading>
      <QueryState {...resource} />
      {data && (
        <>
          <p className="workspace-caption">
            Referência: {dateLabel(data.today)} · horário de Brasília
          </p>
          <section
            className="workspace-metrics"
            aria-label="Grupos de vencimento"
          >
            {data.windows.map((w) => (
              <button
                className="workspace-window"
                key={w.group}
                aria-pressed={w.group === f.values.group}
                onClick={() =>
                  f.apply({ ...f.values, group: w.group, index: '0' })
                }
              >
                <span>{groups[w.group]}</span>
                <strong>{w.totals.count} pendência(s)</strong>
                <small>
                  A receber {money(w.totals.income)} · A pagar{' '}
                  {money(w.totals.expense)}
                </small>
              </button>
            ))}
          </section>
          <Panel title="Próximos compromissos">
            <FilterBar
              draft={f.draft}
              setDraft={f.setDraft}
              submit={() => f.apply({ ...f.draft, index: '0' })}
              fields={[
                { name: 'search', label: 'Buscar pendência', type: 'search' },
                {
                  name: 'group',
                  label: 'Vencimento',
                  options: Object.entries(groups),
                },
                {
                  name: 'type',
                  label: 'Tipo',
                  options: [
                    ['', 'Receitas e despesas'],
                    ['INCOME', 'A receber'],
                    ['EXPENSE', 'A pagar'],
                  ],
                },
                accountControl(resource.data!.accounts),
                {
                  name: 'source',
                  label: 'Origem',
                  options: [
                    ['ALL', 'Todas'],
                    ['SINGLE', 'Sem série'],
                    ['RECURRING', 'Recorrências'],
                    ['INSTALLMENT', 'Parcelamentos'],
                  ],
                },
              ]}
            />
            <Metrics
              items={[
                ['A receber', money(data.totals.income)],
                ['A pagar', money(data.totals.expense)],
                ['Pendências', data.totals.count],
              ]}
            />
            <Table
              rows={data.content}
              keyOf={(r) => r.id}
              columns={[
                {
                  label: 'Lançamento',
                  render: (r) => (
                    <>
                      <strong>{r.description}</strong>
                      <small>{r.category}</small>
                    </>
                  ),
                },
                { label: 'Vencimento', render: (r) => dateLabel(r.dueDate) },
                { label: 'Restante', render: (r) => money(r.remainingAmount) },
                {
                  label: 'Ações',
                  render: (r) => (
                    <div className="workspace-row-actions">
                      <button
                        className="btn secondary small"
                        onClick={() => bridge.details(r.id)}
                      >
                        Detalhes
                      </button>
                      {bridge.canWrite && (
                        <button
                          className="btn small"
                          onClick={() => bridge.action('pay', r)}
                        >
                          {r.type === 'INCOME' ? 'Receber' : 'Pagar'}
                        </button>
                      )}
                    </div>
                  ),
                },
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
          <p className="workspace-caption">
            Totais de todas as páginas filtradas. Valores previstos, ainda não
            realizados.
          </p>
        </>
      )}
    </>
  );
}
