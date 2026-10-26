# OFX e conciliação — 0.13.0

A conciliação compara movimentos bancários com lançamentos existentes. Importar um extrato cria somente movimentos em revisão. Não cria receitas/despesas, não altera saldos iniciais e não substitui o financeiro pelo saldo do banco.

## Fluxo da API

Todas as rotas usam a sessão autenticada e isolamento por empresa. POST exige CSRF e perfil ADMIN/FINANCE; VIEWER pode consultar GET.

Base: `/api/v1/finance/reconciliation`.

| Método | Rota | Conteúdo / resultado |
|---|---|---|
| POST | `/preview` | `{accountId, ofx}` → conta de destino, banco, final da conta, contagens e registros READY/DUPLICATE/INVALID. Sem gravação. |
| POST | `/import` | `{accountId, ofx, selectedRows:[1,2]}` → `{imported, skipped}`. Revalida o arquivo e a seleção; inválidos selecionados impedem toda a operação. Repetidos são ignorados. |
| GET | `?accountId=…&status=OPEN&page=0&size=25` | Movimentos persistidos, até 50 por página, ordenados por data/criação/ID. Status opcional OPEN/LINKED/IGNORED; contagens globais da conta. |
| GET | `/{id}/candidates?search=…` | Até 100 lançamentos disponíveis de mesmo valor, tipo e conta; busca por descrição até 120 caracteres. Realizados exigem mesma data. |
| POST | `/{id}/resolve` | `{revision, action, entryId?, category?, categoryId?, reason?}` → movimento atualizado. |
| POST | `/group/candidates` | `{accountId, movementIds:[…]}` → até 50 lançamentos pendentes/parciais da mesma conta, direção e com saldo suficiente. |
| POST | `/group` | `{accountId, movementIds:[…], entryId, reason}` → grupo conciliado. Aceita de 2 a 50 movimentos abertos de mesma direção; cada valor é baixado separadamente na data do movimento. |
| GET | `/example` | Download de OFX fictício com dois movimentos do dia para conta de avaliação. |

Cada movimento retorna ID, FITID, descrição, data, valor assinado como string decimal, situação, revisão, motivo, lançamento vinculado, identificador do grupo (quando houver), quantidade de movimentos do grupo e até três sugestões. A busca manual não limita a data de vencimento dos pendentes. Sugestões consideram pendentes com vencimento em ±7 dias ou realizados com a mesma data, com prioridade para descrição normalizada e proximidade; são limitadas a uma amostra de dez candidatos. Não são aprovação automática e não garantem correspondência.

## Decisões

- `LINK` com `entryId`: conta, empresa, sinal/tipo e valor devem coincidir; cancelados ou já vinculados são rejeitados. Pendentes são baixados na data do banco, incluindo receitas de vendas e séries. Realizados precisam ter a mesma data de pagamento e não recebem outra baixa. Transferências existentes podem ser vinculadas por lado/conta; nunca são criadas automaticamente.
- `CREATE`: cria um realizado com valor absoluto, tipo pelo sinal, vencimento/pagamento na data bancária e descrição do extrato. Exige categoria avulsa ou `categoryId` válido. Quando há candidatos de mesmo valor/tipo, exige `reason` para justificar que o movimento é distinto.
- `IGNORE`: exige motivo de até 255 caracteres e mantém o movimento consultável, sem efeito financeiro.
- `REOPEN`: exige motivo e remove decisão/vínculo. **Não desfaz pagamento nem exclui o lançamento criado.** Corrija o financeiro pelas ações existentes e então revise a conciliação novamente. Cancelar lançamento/venda vinculado exige primeiro reabrir a conciliação.

A revisão individual aumenta a cada decisão. A criação do grupo bloqueia a empresa, revalida que todos os movimentos continuam abertos e valida o saldo restante antes de qualquer baixa; a transação inteira é revertida se uma baixa falhar. As baixas parciais criadas ficam no histórico normal de pagamentos e no lote. Movimentos de um grupo não podem ser reabertos individualmente; isso evita desassociar só parte de pagamentos já registrados.

## Formato, limites e preservação

O leitor aceita OFX bancário SGML 1.x com fechamento implícito de campos escalares e XML 2.x sem namespaces/atributos, uma única `STMTRS`, `CURDEF=BRL`, `BANKACCTFROM` com BANKID/ACCTID/ACCTTYPE e uma BANKTRANLIST. BRANCHID é opcional. Datas DTPOSTED usam AAAAMMDD, opcionalmente hora, fração e offset; a data contábil do banco é preservada sem conversão de fuso. Valores usam ponto decimal, sinal opcional, até 12 inteiros e duas casas; zero, datas futuras, campos obrigatórios ausentes e correções CORRECTFITID/CORRECTACTION são inválidos. O sinal de TRNAMT determina entrada/saída, independentemente de TRNTYPE.

Limites: até 1 MB de conteúdo UTF-8 no servidor e 500 movimentos. A interface lê bytes UTF-8 com fallback Windows-1252. DTD, entidades externas, comentários, declarações não suportadas, documentos múltiplos, contas múltiplas e estruturas malformadas são rejeitados. Não há resolução de entidades externas nem acesso à rede; apenas escapes XML padrão e entidades numéricas são decodificados. Cartões e investimentos não são aceitos.

BANKID/BRANCHID/ACCTID/ACCTTYPE identificam a origem por hash SHA-256 com componentes delimitados por tamanho. A primeira importação associa a origem à conta Fintry e não permite usá-la em outra conta da mesma empresa. Banco/final da conta são exibidos para conferência explícita. Mudanças na identificação fornecida pelo banco exigem revisão; não há remapeamento automático de origem nesta versão.

FITID distingue movimentos na mesma origem. Reimportar o mesmo ID/conteúdo é ignorado, mesmo se o movimento foi ignorado ou reaberto; alterar data, valor ou descrição sob o mesmo ID gera conflito na prévia. Repetições dentro do arquivo são sinalizadas e apenas a primeira ocorrência válida pode ser importada. Não se deve presumir duplicidade apenas porque dois movimentos possuem mesmo valor/data/descrição e FITIDs diferentes.

São persistidos os movimentos, identificadores bancários, hash da origem, banco, final mascarado da conta e decisões auditadas. O arquivo original e os números completos de agência/conta não são armazenados. Dados financeiros antigos e migrações publicadas são preservados. O grupo exige uma única direção e um único lançamento; as tarifas devem ser lançadas e conciliadas à parte. Não há compensação automática de sinais, rateio de um movimento por vários lançamentos, ajuste de saldo nem conexão bancária direta.

Referência do formato: [OFX Banking 2.3, FDX](https://financialdataexchange.org/common/Uploaded%20files/OFX%20files/OFX%20Banking%20Specification%20v2.3.pdf).
