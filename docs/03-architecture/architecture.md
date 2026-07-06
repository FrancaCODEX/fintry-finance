# Fintry - Documento de Arquitetura

## Objetivo

Definir os padrões arquiteturais, convenções e decisões técnicas do projeto Fintry para garantir consistência, escalabilidade e facilidade de manutenção.

---

# Estilo Arquitetural

O Fintry será desenvolvido como um **Monólito Modular (Modular Monolith)**.

A aplicação será composta por módulos independentes de domínio, mantendo baixo acoplamento e alta coesão.

Cada módulo será responsável por sua própria lógica de negócio.

---

# Estrutura do Projeto

```
backend/

src/main/java/com/fintry

shared/

config/

security/

exception/

util/

auth/

company/

customer/

product/

finance/

dashboard/

report/
```

Cada módulo conterá sua própria estrutura interna.

Exemplo:

```
company/

controller/

service/

repository/

entity/

dto/

mapper/

validator/
```

---

# Padrão de Camadas

Cada módulo seguirá a seguinte organização:

Controller

↓

Service

↓

Repository

↓

Banco de Dados

Nenhum Controller poderá acessar Repository diretamente.

Toda regra de negócio deverá existir exclusivamente na camada Service.

---

# Banco de Dados

Banco relacional.

Tecnologia:

PostgreSQL

Versionamento:

Flyway

ORM:

Spring Data JPA

---

# API REST

Todas as APIs seguirão o padrão:

```
/api/v1/
```

Exemplos:

```
GET /api/v1/customers

POST /api/v1/products

PUT /api/v1/company

DELETE /api/v1/customers/{id}
```

---

# Padrão das Respostas

Sucesso:

```
{
    "data": {},
    "message": "Operação realizada com sucesso.",
    "timestamp": "...",
    "path": "/api/v1/..."
}
```

Erro:

```
{
    "status": 400,
    "error": "Validation Error",
    "message": "...",
    "timestamp": "...",
    "path": "/api/v1/..."
}
```

Todas as exceções serão tratadas globalmente.

---

# Segurança

Autenticação:

JWT

Autorização:

Spring Security

Perfis:

ADMIN

MANAGER

EMPLOYEE

As senhas serão criptografadas utilizando BCrypt.

---

# Convenções

Classes:

PascalCase

Métodos:

camelCase

Variáveis:

camelCase

Constantes:

UPPER_SNAKE_CASE

Endpoints:

kebab-case

Pacotes:

lowercase

---

# Validação

Todas as entradas serão validadas utilizando Bean Validation.

Exemplos:

@NotBlank

@Email

@Size

@Positive

Nenhuma validação deverá ficar no Controller.

---

# Mapeamento

Conversão entre Entity e DTO:

MapStruct

Nenhuma Entity será exposta diretamente pela API.

---

# Documentação

Toda API será documentada automaticamente utilizando OpenAPI/Swagger.

---

# Testes

Serão desenvolvidos:

* Testes unitários
* Testes de integração
* Testes de controllers
* Testes de services

Ferramentas:

JUnit 5

Mockito

Testcontainers (em fases posteriores)

---

# Logs

Utilizar SLF4J.

Nunca utilizar System.out.println.

Todos os erros deverão ser registrados.

---

# Versionamento

Fluxo baseado em Git Flow simplificado.

Branch principal:

main

Branch de desenvolvimento:

develop

Branches de funcionalidades:

feature/nome-da-feature

Correções:

fix/nome-da-correcao

---

# Commits

Seguir Conventional Commits.

Exemplos:

feat:

fix:

refactor:

docs:

test:

chore:

style:

---

# Dependências

Gerenciamento através do Maven.

---

# Padrões de Código

* Classes pequenas e coesas.
* Métodos curtos.
* Evitar duplicação de código.
* Aplicar princípios SOLID quando fizer sentido.
* Preferir composição à herança.
* Evitar lógica de negócio em Controllers.
* Evitar consultas complexas diretamente no Controller.

---

# Princípios do Projeto

1. Clareza antes de complexidade.
2. Código legível é prioridade.
3. Segurança desde o início.
4. Arquitetura preparada para crescimento.
5. Documentação faz parte do produto.
6. Todo código deve possuir um propósito claro.
