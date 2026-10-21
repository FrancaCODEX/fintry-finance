"use strict";
const subscription = { data: null, payments: null };
function resetSubscription() {
  subscription.data = null;
  subscription.payments = null;
  state.subscriptionAccess = null;
}
const subscriptionStatus = {
  EVALUATION: "Avaliação",
  SIMULATED: "Plano em simulação",
  LEGACY: "Acesso preservado",
  TRIAL: "Período de teste",
  ACTIVE: "Assinatura ativa",
  EXPIRED: "Período encerrado",
  CANCELLED: "Assinatura encerrada",
  INACTIVE: "Ativação necessária",
};
function subscriptionDate(value) {
  return value
    ? new Intl.DateTimeFormat("pt-BR", {
        dateStyle: "medium",
        timeStyle: "short",
        timeZone: "America/Sao_Paulo",
      }).format(new Date(value))
    : "—";
}
function subscriptionSize(value) {
  return (
    new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 1 }).format(
      value / 1048576,
    ) + " MB"
  );
}
async function loadSubscription(id) {
  const [data, payments] = await Promise.all([
    api("/subscription"),
    api("/subscription/payments"),
  ]);
  if (id !== requestId || !state.user || state.page !== "subscription") return;
  subscription.data = data;
  subscription.payments = payments;
  shell();
  const selected = data.plans.find((p) => p.code === data.plan),
    evaluation = data.mode === "EVALUATION",
    pending = data.requests.find((r) => r.status === "PENDING");
  const meter = (name, value, limit, bytes = false) =>
    `<article class="card plan-usage"><span>${name}</span><strong>${bytes ? subscriptionSize(value) : value}<small> / ${data.limited ? (bytes ? subscriptionSize(limit) : limit) : "sem limite aplicado"}</small></strong><progress value="${data.limited ? Math.min(value, limit) : 0}" max="${limit}" aria-label="Uso de ${name}"></progress><p>${data.limited && value >= limit ? "Limite atingido. Confira as opções de plano." : name === "Pessoas" ? "Pessoas ativas e convites pendentes ocupam vagas." : name === "Comprovantes" ? "Remover anexos libera espaço; os lançamentos ficam preservados." : "Contas financeiras cadastradas na empresa."}</p></article>`;
  document.querySelector("#page-content").innerHTML =
    head(
      "Um plano para o seu momento.",
      "Acompanhe seu uso e escolha a capacidade que combina com a sua empresa.",
      '<button class="btn secondary" data-action="subscription-refresh">Atualizar uso</button>',
    ) +
    `<section class="card plan-hero"><div><span class="eyebrow">PLANO E ASSINATURA · FINTRY</span><h2>${data.status === "LEGACY" ? "Seu acesso continua preservado" : selected.name}</h2><p>${evaluation ? "Ambiente de avaliação: experimente limites e recursos sem cobrança ou contratação real." : data.status === "LEGACY" ? "Esta empresa já utilizava o Fintry. A atualização mantém o acesso anterior, sem impor limites retroativos." : data.status === "TRIAL" ? "Seu teste de 14 dias inclui a capacidade do plano Profissional. Nenhuma cobrança é criada ao iniciar o teste." : !data.writable ? "Continue consultando e exportando seus dados. Solicite um plano para voltar a registrar novas operações." : "Seu período de acesso está confirmado. A renovação é feita por uma nova mensalidade ou pelo atendimento comercial."}</p><span class="badge ${data.writable ? "paid" : "cancelled"}">${e(subscriptionStatus[data.status])}</span>${!evaluation && !["LEGACY", "INACTIVE"].includes(data.status) ? `<p class="plan-validity">${data.status === "TRIAL" ? "Teste até" : "Acesso até"} <strong>${subscriptionDate(data.status === "TRIAL" ? data.trialEndsAt : data.paidUntil)}</strong>${data.cancelAtPeriodEnd ? " · Encerramento programado ao final do período." : ""}</p>` : ""}</div>${isAdmin() && !evaluation && data.status === "ACTIVE" ? `<button class="btn secondary" data-action="subscription-${data.cancelAtPeriodEnd ? "keep" : "cancel"}">${data.cancelAtPeriodEnd ? "Manter assinatura" : "Programar encerramento"}</button>` : ""}</section>` +
    `<section class="plan-usage-grid" aria-label="Uso do seu plano">${meter("Pessoas", data.usage.members, selected.members)}${meter("Contas", data.usage.accounts, selected.accounts)}${meter("Comprovantes", data.usage.storageBytes, selected.storageBytes, true)}</section>` +
    `${pending ? `<section class="card plan-request"><div><span class="eyebrow">SOLICITAÇÃO EM ANDAMENTO</span><h2>${e(data.plans.find((p) => p.code === pending.plan).name)}</h2><p>Confira o preço e a forma de pagamento antes de gerar a mensalidade. Seu plano atual continua valendo até a confirmação.</p><label class="field">Referência da solicitação<input readonly value="${pending.id}"></label></div>${isAdmin() ? `${payments.enabled && !payments.invoices.some((i) => i.requestId === pending.id) && data.plans.find((p) => p.code === pending.plan).monthlyPrice != null && !["LEGACY", "INACTIVE"].includes(data.status) ? `<button class="btn" data-action="subscription-checkout" data-id="${pending.id}">Gerar mensalidade</button>` : ""}<button class="btn secondary" data-action="subscription-request-cancel" data-id="${pending.id}">Cancelar solicitação</button>` : ""}</section>` : ""}` +
    `<div class="plan-section-heading"><h2>Escolha a capacidade ideal</h2><p>Todos os planos incluem controle financeiro, relatórios, produtos, vendas e conciliação. As tarifas do provedor de pagamentos são separadas.</p></div><section class="plan-catalog" aria-label="Comparação de planos">${data.plans.map((p) => `<article class="card plan-option ${p.code === data.plan ? "is-current" : ""}"><span class="eyebrow">${p.code === data.plan ? "SEU PLANO ATUAL" : "FINTRY"}</span><h3>${e(p.name)}</h3><p class="plan-description">${e(p.description)}</p><div class="plan-price">${p.monthlyPrice == null ? "Condições a definir" : money(p.monthlyPrice) + "<small> / mês</small>"}</div><ul><li><strong>${p.members}</strong> pessoas, incluindo convites</li><li><strong>${p.accounts}</strong> contas financeiras</li><li><strong>${subscriptionSize(p.storageBytes)}</strong> para comprovantes</li><li>Lançamentos sem limite de quantidade</li><li>Todos os módulos financeiros e comerciais</li></ul>${isAdmin() ? `<button class="btn ${p.code === data.plan ? "secondary" : ""}" data-action="subscription-${evaluation ? "simulate" : "request"}" data-plan="${p.code}" ${pending ? "disabled" : ""}>${evaluation ? "Experimentar plano" : p.code === data.plan && data.status === "ACTIVE" ? "Solicitar renovação" : "Solicitar plano"}</button>` : '<p class="notice">Peça ao administrador para gerenciar o plano.</p>'}</article>`).join("")}</section><p class="notice">${evaluation ? "As mudanças feitas aqui são simulações. Elas não se tornam uma assinatura comercial quando o ambiente é publicado." : "Escolher um plano registra uma solicitação. Quando o pagamento integrado estiver disponível, você pode gerar uma mensalidade para 30 dias de acesso. Só o recebimento confirmado libera o período. Não há débito automático nem geração recorrente; cada renovação exige sua confirmação. O atendimento comercial continua disponível."} Uma mudança para menor capacidade exige que o uso atual caiba no plano. Seus dados não são apagados ao vencer ou encerrar o acesso.</p>` +
    subscriptionPayments(payments, data) +
    `<section class="card plan-history"><h2>Últimas solicitações</h2>${data.requests.length ? data.requests.map((r) => `<article><div><strong>${e(data.plans.find((p) => p.code === r.plan).name)}</strong><small>${subscriptionDate(r.createdAt)}${r.activeUntil ? " · Acesso até " + subscriptionDate(r.activeUntil) : ""}</small></div><span class="badge ${r.status === "APPROVED" ? "paid" : r.status === "PENDING" ? "pending" : "cancelled"}">${{ PENDING: "Aguardando ativação", APPROVED: "Ativada", CANCELLED: "Cancelada" }[r.status]}</span></article>`).join("") : '<p class="muted">Nenhuma solicitação registrada. As simulações ficam no histórico de alterações.</p>'}</section>`;
}
async function subscriptionAction(button) {
  const action = button.dataset.action;
  if (!action?.startsWith("subscription-")) return false;
  if (action === "subscription-refresh") {
    await safeLoad();
    return true;
  }
  if (!isAdmin()) return true;
  const data = subscription.data,
    userId = state.user.id,
    revision = data.revision;
  const check = () => {
    if (state.user?.id !== userId || !isAdmin())
      throw new Error("Sua sessão mudou. Atualize a página.");
  };
  if (action === "subscription-simulate" || action === "subscription-request") {
    const plan = data.plans.find((p) => p.code === button.dataset.plan),
      simulate = action.endsWith("simulate");
    showDialog(
      simulate ? "Experimentar " + plan.name : "Solicitar " + plan.name,
      simulate
        ? "Simulação no ambiente de avaliação."
        : "A solicitação será registrada para conferência comercial.",
      `<p>${simulate ? "Os limites do plano serão aplicados nesta empresa para você conhecer a experiência. Nenhuma cobrança será criada." : "O plano atual continuará valendo até a ativação. O operador do Fintry precisa confirmar o preço, as condições e o pagamento ou autorização antes de liberar o período."}</p><p class="notice">${plan.members} pessoas · ${plan.accounts} contas · ${subscriptionSize(plan.storageBytes)} para comprovantes. O uso atual precisa caber nesses limites.</p>`,
      async () => {
        check();
        await send("/subscription/" + (simulate ? "simulate" : "requests"), {
          plan: plan.code,
          revision,
        });
        toast(
          simulate
            ? "Plano aplicado em simulação."
            : "Solicitação registrada. Aguarde a ativação.",
        );
      },
      simulate ? "Aplicar simulação" : "Registrar solicitação",
    );
  }
  if (action === "subscription-checkout") {
    const pending = data.requests.find((r) => r.id === button.dataset.id);
    const plan = data.plans.find((p) => p.code === pending.plan);
    showDialog(
      "Mensalidade · " + plan.name,
      "Confira as condições antes de emitir.",
      `<div class="subscription-order"><span>Total desta mensalidade</span><strong>${money(plan.monthlyPrice)}</strong><small>30 dias de acesso · pagamento único</small></div><p class="notice">${subscription.payments.environment === "PRODUCTION" ? "Esta ação gera uma cobrança real para o Fintry." : "Sandbox: cobrança de teste, sem ativação de acesso comercial."} O vencimento será em três dias. O período começa após o recebimento confirmado, ou ao final do acesso pago vigente, se houver. Não há débito automático. Uma mudança de plano durante um período pago precisa de atendimento comercial.</p>${field("Nome ou razão social do pagador", "payerName", "text", 'required maxlength="120" autocomplete="organization"')}${field("CPF ou CNPJ do pagador (somente números)", "document", "text", 'required inputmode="numeric" pattern="[0-9]{11}|[0-9]{14}" maxlength="14" autocomplete="off"')}<label class="field">Pagamento<select name="method"><option value="PIX">Pix</option><option value="BOLETO">Boleto</option></select></label><label class="check"><input type="checkbox" name="accepted" required> Aceito o valor e o período de 30 dias, sem renovação automática. Os dados do pagador serão enviados ao Asaas para emitir a cobrança.</label>`,
      async (form) => {
        check();
        const invoice = await send("/subscription/payments", {
          requestId: pending.id,
          price: plan.monthlyPrice,
          method: form.method,
          payerName: form.payerName,
          document: form.document,
          accepted: form.accepted === "on",
        });
        toast(
          invoice.status === "REVIEW"
            ? "Emissão não confirmada. Consulte a mensalidade antes de continuar."
            : "Mensalidade gerada. Abra a fatura para pagar.",
        );
      },
      "Gerar " + money(plan.monthlyPrice),
    );
  }
  if (action === "subscription-payment-refresh") {
    check();
    button.disabled = true;
    try {
      await send(`/subscription/payments/${button.dataset.id}/refresh`, {});
      await safeLoad();
      toast("Mensalidade consultada.");
    } finally {
      button.disabled = false;
    }
  }
  if (action === "subscription-payment-cancel") {
    showDialog(
      "Cancelar mensalidade?",
      "O acesso já concedido será preservado.",
      "<p>A cobrança pendente será cancelada no Asaas. Aguarde a confirmação e depois cancele a solicitação para escolher outro plano. Se o pagamento já tiver sido feito, procure o atendimento comercial.</p>",
      async () => {
        check();
        await send(`/subscription/payments/${button.dataset.id}/cancel`, {});
        toast("Cancelamento consultado. Confira a situação da mensalidade.");
      },
      "Cancelar mensalidade",
    );
  }
  if (action === "subscription-request-cancel")
    showDialog(
      "Cancelar solicitação?",
      "Seu acesso atual será preservado.",
      "<p>A contratação pendente será cancelada. Você poderá solicitar outro plano depois.</p>",
      async () => {
        check();
        await send(`/subscription/requests/${button.dataset.id}/cancel`, {
          revision,
        });
        toast("Solicitação cancelada.");
      },
      "Cancelar solicitação",
    );
  if (action === "subscription-cancel" || action === "subscription-keep") {
    const cancel = action.endsWith("cancel");
    showDialog(
      cancel ? "Programar encerramento?" : "Manter assinatura?",
      `Seu período atual vai até ${subscriptionDate(data.paidUntil)}.`,
      `<p>${cancel ? "O acesso de escrita continuará até essa data. Depois, consultas, exportações e segurança da conta permanecerão disponíveis. Seus dados serão preservados." : "A preferência de encerramento será removida. A renovação continua dependendo da contratação e ativação de um novo período; esta ação não cria cobrança."}</p>`,
      async () => {
        check();
        await send("/subscription/" + (cancel ? "cancel" : "keep"), {
          revision,
        });
        toast(cancel ? "Encerramento programado." : "Preferência atualizada.");
      },
      cancel ? "Programar encerramento" : "Confirmar",
    );
  }
  return true;
}

