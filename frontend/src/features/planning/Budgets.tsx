import { useState } from 'react';
import type { Bridge } from '../../app/bridge';
import { query, type Category } from '../../api/client';
import { useResource } from '../../shared/useResource';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import {
  Controls,
  FilterBar,
  Metrics,
  usePageFilters,
  useCommand,
  Feedback,
  Confirm,
  mutation,
  type Values,
} from '../../shared/workspace';
import { Dialog } from '../../shared/Dialog';
import { money, validMonth } from '../../shared/format';
import type { Budgets as Result, Budget } from './types';
export function Budgets({ bridge }: { bridge: Bridge }) {
  const f = usePageFilters('budgets', { month: bridge.month }, ['month']);
  const month = validMonth(f.values.month) ? f.values.month : bridge.month;
  const resource = useResource('budgets:' + month, async (signal) => {
    const [data, categories] = await Promise.all([
      bridge.request<Result>('/finance/budgets?' + query({ month }), {
        signal,
      }),
      bridge.request<Category[]>('/categories', { signal }),
    ]);
    return { data, categories };
  });
  const [edit, setEdit] = useState<Budget | null | undefined>(),
    [remove, setRemove] = useState<Budget>(),
    [copy, setCopy] = useState(false);
  const data = resource.data?.data;
  return (
    <>
      <PageHeading
        title="Orçamentos por categoria"
        description="Defina limites mensais e acompanhe o que já foi comprometido."
      >
        {bridge.canWrite && (
          <>
            <button className="btn secondary" onClick={() => setCopy(true)}>
              Copiar mês anterior
            </button>
            <button className="btn" onClick={() => setEdit(null)}>
              Novo orçamento
            </button>
          </>
        )}
      </PageHeading>
      <Panel title="Mês de referência">
        <FilterBar
          draft={f.draft}
          setDraft={f.setDraft}
          submit={() => f.apply({ ...f.draft, index: '0' })}
          fields={[
            {
              name: 'month',
              label: 'Mês do orçamento',
              type: 'month',
              required: true,
              min: '2000-01',
              max: '2100-12',
            },
          ]}
        />
      </Panel>
      <QueryState {...resource} />
      {data && (
        <>
          <Metrics
            items={[
              ['Limites definidos', money(data.limitAmount)],
              ['Pago', money(data.paid)],
              ['Pendente', money(data.pending)],
              ['Disponível após pendências', money(data.remaining)],
            ]}
          />
          <div className="workspace-catalog-grid">
            {data.content.map((row) => (
              <article className="workspace-record" key={row.id}>
                <header>
                  <h2>{row.category}</h2>
                  <span className="workspace-status">
                    {{
                      OK: 'Dentro do limite',
                      WARNING: 'Atenção ao limite',
                      EXCEEDED: 'Limite excedido',
                    }[row.status] || row.status}
                  </span>
                </header>
                {!row.active && <p>Categoria inativa · histórico preservado</p>}
                <dl>
                  <dt>Limite</dt>
                  <dd>{money(row.limitAmount)}</dd>
                  <dt>Comprometido</dt>
                  <dd>{money(row.committed)}</dd>
                  <dt>Pago</dt>
                  <dd>{money(row.paid)}</dd>
                  <dt>Pendente</dt>
                  <dd>{money(row.pending)}</dd>
                  <dt>Disponível após pendências</dt>
                  <dd>{money(row.remaining)}</dd>
                </dl>
                <progress
                  value={Math.min(100, row.percentage)}
                  max={100}
                  aria-label={`${row.category}: ${row.percentage}% comprometido`}
                />
                {bridge.canWrite && (
                  <div className="workspace-record-actions">
                    <button
                      className="btn secondary small"
                      onClick={() => setEdit(row)}
                    >
                      Editar limite
                    </button>
                    <button
                      className="btn text small"
                      onClick={() => setRemove(row)}
                    >
                      Remover
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
          {!data.content.length && (
            <p className="workspace-empty">
              Crie o primeiro limite ou copie os limites do mês anterior.
            </p>
          )}
          <Panel title="Despesas sem orçamento">
            <Metrics
              items={[
                ['Pago sem limite definido', money(data.unbudgetedPaid)],
                ['Pendente sem limite definido', money(data.unbudgetedPending)],
              ]}
            />
          </Panel>
        </>
      )}
      <p className="workspace-caption">
        Pago + pendente pelo vencimento no mês. Atenção a partir de 80%;
        excedido acima de 100%. Orçamentos não bloqueiam lançamentos nem
        reservam saldo. Transferências e cancelados ficam fora.
      </p>
      {edit !== undefined && resource.data && (
        <BudgetEditor
          bridge={bridge}
          month={month}
          row={edit}
          categories={resource.data.categories}
          close={() => setEdit(undefined)}
          saved={(savedMonth) => {
            f.apply({ ...f.values, month: savedMonth, index: '0' });
            setEdit(undefined);
            resource.retry();
          }}
        />
      )}
      {remove && (
        <Confirm
          title="Remover orçamento?"
          description={`O limite de ${money(remove.limitAmount)} de ${remove.category} será removido. Despesas e histórico serão preservados.`}
          close={() => setRemove(undefined)}
          confirm={async () => {
            await bridge.request(
              '/finance/budgets/' + remove.id,
              mutation('DELETE'),
            );
            resource.retry();
          }}
        />
      )}
      {copy && (
        <Confirm
          title="Copiar limites do mês anterior?"
          description={`Destino: ${month}. Categorias inativas e limites já existentes serão ignorados. Lançamentos não são copiados.`}
          close={() => setCopy(false)}
          confirm={async () => {
            await bridge.request(
              '/finance/budgets/copy',
              mutation('POST', { month }),
            );
            resource.retry();
          }}
        />
      )}
    </>
  );
}
function BudgetEditor({
  bridge,
  month,
  row,
  categories,
  close,
  saved,
}: {
  bridge: Bridge;
  month: string;
  row: Budget | null;
  categories: Category[];
  close: () => void;
  saved: (month: string) => void;
}) {
  const available = categories.filter(
    (c) => c.kind !== 'INCOME' && (c.active || c.id === row?.categoryId),
  );
  const [values, setValues] = useState<Values>({
    month,
    categoryId: row?.categoryId || available[0]?.id || '',
    limitAmount: String(row?.limitAmount || ''),
  });
  const cmd = useCommand();
  return (
    <Dialog
      title={row ? 'Editar orçamento' : 'Novo orçamento'}
      description="O limite acompanha despesas vinculadas à categoria cadastrada."
      busy={cmd.busy}
      close={close}
    >
      <form
        className="workspace-finance-form"
        onSubmit={async (e) => {
          e.preventDefault();
          if (
            await cmd.run(async () => {
              await bridge.request(
                '/finance/budgets' + (row ? '/' + row.id : ''),
                mutation(row ? 'PUT' : 'POST', values),
              );
            })
          )
            saved(values.month);
        }}
      >
        {available.length ? (
          <>
            <Controls
              values={values}
              setValues={setValues}
              fields={[
                {
                  name: 'month',
                  label: 'Mês',
                  type: 'month',
                  required: true,
                  min: '2000-01',
                  max: '2100-12',
                },
                {
                  name: 'limitAmount',
                  label: 'Limite mensal (R$)',
                  type: 'number',
                  required: true,
                  min: '0.01',
                  max: '999999999999.99',
                  step: '0.01',
                },
                {
                  name: 'categoryId',
                  label: 'Categoria',
                  required: true,
                  options: available.map((c) => [c.id, c.name]),
                },
              ]}
            />
            <Feedback error={cmd.error} />
            <button className="btn" disabled={cmd.busy}>
              Salvar orçamento
            </button>
          </>
        ) : (
          <>
            <p>
              Cadastre uma categoria ativa de despesa ou mista para definir um
              limite.
            </p>
            <button
              type="button"
              className="btn"
              onClick={() => {
                close();
                bridge.navigate('categories');
              }}
            >
              Ir para Categorias
            </button>
          </>
        )}
      </form>
    </Dialog>
  );
}
