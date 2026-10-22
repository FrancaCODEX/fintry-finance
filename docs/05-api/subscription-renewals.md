# Geração recorrente de mensalidades — 0.18.0

O Fintry pode gerar as próximas faturas de Pix/boleto após autorização explícita do Administrador. A recorrência é executada pelo próprio Fintry; cada fatura continua exigindo pagamento. Não há débito automático, cartão ou assinatura mensal nativa no Asaas. O ciclo corresponde a **30 dias corridos**, não a um mês civil.

## Autorização e pagamento

Em **Plano e assinatura → Renovação de mensalidades**, o Administrador confere o preço atual e aceita a emissão recorrente até interromper a autorização. São registrados responsável, instante, plano, valor e origem. A autorização exige uma mensalidade de produção recebida e compatível com o plano ativo, consultada novamente no Asaas. Não é ativada por padrão, por atualização, por início do teste ou por contratação manual/legada. Sandbox nunca concede acesso comercial e não aceita esta autorização nesta versão.

O pagador e a forma Pix/boleto são reaproveitados da fatura recebida. O Fintry guarda o identificador do cliente Asaas, sem copiar CPF/CNPJ para a autorização. Alterar o cadastro desse cliente no Asaas é responsabilidade do operador. A emissão solicita juros e multa zero e não solicita envio postal. Não há envio de e-mail ou mensagem nesta etapa; as faturas são acessadas pelo painel.

A partir de três dias antes do fim do acesso pago, a rotina reserva uma mensalidade para o plano/valor autorizados. O vencimento é o dia do fim do período em São Paulo ou três dias após a emissão, o que ocorrer depois. O acesso só é estendido pelo recebimento confirmado, uma única vez por fatura, conforme as regras da [cobrança integrada](subscription-payments.md).

A aplicação precisa estar funcionando para gerar a fatura. Se ficar indisponível, a próxima execução pode gerar **uma** mensalidade de recuperação; não há cobrança acumulada de períodos em que não houve acesso. O novo período começa no maior entre a confirmação e o fim do acesso pago vigente.

## Interromper e pausar

- **Interromper emissões** impede novas reservas e preserva o acesso já pago. Uma reserva/emissão já iniciada pode terminar; ela precisa ser consultada e cancelada separadamente na lista de mensalidades. A ação não realiza estorno.
- **Programar encerramento** também desativa a autorização recorrente. **Manter assinatura** remove a preferência de encerramento, mas não autoriza novamente a recorrência.
- Uma fatura recorrente recebida após a interrupção libera o período pago, mantendo o encerramento programado e a autorização desativada.
- Mudança de preço suspende novas emissões, inclusive redução. O Administrador precisa conferir e autorizar o valor atual. Faturas existentes mantêm seu valor registrado.
- Solicitação pendente, mudança de plano, conta/ambiente diferente, pagamento de origem em conferência ou uso acima da capacidade pausam novas emissões. Uma fatura vencida não gera novas faturas em cascata.
- Cancelar uma fatura não recria automaticamente outra do mesmo ciclo. Mesmo interromper e autorizar novamente não apaga a reserva anterior. Renove manualmente ou procure atendimento.

## Execução e recuperação

A opção `SAAS_RENEWALS_SCHEDULER_ENABLED=true` habilita a rotina. O Compose de produção usa `true` por padrão; o Compose local e a execução Java direta usam `false`. A rotina ainda exige `SAAS_PAYMENTS_ENABLED=true`, modo comercial, ambiente de pagamentos de produção e autorização por empresa. Deixe os pagamentos desabilitados até definir preços, condições e homologar a conta recebedora.

A verificação ocorre a cada minuto, com início após um minuto. Até 100 autorizações são verificadas por rodada e cada uma recebe intervalo de uma hora até a próxima verificação. Isso limita consultas e dá oportunidade às demais empresas. Há bloqueio por empresa e vínculo único entre empresa e fim de período: múltiplas instâncias e verificações simultâneas não geram outra fatura do mesmo ciclo. O botão **Verificar renovação** verifica somente a empresa autenticada e pode antecipar a verificação de uma autorização existente; nunca autoriza recorrência sozinho.

Reserva, solicitação e vínculo do ciclo são confirmados no banco **antes** do POST de pagamento. Timeout ou resposta inconclusiva não provoca nova emissão automática. Use **Consultar** na mensalidade para recuperar a correspondência no Asaas. Se o processo parar depois da reserva e antes do POST, pode não existir fatura remota: a reserva fica preservada e exige conferência assistida. Não apague a reserva nem altere o banco para forçar repetição. Rotina desabilitada aparece no painel; o Administrador ainda pode verificar sua autorização pelo botão.

Depois de um backup/restauração, preserve autorizações, reservas e credenciais, confira a conta recebedora e as cobranças abertas antes de reativar a rotina. O backup PostgreSQL inclui as novas tabelas. Consulte o webhook e as mensalidades pendentes antes de interpretar uma reserva como falha de emissão.

## API

- `GET /api/v1/subscription/renewal`: autorização, estado, valor, datas e disponibilidade de nova autorização. Não expõe chave, token ou cadastro do pagador.
- `POST /api/v1/subscription/renewal/enable`: `sourceInvoiceId`, `price`, `revision`, `accepted=true`.
- `POST /api/v1/subscription/renewal/disable`: `revision`.
- `POST /api/v1/subscription/renewal/check`: verifica a empresa atual, respeitando autorização, ciclo e pendências.

POSTs exigem Administrador, sessão e CSRF. Continuam disponíveis após o vencimento para interromper/verificar uma autorização existente. Testes usam um servidor Asaas HTTP simulado; recorrência real, titularidade, disponibilidade da instalação e condições comerciais continuam dependendo de homologação. Não houve cobrança real nesta entrega. Pix Automático, assinatura Asaas, nota fiscal e avisos de inadimplência continuam pendentes.

Contrato do provedor: [criar cobrança com cliente existente](https://docs.asaas.com/reference/criar-nova-cobranca), [eventos de pagamento](https://docs.asaas.com/docs/webhook-para-cobrancas).
