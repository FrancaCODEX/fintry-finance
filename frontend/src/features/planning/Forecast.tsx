import type { Bridge } from '../../app/bridge';
import { query, type Forecast as Result, type Account } from '../../api/client';
import { useResource } from '../../shared/useResource';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import {
  FilterBar,
  Metrics,
  Table,
  usePageFilters,
  accountControl,
} from '../../shared/workspace';
import { money, dateLabel } from '../../shared/format';
export function Forecast({ bridge }: { bridge: Bridge }) {
  const f = usePageFilters(
    'forecast',
    { days: '90', includeOverdue: 'false' },
    ['days', 'includeOverdue', 'accountId'],
  );
  const resource = useResource(
    'forecast:' + query(f.values),
    async (signal) => {
      const [data, accounts] = await Promise.all([
        bridge.request<Result>('/reports/forecast?' + query(f.values), {
          signal,
        }),
        bridge.request<Account[]>('/finance/accounts', { signal }),
      ]);
      return { data, accounts };
    },
  );
  const data = resource.data?.data;
  return (
    <>
      <PageHeading
        title="Fluxo projetado"
        description="Enxergue o caminho do caixa a partir dos vencimentos cadastrados."
      >
        <button
          className="btn secondary"
          onClick={() => bridge.navigate('scenarios')}
        >
          Simular cenários
        </button>
      </PageHeading>
      <Panel title="Horizonte da projeção">
        <FilterBar
          draft={f.draft}
          setDraft={f.setDraft}
          submit={() => f.apply({ ...f.draft, index: '0' })}
          fields={[
            {
              name: 'days',
              label: 'Horizonte',
              options: [30, 60, 90, 180, 366].map((n) => [
                String(n),
                `Até ${n} dias`,
              ]),
            },
            accountControl(resource.data?.accounts || []),
            {
              name: 'includeOverdue',
              label: 'Vencidas como previstas para hoje',
              options: [
                ['false', 'Não considerar'],
                ['true', 'Considerar vencidas'],
              ],
            },
          ]}
        />
      </Panel>
      <QueryState {...resource} />
      {data && (
        <>
          <Metrics
            items={[
              ['Saldo atual', money(data.openingBalance)],
              ['Saldo estimado ao final', money(data.closingBalance)],
              ['A receber no horizonte', money(data.pendingIncome)],
              ['A pagar no horizonte', money(data.pendingExpense)],
            ]}
          />
          <Panel title="No radar da projeção">
            <div className="workspace-empty">
              <strong>
                Menor saldo estimado: {money(data.minimumBalance)}
              </strong>
              <p>
                {data.firstNegativeDate
                  ? `Primeiro saldo negativo em ${dateLabel(data.firstNegativeDate)}.`
                  : 'Nenhum saldo negativo no horizonte.'}
              </p>
              <p>
                Vencidos a receber: {money(data.overdueIncome)} · A pagar:{' '}
                {money(data.overdueExpense)}
              </p>
            </div>
          </Panel>
          <Panel title="Evolução estimada do saldo">
            <ForecastChart data={data} />
            <Table
              rows={data.days}
              keyOf={(r) => r.date}
              columns={[
                { label: 'Data', render: (r) => dateLabel(r.date) },
                { label: 'A receber', render: (r) => money(r.income) },
                { label: 'A pagar', render: (r) => money(r.expense) },
                { label: 'Saldo estimado', render: (r) => money(r.balance) },
              ]}
            />
          </Panel>
          <p className="workspace-caption">
            A estimativa não altera saldos nem registra pagamentos. Totais
            líquidos por dia; não representam horários de entrada e saída.{' '}
            {data.includeOverdue
              ? 'Vencidas foram incluídas no dia de hoje.'
              : 'Vencidas não foram incluídas no saldo futuro.'}
          </p>
        </>
      )}
    </>
  );
}
function ForecastChart({ data }: { data: Result }) {
  const values = data.days.map((d) => Number(d.balance));
  const low = Math.min(0, ...values),
    high = Math.max(1, ...values),
    range = high - low || 1;
  const x = (i: number) => 50 + (i / Math.max(1, values.length - 1)) * 700,
    y = (v: number) => 25 + ((high - v) / range) * 180;
  return (
    <svg
      viewBox="0 0 800 245"
      className="workspace-cash-chart"
      role="img"
      aria-label="Evolução diária estimada; valores na tabela abaixo"
    >
      <line x1="50" x2="750" y1={y(0)} y2={y(0)} stroke="var(--line)" />
      <polyline
        points={values.map((v, i) => `${x(i)},${y(v)}`).join(' ')}
        fill="none"
        stroke="var(--green)"
        strokeWidth="3"
      />
      <text x="50" y="235">
        {dateLabel(data.start)}
      </text>
      <text x="750" y="235" textAnchor="end">
        {dateLabel(data.end)}
      </text>
    </svg>
  );
}
