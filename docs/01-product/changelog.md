# Changelog do Fintry

As versões seguem `MAJOR.MINOR.PATCH`. Funcionalidades e melhorias recebem versão menor; correções isoladas recebem versão de correção. Antes de 1.0, mudanças incompatíveis também incrementam a versão menor. A versão da aplicação é definida em `backend/pom.xml`.

## [0.24.0] - 2026-10-26

### Dados de exemplo ampliados

- Pacote 2 acrescenta um kit de produtos, três serviços e propostas com situações de rascunho, negociação e aprovação.
- A aprovação de exemplo usa os fluxos reais de proposta, venda, estoque e conta a receber; o lançamento fica pendente, sem simular pagamento recebido.
- Empresas que já carregaram o pacote anterior podem solicitar a expansão. O pacote original, registros criados pela empresa e alterações feitas nos exemplos são preservados.
- O controle de versão do pacote evita duplicar a expansão em solicitações repetidas ou simultâneas. A atualização permanece explícita e afeta os indicadores.
- A tela Dados de exemplo informa o conteúdo novo e oferece atualização do pacote existente; os exemplos nunca são carregados automaticamente.

## [0.23.0] - 2026-10-26

### Operação das notificações de cobrança

- Webhooks Asaas são autenticados e registrados em fila persistente antes de responder ao provedor; eventos repetidos são deduplicados por conexão e identificador.
- Processamento em segundo plano consulta a API Asaas e confirma os dados vinculados ao lançamento; o status e o valor enviados pelo webhook continuam sem autoridade para dar baixa.
- Falhas temporárias recebem novas tentativas com espera progressiva. Após oito tentativas, o evento fica em falha para revisão e o Administrador pode solicitar novo processamento.
- Painel administrativo mostra eventos recentes, pendentes, em processamento e com falha, sem expor chaves, tokens ou dados de pagamento.
- Migração aditiva cria a fila e seus índices; eventos órfãos ou divergentes não alteram lançamentos e podem ser revisados.
- A rotina de eventos é iniciada pela aplicação sem configuração manual no ambiente. Sandbox e Produção seguem as credenciais já configuradas por empresa.

## [0.22.0] - 2026-10-26

### Primeiros passos e identidade da empresa

- Dashboard de empresas em início de uso apresenta um checklist de conta financeira, categorias e primeiro lançamento, com progresso e acesso direto a cada etapa. O checklist some quando os três cadastros existem.
- Nova página Empresa permite que Administradores atualizem nome fantasia, razão social, documento, e-mail e telefone. O nome fantasia atualizado aparece imediatamente no cabeçalho da aplicação.
- Edição usa o endpoint existente, mantém isolamento por empresa, respeita a permissão Administrador e registra as alterações no histórico de auditoria.
- Orientação para explorar o pacote completo de dados fictícios continua disponível no checklist. A consulta do primeiro lançamento verifica o histórico completo sem carregar transações adicionais.
- Nenhuma migração ou mudança nas regras financeiras, de acesso comercial ou de cobrança.

## [0.21.0] - 2026-10-23

### Visual e experiência

- Dashboard reorganizado com destaque para o saldo atual de todas as contas, indicadores realizados do mês e previsões pendentes com contextos distintos.
- Atalhos para lançamentos, transferências, relatórios e conciliação; perfis sem permissão de escrita recebem alternativas de consulta.
- Gráfico de seis meses permite consultar valores com mouse, toque e teclado, incluindo navegação por setas, resumo do mês selecionado e tabela alternativa acessível.
- Painel de atrasos atuais mantém sua própria data de referência, independente do mês selecionado. Ações e detalhes existentes continuam disponíveis.
- Layout responsivo, contraste nos temas claro/escuro, estados sem movimentação e retorno rápido ao mês atual.

### Código e clareza dos indicadores

- Renderização e interação do dashboard separadas em `dashboard.js`, com verificação de sintaxe no CI. Categorias continuam usando o componente compartilhado com relatórios.
- Comparação de resultado anterior negativo apresenta variação em reais, evitando uma porcentagem de interpretação ambígua.
- Nenhuma alteração no cálculo do backend, datas de vencimento, saldos, pagamentos ou regras de cobrança. Resultado de caixa continua identificado como diferente de lucro contábil.

## [0.20.1] - 2026-10-23

### Correções

