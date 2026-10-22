# Conta Asaas real para receber mensalidades do Fintry

Esta conta é a do responsável pelo Fintry, que receberá as mensalidades do SaaS. As contas usadas por empresas clientes para emitir suas próprias cobranças continuam separadas. O cadastro de um usuário no Fintry não depende de uma conta Asaas.

## Abrir a conta e obter acesso à API

1. Acesse [asaas.com](https://www.asaas.com/) e selecione **Criar conta grátis**. Cadastre e-mail e senha.
2. Complete o cadastro e envie os documentos solicitados no processo de abertura. Confira o tipo de cadastro disponível e adequado à sua situação na plataforma.
3. Para gerar a chave de integração, o cadastro precisa estar aprovado e os dados comerciais, como atividade e faturamento, preenchidos.
4. Pela interface web, como Administrador, acesse **Menu do usuário → Integrações → Chaves API → Gerar chave de API**. Identifique a chave, por exemplo, como `Fintry produção`, e conclua a confirmação pelo método solicitado pelo Asaas.
5. Guarde a chave imediatamente em local protegido. Ela aparece apenas uma vez. Não a coloque no código, no Git, no navegador ou em mensagens. As chaves de Sandbox e Produção são diferentes.

Se a geração da chave estiver desabilitada, confira **Menu do usuário → Minha conta → Informações → Dados comerciais**, conforme a orientação oficial. A abertura gratuita não significa que todos os recebimentos ou serviços sejam gratuitos: confira as [condições e tarifas atuais](https://www.asaas.com/precos-e-taxas) antes de definir os preços do SaaS.

## Conectar ao Fintry

A configuração fica no arquivo de ambiente protegido do servidor, como `deploy/production.env`, fora do Git. Consulte [mensalidades integradas](../05-api/subscription-payments.md) para os campos completos.

| Configuração | Finalidade |
|---|---|
| `SAAS_PAYMENTS_ACCOUNT` | Identificador estável para a conta recebedora nesta instalação; não é a chave API |
| `SAAS_PAYMENTS_API_KEY` | Chave da conta Asaas de produção do próprio Fintry |
| `SAAS_PAYMENTS_WEBHOOK_TOKEN` | Token exclusivo para validar as notificações recebidas |
| `SAAS_PAYMENTS_ENVIRONMENT` | `PRODUCTION` para a conta real |
| `SAAS_PRICE_ESSENTIAL`, `SAAS_PRICE_PRO`, `SAAS_PRICE_BUSINESS` | Preços definidos pelo responsável comercial |
| `SAAS_PAYMENTS_ENABLED` | Mantenha `false` durante a preparação; habilite após validar a instalação e a integração |

Em **Menu do usuário → Integrações → Webhooks → Criar Webhook**, cadastre o endereço público HTTPS:

```
https://SEU-DOMINIO/webhooks/fintry-subscription/asaas
```

Configure o token exclusivo e os eventos de cobrança descritos no [guia de mensalidades](../05-api/subscription-payments.md). O Asaas precisa alcançar esse endereço pela internet; `localhost` do seu computador não é o endereço público da instalação. O webhook não é a rota das cobranças emitidas pelos clientes do Fintry.

A conta real, os preços, a publicação HTTPS e a homologação são etapas distintas. Criar a conta Asaas não publica o Fintry e não ativa automaticamente a cobrança. Atualize somente o serviço da aplicação conforme o [guia de publicação](deployment.md), preservando volumes e backups. A [recorrência](../05-api/subscription-renewals.md) ainda exige autorização explícita por empresa, mesmo com a rotina do servidor habilitada.

## Referências oficiais

Conferidas em 22/10/2026:

- [Como criar minha conta no Asaas](https://central.ajuda.asaas.com/hc/pt-br/articles/31406336360987-Como-criar-minha-conta-no-Asaas).
- [Como gerar uma chave API e conferir dados comerciais](https://central.ajuda.asaas.com/hc/pt-br/articles/33618186066331-Como-gerar-uma-nova-chave-de-API-no-Asaas).
- [Chaves de API: ambientes e armazenamento](https://docs.asaas.com/docs/chaves-de-api).
- [Configurar um Webhook](https://central.ajuda.asaas.com/hc/pt-br/articles/55668829191195-Como-criar-e-configurar-um-Webhook-no-Asaas).
