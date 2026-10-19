# Fintry

**Control today. Grow tomorrow.**

Gestão financeira para pequenas empresas, desenvolvida por **Vinicius Azeredo França**. O Fintry organiza contas, receitas, despesas e o fluxo de caixa em uma interface em português.

![Dashboard do Fintry com dados fictícios](docs/screenshots/dashboard.webp)

## Rodar com Docker (recomendado)

Pré-requisito: Docker Desktop com Docker Compose. No Windows, abra o terminal na pasta do projeto com o Docker Desktop iniciado.

```bash
git clone https://github.com/FrancaCODEX/fintry-finance.git
cd fintry-finance
git switch codex/fintry-cadastros-e-edicao
docker compose up --build -d
```

Abra **http://localhost:8080**, clique em **Criar conta** e preencha seu nome, empresa, CNPJ com 14 dígitos, e-mail e uma senha com pelo menos 10 caracteres. Para uma avaliação, use dados fictícios.

Na tela inicial, escolha **Explorar com dados de exemplo** para cadastrar duas contas e movimentações fictícias dos últimos seis meses. Essa ação é opcional e só fica disponível antes de cadastrar contas. Também é possível começar do zero.

O primeiro build baixa as dependências Java. Para acompanhar a inicialização:

```bash
docker compose logs -f app
```

Para parar, preservando os dados:

```bash
docker compose down
```

Os dados do PostgreSQL ficam no volume `fintry-data`. Não use `docker compose down -v` se quiser preservá-los. Para mudar a porta, copie `.env.example` para `.env` e ajuste `APP_PORT` (por exemplo, `8081`). Se já houver um banco criado por uma versão anterior do projeto, este Compose usa seu próprio volume; os dados anteriores não são importados automaticamente.

## Rodar sem Docker

Pré-requisito: **JDK 21**. O Maven Wrapper já acompanha o projeto.

Linux / WSL / macOS:

```bash
cd backend
sh mvnw spring-boot:run -Dspring-boot.run.profiles=demo
```

Windows / PowerShell:

```powershell
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=demo"
```

Abra **http://localhost:8080**. O perfil `demo` usa H2 persistido em `backend/data/`, sem precisar instalar PostgreSQL. H2 é uma alternativa para avaliação local; PostgreSQL é o banco principal. Os dois ambientes têm bancos separados. Não há credencial padrão: crie seu próprio acesso.

## O que já funciona

- Cadastro de empresa e administrador; login e logout com sessão HTTP.
- Isolamento dos dados por empresa, derivado da sessão autenticada.
- Contas financeiras com saldo inicial e saldo calculado.
- Receitas e despesas com valor, categoria, conta e vencimento.
- Lançamentos pendentes e realizados; baixa com data de pagamento.
- Edição de lançamentos pendentes, sem alterar saldos realizados.
- Cancelamento preservando o histórico; para corrigir um registro realizado, cancele e cadastre novamente.
- Clientes e fornecedores com busca, paginação, edição e inativação/reativação.
- Categorias de receitas/despesas com cadastro, edição e inativação.
- Vínculo opcional de lançamentos a clientes/fornecedores e categorias cadastradas.
- Transferência atômica entre duas contas da mesma empresa.
- Busca, filtro por tipo/status, período mensal e paginação de lançamentos.
- Dashboard: saldo atual, receitas/despesas realizadas, resultado, pendências e gráfico de seis meses.
- Layout responsivo e dados de exemplo opcionais.
- Migrações Flyway e testes de integração HTTP.

**Como os números são calculados:** saldo inicial + receitas realizadas − despesas realizadas. Pendências usam o vencimento; indicadores realizados usam a data de pagamento. O saldo disponível é o saldo atual de todas as contas, independentemente do mês selecionado. Transferências afetam cada conta, mas não entram nas receitas/despesas e no resultado. Valores são armazenados com `BigDecimal` e duas casas decimais. A baixa repetida do mesmo lançamento não duplica o movimento.

## Stack e estrutura

Java 21 · Spring Boot 4.1 · Spring Security · JPA/Hibernate · PostgreSQL · Flyway · Docker.

A interface inicial usa HTML, CSS e JavaScript servidos pelo próprio Spring Boot. Assim, o primeiro uso exige apenas uma aplicação, sem instalação de Node ou processo separado para o frontend. React/TypeScript permanecem como uma possibilidade de evolução, não como funcionalidade implementada.

```text
backend/src/main/java/com/fintry/
  auth/       # cadastro, sessão e empresa autenticada
  company/    # dados da própria empresa
  finance/    # contas, lançamentos, transferências e indicadores
  directory/  # clientes, fornecedores e categorias financeiras
  shared/     # configuração, entidades base e erros
backend/src/main/resources/
  static/           # interface
  db/migration/     # PostgreSQL; mantém V1 e V2 existentes
  db/demo/          # schema equivalente para H2
```

## Testes

```bash
cd backend
sh mvnw verify
```

Por padrão, os testes usam H2. Para repetir a suíte contra um PostgreSQL local dedicado, crie `fintry_test` (usuário `fintry`, senha `fintry-test`) e execute:

```bash
sh mvnw -Dspring.profiles.active=postgres-test verify
```

A CI executa ambos com JDK 21 e também constrói a imagem Docker. A suíte cobre autenticação, CSRF, isolamento entre empresas, valores inválidos, datas, filtros, baixa repetida, cancelamento e transferências.

## Próximas entregas

- Recuperação de senha, convite de usuários e papéis de acesso.
- Recorrências e parcelamento de lançamentos.
- Relatórios e exportações.
- Produtos e estoque, após consolidar o financeiro.
- Assinaturas e integrações bancárias, após validar o produto.

Esta é uma primeira versão funcional para desenvolvimento e demonstração. Antes de oferecer o serviço a clientes, faltam recuperação de senha, limitação de tentativas de login, backups, observabilidade e validação operacional. Para publicar, configure HTTPS, `COOKIE_SECURE=true`, credenciais próprias e a infraestrutura de acesso; o Compose padrão expõe a aplicação apenas no computador local. Sessões são armazenadas no processo e expiram em 30 minutos de inatividade; reiniciar a aplicação exige novo login.

Contato: **vfranca.dev@gmail.com**

## Atualizar da primeira versão

Na pasta do projeto:

```bash
git fetch origin
git switch codex/fintry-cadastros-e-edicao
git pull --ff-only origin codex/fintry-cadastros-e-edicao
docker compose up --build -d
```

A atualização adiciona tabelas e vínculos por migração Flyway; não precisa recriar o banco nem remover o volume. Os lançamentos antigos mantêm a categoria em texto e podem continuar sendo usados. Não execute `docker compose down -v` para atualizar.

Os novos menus são **Clientes e fornecedores** e **Categorias**. Cadastre os registros e selecione-os no formulário de lançamento. Um contato pode ser cliente, fornecedor ou ambos. Receitas aceitam clientes; despesas aceitam fornecedores. Categorias podem ser de receitas, despesas ou ambas.

Inativar um cadastro preserva os lançamentos existentes. Um vínculo antigo inativo pode ser mantido ao editar sua pendência; novos vínculos exigem cadastros ativos. Nomes de contato/categoria ficam registrados no lançamento no momento em que ele é salvo, preservando o histórico em caso de renomeação. O tipo de um cadastro vinculado não pode ser alterado. CPF/CNPJ de contatos são opcionais; a validação nesta versão verifica formato e quantidade de dígitos, não consulta situação cadastral.
