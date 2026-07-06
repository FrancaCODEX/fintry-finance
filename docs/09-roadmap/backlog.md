# Fintry - Product Backlog

## Objetivo

Este documento organiza todas as funcionalidades do Fintry em ordem de prioridade, permitindo acompanhar a evolução do produto durante seu desenvolvimento.

---

# Status

* 🟢 Done
* 🟡 In Progress
* ⚪ Planned
* 🔴 Blocked

---

# Prioridades

* 🔥 Critical
* 🟠 High
* 🟡 Medium
* 🔵 Low

---

# Sprint 0 - Planejamento

| ID      | Item                 | Tipo          | Prioridade  | Status         |
| ------- | -------------------- | ------------- | ----------- | -------------- |
| DOC-001 | Documento de Visão   | Documentation | 🔥 Critical | 🟢 Done        |
| DOC-002 | PRD                  | Documentation | 🔥 Critical | 🟢 Done        |
| DOC-003 | Arquitetura          | Documentation | 🔥 Critical | 🟢 Done        |
| DOC-004 | Modelo de Domínio    | Documentation | 🔥 Critical | 🟢 Done        |
| DOC-005 | User Stories         | Documentation | 🔥 Critical | 🟢 Done        |
| DOC-006 | Product Backlog      | Documentation | 🔥 Critical | 🟢 Done        |
| DOC-007 | ADRs                 | Documentation | 🟠 High     | ⚪ Planned      |
| DOC-008 | DER (Banco de Dados) | Documentation | 🔥 Critical | 🟡 In Progress |
| DOC-009 | Wireframes           | Documentation | 🟠 High     | ⚪ Planned      |

---

# Sprint 1 - Infraestrutura

## Objetivo

Criar a base técnica do projeto.

| ID        | Item                         | Tipo | Prioridade  | Status    |
| --------- | ---------------------------- | ---- | ----------- | --------- |
| INFRA-001 | Criar projeto Spring Boot    | Task | 🔥 Critical | ⚪ Planned |
| INFRA-002 | Configurar Java 21           | Task | 🔥 Critical | ⚪ Planned |
| INFRA-003 | Configurar Maven             | Task | 🔥 Critical | ⚪ Planned |
| INFRA-004 | Configurar PostgreSQL        | Task | 🔥 Critical | ⚪ Planned |
| INFRA-005 | Configurar Docker            | Task | 🔥 Critical | ⚪ Planned |
| INFRA-006 | Configurar Docker Compose    | Task | 🟠 High     | ⚪ Planned |
| INFRA-007 | Configurar Flyway            | Task | 🔥 Critical | ⚪ Planned |
| INFRA-008 | Configurar Swagger/OpenAPI   | Task | 🔥 Critical | ⚪ Planned |
| INFRA-009 | Configurar Spring Security   | Task | 🟠 High     | ⚪ Planned |
| INFRA-010 | Configurar estrutura modular | Task | 🔥 Critical | ⚪ Planned |

---

# Sprint 2 - Autenticação

## Objetivo

Implementar autenticação e autorização.

| ID       | Item                 | Tipo  | Prioridade  | Status    |
| -------- | -------------------- | ----- | ----------- | --------- |
| AUTH-001 | Cadastro de usuário  | Story | 🔥 Critical | ⚪ Planned |
| AUTH-002 | Login                | Story | 🔥 Critical | ⚪ Planned |
| AUTH-003 | JWT                  | Task  | 🔥 Critical | ⚪ Planned |
| AUTH-004 | Refresh Token        | Story | 🟠 High     | ⚪ Planned |
| AUTH-005 | Logout               | Story | 🟠 High     | ⚪ Planned |
| AUTH-006 | Recuperação de senha | Story | 🟠 High     | ⚪ Planned |

---

# Sprint 3 - Empresa

## Objetivo

Cadastrar e gerenciar empresas.

| ID          | Item                | Tipo  | Prioridade  | Status    |
| ----------- | ------------------- | ----- | ----------- | --------- |
| COMPANY-001 | Cadastro de empresa | Story | 🔥 Critical | ⚪ Planned |
| COMPANY-002 | Atualizar empresa   | Story | 🟠 High     | ⚪ Planned |
| COMPANY-003 | Upload de logo      | Story | 🟡 Medium   | ⚪ Planned |

---

# Sprint 4 - Clientes

## Objetivo

Gerenciar clientes.

