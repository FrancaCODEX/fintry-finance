# Fintry - Documento de Domínio (Business Domain)

## Objetivo

Este documento define as principais entidades do sistema, seus relacionamentos e responsabilidades.

O objetivo é garantir que todas as funcionalidades futuras sejam desenvolvidas sobre um modelo de negócio consistente.

---

# Visão Geral

O Fintry é um sistema SaaS multiempresa.

Cada empresa possui seus próprios usuários, clientes, produtos e movimentações financeiras.

Os dados de uma empresa nunca devem ser acessíveis por outra empresa.

---

# Entidades Principais

## Company

Representa uma empresa cadastrada na plataforma.

### Responsabilidades

* armazenar dados cadastrais;
* representar o proprietário dos dados;
* servir como limite de isolamento entre empresas (multi-tenant lógico).

### Campos

* id
* legalName
* tradeName
* document (CNPJ)
* email
* phone
* logoUrl
* status
* createdAt
* updatedAt

---

## User

Representa uma pessoa que utiliza o sistema.

Todo usuário pertence obrigatoriamente a uma empresa.

### Campos

* id
* firstName
* lastName
* email
* password
* role
* active
* lastLogin
* companyId
* createdAt
* updatedAt

---

## Customer

Representa um cliente da empresa.

### Campos

* id
* companyId
* name
* document
* email
* phone
* address
* observations
* active
* createdAt
* updatedAt

---

## Product

Representa um produto ou serviço vendido pela empresa.

### Campos

* id
* companyId
* sku
* name
* description
* categoryId
* salePrice
* costPrice
* quantityInStock
* active
* createdAt
* updatedAt

---

## Category

Categoria utilizada para organizar produtos.

### Campos

* id
* companyId
* name
* description

---

## FinancialCategory

Categoria financeira.

Exemplos:

* Alimentação
* Marketing
* Transporte
* Salários
* Impostos

---

## FinancialAccount

Representa uma conta financeira.

Exemplos:

* Caixa
* Nubank
* Banco do Brasil
* Itaú
* Carteira

Campos:

* id
* companyId
* name
* type
* initialBalance
* currentBalance

---

## Transaction

Representa qualquer movimentação financeira.

Pode ser:

* Receita
* Despesa
* Transferência

Campos

* id
* companyId
* accountId
* categoryId
* customerId
* description
* type
* amount
* dueDate
* paymentDate
* status
* createdAt

---

## Dashboard

Não possui tabela própria.

Os dados serão calculados a partir das movimentações financeiras.

Indicadores:

* Receita Total
* Despesa Total
* Lucro
* Fluxo de Caixa
* Produtos Mais Vendidos
* Clientes Mais Ativos

---

# Relacionamentos

Company

↓

Users

↓

Customers

↓

Transactions

↓

Financial Accounts

↓

Financial Categories

↓

Products

↓

Categories

---

# Papéis de Usuário

## ADMIN

Possui acesso total ao sistema.

Pode:

* gerenciar usuários;
* alterar configurações;
* cadastrar empresas;
* visualizar relatórios.

---

## MANAGER

Pode operar praticamente todo o sistema.

Não pode alterar configurações administrativas.

---

## EMPLOYEE

Possui acesso restrito.

Pode:

* cadastrar clientes;
* cadastrar produtos;
* lançar movimentações.

Não pode excluir informações críticas.

---

# Regras de Negócio

## Empresas

Uma empresa nunca poderá visualizar dados de outra empresa.

---

## Usuários

Todo usuário pertence a exatamente uma empresa.

---

## Produtos

Não será permitido estoque negativo.

---

## Movimentações

Uma movimentação nunca poderá possuir valor igual ou menor que zero.

---

## Receitas

Devem atualizar automaticamente o saldo da conta financeira quando forem marcadas como pagas.

---

## Despesas

Devem reduzir automaticamente o saldo da conta financeira quando forem pagas.

---

## Exclusões

Nenhuma entidade principal será removida fisicamente do banco.

Será utilizado Soft Delete sempre que possível.

---

# Auditoria

As principais entidades deverão possuir:

* createdAt
* updatedAt
* createdBy
* updatedBy

Preparando o sistema para auditoria futura.

---

# Escalabilidade

O modelo foi projetado para permitir futuras funcionalidades como:

* múltiplas filiais;
* múltiplas moedas;
* integração bancária;
* emissão de boletos;
* PIX;
* Open Finance;
* emissão de notas fiscais;
* assinatura de planos;
* aplicativo mobile.

---

# Filosofia do Domínio

O domínio deve permanecer independente de frameworks.

As regras de negócio pertencem ao domínio, e não à tecnologia utilizada para implementá-las.

Toda nova funcionalidade deverá respeitar os conceitos definidos neste documento.
