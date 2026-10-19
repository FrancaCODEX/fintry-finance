# Changelog do Fintry

As versões seguem `MAJOR.MINOR.PATCH`. Funcionalidades e melhorias recebem versão menor; correções isoladas recebem versão de correção. Antes de 1.0, mudanças incompatíveis também incrementam a versão menor. A versão da aplicação é definida em `backend/pom.xml`.

## [0.5.0] - 2026-10-19

### Funcionalidades

- Painel lateral de detalhes com conta, categoria, contato, situação, vencimento, baixa e identificação de parcela/repetição.
- Acesso aos detalhes na lista, no dashboard e nos relatórios, com edição de pendências, baixa e cancelamento conforme as regras existentes.
- Comprovantes PDF, PNG e JPEG vinculados a cada lançamento, com envio, download e remoção confirmada.
- Metadados de nome, tamanho, data e usuário que enviou; arquivos e vínculos isolados por empresa e preservados após baixa/cancelamento.
- Modo escuro com alternância, preferência salva no navegador e tema inicial baseado no sistema.

### Melhorias

- Bordas e indicação de foco mais visíveis em campos, seletores, datas e áreas de texto nos modos claro e escuro.
- Superfícies, tabelas, cartões, gráficos, diálogos, estados e formulários adaptados aos temas.
- Tema aplicado no carregamento inicial, antes da folha de estilos, e atualizado entre abas do navegador.
- Painel responsivo com foco contido pelo diálogo nativo, fechamento por Escape e atualização após ações financeiras.
- Comprovantes armazenados no banco junto dos metadados, incluídos no mesmo backup, sem uploads públicos ou novo volume.

### Correções

- Campos sobre fundo claro deixam de depender de bordas de contraste muito baixo para sua identificação.
- Requisições de arquivo usam multipart com CSRF, sem cabeçalho JSON indevido, e exibem mensagens de tamanho/formato.
- Validação do conteúdo real dos arquivos impede anexos disfarçados com extensão permitida.
- Limite de 10 arquivos é verificado sob bloqueio do lançamento, inclusive para envios concorrentes.

### Atualização

- PostgreSQL V6 e H2 V4 adicionam comprovantes e vínculo composto de empresa/lançamento; migrações anteriores preservadas.
- Até 10 arquivos por lançamento, 5 MB por arquivo e 20 megapixels por imagem. PDF sem senha; não há prévia inline dos arquivos.
- Remover um comprovante exclui o arquivo, sem excluir ou alterar o lançamento. Não há novo histórico geral de auditoria nesta etapa.
- Preserve o banco e seus volumes; os backups passam a incluir também o conteúdo dos comprovantes.

## [0.4.0] - 2026-10-19

### Funcionalidades

- Relatórios por período (até 366 dias), conta, tipo, situação, categoria e cliente/fornecedor, com paginação e totais de todos os registros filtrados.
- Escolha entre data de pagamento/recebimento e data de vencimento, separando realizados, pendentes e cancelados.
- Exportações CSV, Excel nativo (.xlsx) e PDF paginado, com os filtros aplicados e até 5.000 lançamentos por arquivo.
- Fluxo de caixa projetado por dia, conta e horizonte, partindo do saldo atual e considerando pendências e séries já cadastradas.
- Opção de incluir vencidas como previstas para hoje; indicação do menor saldo estimado e da primeira data com saldo negativo.

### Melhorias

- Dashboard com comparação dos indicadores com o mês anterior e distribuição de despesas realizadas por categoria.
- Novas telas responsivas com filtros, gráfico de saldo acumulado e atalhos entre relatórios e projeção.
- Navegação no celular posiciona o menu ativo na área visível.
- Exportações usam consultas isoladas por empresa; CSV neutraliza fórmulas e Excel armazena textos como strings e valores como números.
- Cobertura de totais além da primeira página, projeção, isolamento e estrutura dos arquivos exportados.

### Correções

