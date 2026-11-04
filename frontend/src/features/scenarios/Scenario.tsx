import { useEffect, useMemo, useRef, useState } from 'react';
import { saveLocation } from '../../app/location';
import type { Bridge } from '../../app/bridge';
import {
  query,
  workspaceApi,
  type EntryPage,
  type ScenarioInput,
  type ScenarioPreview,
} from '../../api/client';
import { money, dateLabel } from '../../shared/format';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import { useResource } from '../../shared/useResource';
type Delay = { entryId: string; label: string; delayDays: number };
type Hypothesis = {
  id: string;
  label: string;
  type: 'INCOME' | 'EXPENSE';
  amount: string;
  date: string;
  occurrences: number;
};
export function Scenario({ bridge }: { bridge: Bridge }) {
  const [days, setDays] = useState(90),
    [accountId, setAccount] = useState(''),
    [includeOverdue, setOverdue] = useState(false);
  const [shifts, setShifts] = useState<Delay[]>([]),
    [hypotheses, setHypotheses] = useState<Hypothesis[]>([]);
  const [search, setSearch] = useState(''),
    [term, setTerm] = useState(''),
    [candidatePage, setCandidatePage] = useState(0),
    [chosen, setChosen] = useState('');
  const [result, setResult] = useState<ScenarioPreview>(),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [selected, setSelected] = useState(0),
    [appliedSignature, setAppliedSignature] = useState('');
  const pending = useRef<AbortController | null>(null),
    applied = useRef<ScenarioInput | null>(null);
  const client = useMemo(() => workspaceApi(bridge.request), [bridge]);
  const accounts = useResource('scenario-accounts', (signal) =>
    client.accounts(signal),
  );
  useEffect(() => {
    const timer = setTimeout(() => {
      setTerm(search);
      setCandidatePage(0);
    }, 250);
    return () => clearTimeout(timer);
  }, [search]);
  const candidates = useResource(
    'candidates:' +
      JSON.stringify({ days, accountId, includeOverdue, term, candidatePage }),
    (signal) =>
      bridge.request<EntryPage>(
        '/scenarios/candidates?' +
          query({
            days,
            accountId,
            includeOverdue,
            search: term,
            page: candidatePage,
            size: 30,
          }),
        { signal },
      ),
  );
  function draft(): ScenarioInput {
    return {
      days,
      ...(accountId ? { accountId } : {}),
      includeOverdue,
      shifts: shifts.map(({ entryId, delayDays }) => ({ entryId, delayDays })),
      hypotheses: hypotheses.map(({ id, ...h }) => ({
        ...h,
        amount: h.amount.replace(',', '.'),
      })),
    };
  }
  const signature = JSON.stringify(draft());
  const dirty = appliedSignature !== signature;
  async function run(input: ScenarioInput, signatureToApply: string) {
    pending.current?.abort();
    const controller = new AbortController();
    pending.current = controller;
    setBusy(true);
    setError('');
    try {
      const next = await bridge.request<ScenarioPreview>('/scenarios/preview', {
        method: 'POST',
        body: JSON.stringify(input),
        signal: controller.signal,
      });
      if (controller.signal.aborted) return;
      applied.current = input;
      setResult(next);
      setSelected(
        Math.max(
          0,
          next.baseline.days.findIndex((d) => d.date === next.explanation.date),
        ),
      );
      setAppliedSignature(signatureToApply);
    } catch (e) {
      if (!controller.signal.aborted)
        setError(
          e instanceof Error
            ? e.message
            : 'Não foi possível comparar o cenário.',
        );
    } finally {
      if (!controller.signal.aborted) setBusy(false);
    }
  }
  useEffect(() => {
    saveLocation('scenarios', {}, false);
    void run(
      { days: 90, includeOverdue: false, shifts: [], hypotheses: [] },
      JSON.stringify({
        days: 90,
        includeOverdue: false,
        shifts: [],
        hypotheses: [],
      }),
    );
    return () => pending.current?.abort();
  }, []);
  const point = result?.scenario.days[selected];
  function explain(index: number) {
    setSelected(index);
    if (result && applied.current)
      void run(
        { ...applied.current, explainDate: result.scenario.days[index].date },
        appliedSignature,
      );
  }
  function setScope(account: string, horizon: number, overdue: boolean) {
    setAccount(account);
    setDays(horizon);
    setOverdue(overdue);
    setShifts([]);
    setChosen('');
    setCandidatePage(0);
  }
  const today =
    result?.baseline.start ||
    new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Sao_Paulo' }).format(
      new Date(),
    );
  const balances = result
    ? [...result.baseline.days, ...result.scenario.days].map((p) =>
        Number(p.balance),
      )
    : [];
  const min = Math.min(0, ...balances),
    max = Math.max(1, ...balances),
    span = max - min;
  const plotY = (value: string) => 220 - ((Number(value) - min) / span) * 180;
  const plotX = (index: number) =>
    60 + (index / Math.max(1, (result?.scenario.days.length || 1) - 1)) * 680;
  return (
    <>
      <PageHeading
        title="E se o caixa mudasse?"
        description="Compare hipóteses com o fluxo previsto e acompanhe o efeito em cada dia."
      />
      <div className="workspace-scenario-note">
        <strong>Um espaço para planejar.</strong> As hipóteses ficam nesta
        página e não alteram lançamentos, pagamentos ou saldos reais.
      </div>
      <div className="workspace-scenario-layout">
        <form
          className="workspace-scenario-editor"
          onSubmit={(e) => {
            e.preventDefault();
            void run(draft(), signature);
          }}
        >
          <Panel title="Prepare o cenário" eyebrow="01 · PREMISSAS">
            <div className="workspace-scenario-fields">
              <QueryState {...accounts} />
              <label className="field">
                Horizonte
                <select
                  value={days}
                  onChange={(e) =>
                    setScope(accountId, Number(e.target.value), includeOverdue)
                  }
                >
                  {[30, 60, 90, 180, 366].map((n) => (
                    <option key={n} value={n}>
                      {n} dias
                    </option>
                  ))}
                </select>
              </label>
              <label className="field">
                Conta da projeção
                <select
                  value={accountId}
                  onChange={(e) =>
                    setScope(e.target.value, days, includeOverdue)
                  }
                >
                  <option value="">Todas as contas</option>
                  {accounts.data?.map((a) => (
                    <option key={a.id} value={a.id}>
                      {a.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="check">
                <input
                  type="checkbox"
                  checked={includeOverdue}
                  onChange={(e) => setScope(accountId, days, e.target.checked)}
                />
                Considerar vencidas como previstas para hoje
              </label>
              <p className="workspace-caption">
                Mudar o escopo remove os adiamentos selecionados para consultar
                apenas movimentos da nova projeção.
              </p>
            </div>
          </Panel>
          <Panel
            title="Adie um recebimento ou pagamento"
            eyebrow="02 · MOVIMENTOS EXISTENTES"
          >
            <div className="workspace-scenario-fields">
              <label className="field">
                Buscar movimento pendente
                <input
                  type="search"
                  maxLength={255}
                  value={search}
                  onChange={(e) => setSearch(e.target.value)}
                  placeholder="Descrição do lançamento"
                />
              </label>
              <label className="field">
                Movimento para adiar
                <select
                  value={chosen}
                  onChange={(e) => setChosen(e.target.value)}
                >
                  <option value="">Escolha um movimento</option>
                  {candidates.data?.content.map((row) => (
                    <option
                      key={row.id}
                      value={row.id}
                      disabled={shifts.some((s) => s.entryId === row.id)}
                    >
                      {row.description} · restante {money(row.remainingAmount)}
                    </option>
                  ))}
                </select>
              </label>
              <QueryState {...candidates} />
              <div className="workspace-row-actions">
                <button
                  type="button"
                  className="btn secondary small"
                  disabled={!chosen || shifts.length >= 20}
                  onClick={() => {
                    const row = candidates.data?.content.find(
                      (r) => r.id === chosen,
                    );
                    if (row && !shifts.some((s) => s.entryId === row.id)) {
                      setShifts([
                        ...shifts,
                        {
                          entryId: row.id,
                          label: row.description,
                          delayDays: 7,
                        },
                      ]);
                      setChosen('');
                    }
                  }}
                >
                  Adicionar adiamento
                </button>
                <button
                  type="button"
                  className="btn text small"
                  disabled={!candidatePage}
                  onClick={() => setCandidatePage((p) => p - 1)}
                >
                  Anteriores
                </button>
                <button
                  type="button"
                  className="btn text small"
                  disabled={
                    !candidates.data ||
                    candidatePage + 1 >= candidates.data.totalPages
                  }
                  onClick={() => setCandidatePage((p) => p + 1)}
                >
                  Próximos
                </button>
              </div>
              {candidates.data && !candidates.data.totalElements && (
                <p className="workspace-caption">
                  Nenhum movimento pendente neste escopo. Você ainda pode
                  adicionar hipóteses abaixo.
                </p>
              )}
              {shifts.map((s, i) => (
                <div key={s.entryId} className="workspace-hypothesis">
                  <strong>{s.label}</strong>
                  <label className="field">
                    Dias de adiamento
                    <input
                      type="number"
                      min={1}
                      max={366}
                      required
                      value={s.delayDays}
                      onChange={(e) =>
                        setShifts(
                          shifts.map((item, j) =>
                            j === i
                              ? { ...item, delayDays: Number(e.target.value) }
                              : item,
                          ),
                        )
                      }
                    />
                  </label>
                  <button
                    type="button"
                    className="btn text small"
                    onClick={() => setShifts(shifts.filter((_, j) => j !== i))}
                  >
                    Remover adiamento
                  </button>
                </div>
              ))}
            </div>
          </Panel>
          <Panel
            title="Inclua uma hipótese"
            eyebrow="03 · NOVAS ENTRADAS OU SAÍDAS"
            action={
              <button
                type="button"
                className="btn secondary small"
                disabled={hypotheses.length >= 20}
                onClick={() =>
                  setHypotheses([
                    ...hypotheses,
                    {
                      id: crypto.randomUUID(),
                      label: '',
                      type: 'EXPENSE',
                      amount: '',
                      date: today,
                      occurrences: 1,
                    },
                  ])
                }
              >
                ＋ Adicionar hipótese
              </button>
            }
          >
            <div className="workspace-scenario-fields">
              {hypotheses.length === 0 && (
                <p className="workspace-caption">
                  Uma contratação, um novo cliente ou uma despesa eventual:
                  explore o impacto antes de decidir.
                </p>
              )}
              {hypotheses.map((h, i) => {
                function update(change: Partial<Hypothesis>) {
                  setHypotheses(
                    hypotheses.map((item, j) =>
                      i === j ? { ...item, ...change } : item,
                    ),
                  );
                }
                return (
                  <fieldset key={h.id} className="workspace-hypothesis">
                    <legend>Hipótese {i + 1}</legend>
                    <label className="field">
                      Descrição da hipótese
                      <input
                        required
                        maxLength={120}
                        value={h.label}
                        onChange={(e) => update({ label: e.target.value })}
                        placeholder="Ex.: nova despesa mensal"
                      />
                    </label>
                    <div className="workspace-pair">
                      <label className="field">
                        Tipo da hipótese
                        <select
                          value={h.type}
                          onChange={(e) =>
                            update({
                              type: e.target.value as Hypothesis['type'],
                            })
                          }
                        >
                          <option value="EXPENSE">Despesa</option>
                          <option value="INCOME">Receita</option>
                        </select>
                      </label>
                      <label className="field">
                        Valor da hipótese
                        <input
                          required
                          inputMode="decimal"
                          pattern="[0-9]+([,.][0-9]{1,2})?"
                          placeholder="1500,00"
                          value={h.amount}
                          onChange={(e) => update({ amount: e.target.value })}
                        />
                      </label>
                    </div>
                    <div className="workspace-pair">
                      <label className="field">
                        Primeira data
                        <input
                          type="date"
                          required
                          min={today}
                          value={h.date}
                          onChange={(e) => update({ date: e.target.value })}
                        />
                      </label>
                      <label className="field">
                        Ocorrências mensais
                        <input
                          type="number"
                          required
                          min={1}
                          max={12}
                          value={h.occurrences}
                          onChange={(e) =>
                            update({ occurrences: Number(e.target.value) })
                          }
                        />
                      </label>
                    </div>
                    <button
                      type="button"
                      className="btn text small"
                      onClick={() =>
                        setHypotheses(hypotheses.filter((_, j) => j !== i))
                      }
                    >
                      Remover hipótese
                    </button>
                  </fieldset>
                );
              })}
            </div>
          </Panel>
          {error && (
            <p className="workspace-message" role="alert">
              {error}
            </p>
          )}
          <button
            type="submit"
            className="btn workspace-compare"
            disabled={busy}
            aria-busy={busy}
          >
            {busy ? 'Comparando…' : 'Comparar cenário'}
          </button>
        </form>
        <div className="workspace-scenario-results" aria-busy={busy}>
          {dirty && result && (
            <p className="workspace-scenario-note" role="status">
              Há alterações nas premissas. Use Comparar cenário para atualizar
              os resultados.
            </p>
          )}
          {!result && (
            <QueryState
              loading={busy}
              error={error}
              retry={() => void run(draft(), signature)}
            />
          )}
          {result && (
            <>
              <section
                className="workspace-scenario-metrics"
                aria-label="Comparação do saldo final"
              >
                <article>
                  <span>Saldo final previsto</span>
                  <strong>{money(result.baseline.closingBalance)}</strong>
                  <small>Com os movimentos atuais</small>
                </article>
                <article>
                  <span>Saldo final no cenário</span>
                  <strong>{money(result.scenario.closingBalance)}</strong>
                  <small>Incluindo suas hipóteses</small>
                </article>
                <article>
                  <span>Diferença no saldo final</span>
                  <output
                    aria-label="Diferença no saldo final"
                    className={
                      Number(result.closingDelta) < 0
                        ? 'workspace-expense'
                        : 'workspace-income'
                    }
                  >
                    {money(result.closingDelta)}
                  </output>
                  <small>Cenário menos previsão atual</small>
                </article>
              </section>
              <Panel
                title="Do previsto ao possível"
                eyebrow="COMPARAÇÃO DIÁRIA"
              >
                <div className="workspace-projection-legend">
                  <span>— Previsão atual</span>
                  <strong>— Seu cenário</strong>
                </div>
                <svg
                  className="workspace-projection-chart"
                  viewBox="0 0 800 260"
                  role="img"
                  aria-label="Comparação entre o saldo diário previsto e o saldo no cenário. Use o controle de dia abaixo para consultar a composição."
                  onClick={(e) => {
                    const rect = e.currentTarget.getBoundingClientRect();
                    const ratio = Math.max(
                      0,
                      Math.min(
                        1,
                        (((e.clientX - rect.left) / rect.width) * 800 - 60) /
                          680,
                      ),
                    );
                    explain(
                      Math.round(ratio * (result.scenario.days.length - 1)),
                    );
                  }}
                >
                  {[0, 1, 2, 3].map((i) => (
                    <g key={i}>
                      <line
                        x1={60}
                        x2={740}
                        y1={40 + i * 60}
                        y2={40 + i * 60}
                        stroke="var(--line)"
                        strokeDasharray="4 5"
                      />
                      <text x={50} y={44 + i * 60} textAnchor="end">
                        {new Intl.NumberFormat('pt-BR', {
                          notation: 'compact',
                          maximumFractionDigits: 1,
                        }).format(max - (span * i) / 3)}
                      </text>
                    </g>
                  ))}
                  <path
                    d={result.baseline.days
                      .map(
                        (p, i) =>
                          `${i ? 'L' : 'M'}${plotX(i)},${plotY(p.balance)}`,
                      )
                      .join(' ')}
                    fill="none"
                    stroke="var(--muted)"
                    strokeDasharray="6 5"
                    strokeWidth={2}
                  />
                  <path
                    d={result.scenario.days
                      .map(
                        (p, i) =>
                          `${i ? 'L' : 'M'}${plotX(i)},${plotY(p.balance)}`,
                      )
                      .join(' ')}
                    fill="none"
                    stroke="var(--green)"
                    strokeWidth={3}
                  />
                  {point && (
                    <>
                      <line
                        x1={plotX(selected)}
                        x2={plotX(selected)}
                        y1={25}
                        y2={235}
                        stroke="var(--line)"
                      />
                      <circle
                        cx={plotX(selected)}
                        cy={plotY(point.balance)}
                        r={5}
                        fill="var(--green)"
                        stroke="var(--paper)"
                        strokeWidth={2}
                      />
                    </>
                  )}
                </svg>
                <div className="workspace-scenario-fields">
                  <label className="field">
                    Dia da projeção
                    <input
                      type="range"
                      min={0}
                      max={result.scenario.days.length - 1}
                      value={selected}
                      onChange={(e) => setSelected(Number(e.target.value))}
                    />
                  </label>
                  {point && (
                    <div className="workspace-day-selection">
                      <span>{dateLabel(point.date)}</span>
                      <strong>{money(point.balance)}</strong>
                      <button
                        type="button"
                        className="btn secondary small"
                        disabled={busy}
                        onClick={() => explain(selected)}
                      >
                        Ver composição deste dia
                      </button>
                    </div>
                  )}
                  <p className="workspace-caption">
                    Menor saldo do cenário:{' '}
                    <b>{money(result.scenario.minimumBalance)}</b>.{' '}
                    {result.scenario.firstNegativeDate
                      ? 'Primeiro dia negativo: ' +
                        dateLabel(result.scenario.firstNegativeDate) +
                        '.'
                      : 'Nenhum saldo negativo no horizonte.'}
                  </p>
                </div>
              </Panel>
              <Panel
                title={'Por dentro de ' + dateLabel(result.explanation.date)}
                eyebrow="SALDO ANTERIOR + ENTRADAS − SAÍDAS"
              >
                <div className="workspace-day-comparison">
                  <div>
                    <span>Previsão atual</span>
                    <p>
                      {money(result.explanation.baselineOpening)} +{' '}
                      {money(result.explanation.baselineIncome)} −{' '}
                      {money(result.explanation.baselineExpense)}
                    </p>
                    <strong>{money(result.explanation.baselineBalance)}</strong>
                  </div>
                  <div>
                    <span>Seu cenário</span>
                    <p>
                      {money(result.explanation.scenarioOpening)} +{' '}
                      {money(result.explanation.scenarioIncome)} −{' '}
                      {money(result.explanation.scenarioExpense)}
                    </p>
                    <strong>{money(result.explanation.scenarioBalance)}</strong>
                  </div>
                </div>
                <div className="workspace-sources">
                  {result.explanation.sources.map((s, i) => (
                    <div key={i}>
                      <div>
                        {s.entryId ? (
                          <button
                            className="workspace-row-name"
                            onClick={() => bridge.details(s.entryId!)}
                          >
                            {s.label}
                          </button>
                        ) : (
                          <strong>{s.label}</strong>
                        )}
                        <small>
                          {s.kind === 'HYPOTHESIS'
                            ? 'Hipótese'
                            : s.scenarioIncluded
                              ? 'Movimento incluído no cenário'
                              : 'Movimento adiado para ' +
                                dateLabel(s.projectedDate)}
                        </small>
                      </div>
                      <span
                        className={
                          s.type === 'INCOME'
                            ? 'workspace-income'
                            : 'workspace-expense'
                        }
                      >
                        {s.type === 'INCOME' ? '+' : '−'} {money(s.amount)}
                      </span>
                    </div>
                  ))}
                  {!result.explanation.sources.length && (
                    <p className="workspace-caption">
                      Nenhum movimento neste dia. O saldo vem do dia anterior.
                    </p>
                  )}
                  {result.explanation.truncated && (
                    <p className="workspace-caption">
                      Exibindo os primeiros 100 de{' '}
                      {result.explanation.totalSources} movimentos. Os totais
                      incluem todos.
                    </p>
                  )}
                </div>
              </Panel>
              <details className="workspace-panel workspace-scenario-effects">
                <summary>
                  {result.effects.length} efeito(s) das hipóteses e adiamentos
                </summary>
                {result.effects.map((effect, i) => (
                  <div className="workspace-effect" key={i}>
                    <span>
                      {effect.label}
                      <small>
                        {effect.kind === 'DELAY'
                          ? 'Adiado para '
                          : 'Previsto para '}
                        {dateLabel(effect.projectedDate)}
                        {effect.outsideHorizon ? ' · fora do horizonte' : ''}
                      </small>
                    </span>
                    <b>{money(effect.amount)}</b>
                  </div>
                ))}
              </details>
            </>
          )}
        </div>
      </div>
    </>
  );
}
