import type { Bridge } from '../../app/bridge';
import { query } from '../../api/client';
import { useResource } from '../../shared/useResource';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import {
  FilterBar,
  usePageFilters,
  timestamp,
  today,
  Table,
} from '../../shared/workspace';
import { Pagination } from '../../shared/Collection';
import type { Page } from '../planning/types';
interface Row {
  id: string;
  actorName: string;
  action: string;
  resource: string;
  resourceId?: string;
  label: string;
  occurredAt: string;
  changes: { field: string; before: string; after: string }[];
}
export const resources: Record<string, string> = {
  ENTRY: 'Lançamentos e comprovantes',
  ACCOUNT: 'Contas',
  TRANSFER: 'Transferências',
  SERIES: 'Parcelas e recorrências',
  CONTACT: 'Clientes e fornecedores',
  CATEGORY: 'Categorias',
  COMPANY: 'Empresa',
  USER: 'Equipe',
  ACCESS: 'Segurança do acesso',
  PRODUCT: 'Produtos e serviços',
  PROPOSAL: 'Propostas comerciais',
  RECONCILIATION: 'Conciliação bancária',
  OPERATIONS: 'Ambiente',
  BILLING: 'Cobranças',
  SUBSCRIPTION: 'Plano e assinatura',
  STOCK: 'Estoque',
  SALE: 'Vendas',
  BUDGET: 'Orçamentos',
  SAMPLE: 'Dados de exemplo',
};
export function Audit({ bridge }: { bridge: Bridge }) {
  const f = usePageFilters(
    'audit',
    { start: today().slice(0, 7) + '-01', end: today() },
    ['start', 'end', 'resource', 'search'],
  );
  const resource = useResource('audit:' + query(f.values), (signal) => {
    const { index, ...values } = f.values;
    return bridge.request<Page<Row>>(
      '/audit?' + query({ ...values, page: f.index }),
      { signal },
    );
  });
  const data = resource.data;
  return (
    <>
      <PageHeading
        title="Histórico de alterações"
        description="Quem fez o quê, quando e quais dados mudaram."
      />
      <Panel title="Consultar histórico">
        <FilterBar
          draft={f.draft}
          setDraft={f.setDraft}
          submit={() => f.apply({ ...f.draft, index: '0' })}
          fields={[
            { name: 'start', label: 'De', type: 'date', required: true },
            { name: 'end', label: 'Até', type: 'date', required: true },
            {
              name: 'resource',
              label: 'Área',
              options: [['', 'Todas as áreas'], ...Object.entries(resources)],
            },
            {
              name: 'search',
              label: 'Buscar no histórico',
              type: 'search',
              maxLength: 120,
            },
          ]}
        />
        <QueryState {...resource} />
        {data && (
          <>
            <div className="workspace-audit-list">
              {data.content.map((row) => (
                <article key={row.id}>
                  <header>
                    <strong>{bridge.auditLabel(row.action)}</strong>
                    <time>{timestamp(row.occurredAt)}</time>
                  </header>
                  <p>{row.label}</p>
                  <small>
                    {row.actorName} · {resources[row.resource] || row.resource}
                  </small>
                  {row.changes.length > 0 && (
                    <details>
                      <summary>
                        Ver {row.changes.length} campo(s) alterado(s)
                      </summary>
                      <Table
                        rows={row.changes}
                        keyOf={(c) => c.field}
                        columns={[
                          { label: 'Campo', render: (c) => c.field },
                          {
                            label: 'Antes',
                            render: (c) => bridge.auditValue(c.before),
                          },
                          {
                            label: 'Depois',
                            render: (c) => bridge.auditValue(c.after),
                          },
                        ]}
                      />
                    </details>
                  )}
                  {row.resourceId &&
                    ['SALE', 'ENTRY'].includes(row.resource) && (
                      <button
                        className="btn text small"
                        onClick={() =>
                          row.resource === 'ENTRY'
                            ? bridge.details(row.resourceId!)
                            : bridge.hostAction('sale-details', {
                                id: row.resourceId!,
                              })
                        }
                      >
                        Abrir registro
                      </button>
                    )}
                </article>
              ))}
            </div>
            {!data.content.length && (
              <p className="workspace-empty">
                Nenhuma alteração neste período.
              </p>
            )}
            <Pagination
              page={f.index}
              pages={data.totalPages}
              total={data.totalElements}
              onChange={(index) =>
                f.apply({ ...f.values, index: String(index) })
              }
            />
          </>
        )}
      </Panel>
      <p className="workspace-caption">
        Horários de Brasília. Consulte até 366 dias por vez. Senhas, links de
        acesso e conteúdo de comprovantes não são registrados.
      </p>
    </>
  );
}
