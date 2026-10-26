# Visão geral — 0.21.0

O saldo destacado é o saldo atual de todas as contas, incluindo saldos iniciais e movimentos realizados. Ele independe do mês selecionado. Os três indicadores ao lado mostram receitas, despesas e resultado de caixa realizados pela data de pagamento ou recebimento no mês escolhido. Resultado de caixa não representa lucro contábil.

O gráfico mostra os seis meses terminando no mês selecionado. Passe o mouse, toque ou foque um mês para consultar os valores. As setas esquerda/direita percorrem os meses; Enter e espaço também selecionam. A seção **Ver valores em tabela** permite ler todos os valores sem depender das barras. Selecionar um ponto apenas consulta o gráfico, sem alterar o mês da página ou criar lançamentos.

**No seu radar** apresenta receitas e despesas ainda pendentes pelo vencimento no mês escolhido. Esses valores não integram o saldo realizado antes da baixa. O resumo de atrasos e a lista **Hoje e nos próximos 7 dias** usam a data atual de São Paulo, independente do mês em análise. O botão **Ir para mês atual** volta ao mês de hoje sem limpar os dados da empresa.

Os atalhos respeitam as permissões: lançamentos e transferências aparecem para quem pode registrar operações; demais perfis recebem acesso às contas e pendências. Relatórios e conciliação continuam consultáveis de acordo com as permissões existentes. Sem conta, o atalho de lançamento abre o cadastro de conta já existente no fluxo.

A comparação usa o mês anterior. Sem base anterior, isso é informado em texto. Quando o resultado anterior é negativo, a variação aparece em reais para evitar uma porcentagem ambígua. Nenhum percentual é tratado como recomendação de negócio.

A atualização reorganiza o front-end e usa os mesmos endpoints e totais do backend. Não há migração, nova cobrança, projeção automática, substituição de dados ou alteração nos critérios de cálculo.
