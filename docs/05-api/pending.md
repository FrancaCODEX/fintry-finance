# Central de pendências — 0.8.0

`GET /api/v1/finance/pending` exige sessão. ADMIN, FINANCE e VIEWER consultam os dados da própria empresa. Não existem novas rotas de escrita: baixa/edição/cancelamento continuam nas rotas financeiras, com CSRF e as regras existentes.

| Parâmetro | Valores |
|---|---|
| group | ALL (padrão), OVERDUE, TODAY, NEXT7, NEXT30, UPCOMING |
| type | INCOME ou EXPENSE; omitido para ambos |
| accountId | UUID de conta da própria empresa; outra/inexistente retorna 404 |
| source | ALL (padrão), SINGLE (sem série), RECURRING ou INSTALLMENT |
| search | Descrição, até 255 caracteres; %, _ e ! são literais |
| page / size | Página a partir de 0; tamanho de 1 a 100, padrão 20 |

Resposta: `content` (EntryView, incluindo vínculos de série/venda), `totalElements`, `totalPages`, `number`, `today`, `group`, `totals` e `windows`. Totals contém count, income, expense; windows traz os quatro grupos de vencimento e seus totais.

A data usa Clock no servidor em America/Sao_Paulo. OVERDUE é anterior a hoje, TODAY igual a hoje, NEXT7 de amanhã a hoje+7 inclusive, NEXT30 de hoje+8 a hoje+30 inclusive. UPCOMING combina hoje e próximos 7 dias para o dashboard. ALL não restringe data, incluindo além de 30 dias.

Base: status PENDING, transferId nulo, empresa da sessão; filtros de tipo/conta/origem/descrição aplicados tanto à lista quanto aos indicadores. SINGLE inclui avulsos e receitas de vendas sem série. Os indicadores de cada grupo independem do group selecionado; totals e paginação correspondem ao grupo selecionado. Não misturar totais de janelas com valores de saldo realizado.

Ordenação estável por vencimento, criação e id, em ordem crescente, priorizando as pendências mais antigas. A agregação usa uma consulta com somas condicionais, sem carregar todos os lançamentos. Listagem e agregados usam a mesma transação REPEATABLE_READ para evitar divergência por uma baixa concorrente. A leitura não cria eventos de auditoria e não altera o financeiro/estoque.

O dashboard usa UPCOMING com size=5 e consulta o indicador de vencidas da mesma resposta. Esse bloco é do período atual; não segue o mês selecionado nos gráficos históricos. A central mantém a paginação e permite consultar todas as datas.
