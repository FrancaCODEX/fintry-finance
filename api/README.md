# Contratos do workspace

`workspace.openapi.json` cobre consultas de contas, indicadores, lançamentos, detalhes e projeção. A autenticação usa a sessão HTTP existente; a empresa sempre vem da sessão, nunca de um parâmetro selecionado pelo cliente.

```bash
cd frontend
npm ci
npm run contracts
npm run contracts:check
npm run typecheck
npm test
```

Os tipos gerados são versionados para revisão. O contrato é parcial; consulte `docs/05-api/endpoints.md` para as outras operações, erros e permissões. O transporte compartilhado mantém o token CSRF na memória, envia cookies apenas na mesma origem e não repete mutações automaticamente.
