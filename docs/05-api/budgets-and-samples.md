# Orçamentos e exemplos — 0.9.0

As rotas exigem sessão e isolam os dados por empresa. ADMIN/FINANCE escrevem com CSRF; VIEWER consulta.

| Rota | Comportamento |
|---|---|
| GET /api/v1/finance/budgets?month=2026-10 | Categorias com limite, realizado/pendente, percentual/situação, totais e despesas sem orçamento |
| POST /api/v1/finance/budgets | Cria limite; corpo month, categoryId, limitAmount; 201 com UUID |
| PUT /api/v1/finance/budgets/{id} | Atualiza mês/categoria/limite, com as mesmas validações |
| DELETE /api/v1/finance/budgets/{id} | Remove somente o limite; 204; preserva despesas e auditoria |
| POST /api/v1/finance/budgets/copy | Corpo month (destino); copia limites do mês imediatamente anterior, retorna copied/skipped |
| GET /api/v1/finance/sample | loaded, packVersion e completedAt do pacote desta empresa |
| POST /api/v1/finance/sample | Adiciona os pacotes incrementais 1–3 explicitamente; retorna a versão atual sem duplicar; 201 |

month tem formato AAAA-MM de 2000-01 a 2100-12. O limite deve ser positivo, até 12 inteiros e 2 decimais. Apenas categorias de despesa/mistas da própria empresa; novas associações exigem categoria ativa. Categoria inativa já vinculada permite editar o limite. Categoria/mês duplicado retorna 409; orçamento/categoria de outra empresa retorna 404.

Consumo: despesas por vencimento no mês, PAID/PENDING, sem transferências. Pago+pendente formam committed; remaining pode ser negativo; percentage não é truncado a 100. WARNING de 80% até 100% inclusive, EXCEEDED acima de 100%. Os totais de orçamento incluem apenas categorias com limite; unbudgetedPaid/unbudgetedPending incluem as demais, inclusive categoryId nulo. O acompanhamento usa id da categoria, mesmo após renomear. A listagem usa REPEATABLE_READ para manter os agregados consistentes.

A cópia ignora categorias inativas e limites já definidos no destino. Não copia lançamentos. Alterar/remover orçamento não altera contas, saldo ou despesas. Operações são auditadas como BUDGET.

O pacote fictício pode complementar dados existentes, com identificador próprio nos nomes/SKUs. Bloqueio da empresa, transação única e sample_runs impedem repetição/concorrência e registram conclusão apenas após sucesso integral, incluindo PDFs. Nos perfis H2 demo/test, LOCK_TIMEOUT=30000 permite aguardar a transação anterior, inclusive durante a geração inicial dos PDFs. Não há carregamento automático ou função de reset; repetição não recria itens editados/removidos pelo usuário. Contatos usam domínio example.invalid; documentos/telefones fictícios e PDFs sem validade. Não cria contas de usuário nem simula autoria antiga. Histórico registra as ações reais de inclusão. Empresas que usaram pacotes antigos podem solicitar as expansões seguintes uma vez cada, sem migrar/substituir registros anteriores. O pacote 3 adiciona baixas parciais e cinco movimentos OFX fictícios; dois são conciliados em grupo, dois são vinculados individualmente e um fica em revisão. Nenhuma instituição financeira é acessada.
