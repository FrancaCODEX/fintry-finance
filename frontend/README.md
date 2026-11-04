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

A 0.39.0 adiciona `features/catalog` para produtos, serviços, vendas, propostas, contatos, categorias, equipe e contas. Consultas canceláveis e filtros na URL ficam no React; `catalogAction` entrega o retrato da listagem ao host antes de abrir os formulários existentes. O histórico de estoque usa navegação com URL para permitir retorno ao catálogo filtrado. Os estilos globais compartilhados estão em `redesign.css`, após os estilos do workspace.