- Datas de criação de solicitações de plano e mensalidades são enviadas com fuso explícito (`Z`), evitando interpretar o horário UTC do container como horário local do navegador.
- Exibição da assinatura continua no fuso de São Paulo: uma solicitação de 19:25 UTC aparece como 16:25, inclusive em navegadores configurados para outros fusos.
- Conversão utiliza o fuso da JVM que também é utilizado pela auditoria de criação. Nenhum registro, vencimento, período de teste ou acesso pago é regravado.

## [0.20.0] - 2026-10-23

### Experiência e diagnóstico

- Página de assinatura distingue avaliação sem cobrança, emissão desabilitada e emissão em Sandbox/produção, com preços ainda não configurados por plano.
- Orientações expansíveis para o Administrador explicam quais variáveis conferir e que a aplicação precisa ser recriada usando o arquivo correto. O diagnóstico usa a configuração carregada; não declara credenciais ou webhook homologados.
- Capacidade Profissional inicial identificada como teste gratuito ou avaliação, sem sugerir assinatura paga. Prazo, dados, limites e contratos existentes são preservados.

### Melhorias e correções

- Solicitações informam a próxima ação para emitir ou o impedimento específico, incluindo preço ausente, acesso legado/simulado e mudança durante período pago. Solicitar não emite cobrança sem a confirmação do pagador.
- Botão de emissão usa os planos com preço efetivamente configurado para a integração, evitando oferecê-lo com preço apenas no catálogo. Solicitações já vinculadas a fatura orientam a consulta no histórico.
- Textos de Sandbox deixam explícito que recebimentos de teste não ativam acesso comercial; avaliação não promete fatura ou link.
- Painel adaptado ao celular e aos temas claro/escuro, exemplo de ambiente local completo e roteiro de diagnóstico/homologação. Nenhuma chamada ao provedor é feita ao carregar a página.

## [0.19.0] - 2026-10-22

### Funcionalidades e experiência

- Após criar a conta e entrar automaticamente, o usuário conhece os planos disponíveis antes de acessar o financeiro.
- Primeiro acesso apresenta o plano inicial Profissional e o teste de 14 dias no modo comercial, com a data de término obtida do servidor. Continuar com o teste não exige contratação ou pagamento.
- Ações para comparar planos ou entrar no financeiro, com apresentação adaptada ao celular e aos temas claro/escuro. O ambiente de avaliação tem texto próprio e não simula uma assinatura comercial ativa.

### Melhorias e operação

- Cadastro informa que a criação da conta não gera cobrança. Contratação e autorização de recorrência continuam exigindo confirmação separada.
- Login normal continua abrindo o dashboard. Sair da apresentação não altera plano, prazo, solicitações ou pagamentos.
- Documentação de primeiro acesso e abertura/configuração da conta Asaas do operador, versão e imagem de produção atualizadas.

## [0.18.0] - 2026-10-22

### Funcionalidades

- Autorização explícita para geração recorrente de mensalidades Pix/boleto do próprio Fintry, com valor, plano, pagador e forma de pagamento vinculados a uma mensalidade de produção já recebida.
- Rotina no servidor reserva uma fatura por ciclo a partir de três dias antes do fim do acesso pago. Recebimento confirmado continua concedendo um único período de 30 dias; não há débito automático.
- Interrupção das próximas emissões, verificação manual da autorização e integração com o encerramento do período. Faturas já iniciadas têm cancelamento separado.

### Visual, melhorias e correções

- Painel de renovação com valor autorizado, fim do acesso e previsão de emissão; estados de pausa, confirmação de consentimento e ações em claro/escuro e celular.
- Preços do catálogo identificados como períodos de 30 dias, alinhados à duração efetiva do acesso.
- Alterações de preço exigem novo consentimento; solicitações pendentes, capacidade excedida, alterações de conta/plano e pagamentos em conferência pausam novas emissões.
- Bloqueio por empresa e vínculo único por ciclo impedem duplicidades entre instâncias. Respostas incertas preservam a reserva para consulta, sem repetir POST automaticamente nem recriar faturas canceladas.
- Novas faturas solicitam juros e multa zero e não solicitam envio postal. Interromper a recorrência preserva acesso pago e não estorna pagamentos.

### Operação comercial

