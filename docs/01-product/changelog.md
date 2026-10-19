# Histórico

## 0.2.0 — Cadastros e edição

- Clientes/fornecedores com busca, paginação, edição e inativação/reativação.
- Categorias com classificação por receita/despesa e unicidade por empresa.
- Vínculos opcionais nos lançamentos, com validação de tipo e empresa.
- Edição de pendências sob bloqueio, preservando os saldos e os registros realizados.
- Migrações incrementais PostgreSQL V4 e H2 V2 preservam dados existentes.
- Nomes registrados nos lançamentos mantêm o histórico após inativar ou renomear cadastros.

## 0.1.0 — Primeira versão funcional

- Cadastro, autenticação com sessão e CSRF, isolamento por empresa.
- Contas financeiras, receitas, despesas, baixa e cancelamento com histórico.
- Transferências atômicas e cálculo de saldo sem coluna de saldo mutável.
- Dashboard, filtros e paginação, interface responsiva em português.
- Dados fictícios opcionais, Docker Compose e modo H2 local.
- Testes HTTP de regras financeiras, autenticação e isolamento.

O CRUD global de empresas foi substituído por consulta e edição da própria empresa em `/api/v1/companies/me`. A criação da empresa ocorre no cadastro inicial. Não há exclusão de empresa exposta nesta versão.
