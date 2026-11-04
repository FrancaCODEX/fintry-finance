import { seriesLabel, canPauseSeries } from '../../shared/series';
import { today } from '../../shared/workspace';
import { useState } from 'react';
import type { Bridge } from '../../app/bridge';
import { query, type EntryPage } from '../../api/client';
import { useResource } from '../../shared/useResource';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import {
  Table,
  usePageFilters,
  Confirm,
  mutation,
} from '../../shared/workspace';
import { Pagination } from '../../shared/Collection';
import { money, dateLabel } from '../../shared/format';
import type { Series as Row, Page } from './types';
export function Series({ bridge }: { bridge: Bridge }) {
  const f = usePageFilters('series', {}, []);
  const [decision, setDecision] = useState<{ row: Row; action: string }>();
  const [selected, setSelected] = useState<string>();
  const resource = useResource('series:' + query(f.values), (signal) =>
    bridge.request<Page<Row>>('/finance/series?page=' + f.index, { signal }),
  );
  const items = useResource('series-items:' + selected, async (signal) =>
    selected
      ? Promise.all([
          bridge.request<EntryPage>(
            '/finance/entries?' + query({ seriesId: selected, size: 100 }),
            { signal },
          ),
          bridge.request<EntryPage>(
            '/finance/entries?' +
              query({ seriesId: selected, size: 100, page: 1 }),
            { signal },
          ),
        ]).then((pages) => pages.flatMap((p) => p.content))
      : [],
  );
  const data = resource.data;
  return (
    <>
      <PageHeading
        title="Parcelas e recorrências"
        description="Organize os próximos meses, com controle das ocorrências."
      >
        {bridge.canWrite && (
          <button className="btn" onClick={() => bridge.action('entry')}>
            Nova série ou lançamento
          </button>
        )}
      </PageHeading>
      <QueryState {...resource} />
      {data && (
        <Panel title="Séries da empresa">
          <Table
            rows={data.content}
            keyOf={(r) => r.id}
            columns={[
              {
                label: 'Série',
                render: (r) => (
                  <>
                    <strong>{r.description}</strong>
                    <small>
                      {r.kind === 'RECURRING'
                        ? 'Recorrência mensal'
                        : 'Parcelamento'}{' '}
                      · {r.occurrences} ocorrências
                    </small>
                  </>
                ),
              },
              {
                label: 'Período',
                render: (r) => (
                  <>
                    {dateLabel(r.firstDueDate)}
                    <small>até {dateLabel(r.lastDueDate)}</small>
                  </>
                ),
              },
              { label: 'Valor total', render: (r) => money(r.totalAmount) },
              {
                label: 'Acompanhamento',
                render: (r) => (
                  <>
                    <span className="workspace-status">{seriesLabel(r)}</span>
                    <small>
                      {r.paid} realizadas · {r.partial} parciais · {r.pending}{' '}
                      pendentes · {r.pausedOccurrences} pausadas · {r.cancelled}{' '}
                      canceladas
                    </small>
                  </>
                ),
              },
              {
                label: 'Ações',
                render: (r) => (
                  <div className="workspace-row-actions">
                    <button
                      className="btn secondary small"
                      onClick={() =>
                        setSelected(selected === r.id ? undefined : r.id)
                      }
                    >
                      Ver ocorrências
                    </button>
                    {bridge.canWrite &&
                      !r.stopped &&
                      (r.pending > 0 ||
                        r.partial > 0 ||
                        r.pausedOccurrences > 0 ||
                        r.paused) && (
                        <>
                          {r.kind === 'RECURRING' &&
                            (r.paused || canPauseSeries(r, today())) && (
                              <button
                                className="btn secondary small"
                                onClick={() =>
                                  setDecision({
                                    row: r,
                                    action: r.paused ? 'resume' : 'pause',
                                  })
                                }
                              >
                                {r.paused ? 'Retomar' : 'Pausar'}
                              </button>
                            )}
                          <button
                            className="btn text small"
                            onClick={() =>
                              setDecision({ row: r, action: 'stop' })
                            }
                          >
                            {r.kind === 'RECURRING'
                              ? 'Encerrar'
                              : 'Cancelar pendências'}
                          </button>
                        </>
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
            onChange={(index) => f.apply({ ...f.values, index: String(index) })}
          />
        </Panel>
      )}
      {selected && (
        <Panel title="Ocorrências da série">
          <QueryState {...items} />
          {items.data && (
            <Table
              rows={items.data}
              keyOf={(r) => r.id}
              columns={[
                {
                  label: 'Ocorrência',
                  render: (r) => `${r.sequenceNumber}/${r.seriesCount}`,
                },
                { label: 'Vencimento', render: (r) => dateLabel(r.dueDate) },
                { label: 'Valor', render: (r) => money(r.amount) },
                {
                  label: 'Situação',
                  render: (r) => (r.seriesPaused ? 'Pausada' : r.status),
                },
                {
                  label: 'Consultar',
                  render: (r) => (
                    <button
                      className="btn secondary small"
                      onClick={() => bridge.details(r.id)}
                    >
                      Detalhes
                    </button>
                  ),
                },
              ]}
            />
          )}
        </Panel>
      )}
      <p className="workspace-caption">
        Pausar suspende apenas ocorrências futuras ainda pendentes. Retomar
        mantém os vencimentos originais. Encerrar é definitivo e preserva
        pagamentos e baixas parciais.
      </p>
      {decision && (
        <Confirm
          title={
            decision.action === 'pause'
              ? 'Pausar recorrência?'
              : decision.action === 'resume'
                ? 'Retomar recorrência?'
                : 'Encerrar pendências da série?'
          }
          description={
            decision.action === 'stop'
              ? 'A ação é definitiva. Pendências serão canceladas; pagamentos e baixas parciais serão preservados.'
              : decision.action === 'pause'
                ? 'Somente ocorrências futuras ainda pendentes serão suspensas. Vencidas e parcialmente pagas continuam ativas.'
                : 'As ocorrências pausadas voltarão com os vencimentos originais; datas passadas podem aparecer como vencidas.'
          }
          close={() => setDecision(undefined)}
          confirm={async () => {
            await bridge.request(
              '/finance/series/' + decision.row.id + '/' + decision.action,
              mutation('POST'),
            );
            resource.retry();
          }}
        />
      )}
    </>
  );
}