- Indicador identificado como resultado de caixa, com explicação para evitar sua interpretação como lucro contábil.
- Limites explícitos de período e volume de exportação, com orientação para refinar os filtros.
- Valores monetários nos cartões ajustam a fonte à largura disponível, evitando quebras de linha em saldos grandes ou negativos.
- Valores monetários no PDF preservados integralmente, ajustando o tamanho da fonte quando necessário.

### Atualização

- Nenhuma nova migração de banco nesta versão; preserve dados e volumes existentes.
- Consultas e exportações não alteram lançamentos nem registram pagamentos. Transferências não compõem receitas/despesas; cancelados não compõem totais.
- Projeções representam valores líquidos diários, sem garantia de recebimento/pagamento nem previsão de horários de movimentação.

## [0.3.0] - 2026-10-19

### Funcionalidades

- Parcelamento mensal de um valor total em 2 a 120 parcelas, com distribuição exata dos centavos.
- Recorrências mensais de valor fixo com quantidade definida de repetições, de 2 a 120, sem renovação automática.
- Tela de acompanhamento de séries com ocorrências realizadas, pendentes e canceladas, e consulta de todos os vencimentos.
- Cancelamento das pendências de uma série preservando ocorrências já pagas ou recebidas.
- Possibilidade de registrar apenas a primeira ocorrência como realizada na criação.

### Melhorias

- Formulário com escolha entre lançamento único, parcelamento e recorrência, campos adaptados ao formato e prévia de datas/valores.
- Identificação de parcela/repetição nas listas de lançamentos.
- Vencimentos ancorados na data original, ajustando meses curtos e anos bissextos sem deslocar os meses seguintes.
- Criação atômica e proteção contra repetição da mesma solicitação, inclusive concorrente.
- Versão visível na interface, fornecida pela configuração gerada a partir do Maven.
- Regras de atualização e verificação automática de consistência entre versão, README e changelog.

### Correções

- Alinhada a versão técnica do projeto com a versão funcional; o Maven ainda declarava 0.0.1-SNAPSHOT.
- Nome do JAR estabilizado como fintry.jar; Docker e artefatos da CI deixam de depender do número da versão.

### Atualização

- PostgreSQL V5 e H2 V3 adicionam séries e vínculos sem alterar as migrações anteriores.
- Lançamentos existentes permanecem disponíveis. Não é necessário remover o banco ou o volume Docker.
- Ocorrências de série não podem ser editadas individualmente nesta versão; é possível dar baixa, cancelar uma ocorrência ou cancelar as pendências e criar outra série.

## [0.2.0] - 2026-10-19

### Funcionalidades e melhorias

- Clientes/fornecedores com busca, paginação, edição e inativação/reativação.
- Categorias com classificação por receita/despesa e unicidade por empresa.
- Vínculos opcionais nos lançamentos, com validação de tipo e empresa.
- Edição de pendências sob bloqueio, preservando os saldos e os registros realizados.
- Migrações incrementais PostgreSQL V4 e H2 V2 preservam dados existentes.
- Nomes registrados nos lançamentos mantêm o histórico após inativar ou renomear cadastros.

## [0.1.0] - 2026-10-19

### Funcionalidades e melhorias

- Cadastro, autenticação com sessão e CSRF, isolamento por empresa.
- Contas financeiras, receitas, despesas, baixa e cancelamento com histórico.
- Transferências atômicas e cálculo de saldo sem coluna de saldo mutável.
- Dashboard, filtros e paginação, interface responsiva em português.
- Dados fictícios opcionais, Docker Compose e modo H2 local.
- Testes HTTP de regras financeiras, autenticação e isolamento.
- CRUD global de empresas substituído por consulta e edição da própria empresa em /api/v1/companies/me. A criação da empresa ocorre no cadastro inicial; não há exclusão física de empresa exposta.

### Correções

- Execução do Maven Wrapper no Docker normaliza finais de linha CRLF de checkouts Windows e força LF no Git.
