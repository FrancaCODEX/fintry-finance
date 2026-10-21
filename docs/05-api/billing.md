# Cobrança integrada — v0.15.0

Primeiro provedor: Asaas v3, Pix convencional e boleto avulso para receitas já existentes. A integração cobra os clientes da empresa; os planos e a assinatura do próprio Fintry continuam no backlog. Não manipula cartões, Pix Automático, split, juros, descontos ou estorno automático.

## Configuração e homologação

1. O operador gera `openssl rand -base64 32` e configura `BILLING_MASTER_KEY` no ambiente do aplicativo. A configuração de cobranças fica desabilitada sem essa chave; o restante da aplicação funciona normalmente. Preserve a chave em cofre separado do banco e teste sua recuperação: restaurar o banco sem ela não permite abrir as credenciais. Não compartilhe a chave no Git ou em logs.
2. O Administrador abre **Cobranças → Conexão Asaas**, escolhe **Testes (Sandbox)** e informa chave API e um token exclusivo para webhook (32–200 caracteres em letras/números/`_`/`-`, diferente da API key). As credenciais são cifradas com AES-256-GCM, nonce aleatório e contexto da empresa/finalidade, sem retorno ao navegador ou auditoria.
3. Copie a URL `/webhooks/asaas/{connectionId}` exibida ao reabrir o formulário. Configure-a no painel Asaas no mesmo ambiente, com o token informado e os eventos de cobranças (criação, atualização, confirmação, recebimento, vencimento, exclusão, restauração, estornos e contestações). A instalação publicada precisa de HTTPS público. Webhooks autenticam `asaas-access-token`; apenas o POST dessa rota dispensa sessão e CSRF. Payload limitado a 64 KiB.
4. Cadastre uma conta financeira que represente o **saldo no Asaas**, um cliente ativo com CPF/CNPJ válido no provedor e uma receita pendente com esse cliente e vencimento atual/futuro. Emita Pix ou boleto. As cobranças usam cadastro de pagador próprio com notificações desabilitadas; não reutilizamos clientes do painel Asaas para não alterar suas preferências. O Fintry não envia mensagens para clientes.
5. Homologue no Sandbox real: emissão, links, Pix copia e cola, simulação de pagamento, callback, cancelamento e indisponibilidade. **Sandbox não dá baixa e não registra tarifas**, mesmo que o provedor informe RECEIVED. Evite usar recebimentos reais para testes, pois cada lançamento admite uma única emissão nesta versão.
6. Depois da homologação, informe as credenciais reais escolhendo Produção. A promoção é unidirecional; mantém a conexão antiga do Sandbox e seus webhooks. Cobranças existentes sempre consultam sua própria conexão. Uma conexão que já emitiu cobranças não pode trocar de conta/chaves; rotação de credenciais ainda requer manutenção assistida, preservando a identidade da conta Asaas. Não apague conexões nem altere cifras diretamente. Produção não volta para Sandbox.
7. Faça a primeira cobrança real somente após validar a conta, recebimento e tarifas no Asaas. Nenhuma credencial real foi fornecida ou testada durante a implementação; a homologação real é uma etapa operacional pendente.

Os hosts do provedor são fixos (`api-sandbox.asaas.com/v3` e `api.asaas.com/v3`), sem URL arbitrária configurável ou redirecionamentos HTTP. URLs de fatura/boleto aceitam somente HTTPS nos hosts Asaas autorizados. Credenciais e respostas completas do provedor não são registradas em logs.

## Fluxo financeiro e falhas

- A reserva local é confirmada antes da primeira chamada externa; o vínculo único por lançamento impede que cliques simultâneos, repetição de POST ou resposta perdida gerem outra emissão. O banco usa novas tabelas Flyway; dados e migrações anteriores são preservados.
- Respostas inconclusivas ficam em **Revisar no Asaas**. **Consultar Asaas** procura o `externalReference` exclusivo `fintry-{chargeId}` e só adota um resultado único e compatível. Não reenvia POST de cobrança ou de cliente. Zero/múltiplos resultados permanecem bloqueados para conferência; nesta versão não há liberação automática, reemissão ou exclusão da reserva.
- Webhook é um aviso para consultar a API autenticada da conexão. Status, valores e datas do corpo do evento não dão baixa. Eventos repetidos ou antigos consultam o estado atual; a data de liquidação persistida impede duplicar receita/tarifa. Eventos desconhecidos são reconhecidos sem efeito, pois a conta Asaas pode ter outras cobranças. Se o aviso chegar antes da gravação do ID remoto, use a consulta manual para recuperar a emissão.
- CONFIRMED mantém a receita pendente. Em Produção, apenas RECEIVED compatível com ID, cliente, referência, forma, valor e vencimento dá baixa. A data é `creditDate` (disponibilidade no Asaas), sem datas futuras. `netValue` deve estar entre zero e o bruto, com duas casas decimais. Ausência/divergência exige revisão.
- Receita bruta é realizada uma vez; diferença para o líquido vira despesa realizada **Tarifas de cobrança** na mesma conta/data. Ex.: R$ 100,00 bruto e R$ 97,50 líquido geram receita de R$ 100,00 e tarifa de R$ 2,50. Uma transferência posterior ao banco deve ser registrada como transferência, sem nova receita. A conciliação atual não agrupa receita e tarifa contra um único crédito líquido do extrato.
- Edição, baixa manual e cancelamento de lançamento/venda/série com cobrança ativa são bloqueados, assim como o cancelamento isolado da tarifa. Após cancelamento confirmado no provedor, a receita fica disponível para operações manuais. O cancelamento de cobrança não cancela venda, não retorna estoque e não apaga lançamento; não permite reemitir a mesma receita nesta versão.
- Estornos/contestações ou mudança de valor/data após liquidação geram revisão e preservam o financeiro e o estoque. Conferência e ajuste contábil exigem operação assistida nesta versão; não há reembolso pela API do Fintry. RECEIVED_IN_CASH também exige revisão, sem baixa automática.
- Histórico de conexão, reserva e transições do provedor é registrado sem segredos. Atores de sincronização aparecem como Integração Asaas.

## API

| Método | Caminho (prefixo `/api/v1`) | Acesso |
|---|---|---|
| GET | `/billing/connection` | Membro da empresa, metadados sem segredos |
| PUT | `/billing/connection` | Administrador + CSRF; environment, apiKey, webhookToken |
| GET | `/billing?page=0` | Membro; paginação de 20 |
| POST | `/billing` | Administrador/Financeiro + CSRF; entryId, method PIX/BOLETO |
| POST | `/billing/{id}/refresh` | Administrador/Financeiro + CSRF; consulta/recupera emissão |
| POST | `/billing/{id}/cancel` | Administrador/Financeiro + CSRF; pendente/vencida |
| POST | `/billing/{id}/pix` | Administrador/Financeiro + CSRF; código copia e cola |

POST `/webhooks/asaas/{connectionId}` fica fora do prefixo e usa token próprio. Falha da API retorna erro para permitir repetição do webhook; divergências persistidas retornam sucesso e ficam em revisão. Esta versão usa consulta manual como recuperação, sem fila persistente de eventos nem tarefa de sincronização agendada.

Referências oficiais consultadas: [criação de cobrança](https://docs.asaas.com/reference/criar-nova-cobranca), [cadastro de pagador](https://docs.asaas.com/reference/criar-novo-cliente), [eventos](https://docs.asaas.com/docs/webhook-para-cobrancas), [autenticação de webhook](https://docs.asaas.com/docs/receba-eventos-do-asaas-no-seu-endpoint-de-webhook).
