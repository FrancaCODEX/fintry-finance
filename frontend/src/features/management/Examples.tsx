import { useState } from 'react';
import type { Bridge } from '../../app/bridge';
import { useResource } from '../../shared/useResource';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import { Confirm, mutation } from '../../shared/workspace';
export function Examples({ bridge }: { bridge: Bridge }) {
  const resource = useResource('examples', (signal) =>
    bridge.request<{ loaded: boolean; packVersion: number }>(
      '/finance/sample',
      { signal },
    ),
  );
  const [confirm, setConfirm] = useState(false);
  const current =
    resource.data?.loaded && (resource.data.packVersion || 1) >= 3;
  const areas = [
    ['dashboard', 'Visão geral', 'Saldo, realizado e projeções do cenário.'],
    [
      'entries',
      'Lançamentos e baixas',
      'Pagamentos parciais, saldo restante e histórico.',
    ],
    [
      'reports',
      'Relatórios',
      'Valores pagos e recebidos pelas datas das baixas.',
    ],
    [
      'reconciliation',
      'Conciliação',
      'Movimentos agrupados e itens em revisão.',
    ],
    [
      'pending',
      'Pendências e séries',
      'Vencimentos, atrasos, recorrências e parcelas.',
    ],
    [
      'contacts',
      'Clientes e fornecedores',
      'Cadastros variados, ativos e inativos.',
    ],
    [
      'budgets',
      'Categorias e orçamentos',
      'Limites dentro do previsto, em atenção e excedidos.',
    ],
    [
      'sales',
      'Produtos, estoque e vendas',
      'Vendas e propostas em situações diferentes.',
    ],
  ];
  return (
    <>
      <PageHeading
        title="Dados de exemplo"
        description="Explore o Fintry com um cenário fictício completo."
      />
      <QueryState {...resource} />
      {resource.data && (
        <>
          <Panel
            title={
              current
                ? 'Seu ambiente de apresentação está pronto.'
                : 'Prepare seu ambiente de apresentação.'
            }
          >
            <div className="workspace-empty">
              <span className="eyebrow">DADOS FICTÍCIOS · PACOTE 3</span>
              <p>
                Os registros recebem a identificação Exemplo ou DEMO. Seus
                cadastros e alterações atuais são preservados; repetir não
                duplica os exemplos.
              </p>
              {bridge.canWrite && (
                <button className="btn" onClick={() => setConfirm(true)}>
                  {current
                    ? 'Conferir pacote de exemplos'
                    : resource.data.loaded
                      ? 'Adicionar exemplos de apresentação'
                      : 'Preparar ambiente de apresentação'}
                </button>
              )}
            </div>
          </Panel>
          <div className="workspace-catalog-grid">
            {areas.map(([page, title, description], i) => (
              <article className="workspace-record" key={page}>
                <span className="eyebrow">
                  {String(i + 1).padStart(2, '0')}
                </span>
                <h2>{title}</h2>
                <p>{description}</p>
                <button
                  className="btn text"
                  onClick={() => bridge.navigate(page)}
                >
                  Explorar →
                </button>
              </article>
            ))}
          </div>
        </>
      )}
      <p className="workspace-caption">
        Extratos, pagamentos e comprovantes fictícios. Nenhuma instituição
        financeira é acessada. Valores entram nos indicadores desta empresa; use
        uma empresa de avaliação.
      </p>
      {confirm && (
        <Confirm
          title="Preparar exemplos de apresentação?"
          description={
            current
              ? 'O pacote já está completo. Repetir não duplica os registros e preserva suas alterações.'
              : 'Serão adicionados exemplos de contas, contatos, financeiro, séries, produtos, serviços, vendas, propostas, orçamentos, comprovantes e extrato OFX. Valores realizados aparecem no dashboard. Dados atuais serão preservados.'
          }
          close={() => setConfirm(false)}
          label="Adicionar exemplos"
          confirm={async () => {
            await bridge.request('/finance/sample', mutation('POST'));
            resource.retry();
          }}
        />
      )}
    </>
  );
}
