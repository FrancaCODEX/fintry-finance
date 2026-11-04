# Fintry workspace

React + TypeScript, integrado ao mesmo domínio do Spring Boot. Node 22.12+ é necessário apenas para desenvolvimento/build; o Docker entrega os assets junto do backend.

```bash
npm ci
npm run contracts:check
npm test
npm run build
```

Depois, inicie o backend conforme o README principal. Ao alterar o frontend, refaça o build e reinicie o backend se estiver usando o JAR empacotado. Em `spring-boot:run`, os assets são lidos dos recursos do projeto.

- `src/app`: fronteira com o host, navegação e URL.
- `src/api`: consultas e tipos gerados do OpenAPI parcial.
- `src/shared`: componentes acessíveis, apresentação e consultas canceláveis.
- `src/features`: componentes e estado de cada área.
- `e2e`: testes de aceitação com dados fictícios isolados por empresa.

```bash
npm run format
npm run format:check
npx playwright install chromium
# Requer backend/target/fintry.jar construído após npm run build.
npm run test:e2e
```

Os testes de navegador iniciam uma aplicação na porta 8091 com H2 em memória. `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` permite usar um Chrome/Chromium já instalado. Não aponte os testes para uma instalação com dados reais.

Valores monetários são apresentados no cliente. Cálculos, permissões, saldos, transações e validações financeiras continuam no backend. O painel global de detalhes e os formulários existentes são reutilizados pela fronteira do workspace durante a migração.

A 0.39.0 cobre as 26 páginas do menu. `features/catalog` contém oito áreas de catálogo; `features/planning` reúne pendências, relatórios, projeção, séries e orçamentos; `features/management` reúne estoque, auditoria, empresa, segurança, ambiente e exemplos. Importações CSV, cobrança e assinatura possuem módulos próprios. O registro de páginas é exaustivo em TypeScript: adicionar uma rota exige registrar seu componente.

Consultas e filtros ficam no React. O host mantém sessão, navegação estrutural e diálogos comerciais/operacionais. `catalogAction` fornece o retrato da listagem antes de abrir os diálogos existentes; `hostAction` sincroniza apenas o contexto de relatórios/cobrança/assinatura necessário para suas ações. Dados financeiros e permissões continuam sendo validados no servidor. A importação CSV exige prévia e confirmação, limpa a seleção ao trocar arquivo/conta e impede o uso de resultados obsoletos.

Os estilos globais compartilhados estão em `redesign.css`, após os estilos do workspace. Não há fallback para páginas HTML quando o bundle falha: o erro permite recarregar e tentar novamente.
