import { useEffect, useState } from 'react';
import type { Bridge } from '../../app/bridge';
import { saveLocation } from '../../app/location';
import { query } from '../../api/client';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import { EmptyState, Pagination } from '../../shared/Collection';
import { Field } from '../../shared/Field';
import { money, dateLabel } from '../../shared/format';
import { useResource } from '../../shared/useResource';
import {
  catalogConfig,
  filterFields,
  readFilters,
  labels,
  type CatalogPage,
  type CatalogRow,
  type CatalogResult,
  type Filters,
} from './model';
const quantity = (n?: number) =>
  new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 3 }).format(n ?? 0);
export function Catalog({
  bridge,
  page,
}: {
  bridge: Bridge;
  page: CatalogPage;
}) {
  const config = catalogConfig[page];
  const [filters, setFilters] = useState(() => readFilters(page));
  const [draft, setDraft] = useState(filters);
  const index = Number(filters.index || 0);
  const resource = useResource(page + ':' + query(filters), async (signal) => {
    if (page === 'categories' || page === 'accounts') {
      const rows = await bridge.request<CatalogRow[]>(config.endpoint, {
        signal,
      });
      const content = rows.filter(
        (row) =>
          (!filters.search ||
            (row.name || '')
              .toLocaleLowerCase('pt-BR')
              .includes(filters.search.toLocaleLowerCase('pt-BR'))) &&
          (!filters.active || String(row.active) === filters.active),
      );
      return {
        content,
        totalElements: content.length,
        totalPages: 1,
        number: 0,
      };
    }
    const { index: _index, ...values } = filters;
    return bridge.request<CatalogResult>(
      config.endpoint +
        '?' +
        query({
          ...values,
          page: index,
          ...(page === 'products' || page === 'services'
            ? { kind: page === 'products' ? 'PRODUCT' : 'SERVICE' }
            : {}),
        }),
      { signal },
    );
  });
  useEffect(() => {
    saveLocation(page, filters, false);
  }, [page, filters]);
  const data = resource.data;
  function update(next: Filters) {
    saveLocation(page, next);
    setFilters(next);
    setDraft(next);
  }
  function action(name: string, row?: CatalogRow) {
    if (data) void bridge.catalogAction(page, data, name, row?.id);
  }
  const canManage = page === 'team' ? bridge.isAdmin : bridge.canWrite;
  const create = canManage ? (
    <button className="btn" onClick={() => action(config.create)}>
      ＋ {config.createLabel}
    </button>
  ) : null;
  const summary =
    data &&
    (page === 'products'
      ? [
          ['Produtos ativos', data.activeCount],
          ['Estoque baixo', data.lowStockCount],
          ['Estoque a custo atual', money(data.stockValue ?? 0)],
        ]
      : page === 'services'
        ? [
            ['Serviços ativos', data.activeCount],
            ['Disponibilidade', 'Sem estoque'],
          ]
        : page === 'team'
          ? [
              ['Acessos ativos', data.active],
              ['Aguardando ativação', data.pending],
            ]
          : page === 'proposals'
            ? [
                ['Rascunhos', data.drafts],
                ['Em negociação', data.negotiating],
                ['Aprovadas', data.approved],
              ]
            : null);
  const relatedPages: [string, string][] =
    config.section === 'Comercial'
      ? [
          ['products', 'Produtos'],
          ['services', 'Serviços'],
          ['proposals', 'Propostas'],
          ['sales', 'Vendas'],
          ['stock', 'Estoque'],
        ]
      : config.section === 'Cadastros'
        ? [
            ['contacts', 'Clientes e fornecedores'],
            ['categories', 'Categorias'],
          ]
        : [];
  return (
    <>
      {relatedPages.length > 0 && (
        <nav
          className="workspace-area-navigation"
          aria-label={`Páginas de ${config.section}`}
        >
          {relatedPages.map(([route, label]) => (
            <button
              key={route}
              aria-current={route === page ? 'page' : undefined}
              onClick={() => bridge.navigate(route)}
            >
              {label}
            </button>
          ))}
        </nav>
      )}
      <PageHeading
        title={config.title}
        description={config.description}
        eyebrow={config.section}
      >
        {page === 'accounts' && canManage && (
          <button className="btn secondary" onClick={() => action('transfer')}>
            Transferir entre contas
          </button>
        )}
        {create}
      </PageHeading>
      {summary && (
        <section className="workspace-metrics" aria-label="Resumo da área">
          {summary.map(([label, value]) => (
            <article key={label}>
              <span>{label}</span>
              <strong>{value}</strong>
            </article>
          ))}
        </section>
      )}
      <Panel title="Seus registros" eyebrow={config.section}>
        <form
          className="workspace-catalog-filters"
          onSubmit={(event) => {
            event.preventDefault();
            update({ ...draft, index: '0' });
          }}
        >
          {filterFields(page).map((field) => (
            <Field key={field.key} label={field.label}>
              {field.options ? (
                <select
                  value={draft[field.key] ?? ''}
                  onChange={(event) =>
                    setDraft({ ...draft, [field.key]: event.target.value })
                  }
                >
                  {field.options.map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              ) : (
                <input
                  type="search"
                  maxLength={120}
                  value={draft[field.key] || ''}
                  onChange={(event) =>
                    setDraft({ ...draft, [field.key]: event.target.value })
                  }
                  placeholder="Digite para buscar…"
                />
              )}
            </Field>
          ))}
          <button className="btn secondary" type="submit">
            Aplicar filtros
          </button>
          <button
            type="button"
            className="btn text"
            onClick={() => update({ index: '0' })}
          >
            Limpar filtros
          </button>
        </form>
        <QueryState {...resource} />
        {data &&
          (data.content.length ? (
            <div className="workspace-catalog-grid">
              {data.content.map((row) => (
                <article className="workspace-record" key={row.id}>
                  <header>
                    <span className="eyebrow">
                      {row.sku ||
                        (row.number ? `#${row.number}` : config.section)}
                    </span>
                    <span
                      className={`workspace-status ${row.status === 'DRAFT' || row.invited || row.lowStock || row.expired ? 'pending' : row.active === false || row.status === 'CANCELLED' ? 'cancelled' : ''}`}
                    >
                      {row.invited
                        ? 'Aguardando ativação'
                        : row.active !== undefined
                          ? row.active
                            ? row.lowStock
                              ? 'Estoque baixo'
                              : 'Ativo'
                            : 'Inativo'
                          : labels[row.status || ''] || 'Conta'}
                    </span>
                  </header>
                  <h2>
                    {row.name ||
                      row.title ||
                      row.contactName ||
                      'Consumidor final'}
                  </h2>
                  {row.document && <p className="muted">{row.document}</p>}
                  {row.contactName && row.title && <p>{row.contactName}</p>}
                  <dl>
                    {row.price !== undefined && (
                      <>
                        <dt>Preço de venda</dt>
                        <dd className="workspace-record-value">
                          {money(row.price)}
                        </dd>
                        <dt>Custo de referência</dt>
                        <dd>{money(row.cost ?? 0)}</dd>
                      </>
                    )}
                    {page === 'products' && (
                      <>
                        <dt>Em estoque</dt>
                        <dd>
                          {quantity(row.quantity)} {row.unit}
                        </dd>
                        <dt>Mínimo</dt>
                        <dd>
                          {quantity(row.minimum)} {row.unit}
                        </dd>
                      </>
                    )}
                    {row.total !== undefined && (
                      <>
                        <dt>Total</dt>
                        <dd className="workspace-record-value">
                          {money(row.total)}
                        </dd>
                      </>
                    )}
                    {page === 'sales' && (
                      <>
                        <dt>Recebimento</dt>
                        <dd>
                          {row.status === 'DRAFT'
                            ? 'Aguardando confirmação'
                            : row.status === 'CANCELLED'
                              ? 'Registros preservados'
                              : labels[row.paymentStatus || ''] || 'A receber'}
                        </dd>
                      </>
                    )}
                    {row.validUntil && (
                      <>
                        <dt>Validade</dt>
                        <dd>
                          {dateLabel(row.validUntil)}{' '}
                          {row.expired && '· Expirada'}
                        </dd>
                      </>
                    )}
                    {row.saleNumber && (
                      <>
                        <dt>Venda vinculada</dt>
                        <dd>
                          #{row.saleNumber}
                          {row.saleStatus === 'CANCELLED' && ' · Cancelada'}
                        </dd>
                      </>
                    )}
                    {row.kind && ['contacts', 'categories'].includes(page) && (
                      <>
                        <dt>Aplicação</dt>
                        <dd>
                          {row.kind === 'BOTH' && page === 'categories'
                            ? 'Receitas e despesas'
                            : labels[row.kind] || row.kind}
                        </dd>
                      </>
                    )}
                    {row.email && (
                      <>
                        <dt>E-mail</dt>
                        <dd>{row.email}</dd>
                      </>
                    )}
                    {row.phone && (
                      <>
                        <dt>Telefone</dt>
                        <dd>{row.phone}</dd>
                      </>
                    )}
                    {row.role && (
                      <>
                        <dt>Perfil</dt>
                        <dd>
                          {labels[row.role] || row.role}
                          {row.id === bridge.user.id && ' · Você'}
                        </dd>
                      </>
                    )}
                    {row.invited && (
                      <>
                        <dt>Convite</dt>
                        <dd>
                          {row.invitationExpiresAt
                            ? `${new Date(row.invitationExpiresAt) > new Date() ? 'Válido até' : 'Expirou em'} ${new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Sao_Paulo' }).format(new Date(row.invitationExpiresAt))}`
                            : 'Revogado'}
                        </dd>
                      </>
                    )}
                    {row.balance !== undefined && (
                      <>
                        <dt>Saldo atual</dt>
                        <dd className="workspace-record-value">
                          {money(row.balance)}
                        </dd>
                        <dt>Saldo inicial</dt>
                        <dd>{money(row.initialBalance ?? 0)}</dd>
                      </>
                    )}
                  </dl>
                  <div className="workspace-record-actions">
                    {canManage &&
                      [
                        'products',
                        'services',
                        'contacts',
                        'categories',
                        'team',
                      ].includes(page) && (
                        <button
                          className="btn secondary small"
                          aria-label={`Editar ${row.name}`}
                          onClick={() =>
                            action(
                              (
                                {
                                  products: 'product-edit',
                                  services: 'service-edit',
                                  contacts: 'edit-contact',
                                  categories: 'edit-category',
                                  team: 'edit-user',
                                } as Record<string, string>
                              )[page],
                              row,
                            )
                          }
                        >
                          Editar
                        </button>
                      )}
                    {page === 'products' && (
                      <>
                        {canManage && row.active && (
                          <button
                            className="btn secondary small"
                            onClick={() => action('stock-new', row)}
                          >
                            Movimentar estoque
                          </button>
                        )}
                        <button
                          className="btn text small"
                          onClick={() => action('stock-history', row)}
                        >
                          Histórico de estoque
                        </button>
                      </>
                    )}
                    {page === 'services' && canManage && (
                      <button
                        className="btn text small"
                        onClick={() => action('proposal-new')}
                      >
                        Criar proposta ↗
                      </button>
                    )}
                    {page === 'sales' && (
                      <button
                        className="btn secondary small"
                        onClick={() => action('sale-details', row)}
                      >
                        Detalhes da venda #{row.number}
                      </button>
                    )}
                    {page === 'proposals' && (
                      <button
                        className="btn secondary small"
                        aria-label={`Detalhes da proposta ${row.number}`}
                        onClick={() => action('proposal-details', row)}
                      >
                        Ver proposta ↗
                      </button>
                    )}
                    {['contacts', 'categories'].includes(page) && canManage && (
                      <button
                        className="btn text small"
                        onClick={() =>
                          action(
                            page === 'contacts'
                              ? 'toggle-contact'
                              : 'toggle-category',
                            row,
                          )
                        }
                      >
                        {row.active ? 'Inativar' : 'Reativar'}
                      </button>
                    )}
                    {page === 'team' && canManage && (
                      <>
                        {!row.active && (
                          <button
                            className="btn text small"
                            onClick={() => action('renew-invite', row)}
                          >
                            Gerar novo convite
                          </button>
                        )}
                        {row.invited && row.invitationExpiresAt && (
                          <button
                            className="btn text small"
                            onClick={() => action('revoke-invite', row)}
                          >
                            Revogar convite
                          </button>
                        )}
                      </>
                    )}
                  </div>
                </article>
              ))}
            </div>
          ) : (
            <EmptyState
              title="Nenhum registro encontrado"
              description="Ajuste os filtros ou adicione o primeiro registro desta área."
            >
              {create}
            </EmptyState>
          ))}
        {data && (
          <Pagination
            page={index}
            pages={data.totalPages}
            total={data.totalElements}
            onChange={(next) => update({ ...filters, index: String(next) })}
          />
        )}
      </Panel>
      <p className="workspace-caption">{config.note}</p>
    </>
  );
}