- Rotina opcional, habilitada no Compose de produção e desabilitada na execução local por padrão; pagamentos continuam desabilitados até configuração e homologação. Não são criadas autorizações para empresas existentes.
- Integração validada com provedor HTTP simulado. Preços, conta recebedora, condições e homologação real continuam pendentes. Pix Automático, cartão, assinatura nativa Asaas, nota fiscal e avisos automáticos não fazem parte desta entrega.

## [0.17.0] - 2026-10-21

### Funcionalidades

- Mensalidades do próprio Fintry por Pix/boleto, com conta recebedora do operador separada das cobranças dos clientes e preços configuráveis no servidor, sem valores predefinidos.
- Emissão confirmada pelo Administrador com preço registrado, consentimento, vencimento em três dias e fatura hospedada no Asaas. Recebimento em produção libera 30 dias de acesso ou estende o período pago do mesmo plano.
- Webhook autenticado e consulta manual conferem o estado pela API. Cada fatura concede um único período; Sandbox e confirmação sem recebimento não ativam acesso comercial.
- Reserva persistente, recuperação de emissão incerta, isolamento por empresa e cancelamento confirmado pelo provedor. Pagamento com divergência ou excesso de capacidade fica em conferência.

### Visual, melhorias e correções

- Histórico de mensalidades com valor, plano, vencimento, ambiente, situação e acesso concedido, com ações de consulta, fatura e cancelamento; apresentação adaptada ao celular e aos temas claro/escuro.
- Resumo do pedido antes da emissão, com valor e condições explícitas. Encerramento do acesso e cancelamento da fatura têm ações distintas.
- Ativação manual protegida contra conflito com faturas integradas. Exclusão/restauração após cancelamento não concede acesso; estornos após ativação exigem resolução assistida, sem repetir períodos.

### Operação comercial

- Renovação por nova solicitação confirmada pelo administrador; geração recorrente, débito automático, nota fiscal e recuperação automática de inadimplência continuam pendentes.
- Preços/condições e conta recebedora precisam ser definidos e homologados pelo operador. Integração validada com servidor HTTP simulado; nenhum pagamento real, credencial comercial ou publicação foi efetuado nesta entrega.

## [0.16.0] - 2026-10-21

### Funcionalidades

- Catálogo SaaS Essencial, Profissional e Gestão com limites de pessoas, contas e armazenamento; todos os planos mantêm os módulos e lançamentos sem limite de quantidade. Preços permanecem a definir pelo responsável comercial.
- Novas empresas no modo comercial iniciam teste de 14 dias. Empresas existentes recebem acesso legado preservado, sem bloqueio retroativo.
- Solicitação de plano/renovação, cancelamento da solicitação e programação de encerramento ao fim do período ativo. Selecionar um plano não confirma pagamento nem concede acesso pago.
- Ativação manual pelo operador via ferramenta local de administração, com conferência comercial, referência, prazo, auditoria, transação, revalidação de capacidade e repetição idempotente. Não há endpoint público de ativação.
- Ambiente de avaliação permite experimentar planos sem cobrança; produção exige modo comercial e não transforma simulação em assinatura paga.

### Visual, melhorias e correções

- Página Plano e assinatura com cartões comparativos, indicadores de capacidade, estado de acesso e histórico de solicitações, em claro/escuro e celular.
- Limites aplicados no servidor com bloqueio por empresa, inclusive convites, reativações, importação de exemplos e uploads simultâneos em lançamentos diferentes. Revogar convite libera sua vaga.
- Vencimento preserva consultas, exportações, comprovantes, segurança e solicitações de plano. Remoção de anexos continua disponível para liberar espaço. Pagamentos Asaas e confirmação de cobranças existentes continuam sendo processados.

### Operação comercial

- A contratação e os períodos pagos são manuais nesta etapa. Preços, termos, cobrança automática da assinatura, nota fiscal, recuperação de inadimplência e homologação da conta recebedora do próprio Fintry continuam pendentes. Nenhum plano foi vendido nem pagamento real efetuado nesta entrega.

## [0.15.0] - 2026-10-21

### Funcionalidades

