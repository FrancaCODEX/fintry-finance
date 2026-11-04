# Roadmap do Fintry

Ordem de evolução acordada em 19/10/2026. Cada entrega atualiza a versão Maven, o README e o changelog canônico, separando funcionalidades, melhorias e correções.

| Versão | Escopo | Situação |
|---|---|---|
| 0.1.0 | Acesso, empresas, contas, lançamentos, transferências e dashboard | Incorporada à main |
| 0.2.0 | Clientes, fornecedores, categorias, vínculos e edição de pendências | Incorporada à main |
| 0.3.0 | Recorrências mensais finitas, parcelamentos e prévia no formulário | Incorporada à main |
| 0.4.0 | Relatórios/exportações e fluxo de caixa projetado; evolução do dashboard | Incorporada à main |
| 0.5.0 | Comprovantes, painel lateral de detalhes, modo escuro e contraste dos formulários | Incorporada à main |
| 0.6.0 | Recuperação de senha, usuários, permissões e histórico de alterações | Incorporada à main (PRs #6 e #7) |
| 0.7.0 | Produtos, estoque e vendas | Incorporada à main |
| 0.8.0 | Central de pendências, vencimentos no dashboard, cartões no celular e melhorias de formulários/carregamento | Incorporada à main |
| 0.9.0 | Orçamento por categoria e dados de exemplo completos | Incorporada à main |
| 0.10.0 | Prioridade visual: navegação, rolagem e refinamento do front-end | Incorporada à main |
| 0.10.1 | Correção da barra de rolagem do menu e da página | Incorporada à main |
| 0.11.0 | Importação CSV com prévia, seleção e identificação de possíveis duplicidades | Incorporada à main |
| 0.12.0 | Serviços, propostas comerciais, PDF e aprovação integrada à venda/receita | Incorporada à main |
| 0.13.0 | Importação OFX, revisão persistente e conciliação bancária por movimento | Incorporada à main |
| 0.14.0 | Preparação de produção, HTTPS, diagnóstico, e-mail e backup/restauração | Incorporada à main; publicação real depende da infraestrutura |
| 0.15.0 | Cobranças Asaas, Pix/boleto, consulta, webhook e tarifas | Incorporada à main; homologação real depende de credenciais |
| 0.16.0 | Planos SaaS, capacidade, teste, solicitações e ativação manual | Incorporada à main; base de capacidade e ativação manual |
| 0.17.0 | Mensalidades Pix/boleto, preços configuráveis e ativação/renovação por recebimento confirmado | Incorporada à main; homologação real pendente |
| 0.18.0 | Geração recorrente autorizada de mensalidades, controle por ciclo e painel de renovação | Incorporada à main; sem débito automático, homologação real pendente |
| 0.19.0 | Primeiro acesso com apresentação dos planos e continuidade do teste padrão | Incorporada à main |
| 0.20.0 | Diagnóstico de disponibilidade de cobrança, preços pendentes e clareza entre teste e contratação | Incorporada à main; homologação real continua pendente |
| 0.20.1 | Correção do fuso de criação de solicitações e mensalidades na assinatura | Incorporada à main |
| 0.21.0 | Refinamento do dashboard, consulta acessível dos gráficos, atalhos e organização do front-end | Incorporada à main (PR #24) |
| 0.22.0 | Perfil da empresa e checklist de primeiros passos integrado ao dashboard | Incorporada à main |
| 0.23.0 | Fila persistente de webhooks Asaas, tentativas automáticas e painel operacional | Incorporada à main (PR #26) |
| 0.24.0 | Expansão segura dos exemplos para serviços e propostas comerciais | Incorporada à main (PR #27) |
| 0.25.0 | Pagamentos parciais e acompanhamento do saldo restante | Incorporada à main (PR #28) |
| 0.26.0 | Conciliação agrupada, divergências e tarifas com histórico | Incorporada à main (PR #29) |
| 0.27.0 | Modelos de recorrência com pausa, retomada e término controlado | Incorporada à main (PR #30) |
| 0.28.0 | Preparação da versão de apresentação com exemplos de pagamentos parciais, relatórios e conciliação | Incorporada à main (PR #31) |
| 0.29.0 | Roteiro de apresentação contextual no dashboard e organização da página de exemplos | Incorporada à main (PR #32) |
| 0.30.0 | Refinamento visual amplo do front-end: superfícies, hierarquia, navegação, componentes e telas móveis | Incorporada à main (PR #33) |
| 0.31.0 | Indicadores analíticos com minigráficos acessíveis no dashboard | Incorporada à main (PR #34) |
| 0.32.0 | Estúdio de gráficos interativo com séries, períodos e visualizações personalizadas | Incorporada à main (PR #35) |
| 0.32.1 | Documentação do dashboard e README alinhados para demonstração | Incorporada à main (PR #36) |
| 0.32.2 | Documentação de apresentação alinhada sem roteiro de vídeo versionado | Incorporada à main (PR #37) |
| 0.32.3 | Correção do gráfico de linhas e remoção do cartão tutorial do dashboard | Incorporada à main (PR #38) |
| 0.32.4 | Camada de seleção e tooltip do gráfico de linhas | Em preparação |

Os números futuros representam versões de funcionalidades planejadas; correções entre entregas recebem versões PATCH, por exemplo 0.3.1. O escopo e os números podem ser ajustados conforme a evolução, com registro no changelog.

A base funcional de importações, conciliação, cobrança, planos/assinaturas e recorrências está entregue, com homologação real de provedores e operação externa ainda dependentes de infraestrutura/credenciais. Para a apresentação, as próximas prioridades são revisar o percurso em navegador desktop e celular, manter os exemplos fictícios consistentes e evoluir acessibilidade/feedback sem ampliar o escopo comercial. A identidade visual mantém verde escuro, verde claro e fundo suave.

Por orientação do usuário em 21/10/2026, cobrança integrada foi antecipada à publicação real. A base Asaas é entregue na 0.15.0. Próximas prioridades: homologar o provedor real e validar a instalação (servidor, domínio, SMTP e cópia externa); a base de planos/assinaturas é entregue na 0.16.0 com ativação manual. Próximas etapas comerciais: definir preços/termos, homologar a conta recebedora do Fintry e homologar as mensalidades integradas entregues na 0.17.0, mantendo-as separadas das cobranças dos clientes. Geração recorrente autorizada é entregue na 0.18.0; débito automático, nota fiscal e avisos de inadimplência permanecem nas próximas etapas. Rotação de credenciais, reemissão, estornos assistidos, fila de webhooks e conciliação agrupada continuam como evoluções de cobrança. OFX e conciliação por movimento são entregues na 0.13.0; pagamentos parciais dos lançamentos foram entregues na 0.25.0 e a conciliação agrupada segue na 0.26.0. Conexão bancária automática e regras avançadas continuam no backlog. Evolução dos dados de exemplo para os novos módulos e personalização da empresa continuam no backlog. As entregas seguintes continuam incluindo melhorias de uso e visual.

Em 20/10/2026, o foco da 0.10.0 passou para front-end/CSS e navegação por solicitação do usuário. A importação CSV continua como próxima funcionalidade, seguida de serviços/propostas.


Em 23/10/2026, o usuário confirmou emissão, abertura da fatura e recebimento Sandbox por consulta no seu ambiente. Publicação e recebimento em produção continuam pendentes de validação própria. O dashboard foi refinado na 0.21.0 e a personalização da empresa/primeiro acesso na 0.22.0. A 0.23.0 evolui a operação das notificações Asaas com fila persistente, novas tentativas e painel administrativo. A 0.24.0 amplia os exemplos para serviços e propostas, inclusive aprovação ligada a venda e conta a receber, sem regravar o pacote anterior. A Gestão Avançada começa na 0.25.0 com pagamentos parciais, histórico de baixas e atualização dos indicadores pelo valor efetivamente liquidado. Depois seguem conciliação agrupada e recorrências com pausa, retomada e término controlado. Cada fase continua exigindo confirmação de ações financeiras e prevenção de duplicidades.


Em 26/10/2026, a 0.25.0 foi incorporada à main. A 0.26.0 permite agrupar movimentos bancários da mesma conta e direção em uma baixa parcial ou total, com cada movimento preservado na data própria e o motivo registrado. Tarifas e saídas são conciliadas separadamente, sem compensação automática de sinais.


Em 26/10/2026, após a v0.26.0 incorporada na PR #29, a v0.27.0 implementa pausa, retomada e encerramento controlado de recorrências. A etapa seguinte fica dedicada a uma rodada de UX e interface, priorizando uma apresentação mais limpa, original, bonita e utilizável para demonstrar o projeto no LinkedIn.


Em 26/10/2026, a v0.27.0 foi incorporada à main pela PR #30. A v0.28.0 prepara o percurso de demonstração com exemplos diversos e relacionados; a v0.29.0 organiza a exploração desses fluxos; e a v0.30.0 renova os estilos compartilhados. A v0.31.0 dá uma leitura própria aos indicadores do dashboard com minigráficos acessíveis.


Em 30/10/2026, a v0.32.0 foi incorporada à main pela PR #35: gráficos do dashboard com séries e formato ajustáveis, períodos de 3 ou 6 meses, visualizações salvas por usuário e consulta em tabela. A v0.32.1 prepara a apresentação com documentação alinhada ao produto; os dados financeiros do pacote de exemplos são fictícios.

Em 04/11/2026, a v0.33.0 inicia a evolução autorizada do workspace: estado/transporte separados, consultas documentadas em OpenAPI com tipos TypeScript e chave opcional para repetir baixas parciais com segurança. A sequência acordada é v0.34.0 (dashboard e lançamentos em React) e v0.35.0 (simulador e indicadores explicáveis).

A v0.34.0 entrega dashboard e lançamentos em React/TypeScript, preservando as demais páginas durante a migração. Filtros na URL, detalhes integrados, gráficos acessíveis e build no mesmo domínio preparam o simulador da v0.35.0.

A v0.35.0 conclui a sequência autorizada com cenários hipotéticos de caixa e explicação dos indicadores. Inclui comparação diária, adiamentos pelo restante, repetições mensais, origem dos valores, paginação e consultas também para perfis de leitura. Persistência/compartilhamento de cenários e sugestões de conciliação justificadas continuam como evoluções futuras.


Sequência autorizada em 04/11/2026 após a v0.35.0:

| Versão | Entrega | Situação |
|---|---|---|
| 0.36.0 | Formulários financeiros em React e componentes compartilhados | Em validação |
| 0.37.0 | Cenários salvos, duplicação, histórico de cálculos e recálculo | Planejada |
| 0.38.0 | Sugestões explicáveis e regras reutilizáveis de conciliação, com confirmação | Planejada |
| 0.39.0 | Repaginação completa do frontend, navegação e módulos em React/CSS | Planejada |

A v0.35.1 corrige o build Windows antes desta sequência.
