# Evolução do workspace

## Decisão

O Spring Boot continua responsável por autenticação, autorização, empresa corrente e cálculos monetários. A interface evolui por página, mantendo as demais áreas disponíveis durante a adoção de React/TypeScript. O build final continua sendo servido no mesmo domínio; não se introduz autenticação por tokens no navegador.

## Fundação (0.33.0)

`state.js` inicializa o estado da aplicação. `http.js` concentra transporte, CSRF e falhas de sessão; a renderização de login responde ao evento `fintry:session-expired`. Requisições canceladas preservam `AbortError`. Escritas não são repetidas automaticamente pelo transporte.

`api/workspace.openapi.json` documenta **parte** das consultas já existentes, não toda a API. `frontend/src/api/schema.d.ts` é gerado e `client.ts` fornece consultas tipadas. Modificar o contrato exige regenerar e revisar o diff; o CI recusa arquivos gerados desatualizados. Testes HTTP verificam campos obrigatórios nas respostas reais.

Valores monetários das respostas existentes são números JSON. O cliente usa esses valores para apresentação e coordenadas de gráficos; o servidor mantém `BigDecimal` e é a autoridade de cálculos e saldos. Novos parâmetros monetários devem ser enviados como texto decimal, sem calcular saldos com ponto flutuante no navegador.

## Baixas e tentativas repetidas

`POST /api/v1/finance/entries/{id}/pay` aceita `requestId` UUID opcional. A chave pertence à empresa, é persistida em `entry_payments` e possui índice único. O bloqueio do lançamento serializa tentativas simultâneas; mesma chave, lançamento, data e valor devolvem o estado corrente sem outro pagamento. Conteúdo divergente retorna 409. Integrações antigas continuam aceitas sem chave, com o comportamento anterior; clientes novos devem fornecê-la. Abrir outro formulário cria outra operação.

## Próximas entregas acordadas

- 0.34: dashboard e lançamentos em React, navegação e filtros persistentes na URL, componentes acessíveis e detalhes integrados.
- 0.35: simulação comparável ao fluxo existente, hipóteses separadas dos registros financeiros e origem dos valores consultável.

A divisão inicial mantém um monólito modular. Novos módulos devem expor serviços e DTOs próprios, evitando que controllers façam cálculos ou que componentes alterem entidades diretamente.