- Integração Asaas v3 para emissão de Pix e boleto vinculados a receitas pendentes com cliente e CPF/CNPJ; fatura, boleto e Pix copia e cola acessíveis no painel.
- Conexão por empresa com credenciais cifradas, configuração exclusiva do Administrador e promoção de Sandbox para Produção preservando cobranças e webhooks antigos.
- Consulta manual, recuperação de emissão inconclusiva e webhook autenticado que confirma a situação pela API do provedor. Reserva persistente e vínculo único impedem reemissão em tentativas repetidas ou simultâneas.
- Em Produção, RECEIVED compatível registra baixa e tarifa separada na data de crédito. Sandbox não altera receitas, tarifas ou saldos. Divergências, estornos e contestações ficam em revisão.

### Visual, melhorias e correções

- Página Cobranças com destaque do ambiente, cartões de recebimento, estados, detalhes e ações em claro/escuro e celular.
- Proteção contra alterações manuais conflitantes no lançamento, venda, série e tarifa; cancelamento externo preserva o lançamento e o estoque. Cobranças reativadas no Asaas após cancelamento exigem revisão e não dão baixa em lançamentos que já puderam ser corrigidos.
- Auditoria sem segredos, limites de payload de webhook, URLs do provedor verificadas e documentação de homologação, operação e recuperação da chave de proteção.

### Limitações de ativação

- Integração validada com servidor HTTP simulado; Sandbox real e produção dependem das credenciais e da homologação da empresa. Publicação real não foi executada. Uma emissão por lançamento; sem reemissão, rotação de conta/chaves após emissão, reembolso, envio de avisos, cartões ou conciliação agrupada do líquido. Planos do próprio SaaS continuam no backlog.

## [0.14.0] - 2026-10-21

### Funcionalidades

- Composição de produção separada com Caddy/HTTPS, volumes e redes próprios, banco/aplicação sem portas públicas, logs rotacionados e aplicação sem privilégios com sistema de arquivos somente leitura.
- Perfil prod com cookies Secure, cabeçalhos do proxy, encerramento gracioso e validação de PostgreSQL, URL HTTPS, senha do banco, Flyway/Hibernate e SMTP autenticado com TLS/certificado.
- Rotinas administrativas para backup PostgreSQL customizado, manifesto SHA-256, verificação e restauração em nova base. O teste de restauração remove somente sua própria base isolada após sucesso; backups anteriores e banco em uso são preservados.
- Suporte às rotinas também no PostgreSQL do Docker local. Exemplos systemd de backup diário e verificação HTTPS, com falhas registradas no journal.
- Diagnóstico Ambiente exclusivo de Administrador, com disponibilidade, banco, versão, início do processo, cookies e modo de e-mail, sem expor credenciais ou acesso ao backup de todas as empresas.
- Teste de e-mail enviado somente ao próprio Administrador, com CSRF, limite por pessoa e auditoria. Aceite SMTP é distinguido da confirmação de entrega.

### Visual e correções

- Painel Ambiente com indicadores, cartões e ações de diagnóstico em claro/escuro, adaptado ao celular.
- Probes de liveness e readiness separados; prontidão inclui banco, enquanto SMTP não bloqueia consultas financeiras. Probes não dependem da validação de usuário mesmo com cookie antigo durante uma queda do banco.
- Healthcheck da imagem Docker e monitor HTTPS sem redirecionamentos, com certificado validado. A configuração local, os dados e as migrações publicadas são preservados.
- CI ampliado com falhas de backup, guard de produção e instalação Docker isolada para TLS, SMTP e restauração com comprovantes.

### Operação

- Guias de publicação, atualização, recuperação, ambientes, monitoramento e backups. Nenhum servidor, domínio, serviço SMTP ou armazenamento externo foi configurado automaticamente; a publicação real depende desses dados.

## [0.13.0] - 2026-10-21

### Funcionalidades

- Importação OFX bancária 1.x/2.x em BRL, com prévia, seleção, confirmação da conta de destino e revisão persistente. Até 1 MB e 500 movimentos; importar não altera o financeiro.
- Identificação de movimentos repetidos por origem bancária e FITID, inclusive entre arquivos sobrepostos. Identificadores com dados divergentes são sinalizados; a origem não pode ser importada em outra conta da mesma empresa.
- Conciliação individual com lançamentos da mesma conta, tipo e valor: vincular realizado da mesma data preserva o saldo; vincular pendente registra baixa na data do extrato.
- Sugestões por valor/tipo e proximidade de datas, busca manual e decisão explícita. Um lançamento não pode ser vinculado a dois movimentos.
- Criação de lançamento realizado a partir do extrato, com categoria cadastrada ou avulsa. Possíveis correspondências exigem justificativa antes de criar outro lançamento de mesmo valor/tipo.
- Ignorar com motivo e reabrir revisão sem estornar pagamentos. Cancelamentos de lançamentos/vendas conciliados exigem reabrir o vínculo primeiro.
- Auditoria de importação e decisões, revisão contra alterações concorrentes e novas migrações PostgreSQL V12/H2 V10 preservando os dados anteriores.

