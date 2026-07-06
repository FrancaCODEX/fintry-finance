# Fintry - User Stories

## Introdução

Este documento descreve as funcionalidades do Fintry sob a perspectiva do usuário.

Cada história representa um requisito funcional que poderá ser implementado durante o desenvolvimento.

---

# Módulo: Autenticação

## US-001 - Cadastro de Usuário

**Como** um novo usuário

**Quero** criar uma conta

**Para** acessar a plataforma.

### Critérios de Aceitação

* Nome é obrigatório.
* Sobrenome é obrigatório.
* E-mail deve ser único.
* Senha deve possuir no mínimo 8 caracteres.
* Senha deve ser armazenada criptografada.
* O usuário deverá estar vinculado a uma empresa.
* O usuário receberá o perfil `ADMIN` no primeiro cadastro da empresa.

---

## US-002 - Login

**Como** usuário cadastrado

**Quero** realizar login

**Para** acessar minhas informações.

### Critérios de Aceitação

* Login utilizando e-mail e senha.
* Gerar Access Token JWT.
* Gerar Refresh Token.
* Registrar data do último login.
* Bloquear acesso com credenciais inválidas.

---

## US-003 - Logout

**Como** usuário autenticado

**Quero** encerrar minha sessão

**Para** impedir novos acessos utilizando meu token.

---

## US-004 - Recuperação de Senha

**Como** usuário

**Quero** recuperar minha senha

**Para** voltar a acessar minha conta.

---

# Módulo: Empresa

## US-005 - Cadastro da Empresa

**Como** administrador

**Quero** cadastrar minha empresa

**Para** utilizar o sistema.

### Critérios

* Razão Social obrigatória.
* Nome Fantasia obrigatório.
* CNPJ obrigatório.
* E-mail obrigatório.
* Telefone obrigatório.

---

## US-006 - Atualização da Empresa

**Como** administrador

**Quero** atualizar os dados da empresa

**Para** manter as informações corretas.

---

# Módulo: Usuários

## US-007 - Convidar Usuário

**Como** administrador

**Quero** adicionar novos usuários

**Para** que trabalhem comigo.

---

## US-008 - Alterar Perfil

**Como** administrador

**Quero** alterar permissões

**Para** controlar o acesso ao sistema.

---

## US-009 - Desativar Usuário

**Como** administrador

**Quero** desativar um usuário

**Para** impedir seu acesso.

---

# Módulo: Clientes

## US-010 - Cadastrar Cliente

**Como** usuário

**Quero** cadastrar um cliente

**Para** registrar meus compradores.

### Critérios

* Nome obrigatório.
* Cliente pertence à empresa logada.
* Documento opcional.
* Telefone opcional.
* E-mail opcional.

---

## US-011 - Editar Cliente

**Como** usuário

**Quero** editar um cliente

**Para** manter seus dados atualizados.

---

## US-012 - Excluir Cliente

**Como** usuário

**Quero** excluir um cliente

**Para** remover registros desnecessários.

Critério:

* Exclusão lógica (Soft Delete).

---

## US-013 - Pesquisar Cliente

**Como** usuário

**Quero** localizar clientes

**Para** encontrá-los rapidamente.

---

# Módulo: Produtos

## US-014 - Cadastrar Produto

## US-015 - Editar Produto

## US-016 - Excluir Produto

## US-017 - Listar Produtos

## US-018 - Controlar Estoque

---

# Módulo: Financeiro

## US-019 - Criar Conta Financeira

## US-020 - Editar Conta Financeira

## US-021 - Registrar Receita

## US-022 - Registrar Despesa

## US-023 - Registrar Transferência

## US-024 - Marcar Transação como Paga

## US-025 - Cancelar Transação

## US-026 - Filtrar Transações

---

# Módulo: Dashboard

## US-027 - Visualizar Dashboard

Indicadores:

* Saldo atual
* Receitas do mês
* Despesas do mês
* Fluxo de caixa
* Últimas movimentações

---

# Módulo: Relatórios

## US-028 - Exportar PDF

## US-029 - Exportar Excel

---

# Módulo: Segurança

## US-030 - Alterar Senha

## US-031 - Renovar Token

## US-032 - Registrar Logs de Auditoria

---

# Definição de Pronto (Definition of Done)

Uma User Story será considerada concluída quando:

* Regras de negócio implementadas.
* Testes unitários criados.
* Testes de integração executados.
* Endpoint documentado no Swagger.
* Código revisado.
* Documentação atualizada.
* Merge realizado para a branch `develop`.
