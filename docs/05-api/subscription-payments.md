# Mensalidades do próprio Fintry — 0.17.0

A conta recebedora é a do operador do Fintry. Ela é configurada no servidor, separada das conexões Asaas que cada empresa usa para cobrar seus clientes. A mensalidade não cria receitas, tarifas nem movimentações no financeiro da empresa assinante.

## O que está disponível

1. O Administrador solicita um plano em **Plano e assinatura**.
2. Havendo preço e cobrança habilitados, **Gerar mensalidade** apresenta valor, 30 dias de acesso, forma Pix/boleto e consentimento. Nome e CPF/CNPJ são enviados ao Asaas; o Fintry não persiste esses dados do pagador na mensalidade, nem envia e-mail ou ativa notificações do provedor.
3. A fatura hospedada no Asaas abre em outra aba. O vencimento é três dias após a emissão, no calendário de São Paulo. Nenhum dado de cartão passa pelo Fintry.
4. O webhook autenticado consulta a cobrança pela API. Somente `RECEIVED` em produção, com dados compatíveis, ativa o plano. `CONFIRMED` aguarda recebimento; Sandbox nunca libera acesso comercial.
5. O período é de **30 dias corridos**, contado da confirmação pelo Fintry ou do fim do período pago vigente, o que ocorrer depois. O teste gratuito não é somado. Cada fatura concede um único período, inclusive após entregas repetidas ou fora de ordem.

A renovação exige nova solicitação e confirmação de emissão pelo administrador. **Não há débito automático, assinatura recorrente no Asaas, geração agendada de mensalidades, cartão, nota fiscal ou aviso automático de inadimplência nesta versão.** O cancelamento do período na página não cancela uma fatura já emitida: ela tem uma ação própria de cancelamento.

Acesso legado ou simulado exige atendimento comercial antes da migração. Mudanças de plano durante período pago também exigem atendimento; a renovação integrada pode estender o plano atual. Preços são apresentados antes da emissão, sem rateio ou desconto implícito. Preço alterado exige atualizar a página; faturas existentes mantêm o valor registrado.

## Configuração do operador

No arquivo de ambiente protegido, configure:

| Variável | Uso |
|---|---|
| `SAAS_PAYMENTS_ENABLED` | `false` por padrão; `true` habilita emissão em modo `COMMERCIAL` |
| `SAAS_PAYMENTS_ENVIRONMENT` | `SANDBOX` por padrão; `PRODUCTION` emite cobranças reais |
| `SAAS_PAYMENTS_ACCOUNT` | Identificador estável da conta recebedora, 3–100 letras, números, `_` ou `-` |
| `SAAS_PAYMENTS_API_KEY` | Chave da conta Asaas do próprio Fintry |
| `SAAS_PAYMENTS_WEBHOOK_TOKEN` | Token exclusivo de 32–200 letras, números, `_` ou `-`, diferente da chave API |
| `SAAS_PRICE_ESSENTIAL`, `SAAS_PRICE_PRO`, `SAAS_PRICE_BUSINESS` | Preços em reais, ponto decimal, até duas casas, mínimo 5.00; vazio impede emissão integrada para o plano |

Nenhum preço é predefinido ou publicado por esta entrega. A configuração é passada tanto no Compose local quanto no de produção. Após editar o ambiente, recrie somente o serviço `app`, conforme o guia de publicação, sem apagar volumes. Chaves e tokens não são retornados nas APIs, no diagnóstico nem na auditoria. Preserve o arquivo de ambiente fora do repositório, com acesso restrito e cópia protegida.

Cadastre na **conta do próprio Fintry** o webhook HTTPS:

```
https://SEU-DOMINIO/webhooks/fintry-subscription/asaas
```

Use o token exclusivo e os eventos de cobrança, incluindo alterações, recebimento, exclusão/restauração e estornos. Esse endereço não é o webhook das cobranças dos clientes. O corpo é limitado a 64 KiB; somente essa rota POST dispensa CSRF e exige autenticação pelo token. POSTs de gerenciamento exigem sessão, Administrador e CSRF, inclusive quando o teste já venceu.

A conta/ambiente são registrados na fatura e conferidos em cada consulta. Não troque a conta nem promova o ambiente de faturas abertas: conclua a homologação em instalação isolada ou resolva primeiro as faturas de teste. Rotação da chave da mesma conta e do token exige atualização coordenada. Não desabilite a integração enquanto houver faturas aguardando confirmação; o webhook precisa das credenciais para reconciliá-las. Identificador estável é responsabilidade do operador, não uma validação de titularidade pelo Fintry.

## Cancelamento e situações excepcionais

- **Cancelar mensalidade** confirma a exclusão no Asaas; apenas pendentes/vencidas são canceláveis no fluxo normal. O acesso já concedido é preservado. Depois da confirmação, cancele a solicitação para escolher outro plano.
- A intenção de cancelamento é gravada antes da chamada remota. Uma resposta incerta não permite liberação de acesso. Consulte novamente; se o cancelamento continuar pendente, repita a ação. Pagamento simultâneo ao cancelamento exige conferência comercial.
- **Consultar** recupera emissão com resposta incerta buscando a referência própria no provedor. Só um resultado compatível é aceito. Nunca reemite automaticamente após erro/timeout. Duplicar o envio do formulário retorna a mesma fatura.
- Se o processo parar após a reserva e antes de emitir, pode não existir cobrança externa. Ausência ou múltiplas correspondências exige atendimento; não apague a reserva nem tente uma segunda emissão sem conferir a conta Asaas.
- O uso é revalidado na confirmação. Pagamento recebido com excesso de capacidade fica em conferência sem ativar. Ajuste o uso ou procure atendimento e consulte novamente. O valor recebido não desaparece no provedor.
- Divergência de valor, cliente, referência, vencimento ou método impede ativação. Exclusão ou restauração de fatura cancelada nunca concede acesso automaticamente.
- Estorno, contestação ou mudança de pagamento após ativação fica em conferência; o Fintry preserva o período já concedido para resolução assistida. Repetir recebimento não concede um segundo período. Não há reembolso automático ou revogação automática do acesso pago.
- O script de ativação manual rejeita solicitações com fatura integrada não cancelada. Isso evita conceder o mesmo período por dois caminhos. Não faça alterações SQL diretas para contornar essa proteção.

## API e homologação

- `GET /api/v1/subscription/payments`: disponibilidade, ambiente e até 20 últimas mensalidades da empresa.
- `POST /api/v1/subscription/payments`: `requestId`, `price`, `method` (`PIX`/`BOLETO`), `payerName`, `document` e `accepted=true`.
- `POST /api/v1/subscription/payments/{id}/refresh`: consulta e reconciliação.
- `POST /api/v1/subscription/payments/{id}/cancel`: cancelar fatura pendente; não é reembolso.

Homologue com conta Sandbox e instalação descartável: emissão Pix/boleto, link, evento antes da resposta de criação, token inválido, repetição, resposta incerta, divergências e cancelamento/restauração. Sandbox deve continuar sem ativar plano comercial. Depois, confirme preços e condições com o responsável, configure produção e confira uma transação autorizada, renovação e cancelamento. A entrega usa servidor HTTP simulado; **não comprova homologação real do Sandbox, titularidade da conta ou recebimento em produção**.

Referências oficiais: [criar cobrança](https://docs.asaas.com/reference/criar-nova-cobranca), [consultar cobrança](https://docs.asaas.com/reference/recuperar-uma-unica-cobranca), [eventos de cobrança](https://docs.asaas.com/docs/webhook-para-cobrancas).