### Visual e uso

- Página Conciliação com indicadores por situação, cartões de entrada/saída, filtros por conta/situação, paginação e detalhes das correspondências.
- Prévia em tabela no desktop e cartões no celular, formulários de confirmação e apresentação nos temas claro/escuro.
- Leitura UTF-8/Windows-1252 e arquivo OFX fictício para avaliação. Consulta acompanha movimentos; importação e decisões ficam com Administrador/Financeiro.
- Cartões, investimentos, correções bancárias, importação automática por API e conciliação parcial/agrupada ficam fora desta entrega. O arquivo original e a conta bancária completa não são armazenados; o saldo informado pelo banco não substitui o saldo do Fintry.

## [0.12.0] - 2026-10-21

### Funcionalidades

- Catálogo de serviços por unidade de execução (UN), com código único entre produtos/serviços, preço, custo de referência e situação. Serviços não têm estoque nem alertas de reposição.
- Vendas com produtos e serviços; confirmação/cancelamento movimentam apenas itens físicos e mantêm a receita vinculada ao financeiro.
- Propostas com título, cliente opcional, observações, validade, quantidades/preços e itens registrados como estavam na elaboração.
- Rascunho editável, negociação, recusa e cancelamento; retorno de negociação para rascunho permite revisar validade e condições. Revisão impede sobrescrever alterações mais recentes.
- Aprovação interna gera uma venda confirmada e a receita em uma transação. Estoque/cadastros e validade são conferidos novamente, mantendo nomes/preços acordados. Repetir aprovação devolve a venda existente.
- Proposta aprovada permanece como histórico após cancelar a venda; não gera uma segunda venda. Correções financeiras e devoluções de estoque seguem pelo fluxo de cancelamento da venda.
- PDF comercial com fonte incorporada, dados da empresa, cliente, validade, situação, itens, total, observações e paginação. Download autenticado por empresa; sem envio automático de e-mail ou assinatura digital.

### Visual e uso

- Novas páginas Serviços e Propostas, cartões de catálogo, indicadores de negociação, busca/filtros e detalhes com ações contextuais em claro/escuro.
- Formulário de itens compartilhado entre vendas/propostas, identificando serviços sem estoque; layouts adaptados ao celular e feedback durante operações.
- Histórico com identificação de propostas, mudanças de itens e aprovação, além dos eventos da venda e do financeiro.
- Novas migrações preservam produtos/vendas existentes como itens físicos. Sem remoção de dados ou alteração de migrações publicadas.

## [0.11.0] - 2026-10-20

### Funcionalidades

- Importação CSV de receitas/despesas por conta, com modelo para baixar, prévia sem gravação, seleção individual e confirmação explícita.
- CSV UTF-8 com BOM opcional, separador ponto e vírgula ou vírgula, aspas escapadas e campos com quebras de linha; limite de 1 MB e 500 registros.
- Validação de descrição, tipo, valor decimal exato, datas, categoria e situação. Realizados exigem pagamento até hoje; pendentes não aceitam data de pagamento.
- Possíveis duplicidades por empresa/conta, descrição sem diferenciar maiúsculas, tipo, valor e vencimento; cancelados e transferências excluídos da comparação. Repetições no arquivo também são ignoradas.
- Confirmação transacional revalida o arquivo e serializa importações da mesma empresa; reenvio não cria novos lançamentos enquanto os registros correspondentes estiverem ativos.
- Categorias cadastradas são vinculadas por nome; categorias desconhecidas ficam avulsas. Categorias inativas/incompatíveis geram erro. Lançamentos usam o serviço financeiro e o histórico existentes.

### Visual e uso

- Fluxo com três etapas, cartões de validação, filtros por situação e resumo da seleção com totais de receitas e despesas, preservando centavos mesmo em somas elevadas.
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
