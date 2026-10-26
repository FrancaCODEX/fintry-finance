# API implementada — v1

Prefixo: `/api/v1`. O navegador usa cookie de sessão (HttpOnly). Antes de qualquer POST/PUT, obtenha token e nome do header em `GET /auth/csrf`. Envie esse header nas mutações. Nenhum payload financeiro aceita companyId.

| Método | Rota | Uso |
|---|---|---|
| GET | /auth/csrf | Token CSRF; público |
| POST | /auth/register | name, email, password, companyName, document (CNPJ com 14 dígitos); público |
| POST | /auth/login | email, password; cria sessão |
| POST | /auth/logout | Invalida sessão |
| GET | /auth/me | Usuário e empresa autenticados |
| GET, PUT | /companies/me | Consulta/atualiza dados da própria empresa |
| GET, POST | /finance/accounts | Lista/cria conta (name, initialBalance) |
| GET | /finance/entries | Filtros start, end (vencimento), type, status, accountId, search; page (0+), size (1–100) |
| POST | /finance/entries | accountId, description, category, type, amount, dueDate, paid, paymentDate |
| PUT | /finance/entries/{id} | Atualiza somente pendências; mesmo payload de criação, paid=false |
| POST | /finance/entries/{id}/pay | paymentDate e amount opcional; sem amount, baixa o saldo restante |
| POST | /finance/entries/{id}/cancel | Cancela preservando histórico |
| POST | /finance/transfers | sourceId, destinationId, amount, date, description |
| GET | /finance/summary?month=AAAA-MM | Saldo atual e indicadores do mês |
| POST | /finance/sample | Dados fictícios; só para empresa sem contas |

Tipos: INCOME/EXPENSE. Estados: PENDING/PARTIAL/PAID/CANCELLED. Datas: AAAA-MM-DD. Valores: positivos, no máximo 12 dígitos inteiros e 2 decimais (saldo inicial pode ser negativo). Uma baixa parcial aceita `amount` menor ou igual ao saldo restante; omitido, o sistema registra o saldo restante. A resposta apresenta `receivedAmount` e `remainingAmount`; detalhes incluem a lista `payments`. Repetir uma baixa integral sem amount é idempotente. Datas de pagamento não podem estar no futuro, considerando America/Sao_Paulo.

Recursos de outra empresa retornam 404. Credenciais inválidas retornam 401; token CSRF ausente ou inválido retorna 403. Duplicidade retorna 409 e erros de validação 400. Cadastro/login não são rate limited nesta versão; adicionar controle antes de exposição pública.

## Cadastros

- `GET /contacts?search=&kind=CUSTOMER|SUPPLIER|BOTH&active=true|false&page=0&size=20` — lista paginada (máximo 100). O filtro de clientes/fornecedores inclui contatos BOTH. Parâmetros vazios omitem o filtro.
- `POST /contacts`, `PUT /contacts/{id}` — name, kind (CUSTOMER/SUPPLIER/BOTH), document opcional (CPF/CNPJ com ou sem máscara), email, phone, notes e active. Inative com active=false; não há exclusão física.
- `GET /categories` — categorias da própria empresa, incluindo inativas.
- `POST /categories`, `PUT /categories/{id}` — name, kind (INCOME/EXPENSE/BOTH), active. Nome é único por empresa, desconsiderando maiúsculas e espaços nas extremidades.

Lançamentos aceitam `categoryId` e `contactId` opcionais. Com categoryId, a categoria é obtida do cadastro; sem ele, o campo category (texto) continua obrigatório. A resposta inclui os IDs e contactName. Não envie companyId. Vínculo a cadastro de outra empresa retorna 404; cadastro inativo novo ou tipo incompatível retorna 400.

`PUT /finance/entries/{id}` exige estado PENDING e paid=false. Pago, cancelado ou transferência não pode ser editado; use a operação de baixa separadamente. Alterar tipo de contato/categoria já vinculado também é bloqueado. Cadastros inativos podem permanecer em vínculos preexistentes.

## Séries mensais — 0.3.0

- `POST /finance/series`: requestId (UUID estável para repetir a mesma solicitação), kind (INSTALLMENT/RECURRING), occurrences (2–120) e entry (payload normal de lançamento). Em INSTALLMENT, entry.amount é o total; em RECURRING, é o valor de cada ocorrência. entry.paid/paymentDate afetam apenas a primeira. Retorna 201 na criação, 200 na repetição idêntica e 409 se requestId for reutilizado com outros dados.
- `GET /finance/series?page=0&size=20`: lista paginada, máximo 50 séries por página, com contagens de paid/partial/pending/pausedOccurrences/cancelled e flags paused/stopped.
- `POST /finance/series/{id}/pause`: apenas para RECURRING; cancela somente ocorrências PENDING futuras e marca-as para retomada. Vencidas, parciais e pagas não são alteradas. Repetir é seguro.
- `POST /finance/series/{id}/resume`: restaura somente ocorrências canceladas pela pausa, preservando vencimentos originais; série encerrada retorna 409. Repetir é seguro.
- `POST /finance/series/{id}/stop`: encerramento definitivo; cancela PENDING, inclusive vencidas, preserva pagamentos/baixas parciais e remove marcadores de pausa. Repetir é seguro. INSTALLMENT não pode ser pausada/retomada.
- `GET /finance/entries?seriesId={id}`: consulta ocorrências da série com os filtros e a paginação existentes. A resposta inclui seriesId, seriesKind, sequenceNumber e seriesCount.
- `GET /app`: nome e versão da aplicação; público.

