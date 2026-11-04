import type { Bridge } from '../../app/bridge';
import { useResource } from '../../shared/useResource';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import { Table, timestamp } from '../../shared/workspace';
import { money, dateLabel } from '../../shared/format';
import {
  statusLabels,
  invoiceLabels,
  renewalLabels,
  checkoutReason,
  externalInvoiceUrl,
  type Subscription as Data,
  type Payments,
  type Renewal,
} from './types';
const storage = (bytes: number) =>
  new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 1 }).format(
    bytes / 1048576,
  ) + ' MB';
export function Subscription({ bridge }: { bridge: Bridge }) {
  const resource = useResource('subscription', async (signal) => {
    const [data, payments, renewal] = await Promise.all([
      bridge.request<Data>('/subscription', { signal }),
      bridge.request<Payments>('/subscription/payments', { signal }),
      bridge.request<Renewal>('/subscription/renewal', { signal }),
    ]);
    return { data, payments, renewal };
  });
  const result = resource.data;
  const action = (name: string, args: Record<string, string> = {}) =>
    result && bridge.hostAction(name, args, { subscription: result });
  if (!result)
    return (
      <>
        <PageHeading
          title="Plano e assinatura"
          description="Acompanhe o uso e escolha a capacidade para sua empresa."
        />
        <QueryState {...resource} />
      </>
    );
  const { data, payments, renewal } = result,
    selected = data.plans.find((p) => p.code === data.plan)!,
    evaluation = data.mode === 'EVALUATION',
    pending = data.requests.find((r) => r.status === 'PENDING');
  const missing = data.plans.filter(
    (p) => !payments.pricedPlans.includes(p.code),
  );
  const requested = pending && data.plans.find((p) => p.code === pending.plan);
  const invoice =
    pending && payments.invoices.find((i) => i.requestId === pending.id);
  const reason = requested ? checkoutReason(data, payments, requested) : '';
  return (
    <>
      <PageHeading
        title={
          bridge.welcome ? 'Conheça os planos do Fintry.' : 'Plano e assinatura'
        }
        description="Escolha a capacidade que combina com o momento da sua empresa."
      >
        <button className="btn secondary" onClick={resource.retry}>
          Atualizar uso
        </button>
      </PageHeading>
      {bridge.welcome && (
        <Panel title="Sua conta está pronta.">
          <div className="workspace-empty">
            <p>
              {data.status === 'TRIAL'
                ? `Você começou com o ${selected.name} em teste por 14 dias, até ${timestamp(data.trialEndsAt)}.`
                : `Seu plano inicial é o ${selected.name}. Conheça as opções sem obrigação de contratar.`}
            </p>
            <p>Nenhuma mensalidade foi criada ao cadastrar sua conta.</p>
            <button
              className="btn"
              onClick={() => bridge.navigate('dashboard')}
            >
              {data.status === 'TRIAL'
                ? 'Continuar com o teste'
                : 'Entrar no financeiro'}
            </button>
          </div>
        </Panel>
      )}
      <Panel
        title={
          evaluation
            ? 'Avaliação: sem cobrança'
            : !payments.enabled
              ? 'Emissão de mensalidades desabilitada'
              : payments.environment === 'SANDBOX'
                ? 'Sandbox: cobranças de teste'
                : 'Cobrança em produção'
        }
      >
        <div className="workspace-empty">
          <p>
            {evaluation
              ? 'Experimentar um plano altera apenas a simulação de capacidade. Não gera mensalidade, link ou assinatura paga.'
              : !payments.enabled
                ? 'Solicitar um plano registra seu interesse. O responsável pela instalação precisa habilitar a emissão.'
                : payments.environment === 'SANDBOX'
                  ? 'Faturas de teste não ativam acesso comercial.'
                  : 'Solicite o plano e confirme os dados em Gerar mensalidade. Somente o recebimento confirmado libera o período.'}
          </p>
          {missing.length > 0 && (
            <p>
              Preços de emissão ainda não definidos:{' '}
              {missing.map((p) => p.name).join(', ')}.
            </p>
          )}
          {data.status === 'TRIAL' && (
            <p>
              <strong>
                Você está em teste gratuito, não em uma assinatura paga.
              </strong>{' '}
              Capacidade Profissional até {timestamp(data.trialEndsAt)}.
            </p>
          )}
        </div>
      </Panel>
      <Panel
        title={
          data.status === 'LEGACY'
            ? 'Seu acesso continua preservado'
            : selected.name +
              (evaluation
                ? ' · avaliação'
                : data.status === 'TRIAL'
                  ? ' · teste gratuito'
                  : '')
        }
      >
        <div className="workspace-empty">
          <span className="workspace-status">
            {statusLabels[data.status] || data.status}
          </span>
          <p>
            {data.status === 'LEGACY'
              ? 'A atualização mantém o acesso anterior sem impor limites retroativos.'
              : !data.writable
                ? 'Continue consultando e exportando seus dados. Solicite um plano para voltar a registrar operações.'
                : 'Seu acesso permite registrar operações dentro da capacidade disponível.'}
          </p>
          {!evaluation && !['LEGACY', 'INACTIVE'].includes(data.status) && (
            <p>
              {data.status === 'TRIAL' ? 'Teste até' : 'Acesso até'}{' '}
              {timestamp(
                data.status === 'TRIAL' ? data.trialEndsAt : data.paidUntil,
              )}{' '}
              {data.cancelAtPeriodEnd &&
                '· Encerramento programado ao final do período.'}
            </p>
          )}
          {bridge.isAdmin && !evaluation && data.status === 'ACTIVE' && (
            <button
              className="btn secondary"
              onClick={() =>
                action(
                  data.cancelAtPeriodEnd
                    ? 'subscription-keep'
                    : 'subscription-cancel',
                )
              }
            >
              {data.cancelAtPeriodEnd
                ? 'Manter assinatura'
                : 'Programar encerramento'}
            </button>
          )}
        </div>
      </Panel>
      <section className="workspace-metrics" aria-label="Uso do seu plano">
        {[
          ['Pessoas', data.usage.members, selected.members, false],
          ['Contas', data.usage.accounts, selected.accounts, false],
          [
            'Comprovantes',
            data.usage.storageBytes,
            selected.storageBytes,
            true,
          ],
        ].map(([label, used, limit, bytes]) => (
          <article key={String(label)}>
            <span>{label}</span>
            <strong>
              {bytes ? storage(Number(used)) : used} /{' '}
              {data.limited
                ? bytes
                  ? storage(Number(limit))
                  : limit
                : 'sem limite aplicado'}
            </strong>
            <progress
              value={data.limited ? Math.min(Number(used), Number(limit)) : 0}
              max={Number(limit)}
              aria-label={`Uso de ${label}`}
            />
            <small>
              {data.limited && Number(used) >= Number(limit)
                ? 'Limite atingido. Confira as opções de plano.'
                : label === 'Pessoas'
                  ? 'Pessoas ativas e convites pendentes ocupam vagas.'
                  : label === 'Comprovantes'
                    ? 'Remover anexos libera espaço; lançamentos ficam preservados.'
                    : 'Contas financeiras cadastradas.'}
            </small>
          </article>
        ))}
      </section>
      {!evaluation && (
        <Panel title="Renovação de mensalidades">
          <div className="workspace-finance-form">
            <span className="workspace-status">
              {renewalLabels[renewal.status] || renewal.status}
            </span>
            <p>
              {renewal.enabled
                ? renewal.status === 'AUTHORIZED'
                  ? 'Uma fatura poderá ser gerada perto do fim do período. O pagamento por Pix/boleto renova o acesso.'
                  : 'Novas emissões estão pausadas. Confira o plano, o preço, a configuração ou a mensalidade pendente.'
                : 'Você escolhe quando gerar cada mensalidade. Após um pagamento confirmado, pode autorizar a geração recorrente.'}
            </p>
            {renewal.enabled && (
              <>
                <p>Valor autorizado: {money(renewal.amount ?? 0)} / 30 dias</p>
                <p>
                  Fim do acesso: {timestamp(renewal.nextDueAt)} · Emissão a
                  partir de {timestamp(renewal.generationAt)}
                </p>
                {!renewal.schedulerEnabled && (
                  <p>
                    A rotina automática está desabilitada. Use Verificar
                    renovação ou procure o operador.
                  </p>
                )}
              </>
            )}
            <div className="head-actions">
              {bridge.isAdmin && (
                <>
                  {renewal.canEnable &&
                    (!renewal.enabled ||
                      ['PRICE_CHANGED', 'PLAN_CHANGED'].includes(
                        renewal.status,
                      )) && (
                      <button
                        className="btn"
                        onClick={() => action('subscription-renewal-enable')}
                      >
                        Autorizar recorrência
                      </button>
                    )}
                  {renewal.enabled && (
                    <>
                      <button
                        className="btn secondary"
                        onClick={() => action('subscription-renewal-check')}
                      >
                        Verificar renovação
                      </button>
                      <button
                        className="btn secondary"
                        onClick={() => action('subscription-renewal-disable')}
                      >
                        Interromper emissões
                      </button>
                    </>
                  )}
                </>
              )}
            </div>
            <p className="workspace-caption">
              Sem débito automático. Interromper a autorização não cancela
              faturas já iniciadas.
            </p>
          </div>
        </Panel>
      )}
      {pending && requested && (
        <Panel title={'Solicitação em andamento · ' + requested.name}>
          <div className="workspace-finance-form">
            <p>
              {invoice
                ? 'A solicitação já possui uma mensalidade. Confira a situação e o link abaixo. Para escolher outro plano, confirme o cancelamento da fatura e depois cancele esta solicitação.'
                : reason ||
                  'Próximo passo: confira o valor e os dados do pagador em Gerar mensalidade. Solicitar o plano não emite cobrança.'}
            </p>
            <p>Referência: {pending.id}</p>
            {bridge.isAdmin && (
              <div className="head-actions">
                {!invoice && !reason && (
                  <button
                    className="btn"
                    onClick={() =>
                      action('subscription-checkout', { id: pending.id })
                    }
                  >
                    Gerar mensalidade
                  </button>
                )}
                <button
                  className="btn secondary"
                  onClick={() =>
                    action('subscription-request-cancel', { id: pending.id })
                  }
                >
                  Cancelar solicitação
                </button>
              </div>
            )}
          </div>
        </Panel>
      )}
      <section className="plan-catalog" aria-label="Comparação de planos">
        {data.plans.map((p) => (
          <article className="workspace-record" key={p.code}>
            <span className="eyebrow">
              {p.code === data.plan ? 'CAPACIDADE ATUAL' : 'FINTRY'}
            </span>
            <h2>{p.name}</h2>
            <p>{p.description}</p>
            <strong className="workspace-plan-price">
              {p.monthlyPrice == null
                ? 'Condições a definir'
                : money(p.monthlyPrice) + ' / 30 dias'}
            </strong>
            <ul>
              <li>{p.members} pessoas, incluindo convites</li>
              <li>{p.accounts} contas financeiras</li>
              <li>{storage(p.storageBytes)} para comprovantes</li>
              <li>Lançamentos sem limite de quantidade</li>
              <li>Todos os módulos financeiros e comerciais</li>
            </ul>
            {bridge.isAdmin && (
              <button
                className="btn"
                disabled={!!pending}
                onClick={() =>
                  action(
                    evaluation
                      ? 'subscription-simulate'
                      : 'subscription-request',
                    { plan: p.code },
                  )
                }
              >
                {evaluation
                  ? 'Experimentar plano'
                  : p.code === data.plan && data.status === 'ACTIVE'
                    ? 'Solicitar renovação'
                    : 'Solicitar plano'}
              </button>
            )}
          </article>
        ))}
      </section>
      <p className="workspace-caption">
        Tarifas do provedor são separadas. Escolher plano registra uma
        solicitação; receber o pagamento confirmado libera 30 dias. Reduzir
        capacidade exige que o uso atual caiba no plano. Seus dados não são
        apagados ao encerrar o acesso.
      </p>
      <Panel title="Mensalidades e pagamentos">
        <p className="workspace-caption">
          {payments.enabled
            ? payments.environment === 'PRODUCTION'
              ? 'Faturas no Asaas. Recebimento confirmado atualiza o acesso.'
              : 'Asaas Sandbox: mesmo recebidas, faturas de teste não ativam assinatura comercial.'
            : 'Emissão desabilitada. Nenhuma mensalidade é criada sem confirmação.'}
        </p>
        <Table
          rows={payments.invoices}
          keyOf={(i) => i.id}
          columns={[
            {
              label: 'Mensalidade',
              render: (i) => (
                <>
                  {money(i.amount)}
                  <small>
                    {data.plans.find((p) => p.code === i.plan)?.name} ·{' '}
                    {i.method === 'PIX' ? 'Pix' : 'Boleto'} ·{' '}
                    {i.environment === 'PRODUCTION' ? 'Produção' : 'Teste'}
                  </small>
                </>
              ),
            },
            { label: 'Vencimento', render: (i) => dateLabel(i.dueDate) },
            {
              label: 'Situação',
              render: (i) => (
                <>
                  {invoiceLabels[i.status] || i.status}
                  {i.message && <small>{i.message}</small>}
                  {i.activeUntil && (
                    <small>Acesso até {timestamp(i.activeUntil)}</small>
                  )}
                </>
              ),
            },
            {
              label: 'Ações',
              render: (i) => (
                <div className="workspace-row-actions">
                  {externalInvoiceUrl(i.invoiceUrl) &&
                    ['PENDING', 'OVERDUE', 'CONFIRMED'].includes(i.status) && (
                      <a
                        className="btn small"
                        href={externalInvoiceUrl(i.invoiceUrl)}
                        target="_blank"
                        rel="noopener noreferrer"
                      >
                        Abrir fatura ↗
                      </a>
                    )}
                  {bridge.isAdmin && payments.enabled && (
                    <>
                      <button
                        className="btn secondary small"
                        onClick={() =>
                          action('subscription-payment-refresh', { id: i.id })
                        }
                      >
                        Consultar
                      </button>
                      {['PENDING', 'OVERDUE', 'CANCELLING'].includes(
                        i.status,
                      ) && (
                        <button
                          className="btn secondary small"
                          onClick={() =>
                            action('subscription-payment-cancel', { id: i.id })
                          }
                        >
                          Cancelar mensalidade
                        </button>
                      )}
                    </>
                  )}
                </div>
              ),
            },
          ]}
        />
      </Panel>
      <Panel title="Últimas solicitações">
        <Table
          rows={data.requests}
          keyOf={(r) => r.id}
          columns={[
            {
              label: 'Plano',
              render: (r) => data.plans.find((p) => p.code === r.plan)?.name,
            },
            { label: 'Solicitada em', render: (r) => timestamp(r.createdAt) },
            {
              label: 'Situação',
              render: (r) =>
                (
                  ({
                    PENDING: 'Aguardando ativação',
                    APPROVED: 'Ativada',
                    CANCELLED: 'Cancelada',
                  }) as Record<string, string>
                )[r.status] || r.status,
            },
            { label: 'Acesso até', render: (r) => timestamp(r.activeUntil) },
          ]}
        />
      </Panel>
    </>
  );
}
