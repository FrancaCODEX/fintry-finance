# Importação de lançamentos CSV

Disponível na 0.11.0 em **Importar CSV** (menu Financeiro e atalho em Lançamentos). Administrador e Financeiro podem gerar prévia e confirmar; Consulta não pode executar os POSTs. Sessão autenticada, CSRF e escopo da empresa são obrigatórios.

## Arquivo

CSV UTF-8, BOM opcional, até 1.048.576 bytes e 500 registros de dados. Separador `;` ou `,`, inferido pelo cabeçalho. Campos contendo separador, aspas ou quebras de linha devem estar entre aspas duplas; uma aspa dentro do campo deve ser duplicada. Não usar a linha `sep=;`. Linhas totalmente vazias são ignoradas. A numeração da prévia conta registros lógicos, começando em 2 após o cabeçalho, e não linhas físicas de campos multilinha.

| Coluna | Regra |
|---|---|
| descricao | Obrigatória, de 1 a 255 caracteres após remover espaços das pontas |
| tipo | Obrigatória: receita / despesa (também INCOME / EXPENSE) |
| valor | Obrigatória: positivo, até 999999999999,99 e duas casas; exemplos 1234,56, 1.234,56 ou 1234.56. Não usar R$, sinal ou notação científica |
| vencimento | Obrigatória, data válida DD/MM/AAAA ou AAAA-MM-DD, anos 1900–2100 |
| categoria | Obrigatória, de 1 a 80 caracteres. Nome existente, sem diferenciar maiúsculas/minúsculas, vincula a categoria cadastrada. Inativa/tipo incompatível gera erro. Nome novo permanece avulso |
| situacao | Opcional; vazia/pendente/PENDING ou realizado/pago/recebido/PAID |
| data_pagamento | Obrigatória para realizado, até hoje em Brasília. Deve ficar vazia para pendente |

Cabeçalhos não diferenciam maiúsculas e acentos. Não são aceitas colunas desconhecidas, repetidas ou registros com quantidade de campos diferente do cabeçalho. Os valores negativos não inferem despesas: informe o tipo e o valor positivo.

## Endpoints

- `GET /api/v1/finance/imports/template`: CSV com cabeçalho e dois exemplos fictícios, para download. Exige sessão; não cria dados.
- `POST /api/v1/finance/imports/preview`: JSON `{ "accountId": "UUID", "csv": "conteúdo do CSV" }`. Retorna `accountName`, contagens `total`, `ready`, `duplicates`, `invalid` e `rows` com `number`, campos do registro, `state` (READY/DUPLICATE/INVALID) e `message`. Estrutura inválida gera 400; problemas de conteúdo são reportados por registro. Não grava lançamentos.
- `POST /api/v1/finance/imports/confirm`: mesmo JSON, acrescido de `selectedRows: [2, 3]`. Seleção obrigatória, de até 500 números únicos existentes no arquivo. Revalida o arquivo e a conta no servidor; qualquer registro selecionado inválido rejeita a operação inteira. Duplicidades selecionadas são ignoradas. Retorna `imported`, `skippedDuplicates`, `income`, `expense` (strings decimais exatas com os totais dos criados, incluindo pendentes e realizados).

A conta de destino precisa pertencer à empresa da sessão. Nenhum identificador de empresa enviado pelo cliente é utilizado. Criação em uma única transação pelo FinanceService; cada lançamento gera evento CREATE/ENTRY no histórico. Os efeitos de saldo/previsão seguem a situação do lançamento.

## Possíveis duplicidades e repetição

Comparação conservadora: mesma empresa e conta, descrição sem espaços nas pontas e sem diferenciar maiúsculas/minúsculas, tipo, valor e vencimento. Categoria, contato, origem e situação/pagamento não diferenciam registros; assim, uma pendência já baixada continua bloqueando sua repetição. Lançamentos cancelados e transferências não bloqueiam. Repetições no próprio arquivo são sinalizadas, mantendo apenas a primeira ocorrência válida como candidata.

Não há opção de forçar duplicidades nesta versão. Se duas operações iguais forem legítimas, confira os dados e cadastre a segunda manualmente. Alterações nos campos comparados ou cancelamento do registro original podem permitir nova importação: não se trata de deduplicação por identificador externo/extrato.

Importações confirmadas para a mesma empresa usam um lock da empresa para serializar a revalidação e a gravação. Repetir a confirmação, inclusive após perda de conexão, ignora os correspondentes ainda ativos. A prévia não reserva registros. Escritas manuais e outros fluxos não usam esse lock de importação, portanto não há garantia de exclusividade contra um lançamento manual simultâneo.

O arquivo fica apenas na memória da página até confirmação, troca de arquivo ou saída da sessão; não é armazenado como anexo. Recarregar o navegador perde a seleção e o arquivo. A API não aceita importação de transferências, recorrências, vendas, contatos ou comprovantes. OFX e conciliação ficam para etapa posterior.
