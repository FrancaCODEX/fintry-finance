import type { Bridge } from '../../app/bridge';
import { query } from '../../api/client';
import { useResource } from '../../shared/useResource';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import { Table, usePageFilters, timestamp } from '../../shared/workspace';
import { Pagination } from '../../shared/Collection';
import type { Page, Stock as Row } from '../planning/types';
export function Stock({ bridge }: { bridge: Bridge }) {
  const f = usePageFilters(
    'stock',
    { productId: bridge.stockProduct?.id || '' },
    ['productId'],
  );
  const resource = useResource('stock:' + query(f.values), (signal) =>
    bridge.request<Page<Row>>(
      '/stock/movements?' +
        query({ productId: f.values.productId, page: f.index }),
      { signal },
    ),
  );
  const data = resource.data;
  return (
    <>
      <PageHeading
        title="Estoque"
        description="Cada entrada, saída e venda preservada no histórico."
      >
        {f.values.productId && (
          <button
            className="btn secondary"
            onClick={() => f.apply({ index: '0' })}
          >
            Ver todos os produtos
          </button>
        )}
        {bridge.canWrite && (
          <button
            className="btn"
            onClick={() => bridge.hostAction('stock-new')}
          >
            Movimentar estoque
          </button>
        )}
      </PageHeading>
      <QueryState {...resource} />
      {data && (
        <Panel title="Movimentações">
          <Table
            rows={data.content}
            keyOf={(r) => r.id}
            columns={[
              {
                label: 'Produto',
                render: (r) => (
                  <>
                    <strong>{r.productName}</strong>
                    <small>{r.sku}</small>
                  </>
                ),
              },
              {
                label: 'Movimento',
                render: (r) => (
                  <>
                    {
                      (
                        {
                          INITIAL: 'Estoque inicial',
                          IN: 'Entrada manual',
                          OUT: 'Saída manual',
                          SALE: 'Venda',
                          RETURN: 'Devolução',
                        } as Record<string, string>
                      )[r.kind]
                    }
                    <small>{r.reason}</small>
                  </>
                ),
              },
              {
                label: 'Quantidade',
                render: (r) =>
                  `${new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 }).format(r.delta)} ${r.unit}`,
              },
              {
                label: 'Saldo após movimento',
                render: (r) => `${r.balance} ${r.unit}`,
              },
              {
                label: 'Autoria',
                render: (r) => (
                  <>
                    {r.actorName}
                    <small>{timestamp(r.createdAt)}</small>
                  </>
                ),
              },
              {
                label: 'Venda',
                render: (r) =>
                  r.saleId ? (
                    <button
                      className="btn secondary small"
                      onClick={() =>
                        bridge.hostAction('sale-details', { id: r.saleId! })
                      }
                    >
                      Abrir venda
                    </button>
                  ) : (
                    '—'
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
      <p className="workspace-caption">
        Para corrigir, registre uma entrada ou saída com motivo. Vendas
        confirmadas e canceladas atualizam o estoque automaticamente.
      </p>
    </>
  );
}
