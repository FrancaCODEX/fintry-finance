# Changelog do Fintry

As versões seguem `MAJOR.MINOR.PATCH`. Funcionalidades e melhorias recebem versão menor; correções isoladas recebem versão de correção. Antes de 1.0, mudanças incompatíveis também incrementam a versão menor. A versão da aplicação é definida em `backend/pom.xml`.

## [0.11.0] - 2026-10-20

### Funcionalidades

- Importação CSV de receitas/despesas por conta, com modelo para baixar, prévia sem gravação, seleção individual e confirmação explícita.
- CSV UTF-8 com BOM opcional, separador ponto e vírgula ou vírgula, aspas escapadas e campos com quebras de linha; limite de 1 MB e 500 registros.
- Validação de descrição, tipo, valor decimal exato, datas, categoria e situação. Realizados exigem pagamento até hoje; pendentes não aceitam data de pagamento.
- Possíveis duplicidades por empresa/conta, descrição sem diferenciar maiúsculas, tipo, valor e vencimento; cancelados e transferências excluídos da comparação. Repetições no arquivo também são ignoradas.
- Confirmação transacional revalida o arquivo e serializa importações da mesma empresa; reenvio não cria novos lançamentos enquanto os registros correspondentes estiverem ativos.
- Categorias cadastradas são vinculadas por nome; categorias desconhecidas ficam avulsas. Categorias inativas/incompatíveis geram erro. Lançamentos usam o serviço financeiro e o histórico existentes.

### Visual e uso

- Fluxo com três etapas, cartões de validação, filtros por situação e resumo da seleção com totais de receitas e despesas.
- Prévia em tabela com cabeçalho fixo no desktop e cartões no celular; confirmação acessível, estados de processamento, erros e conclusão nos dois temas.
- Acesso pelo menu e pela página Lançamentos para Administrador/Financeiro. Permissões, CSRF e isolamento por empresa mantidos no servidor.
- Sem alterações em migrações ou dados existentes. OFX, conciliação bancária e importação de vendas/séries/anexos permanecem fora desta entrega.

## [0.10.1] - 2026-10-20

### Correções visuais

- Corrigido conflito entre a regra antiga da barra lateral e a personalização de rolagem: o menu deixa de exibir a barra cinza nativa no Chromium.
- Removidas as setas das extremidades das barras WebKit; trilho transparente e indicador arredondado seguem as cores do tema em menu, página, tabelas e painéis.
- Corrigida também a precedência da barra da página; navegação móvel e fallback por propriedades padrão preservados.
- Barras continuam nativas, com rolagem por mouse, toque e teclado; cores do sistema respeitadas no modo de alto contraste.
- Sem alterações em dados, migrações ou regras financeiras. Importação CSV permanece como próxima funcionalidade.

## [0.10.0] - 2026-10-20

### Navegação e visual

- Menu dividido em Financeiro, Comercial, Cadastros e Seu espaço, com ícones específicos e destaque da página atual.
- Menu recolhível no desktop, com preferência local, rótulos acessíveis e identificação dos ícones.
- Busca de páginas por nome no menu, insensível a acentos, com atalho / e estado sem resultados.
- Painel de navegação no celular/tablet (até 900 px), usando dialog nativo com foco contido, Escape, clique externo e fechamento ao selecionar a página.
- Cabeçalho fixo com seção/página atual e opção de tema; botão Voltar ao topo em páginas longas e atalho de teclado para pular ao conteúdo.
- Barras de rolagem discretas e nas cores do tema em página, menu, tabelas e painéis, mantendo controles nativos de mouse, toque e teclado.
- Camada visual própria com superfícies, bordas, sombras, espaçamento, botões, campos, tabelas e indicadores consistentes em claro/escuro.
- Estados de foco/hover e respeito a movimento reduzido/cores forçadas; seleção ativa identificada também por texto e marca visual.

### Correções e consistência

