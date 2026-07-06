Sprint 0.1 - Modelagem do Banco de Dados
Objetivo

Antes de criar qualquer entidade Java, vamos definir o banco.

Regra de ouro: o banco deve refletir o negócio, não o framework.

Módulos do MVP

Vamos dividir o sistema em módulos.

Auth

Company

User

Customer

Product

Finance

Dashboard

Cada módulo terá suas próprias entidades.

Modelo inicial
Auth
users
id (UUID)

company_id

first_name

last_name

email

password

role

active

last_login

created_at

updated_at

deleted_at
Company
companies
id (UUID)

legal_name

trade_name

cnpj

email

phone

logo_url

status

created_at

updated_at

deleted_at
Customer
customers
id (UUID)

company_id

name

document

email

phone

address

city

state

zip_code

notes

active

created_at

updated_at

deleted_at
Product
product_categories
id

company_id

name

description
products
id

company_id

category_id

sku

name

description

cost_price

sale_price

stock_quantity

minimum_stock

active

created_at

updated_at

deleted_at
Finance
financial_accounts
id

company_id

name

type

initial_balance

current_balance

created_at

updated_at
financial_categories
id

company_id

name

type

created_at

type

INCOME

EXPENSE
transactions

Essa é a tabela mais importante.

id

company_id

account_id

category_id

customer_id

description

type

status

amount

due_date

payment_date

created_at

updated_at

deleted_at

Enums:

TransactionType

INCOME

EXPENSE

TRANSFER

TransactionStatus

PENDING

PAID

CANCELLED
Relacionamentos
Company

│

├── Users

├── Customers

├── Products

├── Categories

├── Financial Accounts

├── Financial Categories

└── Transactions

Ou seja,

Company é o "dono" de praticamente tudo.

Isso facilita o multi-tenant.

Padrões que quero seguir
IDs

Nada de Long.

Vamos usar

UUID

Motivos:

mais seguro
melhor para APIs
difícil de adivinhar
muito usado em SaaS
Soft Delete

Todas as entidades principais terão

deleted_at

Nunca apagar registro importante.

Auditoria

Todas terão

created_at

updated_at

created_by

updated_by
Enum

Nada de String solta.

Sempre Enum.

Exemplo:

Role

ADMIN

MANAGER

EMPLOYEE
Agora vem uma decisão importante
Package by Feature

Não vamos usar

controller

service

repository

entity

Isso é por camada.

Vamos usar

customer

controller

service

repository

entity

Cada módulo será independente.

É um padrão muito usado hoje e deixa o projeto muito mais organizado.