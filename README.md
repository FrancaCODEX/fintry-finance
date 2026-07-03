# 🚀 Finry

> **Control today. Grow tomorrow.**

Fintry é uma plataforma SaaS de gestão financeira desenvolvida para pequenas e médias empresas. O objetivo é simplificar o controle financeiro, centralizar informações e fornecer indicadores para auxiliar na tomada de decisões.

O projeto está sendo desenvolvido seguindo boas práticas de Engenharia de Software, Arquitetura de Sistemas e Clean Code, servindo tanto como um produto real quanto como um portfólio profissional.

---

# ✨ Funcionalidades

### Autenticação

* Login
* Cadastro
* Refresh Token
* Recuperação de senha
* Controle de permissões

### Gestão da Empresa

* Cadastro da empresa
* Atualização de dados
* Upload de logo

### Clientes

* Cadastro
* Pesquisa
* Atualização
* Exclusão lógica

### Produtos

* Cadastro
* Categorias
* Controle de estoque
* SKU

### Financeiro

* Receitas
* Despesas
* Transferências
* Contas financeiras
* Categorias financeiras
* Fluxo de caixa

### Dashboard

* Saldo atual
* Receitas
* Despesas
* KPIs
* Gráficos

### Relatórios

* PDF
* Excel

---

# 🏗 Arquitetura

O Fintry utiliza uma arquitetura **Modular Monolith**, organizada por domínio de negócio.

```text
auth/

company/

customer/

product/

finance/

dashboard/

report/

shared/
```

Cada módulo possui sua própria estrutura de:

* Controller
* Service
* Repository
* DTO
* Mapper
* Entity
* Validator

---

# 🛠 Tecnologias

## Backend

* Java 21
* Spring Boot 3
* Spring Security
* Spring Data JPA
* PostgreSQL
* Flyway
* MapStruct
* Lombok
* Bean Validation
* OpenAPI / Swagger

## Frontend

* React
* TypeScript
* Vite
* Tailwind CSS
* Axios

## DevOps

* Docker
* Docker Compose
* GitHub Actions

---

# 📂 Estrutura do Projeto

```text
fintry/

backend/

frontend/

docs/

docker/

.github/
```

---

# 📖 Documentação

Toda a documentação do projeto está disponível na pasta `docs`.

## Produto

* Vision
* PRD
* User Stories
* Roadmap

## Negócio

* Modelo de Domínio
* Regras de Negócio

## Arquitetura

* Arquitetura
* ADRs
* Segurança
* Convenções

## Banco de Dados

* DER
* Modelagem
* Migrações

## API

* Endpoints
* Autenticação
* Tratamento de erros

## Testes

* Estratégia
* Testes unitários
* Testes de integração

---

# 🚦 Roadmap

## Sprint 0

* Planejamento
* Documentação
* Arquitetura
* Banco de Dados

## Sprint 1

* Infraestrutura
* Docker
* PostgreSQL
* Flyway
* Swagger

## Sprint 2

* Autenticação

## Sprint 3

* Empresa

## Sprint 4

* Clientes

## Sprint 5

* Produtos

## Sprint 6

* Financeiro

## Sprint 7

* Dashboard

## Sprint 8

* Relatórios

---

# 🚀 Como executar

## Pré-requisitos

* Java 21
* Maven
* Docker
* Docker Compose
* Node.js (LTS)

## Backend

```bash
cd backend

./mvnw spring-boot:run
```

## Frontend

```bash
cd frontend

npm install

npm run dev
```

---

# 🧪 Testes

```bash
./mvnw test
```

---

# 📌 Convenções

* Conventional Commits
* Git Flow simplificado
* Package by Feature
* DTO Pattern
* REST API
* Clean Code
* SOLID

---

# 📈 Status do Projeto

🚧 Em desenvolvimento.

Atualmente o projeto encontra-se na fase de planejamento e estruturação da arquitetura.

---

# 🎯 Objetivos

* Desenvolver uma aplicação escalável.
* Aplicar boas práticas de arquitetura.
* Demonstrar conhecimentos em Java, Spring Boot e React.
* Simular um ambiente de desenvolvimento profissional.

---

# 🤝 Contribuição

Contribuições são bem-vindas.

Caso encontre algum problema ou tenha sugestões, abra uma Issue ou envie um Pull Request.

---

# 📄 Licença

Este projeto está licenciado sob a licença MIT.
