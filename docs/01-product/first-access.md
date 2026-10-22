# Primeiro acesso e plano inicial — 0.19.0

Ao criar o acesso e a empresa pelo formulário do Fintry, o usuário entra automaticamente e vê **Plano e assinatura**, com a comparação dos planos Essencial, Profissional e Gestão.

No modo comercial, a empresa já começa com **Profissional em teste gratuito por 14 dias**. O prazo começa no cadastro, é mantido pelo servidor e não é reiniciado ao comparar planos, entrar no financeiro, recarregar a página ou entrar novamente. A apresentação mostra a data de término real. Esse plano inicial já fazia parte da regra de cadastro; a 0.19.0 acrescenta a apresentação imediata das opções.

- **Continuar com o teste** abre o dashboard, mantendo o plano e o prazo existentes.
- **Comparar planos** leva aos cartões. Solicitar um plano continua sendo uma ação separada; não ativa acesso pago nem emite cobrança.
- O cadastro e a apresentação não criam faturas, pagamentos, solicitações ou autorização de recorrência.
- Login normal e convites continuam abrindo o dashboard. A apresentação é do fluxo de cadastro, sem obrigar o usuário a contratar.
- Em `EVALUATION`, a apresentação identifica o ambiente e suas simulações; não apresenta o ambiente local como assinatura comercial ou teste pago ativo.

Quando o teste comercial termina, as regras de consulta/exportação e contratação continuam valendo. A apresentação não cria plano gratuito permanente, muda preços ou substitui consentimento para cobrança/recorrência.

A conta Asaas do operador é configurada no servidor para receber as mensalidades do SaaS. O usuário não precisa criar uma conta Asaas para cadastrar seu acesso no Fintry. Veja [abertura e configuração da conta recebedora](../07-devops/asaas-account.md).
