import { Field } from '../../shared/Field';
import { useState, useMemo, useEffect } from 'react';
import { workspaceApi } from '../../api/client';
import type { Bridge } from '../../app/bridge';
import { readLocation, saveLocation } from '../../app/location';
import {
  money,
  monthLabel,
  currentMonth,
  validMonth,
} from '../../shared/format';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import { useResource } from '../../shared/useResource';
import { IndicatorDetails } from './IndicatorDetails';
import type { IndicatorKind } from '../../api/client';
import { CashChart } from './CashChart';
function change(value: number, previous: number) {
  if (!previous)
    return value ? 'Sem base no mês anterior' : 'Sem movimentação anterior';
  if (previous < 0) return `${money(value - previous)} vs. mês anterior`;
  return `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1, signDisplay: 'exceptZero' }).format(((value - previous) / previous) * 100)}% vs. mês anterior`;
}
export function Dashboard({ bridge }: { bridge: Bridge }) {
  const [indicator, setIndicator] = useState<IndicatorKind>();
  const [month, setMonth] = useState(() => readLocation(bridge.month).month);
  const client = useMemo(() => workspaceApi(bridge.request), [bridge]);
  const query = useResource('dashboard:' + month, async (signal) => {
    const [accounts, summary, pending] = await Promise.all([
      client.accounts(signal),
      client.summary(month, signal),
      bridge.request<{
        content: {
          id: string;
          description: string;
          dueDate: string;
          remainingAmount: number;
        }[];
        totalElements: number;
      }>('/finance/pending?group=UPCOMING&size=5', { signal }),
    ]);
    return { accounts, summary, pending };
  });
  useEffect(() => {
    saveLocation('dashboard', { month }, false);
    bridge.sync({ month });
  }, [month, bridge]);
  useEffect(() => {
    if (query.data)
      bridge.sync({
        accounts: query.data.accounts,
        summary: query.data.summary,
      });
  }, [query.data, bridge]);
  const data = query.data,
    s = data?.summary;
  function explore(reference: string, type?: string) {
    bridge.navigate('reports', reference, type);
  }
  return (
    <>
      <PageHeading
        title="Seu caixa, com contexto."
        description="Acompanhe o realizado e descubra o que está por trás de cada número."
      >
        <Field className="field workspace-period" label={<>Mês em análise</>}>
          <input
            type="month"
            min="2000-01"
            max="2100-12"
            value={month}
            onChange={(e) => {
              if (validMonth(e.target.value)) {
                saveLocation('dashboard', { month: e.target.value });
                setMonth(e.target.value);
              }
            }}
          />
        </Field>
        <button
          className="btn secondary"
          onClick={() => {
            const next = currentMonth();
            saveLocation('dashboard', { month: next });
            setMonth(next);
          }}
        >
          Mês atual
        </button>
        {bridge.canWrite && (
          <button className="btn" onClick={() => bridge.action('entry')}>
            ＋ Novo lançamento
          </button>
        )}
      </PageHeading>
      <QueryState {...query} />
      {s && data && (
        <>
          {!data.accounts.length && (
            <section className="workspace-onboarding">
              <div>
                <h2>Vamos preparar seu financeiro?</h2>
                <p>
                  Cadastre uma conta e registre o primeiro movimento, ou conheça
                  os exemplos fictícios.
                </p>
              </div>
              {bridge.canWrite && (
                <button
                  className="btn"
                  onClick={() => bridge.action('account')}
                >
                  Cadastrar conta
                </button>
              )}
              <button
                className="btn secondary"
                onClick={() => bridge.navigate('examples')}
              >
                Explorar exemplos
              </button>
            </section>
          )}
          <div className="workspace-overview">
            <section className="workspace-balance">
              <span className="eyebrow">SALDO DISPONÍVEL AGORA</span>
              <strong>{money(s.balance)}</strong>
              <p>Todas as contas · saldos iniciais e baixas realizadas</p>
              <button onClick={() => bridge.navigate('accounts')}>
                Consultar contas <span>↗</span>
              </button>
              <button onClick={() => setIndicator('BALANCE')}>
                Como este saldo é formado? <span>↗</span>
              </button>
            </section>
            <section
              className="workspace-indicators"
              aria-label={`Indicadores de ${monthLabel(month)}`}
            >
              {(
                [
                  [
                    'income',
                    'Receitas realizadas',
                    s.income,
                    s.previousIncome,
                    'INCOME',
                  ],
                  [
                    'expense',
                    'Despesas realizadas',
                    s.expense,
                    s.previousExpense,
                    'EXPENSE',
                  ],
                  [
                    'result',
                    'Resultado de caixa',
                    s.result,
                    s.previousResult,
                    undefined,
                  ],
                ] as const
              ).map(([tone, label, value, previous, type]) => (
                <button
                  key={tone}
                  className={`workspace-kpi ${tone}`}
                  onClick={() => setIndicator(type || 'RESULT')}
                >
                  <span>
                    {label} <i>↗</i>
                  </span>
                  <strong>{money(value)}</strong>
                  <small>{change(value, previous)}</small>
                  <em>Entender valor</em>
                </button>
              ))}
            </section>
          </div>
          <p className="workspace-caption">
            Indicadores de {monthLabel(month)}, pela data da baixa. O resultado
            de caixa não representa lucro contábil.
          </p>
          <div className="workspace-shortcuts">
            <button onClick={() => bridge.navigate('pending')}>
              <span>01</span>
              <b>Organizar pendências</b>
              <small>Vencimentos e próximos compromissos ↗</small>
            </button>
            <button onClick={() => bridge.navigate('scenarios')}>
              <span>02</span>
              <b>Explorar possibilidades</b>
              <small>Simule o efeito de novas decisões ↗</small>
            </button>
            <button onClick={() => bridge.navigate('reconciliation')}>
              <span>03</span>
              <b>Conferir o extrato</b>
              <small>Revise os movimentos bancários ↗</small>
            </button>
          </div>
          <div className="workspace-analysis">
            <CashChart
              summary={s}
              userId={bridge.user.id}
              onExplore={(m) => explore(m)}
            />
            <div className="workspace-aside">
              <Panel
                title="Despesas por categoria"
                eyebrow="NO MÊS SELECIONADO"
              >
                {s.categories.length ? (
                  s.categories.map((c, i) => (
                    <div className="workspace-category" key={i}>
                      <div>
                        <span>{c.category || 'Sem categoria'}</span>
                        <b>{money(c.amount)}</b>
                      </div>
                      <progress
                        value={c.amount}
                        max={Math.max(s.expense, 1)}
                        aria-label={`${c.category}: ${money(c.amount)}`}
                      />
                    </div>
                  ))
                ) : (
                  <p className="workspace-empty">
                    Nenhuma despesa realizada neste período.
                  </p>
                )}
              </Panel>
              <Panel
                title="Nos próximos sete dias"
                eyebrow="VENCIMENTOS ATUAIS"
                action={
                  <button
                    className="btn text small"
                    onClick={() => bridge.navigate('pending')}
                  >
                    Ver todos ↗
                  </button>
                }
              >
                {data.pending.content.length ? (
                  data.pending.content.map((item) => (
                    <button
                      className="workspace-pending-item"
                      key={item.id}
                      onClick={() => bridge.details(item.id)}
                    >
                      <span>
                        {item.description}
                        <small>
                          {new Intl.DateTimeFormat('pt-BR', {
                            timeZone: 'UTC',
                          }).format(new Date(item.dueDate + 'T12:00:00Z'))}
                        </small>
                      </span>
                      <b>{money(item.remainingAmount)}</b>
                    </button>
                  ))
                ) : (
                  <p className="workspace-empty">
                    Nenhum vencimento nos próximos sete dias.
                  </p>
                )}
              </Panel>
            </div>
          </div>
        </>
      )}
      {indicator && (
        <IndicatorDetails
          key={indicator + month}
          kind={indicator}
          month={month}
          bridge={bridge}
          onClose={() => setIndicator(undefined)}
        />
      )}
    </>
  );
}
