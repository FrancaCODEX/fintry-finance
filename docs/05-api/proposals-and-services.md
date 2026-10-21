# Serviços e propostas — v0.12.0

## Catálogo e estoque

Produtos e serviços compartilham `/api/v1/products`, código/SKU único por empresa e preços/custos. O novo campo `kind` é `PRODUCT` ou `SERVICE`; sem informar no cadastro, usa PRODUCT. Em edição, omitir mantém o tipo. Tipo e unidade são fixos para preservar snapshots e histórico.

`GET /products?kind=PRODUCT|SERVICE` filtra o catálogo; omitir lista ambos. A página Produtos e o seletor de estoque filtram PRODUCT. Vendas/propostas aceitam ambos. `low=true` só retorna produtos físicos. Indicadores respeitam o tipo do filtro; serviços não são considerados estoque baixo ou valor de estoque.

Serviços usam UN por unidade de execução; quantidade em venda/proposta aceita até três casas decimais, por exemplo 1,5 execução. `minimum` e `initialQuantity` devem ser zero (estoque inicial pode ser omitido). Saldo permanece zero. `/stock/movements` recusa serviços. Custo é apenas referência; não gera despesa automaticamente. Inativar preserva o histórico e bloqueia novas vendas/aprovações.

As migrações PostgreSQL V11/H2 V9 adicionam kind com default PRODUCT, preservando saldos e itens de vendas anteriores. Serviços não podem converter produtos existentes nem receber movimentação manual. Vendas confirmadas/canceladas movimentam apenas itens físicos, mantendo o comportamento financeiro anterior.

## Propostas

Todos os endpoints são autenticados e limitados à empresa da sessão. ADMIN/FINANCE escrevem com CSRF; VIEWER consulta e baixa PDF.

| Método e caminho após `/api/v1` | Comportamento |
|---|---|
| GET `/proposals` | Lista paginada; filtros status, search (título/cliente, até 120 caracteres), expired (abertas expiradas), page e size (1–100). Contadores drafts/negotiating/approved consideram toda a empresa |
| GET `/proposals/{id}` | Detalhes, snapshot dos itens, revisão version e vínculo com venda |
| POST `/proposals` | Cria rascunho com requestId UUID, title (1–120), contactId opcional, notes (até 500), validUntil (hoje até 2100) e items (1–100). Cada item: productId, quantity (0.001–999999999.999), unitPrice (0–999999999999.99). Itens únicos por catálogo; total deve ser positivo e caber no limite financeiro |
| PUT `/proposals/{id}` | `{ "version": 0, "content": { ...mesmo formato de criação... } }`; somente rascunho. requestId é fixo, itens são reconstruídos e revisão avança mesmo quando só quantidades/preços mudam sem alterar o total |
| POST `/proposals/{id}/status` | `{ "version": 0, "status": "OPEN" }`; transições descritas abaixo |
| POST `/proposals/{id}/approve` | `{ "version": 1, "payment": { "accountId": "UUID", "dueDate": "AAAA-MM-DD", "paid": false, "paymentDate": null } }`; aprova e gera venda confirmada/receita |
| GET `/proposals/{id}/pdf` | PDF para download com título, empresa, cliente, situação/validade, itens e observações. Informações refletem a proposta; identificação da empresa vem do cadastro atual |

Estados: DRAFT (rascunho), OPEN (em negociação), APPROVED (aprovada), REJECTED (recusada), CANCELLED (cancelada). DRAFT permite OPEN ou CANCELLED; OPEN permite voltar a DRAFT, REJECTED ou CANCELLED. Aprovar usa endpoint próprio, apenas de OPEN e dentro da validade. Colocar em OPEN não envia mensagem a ninguém. Expirada é uma condição calculada para DRAFT/OPEN e não altera dados em consulta. Para renovar: voltar a DRAFT e editar validade.

Total calculado no servidor com BigDecimal: arredonda cada quantidade × preço para duas casas HALF_UP, depois soma. Cliente e itens devem pertencer à empresa, estar ativos e ter tipo compatível. A aprovação não usa preços/nomes atuais do catálogo: preserva os acordados, mas verifica atividade e estoque atuais. Propostas não reservam estoque e não aparecem no fluxo de caixa até aprovar.

Criação usa requestId + hash do conteúdo e lock da empresa: repetição idêntica retorna a mesma proposta (200); reutilização com conteúdo diferente gera 409. A revisão version é conferida sob lock em edições/transições/aprovação, retornando 409 para uma tela antiga. Editar uma proposta não altera o hash original da criação. Repetir a criação original após edição continua retornando a proposta existente.

Aprovação é uma transação única: cria venda com snapshots, confirma produtos/serviços e cria receita via FinanceService. Erro de estoque, conta, cliente/catálogo inativo, data ou limite financeiro desfaz todas as alterações, mantendo OPEN. Lock da empresa segue a mesma ordem dos fluxos comerciais e serializa confirmações. Aprovação repetida/concorrente de uma proposta já APPROVED retorna a venda existente, sem nova baixa ou receita; condições de pagamento da repetição não alteram a primeira operação.

Propostas aprovadas não são reabertas/canceladas por este endpoint. Cancelar a venda vinculada devolve somente produtos, cancela a receita e preserva a aprovação na proposta. Repetir aprovação após esse cancelamento continua apontando para a venda cancelada, sem recriar recebíveis. Nenhuma operação faz devolução bancária automaticamente.

Auditoria usa recurso PROPOSAL (CREATE/UPDATE/STATUS/APPROVE) com campos/itens, além dos eventos existentes da venda, estoque e lançamento. Não há assinatura eletrônica, aceitação pública do cliente, envio de e-mail, impostos, descontos gerais ou integração fiscal nesta versão. Caracteres sem representação na fonte PDF padrão são substituídos por `?`; nomes/preços originais permanecem na interface e banco.
