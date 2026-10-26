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
| 0.23.0 | Fila persistente de webhooks Asaas, tentativas automáticas e painel operacional | Em andamento |

Os números futuros representam versões de funcionalidades planejadas; correções entre entregas recebem versões PATCH, por exemplo 0.3.1. O escopo e os números podem ser ajustados conforme a evolução, com registro no changelog.

Depois da base: importações/conciliação, integrações bancárias, cobrança, planos/assinaturas e recorrência sem prazo final. A identidade visual mantém verde escuro, verde claro e fundo suave; a evolução deve priorizar hierarquia, estados de carregamento, feedback de ações, detalhes acessíveis e uso no celular.

Por orientação do usuário em 21/10/2026, cobrança integrada foi antecipada à publicação real. A base Asaas é entregue na 0.15.0. Próximas prioridades: homologar o provedor real e validar a instalação (servidor, domínio, SMTP e cópia externa); a base de planos/assinaturas é entregue na 0.16.0 com ativação manual. Próximas etapas comerciais: definir preços/termos, homologar a conta recebedora do Fintry e homologar as mensalidades integradas entregues na 0.17.0, mantendo-as separadas das cobranças dos clientes. Geração recorrente autorizada é entregue na 0.18.0; débito automático, nota fiscal e avisos de inadimplência permanecem nas próximas etapas. Rotação de credenciais, reemissão, estornos assistidos, fila de webhooks e conciliação agrupada continuam como evoluções de cobrança. OFX e conciliação por movimento são entregues na 0.13.0; conexão bancária automática, valores parciais/agrupados e regras avançadas ficam para evolução. Evolução dos dados de exemplo para os novos módulos e personalização da empresa continuam no backlog. As entregas seguintes continuam incluindo melhorias de uso e visual.

Em 20/10/2026, o foco da 0.10.0 passou para front-end/CSS e navegação por solicitação do usuário. A importação CSV continua como próxima funcionalidade, seguida de serviços/propostas.


Em 23/10/2026, o usuário confirmou emissão, abertura da fatura e recebimento Sandbox por consulta no seu ambiente. Publicação e recebimento em produção continuam pendentes de validação própria. O dashboard foi refinado na 0.21.0 e a personalização da empresa/primeiro acesso na 0.22.0. A 0.23.0 evolui a operação das notificações Asaas com fila persistente, novas tentativas e painel administrativo. As próximas prioridades são ampliar os dados de exemplo dos módulos recentes e então iniciar gestão avançada: pagamentos parciais, conciliação agrupada e evolução das recorrências. Essas prioridades não alteram o ambiente comercial nem habilitam cobrança real automaticamente.
