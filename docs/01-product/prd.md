# Fintry - Product Requirements Document (PRD)

## Informações Gerais

**Produto:** Fintry

**Versão:** 1.0 (MVP)

**Responsável:** Equipe Fintry

**Status:** Em desenvolvimento

---

# 1. Objetivo

O Fintry é uma plataforma SaaS de gestão financeira desenvolvida para pequenas e médias empresas.

Seu objetivo é simplificar o controle financeiro, reduzir o uso de planilhas e fornecer indicadores que auxiliem na tomada de decisões.

---

# 2. Problema

Muitas pequenas empresas controlam suas finanças utilizando planilhas ou ferramentas genéricas.

Isso gera:

* erros de lançamento;
* dificuldade para localizar informações;
* ausência de indicadores;
* falta de histórico;
* perda de produtividade.

---

# 3. Objetivos de Negócio

* reduzir o tempo gasto na gestão financeira;
* facilitar o acompanhamento do fluxo de caixa;
* organizar informações em um único sistema;
* oferecer relatórios simples e intuitivos.

---

# 4. Público-Alvo

* MEIs;
* pequenas empresas;
* lojas;
* prestadores de serviço;
* escritórios;
* empresas familiares.

---

# 5. Funcionalidades do MVP

## Autenticação

Prioridade: Alta

* Cadastro
* Login
* JWT
* Recuperação de senha

---

## Empresas

Prioridade: Alta

* Cadastro
* Edição
* Consulta

---

## Clientes

Prioridade: Alta

CRUD completo.

---

## Produtos

Prioridade: Alta

CRUD completo.

---

## Contas Financeiras

Prioridade: Alta

CRUD completo.

---

## Receitas

Prioridade: Alta

Cadastro, edição, exclusão lógica e filtros.

---

## Despesas

Prioridade: Alta

Cadastro, edição, exclusão lógica e filtros.

---

## Dashboard

Prioridade: Média

Exibir:

* saldo atual;
* receitas do mês;
* despesas do mês;
* fluxo de caixa;
* gráfico mensal.

---

## Relatórios

Prioridade: Média

Exportação em PDF e Excel.

---

# 6. Funcionalidades Futuras

* PIX;
* Open Finance;
* boletos;
* emissão de notas fiscais;
* integração bancária;
* notificações;
* aplicativo mobile;
* multiempresa avançado;
* assinatura de planos.

---

# 7. Critérios de Sucesso

O MVP será considerado concluído quando:

* todas as funcionalidades prioritárias estiverem implementadas;
* autenticação estiver funcionando;
* APIs documentadas;
* aplicação publicada em ambiente de demonstração;
* testes principais implementados;
* documentação atualizada.

---

# 8. Requisitos Não Funcionais

* API REST.
* Tempo médio de resposta inferior a 500 ms em operações comuns.
* Banco PostgreSQL.
* Docker para execução local.
* Autenticação JWT.
* Código documentado.
* Cobertura de testes para regras de negócio críticas.

---

# 9. Métricas

* número de usuários cadastrados;
* empresas cadastradas;
* movimentações financeiras registradas;
* tempo médio de resposta da API;
* cobertura de testes;
* quantidade de deploys realizados.

---

# 10. Restrições

* Não haverá aplicativo mobile no MVP.
* Não haverá integração bancária na primeira versão.
* Não haverá emissão de nota fiscal no MVP.
