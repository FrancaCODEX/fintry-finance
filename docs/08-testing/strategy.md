# Validação da versão com cadastros

A suíte de integração HTTP testa sessões e CSRF, isolamento por empresa, valores e datas, baixa repetida e simultânea, cancelamento e transferências. A evolução inclui cadastros e edição de pendências.

Cenários adicionais:

- Criar, pesquisar, renomear e inativar contatos e categorias.
- Normalizar máscara de CPF/CNPJ e impedir duplicidade dentro da empresa.
- Permitir o mesmo documento em empresas diferentes e bloquear referências cruzadas.
- Validar o tipo do contato/categoria, incluindo contatos que são clientes e fornecedores.
- Editar pendências sem modificar o saldo realizado.
- Bloquear edição de pagos e cancelados; exigir a ação de baixa para pagar.
- Preservar nomes históricos e manter vínculos inativos preexistentes.
- Atualizar banco com dados da primeira versão sem remover lançamentos.

Execute `cd backend` e `sh mvnw verify`. O perfil padrão dos testes usa H2; `-Dspring.profiles.active=postgres-test` utiliza o PostgreSQL dedicado configurado para CI. O teste isolado de migração reproduz a atualização H2 com um pool de conexões, tal como a aplicação.

Na validação local desta entrega foi utilizado Java 17 como override (`-Djava.version=17`); o projeto mantém Java 21 como alvo. Docker e PostgreSQL não estão disponíveis no ambiente local. O workflow do GitHub conserva as verificações de Java 21, PostgreSQL e Docker.

## Séries mensais — 0.3.0

Cobertura adicional para divisão exata dos centavos, datas ancoradas em meses curtos e anos bissextos, valores máximos, chave idempotente em chamadas concorrentes, isolamento de empresas, criação atômica, primeira ocorrência realizada e cancelamento da série sem reverter pagamentos. O teste de migração também aplica a nova migração sobre lançamentos existentes. A interface é verificada em navegador com lançamento único, parcelamento, recorrência, prévia, acompanhamento e cancelamento.

## Relatórios e projeções — 0.4.0

A suíte local totaliza 18 testes aprovados (14 de integração financeira HTTP, 2 de calendário de séries, 1 de migração e 1 de contexto). A cobertura adicional verifica separação entre pagamento e vencimento, cancelados/transferências fora dos totais, filtros, isolamento de empresas, totais sobre 5.001 registros com páginas de 20, limite de exportação e projeção a partir do saldo atual. Pendências vencidas podem entrar na projeção sem serem baixadas; a seleção de conta e a primeira data negativa são conferidas.

CSV é inspecionado quanto a BOM, separador, centavos e neutralização de fórmulas. XLSX tem as partes XML analisadas e verifica valores numéricos e textos sem fórmulas. PDF é reaberto pelo PDFBox para conferir acentos, valores e múltiplas páginas. O build local usa Java 17 com override, mantendo Java 21 no projeto. PostgreSQL e Docker continuam dependendo da execução na CI ou em ambiente com esses serviços.

A conferência em Chromium cobre dashboard, versão, filtros realizados/pendentes, resultado vazio, downloads nos três formatos, exportação com filtros aplicados, saldo projetado negativo e opção de vencidas. As telas são inspecionadas em 1440px e 390px, verificando ausência de rolagem horizontal da página e valores completos nos cartões. O Excel baixado também é aberto pelo openpyxl; o PDF é renderizado e inspecionado visualmente.

## Comprovantes, detalhes e temas — 0.5.0

A suíte totaliza 22 testes: 18 de integração financeira HTTP, 2 de calendário, 1 de migração e 1 de contexto. Os novos cenários verificam PNG/JPEG/PDF válidos, download com bytes idênticos, metadados sem conteúdo binário, preservação após baixa/cancelamento, remoção sem mudar saldo, conta/lançamento/empresa corretos, sessão e CSRF. Também recusam SVG, arquivos disfarçados, vazios ou corrompidos, PDFs com senha e arquivo acima de 5 MB. Dois envios concorrentes a um lançamento com nove anexos resultam em um sucesso e uma recusa; remover um arquivo libera uma vaga.

As migrações H2 V4 e PostgreSQL V6 são incrementais; o teste de migração aplica a evolução sobre lançamentos existentes. Localmente, build e suíte usam Java 17 com override e H2. Java 21, PostgreSQL e Docker permanecem cobertos pelo workflow do GitHub, sem declaração de execução local desses ambientes.

A conferência em Chromium usa 1440px no desktop e 390px no celular. Verifica modo claro/escuro, escolha salva após recarga, tema inicial do sistema, bordas dos campos, painel com edição, envio/download idêntico de PNG e envio de PDF, erros de tipo/tamanho, confirmação de remoção, baixa/cancelamento mantendo anexos, Escape e reabertura. Capturas de formulários, dashboard, relatórios e painel são inspecionadas nos dois temas; a página e o painel móvel não apresentam rolagem horizontal.