- Campos de senha preservam o espaço do botão Mostrar/Ocultar com a nova padronização dos formulários.
- Estrutura da navegação preservada entre consultas, evitando reiniciar a posição da barra lateral a cada atualização dos dados.
- Item ativo mantido visível sem deslocar toda a página. Troca de página retorna o conteúdo ao topo, enquanto filtros permanecem na mesma tela.
- Estado de busca limpo ao sair/trocar de acesso; itens administrativos continuam limitados às permissões da sessão.
- Redimensionar para desktop fecha o menu móvel e libera a rolagem, preservando o foco em um controle visível.
- Sem alterações em dados, migrações ou regras financeiras. Importação CSV permanece como próxima etapa funcional após a prioridade visual solicitada.

## [0.9.0] - 2026-10-20

### Funcionalidades

- Orçamento mensal por categoria de despesa/mista, com criação, edição, remoção e cópia de limites do mês anterior.
- Acompanhamento por vencimento: pago, pendente, comprometido, disponível, percentual e alertas a partir de 80% / acima de 100%.
- Despesas sem orçamento exibidas separadamente, incluindo categorias avulsas; transferências, receitas e cancelados excluídos do consumo.
- Tela Dados de exemplo para adicionar explicitamente um pacote completo, também em empresas com cadastros existentes.
- Pacote com 3 contas, 6 contatos com campos preenchidos, 8 categorias, seis meses de financeiro, atrasos/previstos, séries, 6 produtos, estoque, 4 vendas, orçamentos de dois meses e 2 comprovantes PDF fictícios.

### Melhorias

- Valores dos indicadores adaptados à largura do cartão, preservando números inteiros no celular.
- Cartões de orçamento com progresso, valores e situação em claro/escuro e celular; cópia preserva limites existentes e ignora categorias inativas.
- Contatos de exemplo com documento, e-mail, telefone e observações fictícios; exemplos demonstram cadastros inativos, estoque baixo/zerado, vendas recebidas/pendentes/canceladas e histórico.
- Comprovantes identificados como exemplo e sem validade; vínculos financeiros e movimentos comerciais criados pelos serviços existentes.
- Confirmação na interface explica o efeito dos exemplos nos indicadores; status do pacote e atalhos para explorar as telas.

### Correções e consistência

- H2 aguarda bloqueios por até 30 segundos, permitindo inclusão concorrente de exemplos mesmo na primeira geração de PDFs.
- Exemplos adicionados uma vez por empresa, com bloqueio transacional para evitar duplicação concorrente; repetição preserva edições posteriores.
- Inclusão aditiva, identificada por Exemplo/DEMO, sem substituir registros existentes ou criar usuários, credenciais ou convites.
- Um limite por categoria/mês; vínculos por id resistem à renomeação/inativação. Mudança de tipo de categoria com orçamento é bloqueada.
- Histórico de criação/edição/remoção de limites e da inclusão dos exemplos; consultas não alteram financeiro ou auditoria.
- PostgreSQL V10 / H2 V8 adicionam tabelas e restrições, sem editar migrações publicadas ou apagar dados.

## [0.8.0] - 2026-10-20

### Funcionalidades

- Central de pendências com vencidas, hoje, próximos 7 dias e de 8 a 30 dias, além da consulta de todas as datas.
- Filtros por descrição, tipo, conta e origem; identificação de parcelas, recorrências e receitas de vendas.
- Indicadores por grupo e totais a receber/a pagar calculados sobre todos os resultados filtrados, sem depender da página exibida.
- Ações de detalhes, baixa, edição e cancelamento conforme permissões e regras do lançamento, sem criar outra forma de realizar pagamentos.
- Dashboard com os primeiros cinco vencimentos de hoje aos próximos sete dias e acesso direto à central/atrasos.

### Melhorias

