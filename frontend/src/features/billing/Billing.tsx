import type { Bridge } from '../../app/bridge';
import { useResource } from '../../shared/useResource';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import {
  Metrics,
  Table,
  usePageFilters,
  timestamp,
} from '../../shared/workspace';
import { Pagination } from '../../shared/Collection';
import { money, dateLabel } from '../../shared/format';
import type { Page } from '../planning/types';
import {
  billingLabels,
  type Charge,
  type Connection,
  type Webhook,
} from './types';
export function Billing({ bridge }: { bridge: Bridge }) {
  const f = usePageFilters('billing', {}, []);
  const resource = useResource('billing:' + f.index, async (signal) => {
    const [config, data, operations] = await Promise.all([
      bridge.request<Connection>('/billing/connection', { signal }),
      bridge.request<Page<Charge>>('/billing?page=' + f.index, { signal }),
      bridge.isAdmin
        ? bridge.request<Webhook>('/billing/webhook-operations', { signal })
        : Promise.resolve(null),
    ]);
    return { config, data, operations };
  });
  const result = resource.data;
  const action = (name: string, id?: string) =>
    result && bridge.hostAction(name, id ? { id } : {}, { billing: result });
  return (
    <>
      <PageHeading
        title="Cobranças"
        description="Pix e boleto vinculados às receitas, da emissão à confirmação."
      >
        {bridge.isAdmin && (
          <button
            className="btn secondary"
            onClick={() => action('billing-config')}
          >
            Conexão Asaas
          </button>
        )}
        {bridge.canWrite && (
          <button
            className="btn"
            disabled={!result?.config.configured}
            onClick={() => action('billing-new')}
          >
            Nova cobrança
          </button>
        )}
      </PageHeading>
      <QueryState {...resource} />
      {result && (
        <>
          <Panel
            title={
              !result.config.configured
                ? 'Conecte sua conta para começar'
                : result.config.environment === 'PRODUCTION'
                  ? 'Conta configurada para receber'
                  : 'Experimente antes de cobrar'
            }
          >
            <div className="workspace-empty">
              <span className="workspace-status">
                {result.config.configured
                  ? result.config.environment === 'PRODUCTION'
                    ? 'Produção'
                    : 'Testes'
                  : 'Não conectado'}
              </span>
              <p>
                {!result.config.configured
                  ? 'O administrador pode conectar a empresa e escolher testes ou produção.'
                  : result.config.environment === 'PRODUCTION'
                    ? 'A baixa acontece quando o valor fica disponível no Asaas. A tarifa é registrada separadamente na mesma conta.'
                    : 'Ambiente de testes. Pagamentos simulados não alteram saldos, receitas ou tarifas.'}
              </p>
            </div>
          </Panel>
          {result.operations && (
            <Panel title="Notificações do Asaas">
              <Metrics
                items={[
                  ['Aguardando', result.operations.pending],
                  ['Processando', result.operations.processing],
                  ['Com falha', result.operations.failed],
                ]}
              />
              <p className="workspace-caption">
                Processamento em segundo plano, com novas tentativas para falhas
                temporárias. Até 50 eventos recentes.
              </p>
              <Table
                rows={result.operations.recent}
                keyOf={(r) => r.id}
                columns={[
                  {
                    label: 'Situação',
                    render: (r) =>
                      (
                        ({
                          PENDING: 'Aguardando processamento',
                          PROCESSING: 'Em processamento',
                          PROCESSED: 'Processada',
                          FAILED: 'Falha após tentativas',
                        }) as Record<string, string>
                      )[r.status] || r.status,
                  },
                  {
                    label: 'Recebida em',
                    render: (r) => timestamp(r.receivedAt),
                  },
                  { label: 'Tentativas', render: (r) => r.attempts },
                  { label: 'Mensagem', render: (r) => r.message || '—' },
                  {
                    label: 'Ação',
                    render: (r) =>
                      r.status === 'FAILED' ? (
                        <button
                          className="btn secondary small"
                          onClick={() => action('billing-webhook-retry', r.id)}
                        >
                          Tentar novamente
                        </button>
                      ) : (
                        '—'
                      ),
                  },
                ]}
              />
            </Panel>
          )}
          <Panel
            title="Suas cobranças"
            action={
              <button className="btn secondary small" onClick={resource.retry}>
                Atualizar lista
              </button>
            }
          >
            <Table
              rows={result.data.content}
              keyOf={(r) => r.id}
              columns={[
                {
                  label: 'Cobrança',
                  render: (r) => (
                    <>
                      <strong>{r.description}</strong>
                      <small>
                        {r.method === 'PIX' ? 'Pix' : 'Boleto'} ·{' '}
                        {r.environment === 'PRODUCTION' ? 'Produção' : 'Teste'}
                      </small>
                    </>
                  ),
                },
                { label: 'Vencimento', render: (r) => dateLabel(r.dueDate) },
                {
                  label: 'Situação',
                  render: (r) => (
                    <>
                      {billingLabels[r.status] || r.status}
                      {r.message && <small>{r.message}</small>}
                    </>
                  ),
                },
                {
                  label: 'Valor',
                  render: (r) => (
                    <>
                      {money(r.amount)}
                      {r.netAmount != null && (
                        <small>Líquido {money(r.netAmount)}</small>
                      )}
                    </>
                  ),
                },
                {
                  label: 'Ações',
                  render: (r) => (
                    <button
                      className="btn secondary small"
                      onClick={() => action('billing-details', r.id)}
                    >
                      Ver cobrança
                    </button>
                  ),
                },
              ]}
            />
            <Pagination
              page={f.index}
              pages={result.data.totalPages}
              total={result.data.totalElements}
              onChange={(index) =>
                f.apply({ ...f.values, index: String(index) })
              }
            />
          </Panel>
          <p className="workspace-caption">
            Compartilhe os links com seus clientes. O Fintry não envia avisos
            automáticos. Para emissões sem confirmação ou divergentes, consulte
            o Asaas antes de tentar novamente.
          </p>
        </>
      )}
    </>
  );
}