function subscriptionPayments(payments, data) {
  const labels = {
    ISSUING: "Emitindo",
    PENDING: "Aguardando pagamento",
    CONFIRMED: "Aguardando recebimento",
    OVERDUE: "Vencida",
    PAID: "Acesso liberado",
    TEST_PAID: "Teste recebido",
    CANCELLING: "Cancelamento em confirmação",
    CANCELLED: "Cancelada",
    REVIEW: "Conferência necessária",
  };
  return `<section class="card subscription-invoices"><div class="plan-section-heading"><div><span class="eyebrow">COBRANÇA DO SEU FINTRY</span><h2>Mensalidades e pagamentos</h2></div><span class="badge ${payments.enabled ? "pending" : "cancelled"}">${payments.enabled ? (payments.environment === "PRODUCTION" ? "Produção" : "Sandbox · testes") : "Atendimento comercial"}</span></div><p class="muted">${payments.enabled ? "A fatura abre no ambiente do Asaas. A confirmação do recebimento atualiza seu acesso; você também pode consultar por aqui." : "A emissão integrada depende da configuração da conta recebedora pelo operador. As solicitações de plano continuam disponíveis."}</p>${payments.invoices.length ? `<div class="subscription-invoice-list">${payments.invoices.map((i) => `<article class="subscription-invoice"><div><strong>${money(i.amount)}</strong><span>${e(data.plans.find((p) => p.code === i.plan).name)} · ${i.method === "PIX" ? "Pix" : "Boleto"} · ${i.environment === "SANDBOX" ? "Teste" : "Produção"}</span><small>Vencimento: ${e(i.dueDate.split("-").reverse().join("/"))}${i.activeUntil ? " · Acesso até " + subscriptionDate(i.activeUntil) : ""}</small><span class="badge ${i.status === "PAID" ? "paid" : ["REVIEW", "CANCELLED"].includes(i.status) ? "cancelled" : "pending"}">${e(labels[i.status])}</span>${i.message ? `<p class="notice">${e(i.message)}</p>` : ""}</div><div class="subscription-invoice-actions">${i.invoiceUrl && ["PENDING", "OVERDUE", "CONFIRMED"].includes(i.status) ? `<a class="btn" href="${e(i.invoiceUrl)}" target="_blank" rel="noopener noreferrer">Abrir fatura ↗</a>` : ""}${isAdmin() && payments.enabled ? `<button class="btn secondary" data-action="subscription-payment-refresh" data-id="${i.id}">Consultar</button>${["PENDING", "OVERDUE", "CANCELLING"].includes(i.status) ? `<button class="btn secondary" data-action="subscription-payment-cancel" data-id="${i.id}">Cancelar mensalidade</button>` : ""}` : ""}</div></article>`).join("")}</div>` : '<div class="subscription-empty"><strong>Seu histórico de pagamentos começa aqui</strong><p>Solicite um plano e, quando disponível, gere a mensalidade na solicitação. Nenhuma cobrança é criada sem sua confirmação.</p></div>'}</section>`;
}
