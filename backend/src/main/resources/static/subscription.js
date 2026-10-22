"use strict";
const subscription = {
  data: null,
  payments: null,
  renewal: null,
  welcome: false,
};
function resetSubscription() {
  subscription.data = null;
  subscription.payments = null;
  subscription.renewal = null;
  subscription.welcome = false;
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
  const [data, payments, renewal] = await Promise.all([
    api("/subscription"),
    api("/subscription/payments"),
    api("/subscription/renewal"),
  ]);
  if (id !== requestId || !state.user || state.page !== "subscription") return;
  subscription.data = data;
  subscription.payments = payments;
  subscription.renewal = renewal;
  shell();
  const selected = data.plans.find((p) => p.code === data.plan),
    evaluation = data.mode === "EVALUATION",
    pending = data.requests.find((r) => r.status === "PENDING");
  const meter = (name, value, limit, bytes = false) =>
    `<article class="card plan-usage"><span>${name}</span><strong>${bytes ? subscriptionSize(value) : value}<small> / ${data.limited ? (bytes ? subscriptionSize(limit) : limit) : "sem limite aplicado"}</small></strong><progress value="${data.limited ? Math.min(value, limit) : 0}" max="${limit}" aria-label="Uso de ${name}"></progress><p>${data.limited && value >= limit ? "Limite atingido. Confira as opções de plano." : name === "Pessoas" ? "Pessoas ativas e convites pendentes ocupam vagas." : name === "Comprovantes" ? "Remover anexos libera espaço; os lançamentos ficam preservados." : "Contas financeiras cadastradas na empresa."}</p></article>`;
  const catalog = subscriptionCatalog(data, pending, evaluation);
  document.querySelector("#page-content").innerHTML =
    head(
      subscription.welcome
        ? "Conheça os planos do Fintry."
        : "Um plano para o seu momento.",
      subscription.welcome
        ? "Sua conta está pronta. Compare as opções ou comece a organizar seu financeiro."
        : "Acompanhe seu uso e escolha a capacidade que combina com a sua empresa.",
      '<button class="btn secondary" data-action="subscription-refresh">Atualizar uso</button>',
    ) +
    subscriptionWelcome(data, selected) +
    (subscription.welcome ? catalog : "") +
    `<section class="card plan-hero"><div><span class="eyebrow">PLANO E ASSINATURA · FINTRY</span><h2>${data.status === "LEGACY" ? "Seu acesso continua preservado" : selected.name}</h2><p>${evaluation ? "Ambiente de avaliação: experimente limites e recursos sem cobrança ou contratação real." : data.status === "LEGACY" ? "Esta empresa já utilizava o Fintry. A atualização mantém o acesso anterior, sem impor limites retroativos." : data.status === "TRIAL" ? "Seu teste de 14 dias inclui a capacidade do plano Profissional. Nenhuma cobrança é criada ao iniciar o teste." : !data.writable ? "Continue consultando e exportando seus dados. Solicite um plano para voltar a registrar novas operações." : "Seu período de acesso está confirmado. A renovação é feita por uma nova mensalidade ou pelo atendimento comercial."}</p><span class="badge ${data.writable ? "paid" : "cancelled"}">${e(subscriptionStatus[data.status])}</span>${!evaluation && !["LEGACY", "INACTIVE"].includes(data.status) ? `<p class="plan-validity">${data.status === "TRIAL" ? "Teste até" : "Acesso até"} <strong>${subscriptionDate(data.status === "TRIAL" ? data.trialEndsAt : data.paidUntil)}</strong>${data.cancelAtPeriodEnd ? " · Encerramento programado ao final do período." : ""}</p>` : ""}</div>${isAdmin() && !evaluation && data.status === "ACTIVE" ? `<button class="btn secondary" data-action="subscription-${data.cancelAtPeriodEnd ? "keep" : "cancel"}">${data.cancelAtPeriodEnd ? "Manter assinatura" : "Programar encerramento"}</button>` : ""}</section>` +
    `<section class="plan-usage-grid" aria-label="Uso do seu plano">${meter("Pessoas", data.usage.members, selected.members)}${meter("Contas", data.usage.accounts, selected.accounts)}${meter("Comprovantes", data.usage.storageBytes, selected.storageBytes, true)}</section>` +
    subscriptionRenewal(renewal, data) +
    `${pending ? `<section class="card plan-request"><div><span class="eyebrow">SOLICITAÇÃO EM ANDAMENTO</span><h2>${e(data.plans.find((p) => p.code === pending.plan).name)}</h2><p>Confira o preço e a forma de pagamento antes de gerar a mensalidade. Seu plano atual continua valendo até a confirmação.</p><label class="field">Referência da solicitação<input readonly value="${pending.id}"></label></div>${isAdmin() ? `${payments.enabled && !payments.invoices.some((i) => i.requestId === pending.id) && data.plans.find((p) => p.code === pending.plan).monthlyPrice != null && !["LEGACY", "INACTIVE"].includes(data.status) ? `<button class="btn" data-action="subscription-checkout" data-id="${pending.id}">Gerar mensalidade</button>` : ""}<button class="btn secondary" data-action="subscription-request-cancel" data-id="${pending.id}">Cancelar solicitação</button>` : ""}</section>` : ""}` +
    (subscription.welcome ? "" : catalog) +
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
  if (action === "subscription-start") {
    subscription.welcome = false;
    state.page = "dashboard";
    window.scrollTo({ top: 0, behavior: "instant" });
    await safeLoad();
    return true;
  }
  if (action === "subscription-compare") {
    document.querySelector(".plan-catalog")?.scrollIntoView({
      block: "start",
      behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches
        ? "instant"
        : "smooth",
    });
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
  if (action === "subscription-renewal-enable") {
    const renewal = subscription.renewal;
    const plan = data.plans.find((p) => p.code === data.plan);
    showDialog(
      "Autorizar geração recorrente",
      plan.name + " · pagamento por Pix ou boleto",
      `<div class="subscription-order"><span>Valor por período de 30 dias</span><strong>${money(renewal.offeredPrice)}</strong><small>Sem débito automático</small></div><p>O Fintry poderá gerar uma fatura a partir de três dias antes de terminar seu acesso pago, usando o mesmo pagador e a forma de pagamento da mensalidade já recebida. Cada pagamento confirmado libera um período de 30 dias. Nenhum período é liberado só pela emissão.</p><p class="notice">Uma cobrança pendente ou um preço diferente do autorizado pausa novas emissões. Você pode interromper a autorização a qualquer momento. As faturas já reservadas ou emitidas precisam ser conferidas e canceladas separadamente.</p><label class="check"><input name="accepted" type="checkbox" required> Autorizo a emissão recorrente de ${money(renewal.offeredPrice)} por período de 30 dias, até eu interromper esta autorização.</label>`,
      async (form) => {
        check();
        await send("/subscription/renewal/enable", {
          sourceInvoiceId: renewal.sourceInvoiceId,
          price: renewal.offeredPrice,
          revision: renewal.revision,
          accepted: form.accepted === "on",
        });
        toast("Geração recorrente autorizada.");
      },
      "Autorizar recorrência",
    );
  }
  if (action === "subscription-renewal-disable") {
    const renewal = subscription.renewal;
    showDialog(
      "Interromper próximas emissões?",
      "Seu acesso já pago será preservado.",
      "<p>O Fintry deixará de reservar novas mensalidades recorrentes. Uma emissão já iniciada pode ser concluída: consulte o histórico e cancele essa fatura separadamente se desejar. Esta ação não realiza estorno.</p>",
      async () => {
        check();
        await send("/subscription/renewal/disable", {
          revision: renewal.revision,
        });
        toast("Próximas emissões interrompidas.");
      },
      "Interromper emissões",
    );
  }
  if (action === "subscription-renewal-check") {
    check();
    button.disabled = true;
    try {
      await send("/subscription/renewal/check", {});
      await safeLoad();
      toast("Renovação verificada. Confira as mensalidades.");
    } finally {
      button.disabled = false;
    }
  }
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
      `<p>${cancel ? "O acesso de escrita continuará até essa data. Depois, consultas, exportações e segurança da conta permanecerão disponíveis. Seus dados serão preservados. A autorização de emissões recorrentes também será interrompida; faturas já emitidas precisam ser canceladas separadamente." : "A preferência de encerramento será removida. A geração recorrente continuará desativada até uma nova autorização em Renovação de mensalidades."}</p>`,
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

function subscriptionRenewal(r, data) {
  if (data.mode === "EVALUATION") return "";
  const labels = {
    DISABLED: "Emissão manual",
    AUTHORIZED: "Recorrência autorizada",
    PRICE_CHANGED: "Novo preço exige autorização",
    CONFIGURATION: "Configuração em conferência",
    PLAN_CHANGED: "Plano em conferência",
    REVIEW: "Pagamento em conferência",
    PENDING: "Aguardando resolução da solicitação",
    CYCLE_CLOSED: "Emissão deste ciclo encerrada",
    CAPACITY: "Capacidade em conferência",
  };
  const description = r.enabled
    ? {
        AUTHORIZED:
          "Uma fatura será gerada perto do fim do período. Pague pelo Pix/boleto para renovar seu acesso.",
        PRICE_CHANGED:
          "O preço atual difere do autorizado. Nenhuma nova emissão será feita até você conferir e autorizar o valor.",
        PENDING:
          "Existe uma solicitação ou mensalidade em andamento. Resolva-a antes de uma nova emissão.",
        CYCLE_CLOSED:
          "Já houve uma emissão neste ciclo. Ela não será recriada automaticamente, mesmo após cancelamento. Renove manualmente ou procure atendimento.",
        CAPACITY:
          "O uso atual ultrapassa o plano. Ajuste a capacidade antes da próxima mensalidade.",
      }[r.status] ||
      "As novas emissões estão pausadas. Confira o plano, a configuração ou o pagamento com o atendimento."
    : "Você escolhe quando gerar cada mensalidade. Após um pagamento confirmado, pode autorizar a geração recorrente das próximas faturas.";
  return `<section class="card subscription-renewal" aria-label="Renovação de mensalidades"><div class="renewal-heading"><div><span class="eyebrow">CONTINUIDADE DO SEU FINTRY</span><h2>Renovação de mensalidades</h2></div><span class="badge ${r.status === "AUTHORIZED" ? "paid" : "pending"}">${e(labels[r.status])}</span></div><p class="muted">${e(description)}</p>${r.enabled ? `<dl class="renewal-timeline"><div><dt>Valor autorizado</dt><dd>${money(r.amount)} <small>/ 30 dias</small></dd></div><div><dt>Fim do acesso pago</dt><dd>${subscriptionDate(r.nextDueAt)}</dd></div><div><dt>Emissão a partir de</dt><dd>${subscriptionDate(r.generationAt)}</dd></div></dl>${!r.schedulerEnabled ? '<p class="notice">A rotina automática está desabilitada no servidor. Peça a ativação ao operador ou use Verificar renovação para conferir e gerar uma fatura elegível.</p>' : ""}` : ""}<div class="renewal-actions">${isAdmin() ? `${r.canEnable && (!r.enabled || r.status === "PRICE_CHANGED" || r.status === "PLAN_CHANGED") ? '<button class="btn" data-action="subscription-renewal-enable">Autorizar recorrência</button>' : ""}${r.enabled ? '<button class="btn secondary" data-action="subscription-renewal-check">Verificar renovação</button><button class="btn secondary" data-action="subscription-renewal-disable">Interromper emissões</button>' : ""}` : "<small>Somente o administrador pode alterar a autorização.</small>"}</div><p class="renewal-footnote">Geração de faturas com pagamento por Pix/boleto. Sem débito automático. Cancelar a autorização não cancela faturas já iniciadas.</p></section>`;
}

function subscriptionWelcome(data, plan) {
  if (!subscription.welcome) return "";
  const evaluation = data.mode === "EVALUATION";
  const trial = data.status === "TRIAL";
  return `<section class="card plan-welcome" aria-label="Primeiro acesso"><div><div class="welcome-steps" aria-label="Etapas do primeiro acesso"><span>✓ Conta criada</span><span>2 · Conheça os planos</span></div><h2>Sua conta está pronta.</h2><p>${trial ? `Você já começou com o <strong>${e(plan.name)}</strong> em teste gratuito por 14 dias, até <strong>${subscriptionDate(data.trialEndsAt)}</strong>.` : evaluation ? `O plano inicial é o <strong>${e(plan.name)}</strong>. Neste ambiente de avaliação, você pode conhecer e simular os planos sem contratação real.` : `Seu plano inicial é o <strong>${e(plan.name)}</strong>. Confira abaixo a situação e as opções de capacidade.`}</p><p class="muted">${trial ? "Nenhum pagamento é necessário para começar. Escolher um plano agora é opcional e não ativa cobrança ou renovação automaticamente." : "Criar a conta e visualizar os planos não gera cobrança. Qualquer contratação exige uma confirmação separada."}</p><div class="welcome-actions"><button class="btn" data-action="subscription-start">${trial ? "Continuar com o teste" : "Entrar no financeiro"}</button><button class="btn secondary" data-action="subscription-compare">Comparar planos</button></div></div><aside class="welcome-plan-summary"><span class="eyebrow">SEU PLANO INICIAL</span><strong>${e(plan.name)}</strong><span class="badge paid">${trial ? "Teste gratuito de 14 dias" : evaluation ? "Ambiente de avaliação" : e(subscriptionStatus[data.status])}</span><p>${trial ? "O prazo já começou com a criação da conta. Depois do teste, você poderá escolher a contratação para continuar registrando operações." : "Conheça as opções e comece pelo financeiro. Você pode voltar a esta página pelo menu Plano e assinatura."}</p></aside></section>`;
}

function subscriptionCatalog(data, pending, evaluation) {
  return `<div class="plan-section-heading"><h2>Escolha a capacidade ideal</h2><p>Todos os planos incluem controle financeiro, relatórios, produtos, vendas e conciliação. As tarifas do provedor de pagamentos são separadas.</p></div><section class="plan-catalog" aria-label="Comparação de planos">${data.plans.map((p) => `<article class="card plan-option ${p.code === data.plan ? "is-current" : ""}"><span class="eyebrow">${p.code === data.plan ? "SEU PLANO ATUAL" : "FINTRY"}</span><h3>${e(p.name)}</h3><p class="plan-description">${e(p.description)}</p><div class="plan-price">${p.monthlyPrice == null ? "Condições a definir" : money(p.monthlyPrice) + "<small> / 30 dias</small>"}</div><ul><li><strong>${p.members}</strong> pessoas, incluindo convites</li><li><strong>${p.accounts}</strong> contas financeiras</li><li><strong>${subscriptionSize(p.storageBytes)}</strong> para comprovantes</li><li>Lançamentos sem limite de quantidade</li><li>Todos os módulos financeiros e comerciais</li></ul>${isAdmin() ? `<button class="btn ${p.code === data.plan ? "secondary" : ""}" data-action="subscription-${evaluation ? "simulate" : "request"}" data-plan="${p.code}" ${pending ? "disabled" : ""}>${evaluation ? "Experimentar plano" : p.code === data.plan && data.status === "ACTIVE" ? "Solicitar renovação" : "Solicitar plano"}</button>` : '<p class="notice">Peça ao administrador para gerenciar o plano.</p>'}</article>`).join("")}</section><p class="notice">${evaluation ? "As mudanças feitas aqui são simulações. Elas não se tornam uma assinatura comercial quando o ambiente é publicado." : "Escolher um plano registra uma solicitação. Quando o pagamento integrado estiver disponível, você pode gerar uma mensalidade para 30 dias de acesso. Só o recebimento confirmado libera o período. A geração recorrente pode ser autorizada após uma mensalidade paga. O Pix/boleto continua exigindo seu pagamento, sem débito automático. O atendimento comercial continua disponível."} Uma mudança para menor capacidade exige que o uso atual caiba no plano. Seus dados não são apagados ao vencer ou encerrar o acesso.</p>`;
}
