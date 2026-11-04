# Evolução do workspace

## Decisão

O Spring Boot continua responsável por autenticação, autorização, empresa corrente e cálculos monetários. A interface evolui por página, mantendo as demais áreas disponíveis durante a adoção de React/TypeScript. O build final continua sendo servido no mesmo domínio; não se introduz autenticação por tokens no navegador.

## Fundação (0.33.0)

`state.js` inicializa o estado da aplicação. `http.js` concentra transporte, CSRF e falhas de sessão; a renderização de login responde ao evento `fintry:session-expired`. Requisições canceladas preservam `AbortError`. Escritas não são repetidas automaticamente pelo transporte.

`api/workspace.openapi.json` documenta **parte** das consultas já existentes, não toda a API. `frontend/src/api/schema.d.ts` é gerado e `client.ts` fornece consultas tipadas. Modificar o contrato exige regenerar e revisar o diff; o CI recusa arquivos gerados desatualizados. Testes HTTP verificam campos obrigatórios nas respostas reais.

Valores monetários das respostas existentes são números JSON. O cliente usa esses valores para apresentação e coordenadas de gráficos; o servidor mantém `BigDecimal` e é a autoridade de cálculos e saldos. Novos parâmetros monetários devem ser enviados como texto decimal, sem calcular saldos com ponto flutuante no navegador.

## Baixas e tentativas repetidas

`POST /api/v1/finance/entries/{id}/pay` aceita `requestId` UUID opcional. A chave pertence à empresa, é persistida em `entry_payments` e possui índice único. O bloqueio do lançamento serializa tentativas simultâneas; mesma chave, lançamento, data e valor devolvem o estado corrente sem outro pagamento. Conteúdo divergente retorna 409. Integrações antigas continuam aceitas sem chave, com o comportamento anterior; clientes novos devem fornecê-la. Abrir outro formulário cria outra operação.

## Próximas entregas acordadas

- 0.34: dashboard e lançamentos em React, navegação e filtros persistentes na URL, componentes acessíveis e detalhes integrados.
- 0.35: simulação comparável ao fluxo existente, hipóteses separadas dos registros financeiros e origem dos valores consultável.

A divisão inicial mantém um monólito modular. Novos módulos devem expor serviços e DTOs próprios, evitando que controllers façam cálculos ou que componentes alterem entidades diretamente.

## Workspace React (0.34.0)

O código novo fica em `frontend/src`, organizado em `app`, `shared`, `api` e `features`. Dashboard e lançamentos possuem estado local e consultas identificadas por parâmetros; cada troca cancela consultas anteriores. Os contratos gerados tipam os dados de leitura.

`workspace-host.js` delimita a migração: o host controla sessão, navegação geral e diálogos de operações financeiras; React controla os dois conteúdos de página. O componente é desmontado antes de mudar a página ou encerrar a sessão, cancelando consultas. Os formulários existentes continuam responsáveis por criação, edição, baixa, cancelamento e comprovantes. Outras páginas permanecem na interface existente.

Mês, pesquisa, tipo, situação, conta e página dos lançamentos ficam na URL. Voltar/avançar reaplica a consulta; abrir detalhes não altera a URL. Indicadores realizados levam ao relatório com base de pagamento e filtros limpos para corresponder ao valor exibido. O saldo atual continua independente do mês selecionado.

O build de produção substitui o ambiente de desenvolvimento do React e gera `workspace/fintry.js` e `workspace/fintry.css`. O Docker compila esses arquivos em um estágio Node e copia somente o resultado para o backend; não há Node no contêiner de execução. No checkout de código, execute `npm ci && npm run build` em `frontend` antes de iniciar o backend. Sem assets compilados, os dois conteúdos anteriores continuam disponíveis como compatibilidade de desenvolvimento.

As preferências do gráfico usam a chave anterior por usuário no mesmo navegador; entradas inválidas são normalizadas. O backend continua sendo a autoridade dos valores, escopo da empresa e permissões.

## Cenários e explicações (0.35.0)

`scenario` é um módulo de cálculo sem tabelas próprias. `ScenarioService` aplica deltas sobre os dias agregados de `ReportService.forecast`, consulta lançamentos pela empresa autenticada e nunca chama serviços de escrita. O relógio injetado define a data de negócio. Repetições mensais usam a primeira data como âncora para evitar deriva nos fins de mês.

A resposta oferece previsão atual, cenário, diferença, efeitos e composição diária em uma transação `REPEATABLE_READ`. A busca de candidatos é paginada, limita 50 itens e escapa curingas de texto. A explicação diária consulta no máximo 101 movimentos para apresentar até 100, informa o total e inclui hipóteses correspondentes. Limites de 20 adiamentos, 20 hipóteses, 12 repetições e 366 dias limitam o trabalho por requisição.

`IndicatorExplanationService` lê totais e fontes pela mesma base de pagamentos, sem reconstruir valores no frontend. Os novos DTOs monetários usam texto decimal; o cliente formata a parte inteira com BigInt e mantém os centavos, usando Number somente nas coordenadas visuais dos gráficos.

A única exceção nova à regra de escrita por assinatura/perfil é `POST /api/v1/scenarios/preview`: é uma operação de leitura autenticada com CSRF. Nenhuma API de mutação financeira é liberada por essa exceção. Os testes de integração exercitam VIEWER, isolamento por empresa, entrada inválida e ausência de alterações persistidas.
