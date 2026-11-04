# Simulador de caixa

Em **Simular cenários**, compare o fluxo projetado existente com hipóteses. Selecione horizonte de 30, 60, 90, 180 ou 366 dias, conta ou todas as contas e se deseja considerar vencidas como previstas para hoje.

## Adiamentos e hipóteses

Busque um lançamento pendente ou parcialmente baixado e adie o saldo restante por 1 a 366 dias. Cada lançamento pode aparecer uma vez no cenário, com até 20 adiamentos. A data original permanece no registro real. Vencidas incluídas na projeção são previstas para hoje; o adiamento parte dessa data efetiva.

Adicione até 20 hipóteses de receita/despesa. Informe descrição, valor positivo com até duas casas decimais, primeira data dentro do horizonte e 1 a 12 ocorrências mensais. Uma ocorrência representa uma hipótese eventual. As repetições preservam o dia original quando possível: 31/10, 30/11 e 31/12. Ocorrências e adiamentos que ultrapassam o horizonte são identificados e não entram no saldo do período.

**Comparar cenário** atualiza o resultado. Enquanto houver mudanças ainda não comparadas, a interface mantém os resultados anteriores identificados. Alterar conta, horizonte ou consideração de vencidas remove os adiamentos selecionados, para que o movimento escolhido pertença ao novo escopo.

## Comparação e origem dos números

O saldo inicial é o saldo atual das contas selecionadas, com baixas já registradas. A projeção usa os valores restantes de receitas/despesas pendentes, excluindo transferências, cancelados e ocorrências suspensas. O saldo diário é saldo anterior + receitas previstas − despesas previstas.

Compare o saldo final, a diferença entre cenário e previsão atual, o menor saldo e o primeiro dia negativo. Use o controle de dia ou clique no gráfico e consulte a composição: movimentos existentes e hipóteses são identificados; itens adiados informam a nova data. São exibidas até 100 fontes por dia, com contagem total e aviso quando houver mais; os totais continuam completos.

No dashboard, **Entender valor** explica receitas, despesas e resultado pelas baixas do mês; o saldo atual oferece sua própria fórmula, independente do mês selecionado. As baixas podem ser paginadas e abertas no painel de detalhes.

## Limites

As hipóteses ficam apenas na página e desaparecem ao sair/recarregar; não há cenários persistidos ou compartilhados nesta entrega. Simular não cria lançamentos, pagamentos, tarifas, cobranças ou decisões de conciliação. A previsão usa os dados existentes e as hipóteses informadas, sem prever comportamento de clientes ou movimentação bancária externa.

Perfis Administrador, Financeiro e Somente leitura podem simular, inclusive com assinatura sem escrita habilitada. A simulação não concede permissão para alterar dados reais. As operações financeiras continuam exigindo as confirmações e permissões existentes.
