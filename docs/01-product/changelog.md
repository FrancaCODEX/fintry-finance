# Histórico

## 0.1.0 — Primeira versão funcional

- Cadastro, autenticação com sessão e CSRF, isolamento por empresa.
- Contas financeiras, receitas, despesas, baixa e cancelamento com histórico.
- Transferências atômicas e cálculo de saldo sem coluna de saldo mutável.
- Dashboard, filtros e paginação, interface responsiva em português.
- Dados fictícios opcionais, Docker Compose e modo H2 local.
- Testes HTTP de regras financeiras, autenticação e isolamento.

O CRUD global de empresas foi substituído por consulta e edição da própria empresa em `/api/v1/companies/me`. A criação da empresa ocorre no cadastro inicial. Não há exclusão de empresa exposta nesta versão.