Criação de série é atômica. A mesma chave requestId fica isolada por empresa e é serializada pelo bloqueio da empresa. Contatos/categorias/contas são validados pelo fluxo financeiro existente. As séries são finitas, mensais, sem scheduler ou renovação automática. Ocorrências de série não aceitam edição individual; baixa e cancelamento individual continuam disponíveis. As datas são mantidas ao pausar e retomar; ocorrências que vencerem durante a pausa voltam como pendentes vencidas.

## Relatórios e projeções — 0.4.0

- `GET /reports?start=AAAA-MM-DD&end=AAAA-MM-DD&basis=PAYMENT`: período inclusivo obrigatório, máximo 366 dias. PAYMENT (padrão) usa cada data e valor de baixa, inclusive pagamentos parciais; DUE usa dueDate. Filtros opcionais: status (PAID/PARTIAL/PENDING/CANCELLED), type (INCOME/EXPENSE), accountId, category (trecho do nome histórico, até 80 caracteres) e contact (trecho do nome histórico, até 120). Buscas textuais ignoram maiúsculas, mas preservam acentos. DUE sem status exclui CANCELLED. PAYMENT com status diferente de PAID retorna 400.
- Paginação: page (0+) e size (1–100, padrão 20). Retorno: companyName, filters, totals (income, expense, result, pendingIncome, pendingExpense), categories, content, totalElements, totalPages e number. Totais e categorias usam todos os registros filtrados; categorias agrupam somente despesas realizadas. Linhas incluem nomes históricos, conta, tipo, situação, valor, vencimento, pagamento e identificação de ocorrência.
- `GET /reports/export`: mesmos filtros, sem paginação, mais format=csv|xlsx|pdf. Até 5.000 linhas (pagamentos quando PAYMENT); acima disso retorna 400 solicitando refinamento. Resposta binária com Content-Disposition e Cache-Control: no-store. CSV UTF-8 com BOM, separador ponto e vírgula e proteção contra fórmulas; XLSX usa strings explícitas e valores numéricos; PDF é paginado. Excel/PDF incluem empresa, período, filtros e totais. PDF abrevia textos longos e substitui caracteres não suportados pela fonte; CSV/XLSX preservam os textos completos.
- `GET /reports/forecast?days=90&accountId=&includeOverdue=false`: horizonte de 1–366 dias, partindo de hoje (America/Sao_Paulo) até hoje + days, ambos inclusivos. Saldo inicial é o saldo realizado atual das contas selecionadas. Movimentações previstas são pendências pelo vencimento. Vencidas retornam em overdueIncome/overdueExpense; includeOverdue=true as soma a hoje, sem registrar baixa. Retorna start/end, openingBalance, closingBalance, pendingIncome, pendingExpense, overdueIncome, overdueExpense, minimumBalance, firstNegativeDate (ou null), includeOverdue e days (date, income, expense, balance). O mínimo inclui o saldo inicial e considera o fechamento líquido diário.

Todos os recursos derivam a empresa da sessão. Conta de outra empresa retorna 404. Relatórios e projeções excluem transferências. Cancelados só aparecem quando solicitados explicitamente e não compõem totais. Resultado de caixa não equivale a lucro contábil. O summary também inclui previousIncome, previousExpense, previousResult e categories para o dashboard.

## Comprovantes e detalhes — 0.5.0

- `GET /finance/entries/{id}`: entry (mesmos campos de EntryView), accountName, createdAt e updatedAt. Consulta limitada à empresa autenticada; registro de outra empresa retorna 404. Datas de cadastro/alteração seguem os timestamps locais já usados pelas entidades.
- `GET /finance/entries/{entryId}/attachments`: metadados dos comprovantes, ordenados pelos mais recentes. Cada item contém id, filename, contentType, byteCount, uploadedByName e createdAt; o conteúdo binário não entra na listagem.
- `POST /finance/entries/{entryId}/attachments`: `multipart/form-data`, campo file, um arquivo por envio; exige sessão e header CSRF. Retorna 201 com metadados. PDF sem senha, PNG e JPEG; nome de até 120 caracteres (sem caminhos nem controles), conteúdo validado, até 5 × 1024 × 1024 bytes e até 20 milhões de pixels por imagem. Até 10 arquivos por lançamento, sob bloqueio para evitar ultrapassar o limite em concorrência. Formato inválido/corrompido, arquivo vazio, PDF com senha ou limite de quantidade retorna 400. Arquivo/requisição acima do limite retorna 413 com mensagem; envelope multipart limitado a 6 MB.
- `GET /finance/entries/{entryId}/attachments/{id}`: bytes originais com MIME validado, Content-Disposition attachment e Cache-Control no-store. Nunca expõe caminho público de upload. IDs devem corresponder ao lançamento e à empresa da sessão.
- `DELETE /finance/entries/{entryId}/attachments/{id}`: exige CSRF, retorna 204; exclui arquivo e metadados, sem modificar o lançamento. Arquivo inexistente, de outro lançamento ou de outra empresa retorna 404.

Comprovantes podem pertencer a pendentes, realizados, cancelados e transferências; não afetam saldos e permanecem após baixa/cancelamento. Anexo de uma série é associado somente à ocorrência escolhida. O usuário que enviou é derivado da sessão. Tema é preferência local do navegador e não adiciona endpoint de conta; `/theme.js` é público para o acesso inicial.