| ID           | Item              | Tipo  | Prioridade  | Status    |
| ------------ | ----------------- | ----- | ----------- | --------- |
| CUSTOMER-001 | Cadastrar cliente | Story | 🔥 Critical | ⚪ Planned |
| CUSTOMER-002 | Editar cliente    | Story | 🔥 Critical | ⚪ Planned |
| CUSTOMER-003 | Excluir cliente   | Story | 🔥 Critical | ⚪ Planned |
| CUSTOMER-004 | Listar clientes   | Story | 🔥 Critical | ⚪ Planned |
| CUSTOMER-005 | Paginação         | Task  | 🟠 High     | ⚪ Planned |
| CUSTOMER-006 | Busca por nome    | Story | 🟠 High     | ⚪ Planned |

---

# Sprint 5 - Produtos

## Objetivo

Gerenciar produtos e estoque.

| ID          | Item                | Tipo  | Prioridade  | Status    |
| ----------- | ------------------- | ----- | ----------- | --------- |
| PRODUCT-001 | CRUD de produtos    | Story | 🔥 Critical | ⚪ Planned |
| PRODUCT-002 | CRUD de categorias  | Story | 🔥 Critical | ⚪ Planned |
| PRODUCT-003 | Controle de estoque | Story | 🟠 High     | ⚪ Planned |

---

# Sprint 6 - Financeiro

## Objetivo

Controlar movimentações financeiras.

| ID      | Item                   | Tipo  | Prioridade  | Status    |
| ------- | ---------------------- | ----- | ----------- | --------- |
| FIN-001 | Contas financeiras     | Story | 🔥 Critical | ⚪ Planned |
| FIN-002 | Categorias financeiras | Story | 🔥 Critical | ⚪ Planned |
| FIN-003 | Registrar receita      | Story | 🔥 Critical | ⚪ Planned |
| FIN-004 | Registrar despesa      | Story | 🔥 Critical | ⚪ Planned |
| FIN-005 | Transferências         | Story | 🟠 High     | ⚪ Planned |
| FIN-006 | Fluxo de caixa         | Story | 🔥 Critical | ⚪ Planned |

---

# Sprint 7 - Dashboard

## Objetivo

Apresentar indicadores do negócio.

| ID       | Item                | Tipo  | Prioridade  | Status    |
| -------- | ------------------- | ----- | ----------- | --------- |
| DASH-001 | Dashboard principal | Story | 🔥 Critical | ⚪ Planned |
| DASH-002 | KPIs                | Story | 🟠 High     | ⚪ Planned |
| DASH-003 | Gráficos            | Story | 🟠 High     | ⚪ Planned |

---

# Sprint 8 - Relatórios

## Objetivo

Gerar relatórios gerenciais.

| ID         | Item           | Tipo  | Prioridade | Status    |
| ---------- | -------------- | ----- | ---------- | --------- |
| REPORT-001 | Exportar PDF   | Story | 🟠 High    | ⚪ Planned |
| REPORT-002 | Exportar Excel | Story | 🟠 High    | ⚪ Planned |

---

# Sprint 9 - Qualidade

## Objetivo

Melhorar qualidade e estabilidade.

| ID       | Item                    | Tipo | Prioridade  | Status    |
| -------- | ----------------------- | ---- | ----------- | --------- |
| TEST-001 | Testes unitários        | Task | 🔥 Critical | ⚪ Planned |
| TEST-002 | Testes de integração    | Task | 🔥 Critical | ⚪ Planned |
| TEST-003 | Cobertura mínima de 80% | Task | 🟠 High     | ⚪ Planned |

---

# Sprint 10 - Deploy

## Objetivo

Publicar a aplicação.

| ID         | Item               | Tipo | Prioridade  | Status    |
| ---------- | ------------------ | ---- | ----------- | --------- |
| DEVOPS-001 | GitHub Actions     | Task | 🟠 High     | ⚪ Planned |
| DEVOPS-002 | Deploy Backend     | Task | 🔥 Critical | ⚪ Planned |
| DEVOPS-003 | Deploy Frontend    | Task | 🔥 Critical | ⚪ Planned |
| DEVOPS-004 | Configurar domínio | Task | 🟡 Medium   | ⚪ Planned |

---

# Versão MVP

A versão 1.0 será considerada concluída quando:

* Infraestrutura configurada.
* Autenticação implementada.
* Empresa cadastrada.
* Clientes funcionando.
* Produtos funcionando.
* Financeiro funcionando.
* Dashboard implementado.
* Testes essenciais criados.
* Aplicação publicada.

---

# Pós-MVP (Roadmap Futuro)

## Versão 1.1

* Favoritos
* Upload de documentos
* Notificações

## Versão 1.2

* Redis
* Cache
* Melhorias de performance

## Versão 2.0

* Open Finance
* PIX
* Integração bancária
* Multi-filiais
* Assinaturas
* Aplicativo mobile
