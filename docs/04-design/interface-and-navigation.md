# Interface e navegação — 0.10.0

`style.css` mantém os componentes existentes; `design.css`, carregado depois, define os tokens visuais e a navegação. Os dois temas usam as mesmas estruturas. Não há fonte, framework ou recurso remoto obrigatório. Evite cores de rolagem/foco fixas fora dos tokens; preserve contraste, bordas dos campos e estados inválidos.

`navigation.js` renderiza grupos a partir das permissões da sessão e mantém a lateral no DOM entre consultas. Somente o conteúdo da página é substituído; dados continuam sendo consultados com os controles de sessão/concorrência existentes. Rótulos visuais, aria-current, título do documento e contexto do cabeçalho acompanham a página. O estado transitório de busca é limpo ao encerrar o acesso. A preferência de recolher a lateral usa localStorage com fallback caso indisponível.

Desktop: lateral de 256 px (240 px em telas menores) ou 82 px recolhida. O item ativo recebe aria-current e rótulo mesmo quando somente o ícone aparece. A busca normaliza acentos e informa quantidade de resultados; / funciona fora de campos, conteúdo editável e diálogos financeiros. Não é uma busca de transações.

Até 900 px: botão de menu abre dialog nativo, com foco/modalidade nativos, fechamento por Escape/botão/fundo e bloqueio de rolagem da página ao fundo. A escolha fecha o painel antes de navegar. Ao voltar ao desktop por redimensionamento, fecha/libera o painel e leva o foco ao item ativo visível. A preferência compacta do desktop não esconde rótulos no menu móvel.

Use rolagem nativa. Firefox recebe scrollbar-color/scrollbar-width; navegadores com pseudo-elementos WebKit recebem trilho discreto, thumb arredondado e hover. Nenhuma barra é simulada com JavaScript ou escondida globalmente. Menu/tabelas/painéis contêm o excesso de rolagem para evitar deslocamentos acidentais do fundo. A página mantém sua rolagem normal.

Voltar ao topo aparece após 420 px; clique move o foco ao título e rola ao início, usando movimento instantâneo quando solicitado pelo sistema. Ir para o conteúdo aparece no foco por teclado. Estados hidden mantêm display:none, incluindo resultados da busca. Cores forçadas preservam a identificação da seleção e controles; movimento reduzido desativa transições.

Verificações: desktop expandido/recolhido, busca com acentos e sem resultados, persistência/recarga, posição do menu após consultas, todos os destinos por perfil, celular 320/390 px e tablet, Escape/fundo/foco/redimensionamento, barra e botão de topo, temas, movimento reduzido e fluxos de negócio existentes.
