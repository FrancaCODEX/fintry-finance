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
| POST | /finance/entries/{id}/pay | paymentDate; baixa idempotente |
| POST | /finance/entries/{id}/cancel | Cancela preservando histórico |
| POST | /finance/transfers | sourceId, destinationId, amount, date, description |
| GET | /finance/summary?month=AAAA-MM | Saldo atual e indicadores do mês |
| POST | /finance/sample | Dados fictícios; só para empresa sem contas |

Tipos: INCOME/EXPENSE. Estados: PENDING/PAID/CANCELLED. Datas: AAAA-MM-DD. Valores: positivos, no máximo 12 dígitos inteiros e 2 decimais (saldo inicial pode ser negativo). Datas de pagamento não podem estar no futuro, considerando America/Sao_Paulo.

Recursos de outra empresa retornam 404. Credenciais inválidas retornam 401; token CSRF ausente ou inválido retorna 403. Duplicidade retorna 409 e erros de validação 400. Cadastro/login não são rate limited nesta versão; adicionar controle antes de exposição pública.

## Cadastros

- `GET /contacts?search=&kind=CUSTOMER|SUPPLIER|BOTH&active=true|false&page=0&size=20` — lista paginada (máximo 100). O filtro de clientes/fornecedores inclui contatos BOTH. Parâmetros vazios omitem o filtro.
- `POST /contacts`, `PUT /contacts/{id}` — name, kind (CUSTOMER/SUPPLIER/BOTH), document opcional (CPF/CNPJ com ou sem máscara), email, phone, notes e active. Inative com active=false; não há exclusão física.
- `GET /categories` — categorias da própria empresa, incluindo inativas.
- `POST /categories`, `PUT /categories/{id}` — name, kind (INCOME/EXPENSE/BOTH), active. Nome é único por empresa, desconsiderando maiúsculas e espaços nas extremidades.

Lançamentos aceitam `categoryId` e `contactId` opcionais. Com categoryId, a categoria é obtida do cadastro; sem ele, o campo category (texto) continua obrigatório. A resposta inclui os IDs e contactName. Não envie companyId. Vínculo a cadastro de outra empresa retorna 404; cadastro inativo novo ou tipo incompatível retorna 400.

`PUT /finance/entries/{id}` exige estado PENDING e paid=false. Pago, cancelado ou transferência não pode ser editado; use a operação de baixa separadamente. Alterar tipo de contato/categoria já vinculado também é bloqueado. Cadastros inativos podem permanecer em vínculos preexistentes.
