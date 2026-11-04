import { useEffect, useState } from 'react';
import type { Summary } from '../../api/client';
import { money, monthLabel } from '../../shared/format';
import { Panel } from '../../shared/components';
import {
  defaults,
  readPreferences,
  type ChartConfig,
  type Series,
} from './preferences';
const seriesInfo = {
  income: { label: 'Receitas', color: 'var(--chart-income)' },
  expense: { label: 'Despesas', color: 'var(--chart-expense)' },
  result: { label: 'Resultado', color: 'var(--chart-result)' },
};
export function CashChart({
  summary,
  userId,
  onExplore,
}: {
  summary: Summary;
  userId: string;
  onExplore: (month: string) => void;
}) {
  const [prefs, setPrefs] = useState(() => readPreferences(userId));
  const [selected, setSelected] = useState<string>('');
  const [notice, setNotice] = useState('');
  const [name, setName] = useState('');
  useEffect(() => {
    try {
      localStorage.setItem(
        'fintry:dashboard-charts:' + userId,
        JSON.stringify(prefs),
      );
    } catch {
      setNotice('As preferências ficam disponíveis apenas nesta sessão.');
    }
  }, [prefs, userId]);
  const config: ChartConfig =
    prefs.views.find((v) => v.id === prefs.active) ?? prefs.current;
  const flow = summary.flow.slice(-config.period);
  const active = flow.find((m) => m.month === selected) ?? flow.at(-1);
  function update(change: Partial<ChartConfig>) {
    setPrefs((p) => ({
      ...p,
      current: { ...config, ...change },
      views: p.views.map((v) => (v.id === p.active ? { ...v, ...change } : v)),
    }));
  }
  function toggle(series: Series) {
    const next = config.series.includes(series)
      ? config.series.filter((s) => s !== series)
      : [...config.series, series];
    if (next.length) update({ series: next });
  }
  const value = (m: Summary['flow'][number], s: Series) => m[s];
  const values = flow.flatMap((m) => config.series.map((s) => value(m, s)));
  const min = Math.min(0, ...values),
    max = Math.max(1, ...values),
    span = max - min;
  const y = (v: number) => 232 - ((v - min) / span) * 200;
  const step = 680 / Math.max(flow.length, 1),
    x = (i: number) => 70 + step * (i + 0.5);
  const zero = y(0);
  return (
    <Panel
      title="A história do seu caixa"
      eyebrow="HISTÓRICO REALIZADO"
      action={
        <button
          className="btn text small"
          onClick={() => active && onExplore(active.month)}
        >
          Ver baixas do mês ↗
        </button>
      }
    >
      <div className="workspace-chart-controls">
        <label className="field">
          Visualização
          <select
            aria-label="Visualização salva"
            value={prefs.active}
            onChange={(e) =>
              setPrefs((p) => ({
                ...p,
                active: e.target.value,
                current:
                  p.views.find((v) => v.id === e.target.value) ?? defaults(),
              }))
            }
          >
            <option value="default">Fluxo de caixa</option>
            {prefs.views.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name}
              </option>
            ))}
          </select>
        </label>
        <div
          className="workspace-segments"
          role="group"
          aria-label="Período do gráfico"
        >
          {([3, 6] as const).map((period) => (
            <button
              key={period}
              aria-pressed={config.period === period}
              onClick={() => update({ period })}
            >
              {period} meses
            </button>
          ))}
        </div>
        <div
          className="workspace-segments"
          role="group"
          aria-label="Formato do gráfico"
        >
          {(['bar', 'line'] as const).map((type) => (
            <button
              key={type}
              aria-pressed={config.type === type}
              onClick={() => update({ type })}
            >
              {type === 'bar' ? 'Barras' : 'Linhas'}
            </button>
          ))}
        </div>
      </div>
      <div className="workspace-chart-legend">
        {(Object.keys(seriesInfo) as Series[]).map((s) => (
          <button
            key={s}
            aria-pressed={config.series.includes(s)}
            onClick={() => toggle(s)}
          >
            <i className={'workspace-series-' + s} />
            {seriesInfo[s].label}
          </button>
        ))}
        <details className="workspace-save">
          <summary>Salvar visualização</summary>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!name.trim()) return;
              if (prefs.views.length >= 10) {
                setNotice('Você pode salvar até dez visualizações.');
                return;
              }
              const id = crypto.randomUUID();
              setPrefs((p) => ({
                ...p,
                active: id,
                views: [...p.views, { ...config, id, name: name.trim() }],
              }));
              setName('');
              setNotice('Visualização salva.');
            }}
          >
            <label className="field">
              Nome
              <input
                maxLength={32}
                required
                value={name}
                onChange={(e) => setName(e.target.value)}
              />
            </label>
            <button className="btn small">Salvar</button>
          </form>
        </details>
        {prefs.active !== 'default' && (
          <button
            className="btn text small"
            onClick={() =>
              setPrefs((p) => ({
                ...p,
                active: 'default',
                current: defaults(),
                views: p.views.filter((v) => v.id !== p.active),
              }))
            }
          >
            Excluir visualização
          </button>
        )}
      </div>
      {flow.length > 0 ? (
        <>
          <svg
            className="workspace-cash-chart"
            viewBox="0 0 800 270"
            role="img"
            aria-label={`Fluxo realizado de ${flow.length} meses. Consulte os valores nos botões e na tabela abaixo.`}
          >
            {[0, 1, 2, 3].map((i) => {
              const amount = min + (span * i) / 3;
              return (
                <g key={i}>
                  <line
                    x1={70}
                    x2={750}
                    y1={y(amount)}
                    y2={y(amount)}
                    stroke="var(--line)"
                    strokeDasharray="4 6"
                  />
                  <text x={56} y={y(amount) + 4} textAnchor="end">
                    {new Intl.NumberFormat('pt-BR', {
                      notation: 'compact',
                      maximumFractionDigits: 1,
                    }).format(amount)}
                  </text>
                </g>
              );
            })}
            {active && (
              <rect
                x={x(flow.indexOf(active)) - step / 2 + 4}
                y={20}
                width={step - 8}
                height={225}
                rx={12}
                fill="var(--soft)"
              />
            )}
            <line
              x1={70}
              x2={750}
              y1={zero}
              y2={zero}
              stroke="var(--muted)"
              opacity={0.4}
            />
            {config.series.map((s, si) => (
              <g key={s} fill={seriesInfo[s].color} pointerEvents="none">
                {config.type === 'line' ? (
                  <>
                    <path
                      d={flow
                        .map(
                          (m, i) => `${i ? 'L' : 'M'}${x(i)},${y(value(m, s))}`,
                        )
                        .join(' ')}
                      fill="none"
                      stroke={seriesInfo[s].color}
                      strokeWidth={3}
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    />
                    {flow.map((m, i) => (
                      <circle
                        key={m.month}
                        cx={x(i)}
                        cy={y(value(m, s))}
                        r={m.month === active?.month ? 6 : 4}
                        stroke="var(--paper)"
                        strokeWidth={2}
                      />
                    ))}
                  </>
                ) : (
                  flow.map((m, i) => (
                    <rect
                      key={m.month}
                      x={x(i) + (si - (config.series.length - 1) / 2) * 20 - 8}
                      y={Math.min(y(value(m, s)), zero)}
                      width={16}
                      height={Math.max(1, Math.abs(zero - y(value(m, s))))}
                      rx={4}
                    />
                  ))
                )}
              </g>
            ))}
            {flow.map((m, i) => (
              <rect
                key={m.month}
                x={x(i) - step / 2}
                y={10}
                width={step}
                height={240}
                fill="transparent"
                onPointerEnter={() => setSelected(m.month)}
                onClick={() => setSelected(m.month)}
              />
            ))}
          </svg>
          <div className="workspace-months" aria-label="Selecione um mês">
            {flow.map((m) => (
              <button
                key={m.month}
                aria-pressed={m.month === active?.month}
                onClick={() => setSelected(m.month)}
                onFocus={() => setSelected(m.month)}
              >
                {monthLabel(m.month, true)}
              </button>
            ))}
          </div>
          {active && (
            <div className="workspace-chart-values" aria-live="polite">
              <strong>{monthLabel(active.month)}</strong>
              {config.series.map((s) => (
                <span key={s}>
                  {seriesInfo[s].label}
                  <b>{money(value(active, s))}</b>
                </span>
              ))}
            </div>
          )}
        </>
      ) : (
        <p className="workspace-empty">
          Seu histórico será exibido quando houver movimentações realizadas.
        </p>
      )}
      <details className="workspace-data-table">
        <summary>Consultar valores em tabela</summary>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Mês</th>
                {config.series.map((s) => (
                  <th key={s}>{seriesInfo[s].label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {flow.map((m) => (
                <tr key={m.month}>
                  <th scope="row">{monthLabel(m.month)}</th>
                  {config.series.map((s) => (
                    <td key={s}>{money(value(m, s))}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
      <p className="workspace-caption">
        Valores pela data da baixa. Transferências não compõem o resultado.
        Visualizações ficam neste navegador.
      </p>
      <p role="status" className="workspace-caption">
        {notice}
      </p>
    </Panel>
  );
}
