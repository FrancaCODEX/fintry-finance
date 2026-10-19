# Fintry

**Control today. Grow tomorrow.**

**Versão atual: 0.5.0** · [Changelog](docs/01-product/changelog.md) · [Roadmap](docs/01-product/roadmap.md)

Gestão financeira para pequenas empresas, desenvolvida por **Vinicius Azeredo França**. O Fintry organiza contas, receitas, despesas e o fluxo de caixa em uma interface em português.

![Dashboard do Fintry com dados fictícios](docs/screenshots/dashboard.webp)

## Rodar com Docker (recomendado)

Pré-requisito: Docker Desktop com Docker Compose. No Windows, abra o terminal na pasta do projeto com o Docker Desktop iniciado.

```bash
git clone https://github.com/FrancaCODEX/fintry-finance.git
cd fintry-finance
git switch codex/fintry-v0.5-comprovantes-temas
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

Abra **http://localhost:8080**. O perfil `demo` usa H2 persistido em `./data/` relativo à pasta de execução (`backend/data/` nos comandos acima), sem precisar instalar PostgreSQL. H2 é uma alternativa para avaliação local; PostgreSQL é o banco principal. Os dois ambientes têm bancos separados. Não há credencial padrão: crie seu próprio acesso.

## O que já funciona

- Cadastro de empresa e administrador; login e logout com sessão HTTP.
- Isolamento dos dados por empresa, derivado da sessão autenticada.
- Contas financeiras com saldo inicial e saldo calculado.
- Receitas e despesas com valor, categoria, conta e vencimento.
- Parcelamentos mensais e recorrências de valor fixo, com 2 a 120 ocorrências.
- Prévia de datas/valores, acompanhamento e cancelamento de pendências de séries.
- Lançamentos pendentes e realizados; baixa com data de pagamento.
- Edição de lançamentos pendentes, sem alterar saldos realizados.
- Cancelamento preservando o histórico; para corrigir um registro realizado, cancele e cadastre novamente.
- Clientes e fornecedores com busca, paginação, edição e inativação/reativação.
- Categorias de receitas/despesas com cadastro, edição e inativação.
- Vínculo opcional de lançamentos a clientes/fornecedores e categorias cadastradas.
- Transferência atômica entre duas contas da mesma empresa.
- Busca, filtro por tipo/status, período mensal e paginação de lançamentos.
- Dashboard com saldo atual, resultado de caixa, comparação com o mês anterior, despesas por categoria e gráfico de seis meses.
- Relatórios por período, conta, tipo, situação, categoria e cliente/fornecedor.
- Exportações CSV, Excel (.xlsx) e PDF com os filtros aplicados.
- Fluxo de caixa projetado por dia, com horizonte e conta selecionáveis e indicação de saldo negativo.
- Painel lateral com detalhes e ações de lançamento, sem sair da lista.
- Comprovantes PDF, PNG e JPEG: envio, download e remoção, isolados por empresa.
- Modo claro/escuro, alternância salva no navegador e preferência inicial do sistema.
- Bordas e foco dos campos reforçados nos dois temas.
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
  report/     # relatórios, exportações e projeção de caixa
  attachment/ # comprovantes validados e vinculados aos lançamentos
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

A CI executa ambos com JDK 21 e também constrói a imagem Docker. A suíte cobre autenticação, CSRF, isolamento entre empresas, valores inválidos, datas, filtros, baixa repetida, cancelamento, transferências, séries mensais, relatórios, projeções e arquivos exportados.

## Próximas entregas

A sequência está no [roadmap](docs/01-product/roadmap.md):

- **0.6.0:** recuperação de senha, usuários, permissões e histórico de alterações.
- **0.7.0:** produtos, estoque e vendas.

Depois da base: importações/conciliação, integrações bancárias, cobrança, planos/assinaturas, recorrências sem prazo final e organização avançada dos comprovantes.

Esta é uma primeira versão funcional para desenvolvimento e demonstração. Antes de oferecer o serviço a clientes, faltam recuperação de senha, limitação de tentativas de login, backups, observabilidade e validação operacional. Para publicar, configure HTTPS, `COOKIE_SECURE=true`, credenciais próprias e a infraestrutura de acesso; o Compose padrão expõe a aplicação apenas no computador local. Sessões são armazenadas no processo e expiram em 30 minutos de inatividade; reiniciar a aplicação exige novo login.

Contato: **vfranca.dev@gmail.com**

## Atualizar da primeira versão

Na pasta do projeto:

```bash
git fetch origin
git switch codex/fintry-v0.5-comprovantes-temas
git pull --ff-only origin codex/fintry-v0.5-comprovantes-temas
docker compose up --build -d
```

A atualização adiciona tabelas e vínculos por migração Flyway; não precisa recriar o banco nem remover o volume. Os lançamentos antigos mantêm a categoria em texto e podem continuar sendo usados. Não execute `docker compose down -v` para atualizar.

Os novos menus são **Clientes e fornecedores** e **Categorias**. Cadastre os registros e selecione-os no formulário de lançamento. Um contato pode ser cliente, fornecedor ou ambos. Receitas aceitam clientes; despesas aceitam fornecedores. Categorias podem ser de receitas, despesas ou ambas.

Inativar um cadastro preserva os lançamentos existentes. Um vínculo antigo inativo pode ser mantido ao editar sua pendência; novos vínculos exigem cadastros ativos. Nomes de contato/categoria ficam registrados no lançamento no momento em que ele é salvo, preservando o histórico em caso de renomeação. O tipo de um cadastro vinculado não pode ser alterado. CPF/CNPJ de contatos são opcionais; a validação nesta versão verifica formato e quantidade de dígitos, não consulta situação cadastral.

## Parcelamentos e recorrências — v0.3.0

No formulário **Novo lançamento**, escolha o formato:

- **Lançamento único:** mantém o fluxo existente.
- **Parcelar um valor total:** informe o total e a quantidade. R$ 100,00 em 3 parcelas gera R$ 33,34 + R$ 33,33 + R$ 33,33. Os centavos excedentes ficam nas primeiras parcelas.
- **Repetir valor mensalmente:** informe o valor de cada mês e a quantidade. R$ 250,00 por 4 repetições gera quatro lançamentos de R$ 250,00. Não há renovação automática nesta versão.

Confira a prévia antes de registrar. Vencimento em 31 de janeiro resulta em 28/29 de fevereiro e 31 de março. A opção de pagamento inicial vale somente para a primeira ocorrência; as demais ficam pendentes. As baixas continuam individuais em **Lançamentos**, selecionando o mês do vencimento.

O menu **Parcelas e recorrências** mostra o progresso e todos os vencimentos. **Cancelar pendências** cancela todas as ocorrências pendentes da série, inclusive vencidas, preservando as realizadas. Não há edição individual de ocorrências de série; para ajustar, cancele as pendências e crie uma nova série. Lançamentos únicos pendentes continuam editáveis.

A atualização é incremental e preserva contas, contatos, categorias e lançamentos. O JAR passa a ter nome estável: `backend/target/fintry.jar`. Para executar o pacote sem Docker: `java -jar backend/target/fintry.jar --spring.profiles.active=demo`.

## Relatórios e fluxo projetado — v0.4.0

No menu **Relatórios**, aplique um período de até 366 dias. A base **pagamento / recebimento** considera somente realizados pela data da baixa; **vencimento** permite consultar realizados, pendentes ou cancelados. Totais e despesas por categoria consideram todos os registros filtrados, independentemente da página. Transferências ficam fora do relatório; cancelados podem ser consultados, mas não compõem os totais.

Os botões **CSV**, **Excel** e **PDF** exportam até 5.000 registros com os filtros já aplicados. CSV contém os lançamentos; Excel e PDF também trazem empresa, período, filtros e totais. Valores do Excel são numéricos e textos não viram fórmulas. No PDF, textos longos são abreviados e caracteres fora da fonte ocidental são substituídos; CSV e Excel preservam o texto completo.

Em **Fluxo projetado**, escolha a conta e um horizonte de até 366 dias. O saldo atual é o ponto de partida; pendências entram pela data do vencimento, incluindo parcelas e recorrências já cadastradas. A projeção inclui hoje e o último dia do horizonte. Pendências vencidas são exibidas à parte e ficam excluídas por padrão; a opção de incluí-las as considera previstas para hoje. A tela mostra saldo final, menor saldo e primeira data com saldo negativo, usando os valores líquidos de cada dia. Nenhuma consulta ou exportação registra baixas ou altera o saldo realizado.

A 0.4.0 não modifica o schema do banco. Atualize a aplicação preservando seus dados e volumes.

## Comprovantes, detalhes e temas — v0.5.0

Clique em **Detalhes** na lista ou no dashboard; nos relatórios, clique na descrição do lançamento. O painel lateral mostra conta, categoria, contato, situação, datas, parcela/repetição e as ações permitidas para aquele registro. É possível editar pendências únicas, registrar baixa ou cancelar sem perder a posição na página. Feche pelo botão ou pela tecla Escape.

Em **Comprovantes**, envie um arquivo PDF, PNG ou JPEG por vez: até **5 MB por arquivo**, **10 arquivos por lançamento** e **20 megapixels por imagem**. O servidor verifica o conteúdo, não apenas o nome ou o tipo informado pelo navegador. PDFs protegidos por senha e arquivos corrompidos são recusados. A lista registra nome, tamanho, data e usuário que anexou. Baixar preserva os bytes originais; remover exige confirmação e exclui o arquivo, preservando o lançamento. Baixar ou anexar não muda o saldo. A baixa e o cancelamento preservam os comprovantes; anexos de uma parcela pertencem somente àquela ocorrência.

Os arquivos ficam no próprio banco de dados, junto dos metadados, sem diretório público de uploads nem volume adicional. O backup do banco deve incluir a nova tabela `entry_attachments`, pois ela contém também os arquivos. Na avaliação H2, eles permanecem no banco local em `./data/`, relativo à pasta de execução; no Compose, no volume PostgreSQL existente. O limite de 5 MB usa 5 × 1024 × 1024 bytes. O envio aceita uma requisição de até 6 MB, incluindo o envelope multipart.

O botão **Escuro / Claro** aparece no acesso, no topo da aplicação e no painel de detalhes. Sem escolha salva, o tema acompanha o sistema. Após alternar, a escolha é mantida naquele navegador, inclusive após recarregar ou sair da conta. Limpar a preferência `fintry-theme` no navegador restaura o acompanhamento do sistema. O tema é aplicado antes da folha de estilos; campos têm bordas mais visíveis, fundos próprios e indicação de foco em ambos os modos.

Migrações incrementais: PostgreSQL V6 e H2 V4. Atualize sem recriar o banco nem remover volumes.

## Política de versões

Toda entrega deve atualizar a versão em `backend/pom.xml`, o número acima e o changelog. Funcionalidades/melhorias incrementam MINOR (ex.: 0.3.0 → 0.4.0); correções isoladas incrementam PATCH (ex.: 0.3.0 → 0.3.1). A interface lê a versão do backend, sem manter um segundo número fixo. A CI executa `python3 scripts/check_release.py` para verificar a consistência.
