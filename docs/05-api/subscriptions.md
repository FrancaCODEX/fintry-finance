# Planos e assinatura do Fintry — v0.16.0

Esta entrega organiza capacidade, teste, solicitações e períodos de acesso. A ativação é manual após conferência comercial do operador. Não cria assinatura recorrente no Asaas, não debita cartão, não gera boleto de mensalidade, não confirma pagamento e não usa as chaves das empresas para receber a mensalidade do Fintry.

## Catálogo inicial

| Plano | Pessoas | Contas financeiras | Comprovantes |
|---|---:|---:|---:|
| Essencial | 2 | 3 | 100 MiB |
| Profissional | 5 | 10 | 500 MiB |
| Gestão | 15 | 30 | 2 GiB |

Todos incluem os módulos existentes e lançamentos sem limite de quantidade. Pessoas ativas e convites pendentes ocupam vagas; convites vencidos permanecem reservados até serem revogados. Revogar libera a vaga e invalida o link. Contas contam pelo total cadastrado; não existe arquivamento de contas nesta versão. Comprovantes são somados por empresa, mantendo 5 MiB por arquivo e dez por lançamento. Valores de armazenamento na interface são exibidos em MB usando base 1024.

O catálogo fica em `saas_plans`, sem preços inventados: `monthly_price` é nulo inicialmente. Preços, termos e autorização de pagamento precisam ser definidos antes de vender o serviço. A ferramenta de ativação não verifica extratos nem calcula valores, impostos ou diferenças de plano.

## Ambientes e estados

- **EVALUATION**: padrão local/demo. Os dados normais ficam sem limites aplicados. O Administrador pode experimentar um plano: a empresa passa a SIMULATED e os limites são aplicados para avaliação. Não há pagamento. A simulação não reinicia o teste e não vira período pago ao mudar para produção. Não há retorno de simulação para acesso ilimitado pela interface nesta versão; escolher outro plano continua possível se o uso couber.
- **COMMERCIAL**: padrão obrigatório do perfil/Compose de produção. Novos cadastros recebem TRIAL Profissional por 14 dias a partir da criação, sem cartão ou cobrança automática. Apenas uma ativação manual concede ACTIVE até `paid_until`. Estado SIMULATED em modo comercial fica INACTIVE e exige ativação.
- **LEGACY**: empresas existentes no momento da migração recebem acesso preservado sem limite/expiração. A adesão a um plano comercial exige uma solicitação e aprovação explícitas; não há venda nem bloqueio retroativo. Experimentar um plano em avaliação substitui o estado legado por simulação: antes de publicar esse ambiente, confira e regularize o acesso de cada empresa.
- Ao terminar o teste/período, o acesso fica EXPIRED, ou CANCELLED se havia encerramento programado. O cálculo ocorre por requisição e não exige cron. Não apaga dados nem desativa usuários.
- Acesso vencido mantém leitura, exportações, download/remoção de comprovantes, segurança, administração dos acessos existentes e solicitações de plano. Novos convites/reativações, uploads e novas operações de negócio ficam bloqueados. Administradores ainda podem revogar acessos. Alterações financeiras recebem HTTP 402; limite de capacidade ou revisão desatualizada, 409.
- Webhooks de pagamentos e consulta/cancelamento de cobranças existentes continuam disponíveis. Recebimentos Asaas podem dar baixa e registrar a tarifa mesmo com assinatura SaaS vencida. Nenhuma quota limita lançamentos financeiros gerados por pagamento.

## Solicitação, capacidade e encerramento

O Administrador escolhe **Plano e assinatura → Solicitar plano** (ou renovação). A solicitação preserva o plano atual e não confirma contratação/pagamento. Há uma pendência por empresa; repetição da mesma solicitação devolve a existente, sem duplicar. Trocar a escolha exige cancelar a pendência. A revisão otimista evita operar sobre uma assinatura desatualizada. Usuários Financeiro e Consulta podem ver capacidade, mas não contratar, simular ou encerrar.

Mudanças para menor capacidade exigem que o uso caiba no plano. Nenhuma pessoa, conta ou arquivo é apagado para fazer caber. A aprovação do operador confere novamente os limites, pois o uso pode ter aumentado após a solicitação. Convites e arquivos podem ser removidos para liberar capacidade; se o total de contas excede um plano, escolha maior capacidade, pois ainda não há arquivamento de contas.