- Cartões de lançamentos no celular, com conta, valor, datas, situação e ações; tabela com cabeçalho fixo e rolagem acessível no desktop.
- Identificação explícita de receitas/despesas e destaque para vencidas e vencendo hoje, nos temas claro e escuro.
- Skeletons e indicação de carregamento nas páginas, botão de nova tentativa em falhas e proteção contra respostas atrasadas substituírem a navegação atual.
- Erros de validação ao lado dos campos, aria-invalid/aria-describedby, mensagens em português e foco no primeiro campo inválido.
- Indicação de processamento nos botões de formulário; mensagens locais removidas ao corrigir o campo.
- Comparação de senhas identifica o campo de confirmação. Filtros preservam o foco quando possível; páginas carregadas orientam o foco ao título.

### Correções e consistência

- Data de referência calculada no servidor em America/Sao_Paulo, incluindo a fronteira UTC em que o dia brasileiro ainda é o anterior.
- Grupos não se sobrepõem: hoje, dias 1–7 e dias 8–30; todas as pendências inclui também datas posteriores a 30 dias.
- Consulta com snapshot transacional para manter lista, paginação e totais consistentes; indicadores respeitam conta, tipo, origem e busca literal por %, _ e !.
- Realizados, cancelados e transferências excluídos da central. Leituras não alteram saldo, estoque ou auditoria.
- Página reajustada após a última pendência de uma página sair do filtro, evitando uma página vazia desnecessária.
- Carregamentos/falhas não deixam ações de uma página anterior disponíveis. Estado das pendências limpo ao encerrar/trocar o acesso.

### Atualização

- PostgreSQL V9 e H2 V7 adicionam apenas um índice de empresa/situação/vencimento; dados e migrações anteriores preservados.
- A central consulta lançamentos existentes, sem criar ou duplicar compromissos. Não há notificações automáticas, recorrência infinita ou baixa em lote nesta versão.
- O bloco de vencimentos do dashboard usa a data atual, independentemente do mês escolhido nos demais indicadores.

## [0.7.0] - 2026-10-19

### Funcionalidades

- Catálogo com SKU único por empresa, unidade, preço, custo de referência, estoque inicial/mínimo e inativação sem excluir registros.
- Busca, filtros e indicadores de produtos ativos, estoque baixo e valor do estoque a custo atual.
- Histórico de estoque com origem, motivo, autor, horário de Brasília, quantidade e saldo após cada movimento; filtro por produto e paginação.
- Vendas em rascunho com cliente opcional, até 100 itens, quantidades de três casas e total calculado no servidor, com arredondamento por item.
- Confirmação transacional: baixa dos produtos e criação de receita vinculada, pendente ou já recebida, na conta escolhida.
- Cancelamento com devolução integral ao estoque e estorno da receita, preservando os itens, comprovantes e histórico.
- Auditoria de produtos, movimentações e criação/confirmação/cancelamento de vendas.

### Melhorias

- Telas Produtos, Estoque e Vendas com cartões responsivos, disponibilidade destacada, etapas da venda e formulários com prévia exata do total.
- Seletor de produtos com busca no servidor e cliente opcional a partir dos cadastros ativos.
- Painel do lançamento identifica a venda vinculada e oferece acesso aos itens; baixa e comprovantes continuam no financeiro.
- Interface reflete as permissões: Administrador/Financeiro gerenciam; Somente leitura consulta.
- Estado do módulo comercial limpo ao trocar de usuário, evitando manter filtros e seleções de outra empresa.

### Correções e consistência

- Bloqueios e validações impedem estoque negativo, inclusive em confirmações concorrentes para a última unidade.
- Chaves de solicitação impedem duplicação de rascunhos e movimentos manuais em retentativas; uso com outros dados retorna conflito.
- Confirmar/cancelar novamente uma venda não duplica receitas, saídas ou devoluções.
- Falha em qualquer item, conta ou data reverte toda a confirmação, incluindo movimentos e eventos de auditoria.
- Saúde da aplicação não depende de SMTP quando a recuperação de senha está desabilitada, permitindo avaliação local sem servidor de e-mail.
- Receita de venda protegida contra edição/cancelamento isolado, para manter estoque e financeiro coerentes.
- Itens preservam nome, SKU, unidade e preço combinados, mesmo após alterações/inativação no catálogo.

