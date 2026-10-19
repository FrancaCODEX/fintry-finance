# Produtos, estoque e vendas — 0.7.0

Todas as rotas usam `/api/v1`, sessão e isolamento por empresa. GET permite ADMIN/FINANCE/VIEWER; POST/PUT exige ADMIN/FINANCE e CSRF. A empresa e o autor sempre vêm da sessão. Não existem exclusões de produtos, movimentos ou vendas.

| Método e rota | Entrada / retorno |
|---|---|
| GET /products | search (nome/SKU, até 120), active, low, page, size (1–100); content, paginação e indicadores globais dos ativos |
| POST /products | sku, name, unit (UN/KG/L/M/CX), price, cost, minimum, active, initialQuantity; 201 |
| PUT /products/{id} | mesmos campos, sem initialQuantity; unidade não pode mudar |
| GET /stock/movements | productId opcional, page, size; content paginado com saldo após o movimento |
| POST /stock/movements | requestId UUID, productId, kind (IN/OUT), quantity positiva, reason obrigatório; 200 |
| GET /sales | status (DRAFT/CONFIRMED/CANCELLED), search (cliente/observação), page, size; lista paginada |
| POST /sales | requestId UUID, contactId opcional, notes até 500, items [{productId,quantity,unitPrice}]; 201, ou 200 na retentativa idêntica |
| GET /sales/{id} | cabeçalho, itens preservados e situação atual da receita |
| POST /sales/{id}/confirm | accountId, dueDate, paid, paymentDate quando recebida; 200 |
| POST /sales/{id}/cancel | sem corpo; 200 |

SKU de 1–40 caracteres ASCII (letras/números/ponto/traço/sublinhado), normalizado em maiúsculas e único por empresa. Nome até 120, quantidades até 999.999.999,999 (três casas), preços/custos e total até 999.999.999.999,99. Estoque não pode ser negativo. Cada venda aceita de 1 a 100 produtos distintos, total maior que zero. Total por item = quantidade × preço, HALF_UP em duas casas; total da venda é a soma dos itens. O cliente deve ser ativo, tipo CUSTOMER/BOTH, e produtos ativos na criação/confirmação.

Rascunho não reserva estoque. Confirmação bloqueia a empresa e produtos, verifica saldo e cria uma única receita INCOME / categoria avulsa Vendas de produtos. Todas as alterações e auditoria participam da mesma transação. Ids de outra empresa retornam 404. Repetir confirmação mantém os dados originais, mesmo com outro corpo válido; para corrigir, cancele e refaça a venda. Cancelamento de rascunho não movimenta estoque/caixa; cancelamento confirmado devolve os itens e cancela a receita, incluindo recebidas.

Movimentos manuais e rascunhos usam requestId exclusivo por empresa e recurso, com fingerprint do corpo tipado. Retentativa idêntica devolve o registro existente; outros dados com a mesma chave retornam 409. Confirmação/cancelamento são idempotentes pela situação e bloqueio da venda. Movimentos de venda/devolução não podem ser enviados manualmente. Uma venda cancelada não pode ser reaberta. Correções de estoque usam movimento compensatório, nunca edição do histórico.

`FinancialEntry`/detalhes incluem `saleId` opcional. Edição/cancelamento genérico dessa receita retorna 400; baixa de receita pendente continua permitida. Cancelar a venda não exclui comprovantes. Eventos de auditoria usam PRODUCT, STOCK e SALE; estoque mantém snapshots do nome/SKU/unidade/autor em cada movimento. Quantidades e preços dos itens são preservados.

Os indicadores de produtos ativos/estoque baixo/valor são globais da empresa, independentemente do filtro da lista. Valor do estoque usa custo atual × saldo dos produtos ativos, sem custo médio. Entrada manual de mercadoria não cria despesa financeira. Não há API fiscal, pagamento externo, descontos ou devolução parcial nesta etapa.