Encerrar uma assinatura ativa programa o fim no `paid_until`, preservando o período já concedido. Manter a assinatura retira essa preferência; não cobra nem renova automaticamente. A próxima renovação depende de nova solicitação/ativação. O histórico mostra as últimas dez solicitações, além dos eventos na auditoria.

## Ativação manual pelo operador

Exige acesso administrativo ao host/Compose e ao banco correspondente. A interface do cliente não possui endpoint para escolher ACTIVE, datas de validade, preços ou limites.

1. Confirme com a empresa plano, preço e condições. Verifique pagamento ou autorização de cortesia fora desta ferramenta. Nenhuma mensagem é enviada automaticamente.
2. Obtenha a referência UUID da solicitação mostrada na página Plano e assinatura.
3. No host da instalação, inspecione o alvo, plano e uso. Sem `--apply`, a ferramenta apenas consulta:

```sh
python3 scripts/subscription_ops.py --request UUID_DA_SOLICITACAO
```

4. Após a conferência comercial, ative o período explicitamente (1–366 dias). Use uma referência interna sem dados pessoais ou credenciais:

```sh
python3 scripts/subscription_ops.py --request UUID_DA_SOLICITACAO --days 30 --reference FATURA_INTERNA_123 --apply
```

A ferramenta usa `deploy/production.env` por padrão; aceita `--env-file`, `--overlay`, `--project` e `--local`, seguindo as mesmas verificações de alvo do `ops.py`. Não lê comandos livres nem expõe senha na saída. A operação bloqueia a empresa, verifica pendência e capacidade, ativa o plano, encerra a solicitação, incrementa revisão e grava auditoria numa única transação.

O período começa no maior entre agora e o vencimento pago existente, evitando perder dias já concedidos; uma mudança de plano é aplicada imediatamente, com o vencimento estendido. Conferência de diferença de preço e autorização de benefícios permanece com o operador. Repetir a mesma solicitação com a mesma referência e quantidade de dias não estende novamente nem reativa um acesso alterado depois; parâmetros diferentes para uma solicitação aprovada são recusados. O script não oferece reembolso ou alteração de uma aprovação existente: para novo período, registre uma nova solicitação.

Preserve os backups do banco, pois contêm catálogo, estados, solicitações e auditoria. Para empresas legadas/simuladas, confira a capacidade antes de ativar. Não transforme estados em ACTIVE por SQL manual fora do procedimento validado.

## API

Todos os caminhos usam `/api/v1/subscription` e a empresa da sessão, sem aceitar um companyId do navegador.

| Método | Caminho | Acesso |
|---|---|---|
| GET | `/` | Membro; estado, catálogo, uso e últimas solicitações |
| GET | `/access` | Membro; resumo de acesso para a interface |
| POST | `/simulate` | Administrador + CSRF; avaliação; `{plan,revision}` |
| POST | `/requests` | Administrador + CSRF; comercial; `{plan,revision}` |
| POST | `/requests/{id}/cancel` | Administrador + CSRF; `{revision}` |
| POST | `/cancel` | Administrador + CSRF; agenda encerramento; `{revision}` |
| POST | `/keep` | Administrador + CSRF; retira encerramento; `{revision}` |

Limites são conferidos sob bloqueio da empresa antes de criar contas, reservar novas vagas ou enviar comprovantes. A mesma transação mantém o bloqueio até concluir; importação de dados de exemplo não contorna os limites e sofre rollback integral quando excede capacidade. O modo de produção recusa EVALUATION no início da aplicação.

## Próxima evolução comercial

Definir preços e termos, homologar a conta recebedora do próprio Fintry e implementar checkout/renovação/cobrança recorrente autenticada, sincronização de eventos, recuperação de inadimplência, comunicação autorizada e emissão fiscal. Essa integração deverá permanecer separada das cobranças emitidas pelas empresas aos seus clientes. Nenhuma venda ou transação real foi executada nesta entrega.

## Evolução na 0.17.0

A emissão integrada de mensalidades, preços configuráveis e ativação por recebimento confirmado estão documentados em [mensalidades do Fintry](subscription-payments.md). A ativação manual continua disponível para solicitações sem fatura integrada ou com fatura cancelada. A [0.18.0 acrescenta geração recorrente autorizada](subscription-renewals.md); débito automático continua pendente. As regras acima descrevem a base entregue na 0.16.0.
