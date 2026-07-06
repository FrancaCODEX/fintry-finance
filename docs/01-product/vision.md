# Fintry - Documento de Visão do Produto

## Visão

O Fintry é uma plataforma SaaS para gestão financeira de pequenas e médias empresas. Seu objetivo é centralizar o controle financeiro, facilitar a tomada de decisões e oferecer uma experiência simples, moderna e segura.

O sistema permitirá que empresas acompanhem receitas, despesas, clientes, produtos, estoque e indicadores financeiros em um único lugar.

---

# Problema

Grande parte das pequenas empresas ainda utiliza planilhas para controlar o financeiro.

Isso gera problemas como:

* dificuldade em acompanhar o fluxo de caixa;
* falta de organização;
* perda de informações;
* dificuldade para gerar relatórios;
* ausência de indicadores para tomada de decisão.

O Fintry busca resolver esses problemas oferecendo uma plataforma intuitiva e acessível.

---

# Público-alvo

* Pequenas empresas
* Microempreendedores (MEI)
* Lojas
* Prestadores de serviço
* Escritórios
* Empresas familiares

---

# Objetivos do Produto

* Centralizar informações financeiras.
* Automatizar processos simples.
* Facilitar o controle de receitas e despesas.
* Permitir acompanhamento em tempo real do caixa.
* Gerar relatórios gerenciais.
* Fornecer dashboards com indicadores.

---

# Funcionalidades Principais

## Autenticação

* Cadastro
* Login
* Recuperação de senha
* Alteração de senha
* JWT
* Controle de permissões

---

## Empresas

* Cadastro da empresa
* Dados fiscais
* Endereço
* Contatos
* Logo

---

## Usuários

* Administração de usuários
* Papéis (Admin, Manager e Employee)
* Convites

---

## Clientes

* Cadastro
* Histórico
* Contatos

---

## Produtos

* Cadastro
* Categorias
* Controle de estoque
* SKU

---

## Financeiro

* Receitas
* Despesas
* Categorias
* Contas
* Fluxo de caixa

---

## Dashboard

* Receita mensal
* Despesa mensal
* Saldo
* Gráficos
* Indicadores

---

## Relatórios

* PDF
* Excel
* Exportação

---

# Diferenciais

* Interface moderna
* API REST documentada
* Segurança com JWT
* Arquitetura escalável
* Docker
* Deploy automatizado
* Testes automatizados
* Documentação completa

---

# Tecnologias

## Backend

* Java 21
* Spring Boot
* Spring Security
* Spring Data JPA
* PostgreSQL
* Flyway
* MapStruct
* Bean Validation
* Lombok
* OpenAPI

## Frontend

* React
* TypeScript
* Vite
* Tailwind CSS

## Infraestrutura

* Docker
* Docker Compose
* GitHub Actions

---

# Escopo da Primeira Versão (MVP)

O MVP deverá conter:

* Cadastro e login de usuários
* Cadastro de empresa
* Cadastro de clientes
* Cadastro de produtos
* Controle de receitas e despesas
* Dashboard inicial
* API documentada
* Deploy funcional

---

# Fora do Escopo Inicial

* Integração bancária
* Emissão de notas fiscais
* Aplicativo mobile
* Multi-idiomas
* Inteligência Artificial
* Integração com WhatsApp

Esses recursos poderão ser adicionados em versões futuras.