### Atualização

- PostgreSQL V8 e H2 V6 adicionam catálogo, vendas, itens, movimentos e vínculo da receita; migrações anteriores preservadas.
- Vendas antigas não são reconstruídas. Cadastros e lançamentos existentes seguem funcionando e dados/volumes devem ser preservados.
- Produtos começam no catálogo vazio, sem dados fictícios adicionados automaticamente.
- Valor do estoque é indicativo a custo atual dos produtos ativos. Ajustes de estoque não geram despesas; esta versão não inclui custo médio, NF-e, cobrança automática ou devolução parcial.

## [0.6.0] - 2026-10-19

### Funcionalidades

- Equipe com convites de ativação, renovação/revogação de links e edição de nome, perfil e situação.
- Perfis Administrador, Financeiro e Somente leitura, verificados na API e refletidos na interface.
- Recuperação de senha por e-mail, link de 15 minutos e troca de senha autenticada em Minha segurança.
- Convites de 7 dias, com confirmação da empresa/perfil e criação da senha pelo destinatário; compartilhamento manual do link.
- Histórico por empresa com autor, horário, ação e diferenças de campos; filtros, busca, paginação e abertura do lançamento relacionado.
- Auditoria transacional de contas, lançamentos, baixas/cancelamentos, transferências, séries, cadastros, comprovantes, empresa e acessos.

### Melhorias

- Telas Equipe, Histórico e Minha segurança com hierarquia visual, cartões de acesso, indicadores de situação e adaptação aos temas claro/escuro e ao celular.
- Formulários de acesso com confirmação de senha e botão Mostrar/Ocultar; mensagens de convite expirado, recuperação indisponível e sessão encerrada.
- Perfil real na navegação, aviso de somente leitura e ações financeiras disponíveis conforme a permissão.
- SMTP configurável e Mailpit no Compose para conferir e-mails localmente, sem entrega externa.
- Listagens da equipe e histórico paginadas no banco; histórico em horário de Brasília.

### Correções e segurança

- Mudanças de senha, perfil ou situação invalidam sessões existentes por versão de autenticação.
- Proteção contra desativação/rebaixamento da própria conta administrativa e contra remoção do último Administrador ativo.
- Tokens aleatórios de 256 bits, armazenados somente como hash SHA-256, com expiração, revogação e uso único protegido por bloqueio transacional.
- Links usam URL configurada e fragmento removido da barra ao abrir; nenhum token ou senha é registrado no histórico/log de envio.
- Recuperação responde igualmente para conta desconhecida, inativa ou ativa; envio assíncrono após commit e limites de tentativas por IP/e-mail.
- Histórico registra somente campos permitidos, sem conteúdo de arquivos, senhas ou hashes de senha; ações sem mudanças não geram diferenças duplicadas.

### Atualização

- PostgreSQL V7 e H2 V5 adicionam perfis, situação, versão de sessão, tokens e auditoria. Migrações anteriores preservadas.
- Usuários existentes continuam Administradores ativos; sessões da versão anterior exigem novo login.
- O histórico começa nesta versão, sem reconstrução retroativa. Não há exclusão de usuários pela interface/API.
- Configure `APP_BASE_URL` e SMTP para entrega real; no Compose padrão os e-mails ficam no Mailpit em localhost:8025. Fora do Docker, `MAIL_ENABLED=false` por padrão.
- Limitador local em memória, por instância; múltiplas réplicas exigem limitação centralizada. Fila de e-mail limitada, sem promessa de entrega; confira logs/configuração do SMTP se não receber.
- Preserve o banco e seus volumes durante a atualização.

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
