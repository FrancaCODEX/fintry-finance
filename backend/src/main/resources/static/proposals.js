"use strict";
const commercial = {
  services: null,
  servicePage: 0,
  serviceFilters: { active: "true" },
  proposals: null,
  proposalPage: 0,
  proposalFilters: {},
  pendingProposal: null,
  detailRequest: 0,
};
const proposalStates = {
  DRAFT: "Rascunho",
  OPEN: "Em negociação",
  APPROVED: "Aprovada",
  REJECTED: "Recusada",
  CANCELLED: "Cancelada",
};
function resetCommercial() {
  delete modal.dataset.proposal;
  Object.assign(commercial, {
    services: null,
    servicePage: 0,
    serviceFilters: { active: "true" },
    proposals: null,
    proposalPage: 0,
    proposalFilters: {},
    pendingProposal: null,
    detailRequest: commercial.detailRequest + 1,
  });
}
async function loadCommercial(id) {
  const services = state.page === "services";
  const data = await api(
    services
      ? "/products?" +
          queryParams({
            ...commercial.serviceFilters,
            kind: "SERVICE",
            page: commercial.servicePage,
          })
      : "/proposals?" +
          queryParams({
            ...commercial.proposalFilters,
            page: commercial.proposalPage,
          }),
  );
  if (id !== requestId || !state.user) return;
  commercial[services ? "services" : "proposals"] = data;
  shell();
  document.querySelector("#page-content").innerHTML = services
    ? renderServices()
    : renderProposals();
  document
    .querySelector(services ? "#service-filter" : "#proposal-filter")
    ?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      commercial[services ? "serviceFilters" : "proposalFilters"] =
        Object.fromEntries(new FormData(ev.currentTarget));
      commercial[services ? "servicePage" : "proposalPage"] = 0;
      await safeLoad();
    });
}
function renderServices() {
  const data = commercial.services;
  return (
    head(
      "Seu conhecimento também é receita.",
      "Organize serviços, preços e custos de referência para propostas e vendas.",
      canWrite()
        ? '<button class="btn" data-action="service-new">' +
            icon("plus") +
            "Novo serviço</button>"
        : "",
    ) +
    `<section class="card service-intro"><div class="product-symbol">${icon("sparkles")}</div><div><h2>${data.activeCount} serviço(s) ativo(s)</h2><p>Serviços são vendidos por unidade de execução (UN), com quantidades fracionadas quando necessário. Não possuem estoque. Custos são referências; despesas continuam sendo registradas no financeiro.</p></div></section><section class="card"><form id="service-filter" class="commerce-filters">${field("Buscar serviço", "search", "search", 'maxlength="120" placeholder="Nome ou código"', commercial.serviceFilters.search || "")}<label class="field">Situação<select name="active"><option value="true" ${commercial.serviceFilters.active === "true" ? "selected" : ""}>Ativos</option><option value="false" ${commercial.serviceFilters.active === "false" ? "selected" : ""}>Inativos</option><option value="" ${commercial.serviceFilters.active === "" ? "selected" : ""}>Todos</option></select></label><button class="btn secondary" type="submit">Filtrar serviços</button></form>${data.content.length ? `<div class="service-grid">${data.content.map((p) => `<article class="service-card"><div class="service-card-head"><span class="eyebrow">${e(p.sku)}</span><span class="badge ${p.active ? "" : "cancelled"}">${p.active ? "Ativo" : "Inativo"}</span></div><h2>${e(p.name)}</h2><p>Por unidade de serviço · sem estoque</p><strong class="service-price">${money(p.price)}</strong><dl><div><dt>Custo de referência</dt><dd>${money(p.cost)}</dd></div></dl>${canWrite() ? `<div class="row-actions"><button class="btn secondary small" data-action="service-edit" data-id="${p.id}" aria-label="Editar ${e(p.name)}">Editar serviço</button><button class="btn text small" data-action="proposal-new">Criar proposta →</button></div>` : ""}</article>`).join("")}</div>` : commerceEmpty("Seu primeiro serviço começa aqui.", "Cadastre uma consultoria, instalação, suporte ou outra entrega da sua empresa.", "service-new", "Cadastrar serviço")}${pager("service", data, commercial.servicePage)}</section>`
  );
}
function serviceDialog(p = null) {
  showDialog(
    p ? "Editar serviço" : "Novo serviço",
    "Defina o preço por unidade de execução; serviços não movimentam estoque.",
    field(
      "Código do serviço",
      "sku",
      "text",
      'required pattern="[A-Za-z0-9._-]{1,40}" maxlength="40" placeholder="Ex.: SRV-001"',
      p?.sku || "",
    ) +
      field(
        "Nome do serviço",
        "name",
        "text",
        'required maxlength="120"',
        p?.name || "",
      ) +
      `<div class="form-row">${field("Preço por serviço (R$)", "price", "number", 'required min="0" max="999999999999.99" step="0.01"', p?.price ?? "")}${field("Custo de referência (R$)", "cost", "number", 'required min="0" max="999999999999.99" step="0.01"', p?.cost ?? "0")}</div>${p ? `<label class="field">Situação<select name="active"><option value="true" ${p.active ? "selected" : ""}>Ativo</option><option value="false" ${!p.active ? "selected" : ""}>Inativo</option></select></label>` : ""}<p class="notice">O código é único entre produtos e serviços da empresa. Alterar o catálogo preserva os preços e nomes das propostas e vendas já registradas.</p>`,
    async (data) => {
      await api("/products" + (p ? "/" + p.id : ""), {
        method: p ? "PUT" : "POST",
        body: JSON.stringify({
          ...data,
          kind: "SERVICE",
          unit: "UN",
          minimum: 0,
          active: p ? data.active === "true" : true,
        }),
      });
      toast("Serviço salvo.");
    },
    "Salvar serviço",
  );
}
function proposalBadge(p) {
  return `<span class="badge ${p.status === "DRAFT" || p.status === "OPEN" ? "pending" : p.status === "APPROVED" ? "paid" : "cancelled"}">${proposalStates[p.status]}</span>${p.expired ? '<span class="badge overdue-badge">Validade expirada</span>' : ""}`;
}
function renderProposals() {
  const data = commercial.proposals,
    f = commercial.proposalFilters;
  return (
    head(
      "De uma boa conversa a um negócio fechado.",
      "Prepare propostas com produtos e serviços, acompanhe a negociação e aprove para gerar a venda.",
      canWrite()
        ? '<button class="btn" data-action="proposal-new">' +
            icon("plus") +
            "Nova proposta</button>"
        : "",
    ) +
    `<section class="proposal-metrics" aria-label="Resumo das propostas"><article class="card"><span class="eyebrow">EM PREPARAÇÃO</span><strong>${data.drafts}</strong><p>Rascunhos sem efeito financeiro</p></article><article class="card"><span class="eyebrow">EM NEGOCIAÇÃO</span><strong>${data.negotiating}</strong><p>Confira a validade antes de aprovar</p></article><article class="card"><span class="eyebrow">APROVADAS</span><strong>${data.approved}</strong><p>Com venda e receita vinculadas</p></article></section><section class="card"><form class="commerce-filters" id="proposal-filter">${field("Buscar proposta", "search", "search", 'maxlength="120" placeholder="Título ou cliente"', f.search || "")}<label class="field">Situação<select name="status"><option value="">Todas</option>${Object.entries(
      proposalStates,
    )
      .map(
        ([k, v]) =>
          `<option value="${k}" ${f.status === k ? "selected" : ""}>${v}</option>`,
      )
      .join(
        "",
      )}</select></label><label class="field">Validade<select name="expired"><option value="false">Todas as validades</option><option value="true" ${f.expired === "true" ? "selected" : ""}>Apenas expiradas em aberto</option></select></label><button class="btn secondary" type="submit">Filtrar propostas</button></form>${data.content.length ? `<div class="proposal-list">${data.content.map((p) => `<article class="proposal-card"><div class="proposal-card-main"><div class="proposal-meta"><span class="eyebrow">PROPOSTA #${p.number}</span>${proposalBadge(p)}</div><h2>${e(p.title)}</h2><p>${e(p.contactName || "Consumidor final")}</p><small>Válida até ${dateLabel(p.validUntil)}${p.saleId ? " · Venda #" + p.saleNumber + (p.saleStatus === "CANCELLED" ? " cancelada" : "") : ""}</small></div><div class="proposal-card-side"><strong>${money(p.total)}</strong><button class="btn secondary small" data-action="proposal-details" data-id="${p.id}" aria-label="Detalhes da proposta ${p.number}">Ver proposta</button></div></article>`).join("")}</div>` : commerceEmpty("Sua próxima negociação começa aqui.", "Escolha os itens, defina uma validade e revise a proposta antes de aprovar.", "proposal-new", "Criar primeira proposta")}${pager("proposal", data, commercial.proposalPage)}</section><p class="notice">Colocar em negociação é apenas um registro interno, sem envio de e-mail. Propostas não reservam estoque nem entram nas previsões financeiras até a aprovação.</p>`
  );
}
async function proposalDetails(id) {
  const user = state.user?.id,
    page = state.page,
    seq = ++commercial.detailRequest;
  const p = await api("/proposals/" + id);
  if (
    user !== state.user?.id ||
    page !== state.page ||
    seq !== commercial.detailRequest
  )
    return;
  closeDetails();
  showDialog(
    "Proposta #" + p.number,
    e(p.title),
    `<div class="proposal-detail-heading"><div>${proposalBadge(p)}</div><strong>${money(p.total)}</strong></div><dl class="detail-grid"><div><dt>Cliente</dt><dd>${e(p.contactName || "Consumidor final")}</dd></div><div><dt>Validade</dt><dd>${dateLabel(p.validUntil)}</dd></div></dl><div class="sale-detail-items">${p.items.map((i) => `<article><div><strong>${e(i.productName)}</strong><small>${i.kind === "SERVICE" ? "Serviço" : "Produto"} · ${e(i.sku)} · ${quantityLabel(i.quantity)} ${e(i.unit)} × ${money(i.unitPrice)}</small></div><strong>${money(i.total)}</strong></article>`).join("")}</div>${p.notes ? `<p class="notice proposal-notes">${e(p.notes)}</p>` : ""}${p.status === "APPROVED" ? `<p class="notice">Venda #${p.saleNumber} ${p.saleStatus === "CANCELLED" ? "cancelada. A proposta permanece aprovada como histórico; não gera outra venda." : "gerada na aprovação. Confira o recebimento e eventuais correções na venda vinculada."}</p>` : '<p class="notice">Nomes e preços ficam registrados nesta proposta. A aprovação confere novamente o estoque e os cadastros ativos.</p>'}<div class="proposal-detail-actions"><a class="btn secondary" href="/api/v1/proposals/${p.id}/pdf" download>Baixar PDF</a>${p.saleId ? `<button type="button" class="btn secondary" data-commercial-action="proposal-sale" data-id="${p.saleId}">Abrir venda #${p.saleNumber}</button>` : ""}${canWrite() && p.status === "DRAFT" ? `<button type="button" class="btn secondary" data-commercial-action="proposal-edit" data-id="${p.id}">Editar proposta</button><button type="button" class="btn" data-commercial-action="proposal-status" data-id="${p.id}" data-status="OPEN">Colocar em negociação</button>` : ""}${canWrite() && p.status === "OPEN" ? `<button type="button" class="btn secondary" data-commercial-action="proposal-status" data-id="${p.id}" data-status="DRAFT">Voltar a rascunho</button><button type="button" class="btn" data-commercial-action="proposal-approve" data-id="${p.id}" ${p.expired ? "disabled" : ""}>Aprovar e gerar venda</button><button type="button" class="btn text" data-commercial-action="proposal-status" data-id="${p.id}" data-status="REJECTED">Marcar como recusada</button>` : ""}${canWrite() && ["DRAFT", "OPEN"].includes(p.status) ? `<button type="button" class="btn text" data-commercial-action="proposal-status" data-id="${p.id}" data-status="CANCELLED">Cancelar proposta</button>` : ""}</div>`,
    async () => {},
    "Fechar",
  );
  modal.dataset.proposal = JSON.stringify(p);
}
function currentProposal(id) {
  const p = JSON.parse(modal.dataset.proposal || "null");
  return p?.id === id ? p : null;
}
function proposalStatusDialog(p, to) {
  const titles = {
    OPEN: "Colocar em negociação?",
    DRAFT: "Voltar a rascunho?",
    REJECTED: "Marcar como recusada?",
    CANCELLED: "Cancelar proposta?",
  };
  showDialog(
    titles[to],
    "O histórico da proposta será preservado.",
    `<p class="notice">${to === "OPEN" ? "Esta ação registra a negociação na sua equipe. Nenhum e-mail será enviado; use o PDF para compartilhar a proposta." : to === "DRAFT" ? "A proposta poderá ser editada novamente, incluindo preços e validade." : "Esta proposta será encerrada, sem criar venda ou movimentar o financeiro."}</p>`,
    async () => {
      await send("/proposals/" + p.id + "/status", {
        version: p.version,
        status: to,
      });
      commercial.pendingProposal = p.id;
      toast("Situação da proposta atualizada.");
    },
    "Confirmar situação",
  );
}
async function proposalApprovalDialog(p) {
  const user = state.user?.id,
    seq = commercial.detailRequest;
  const accounts = await api("/finance/accounts");
  if (user !== state.user?.id || seq !== commercial.detailRequest) return;
  if (!accounts.length) {
    toast("Cadastre uma conta antes de aprovar a proposta.");
    return;
  }
  state.accounts = accounts;
  showDialog(
    "Aprovar proposta #" + p.number,
    "A aprovação gera uma venda confirmada e a receita vinculada.",
    `<p class="notice"><strong>${money(p.total)}</strong> · ${e(p.title)}. Produtos serão baixados do estoque; serviços não movimentam estoque. Se algum item não puder ser vendido, a operação inteira será desfeita.</p>` +
      accountOptions("accountId", "Conta de recebimento") +
      field("Vencimento", "dueDate", "date", "required", localDate()) +
      '<label class="check"><input name="paid" type="checkbox"> Marcar como recebida</label><div id="proposal-payment-date" hidden>' +
      field(
        "Data do recebimento",
        "paymentDate",
        "date",
        `max="${localDate()}"`,
        localDate(),
      ) +
      "</div>",
    async (data) => {
      await send("/proposals/" + p.id + "/approve", {
        version: p.version,
        payment: {
          accountId: data.accountId,
          dueDate: data.dueDate,
          paid: data.paid === "on",
          paymentDate: data.paid === "on" ? data.paymentDate : null,
        },
      });
      commercial.pendingProposal = p.id;
      toast("Proposta aprovada. Venda e receita registradas.");
    },
    "Confirmar aprovação",
  );
  const form = modal.querySelector("form");
  form.elements.paid.onchange = () => {
    form.querySelector("#proposal-payment-date").hidden =
      !form.elements.paid.checked;
    form.elements.paymentDate.required = form.elements.paid.checked;
  };
}
async function commercialAction(btn) {
  const action = btn.dataset.action || btn.dataset.commercialAction || "";
  if (!/^(service-|proposal-)/.test(action)) return false;
  if (action.endsWith("-prev") || action.endsWith("-next")) {
    commercial[
      action.startsWith("service-") ? "servicePage" : "proposalPage"
    ] += action.endsWith("-next") ? 1 : -1;
    await safeLoad();
    return true;
  }
  if (!["proposal-details", "proposal-sale"].includes(action) && !canWrite()) {
    toast("Seu perfil permite somente consultas.");
    return true;
  }
  try {
    if (action === "service-new") serviceDialog();
    if (action === "service-edit")
      serviceDialog(
        commercial.services?.content.find((p) => p.id === btn.dataset.id),
      );
    if (action === "proposal-new") saleDialog({ mode: "proposal" });
    if (action === "proposal-details") await proposalDetails(btn.dataset.id);
    if (action === "proposal-sale") {
      modal.close();
      await saleDetails(btn.dataset.id);
    }
    const p = currentProposal(btn.dataset.id);
    if (action === "proposal-edit" && p) {
      modal.close();
      saleDialog({ mode: "proposal", existing: p });
    }
    if (action === "proposal-status" && p)
      proposalStatusDialog(p, btn.dataset.status);
    if (action === "proposal-approve" && p) await proposalApprovalDialog(p);
  } catch (err) {
    toast(err.message);
  }
  return true;
}
document.addEventListener("click", async (ev) => {
  const btn = ev.target.closest("[data-commercial-action]");
  if (!btn) return;
  btn.disabled = true;
  try {
    await commercialAction(btn);
  } finally {
    if (btn.isConnected) btn.disabled = false;
  }
});
