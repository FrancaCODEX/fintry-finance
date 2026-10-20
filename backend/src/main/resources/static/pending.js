"use strict";
const pendingState = {
  result: null,
  page: 0,
  filters: { group: "ALL", source: "ALL" },
};
const pendingGroups = {
  ALL: "Todas as pendências",
  OVERDUE: "Vencidas",
  TODAY: "Vencem hoje",
  NEXT7: "Próximos 7 dias",
  NEXT30: "De 8 a 30 dias",
  UPCOMING: "Hoje e próximos 7 dias",
};
function resetPending() {
  pendingState.result = null;
  pendingState.page = 0;
  pendingState.filters = { group: "ALL", source: "ALL" };
}
async function loadPending(id) {
  const [result, accounts] = await Promise.all([
    api(
      "/finance/pending?" +
        queryParams({ ...pendingState.filters, page: pendingState.page }),
    ),
    api("/finance/accounts"),
  ]);
  if (id !== requestId || !state.user) return;
  if (pendingState.page > 0 && pendingState.page >= result.totalPages) {
    pendingState.page = Math.max(0, result.totalPages - 1);
    return load();
  }
  pendingState.result = result;
  state.accounts = accounts;
  state.entries = result;
  shell();
  document.querySelector("#page-content").innerHTML = renderPending();
  document.querySelector("#pending-filter").onsubmit = async (ev) => {
    ev.preventDefault();
    pendingState.filters = Object.fromEntries(new FormData(ev.currentTarget));
    pendingState.page = 0;
    await safeLoad();
  };
}
function pendingEmpty() {
  return (
    '<div class="empty"><div class="empty-icon">' +
    icon("calendar") +
    '</div><h3>Nenhuma pendência para estes filtros</h3><p>Valores realizados e cancelados ficam fora desta central. Ajuste os filtros ou consulte Lançamentos para ver o histórico.</p><button class="btn secondary" data-action="pending-reset">Limpar filtros</button></div>'
  );
}
function renderPending() {
  const p = pendingState.result,
    f = pendingState.filters;
  return (
    head(
      "Seu financeiro, em dia.",
      "Priorize os vencimentos e registre cada pagamento ou recebimento.",
      `<button class="btn secondary" data-action="reload">Atualizar pendências</button>${canWrite() ? `<button class="btn" data-action="entry">${icon("plus")}Novo lançamento</button>` : ""}`,
    ) +
    `<p class="pending-reference">Referência: ${dateLabel(p.today)} · horário de Brasília</p><section class="pending-windows" aria-label="Grupos de vencimento">${p.windows.map((w) => `<button type="button" class="pending-window ${w.group === f.group ? "selected" : ""} ${w.group === "OVERDUE" ? "overdue-window" : ""}" data-action="pending-group" data-group="${w.group}" aria-pressed="${w.group === f.group}"><span>${pendingGroups[w.group]}</span><strong>${w.totals.count} <small>pendência(s)</small></strong><span class="window-amounts"><span>A receber <b>${money(w.totals.income)}</b></span><span>A pagar <b>${money(w.totals.expense)}</b></span></span></button>`).join("")}</section><p class="pending-scope">Os indicadores respeitam busca, tipo, conta e origem. Selecione um grupo para filtrar os lançamentos.</p><section class="card"><form id="pending-filter" class="pending-filters">${field("Buscar pendência", "search", "search", 'maxlength="255" placeholder="Descrição do lançamento"', f.search || "")}<label class="field">Vencimento<select name="group">${Object.entries(
      pendingGroups,
    )
      .filter(([k]) => k !== "UPCOMING")
      .map(
        ([k, l]) =>
          `<option value="${k}" ${f.group === k ? "selected" : ""}>${l}</option>`,
      )
      .join(
        "",
      )}</select></label><label class="field">Tipo<select name="type"><option value="">Receitas e despesas</option><option value="INCOME" ${f.type === "INCOME" ? "selected" : ""}>A receber</option><option value="EXPENSE" ${f.type === "EXPENSE" ? "selected" : ""}>A pagar</option></select></label><label class="field">Conta<select name="accountId"><option value="">Todas as contas</option>${state.accounts.map((a) => `<option value="${a.id}" ${f.accountId === a.id ? "selected" : ""}>${e(a.name)}</option>`).join("")}</select></label><label class="field">Origem<select name="source"><option value="ALL">Todas as origens</option>${[
      ["SINGLE", "Sem série"],
      ["RECURRING", "Recorrências"],
      ["INSTALLMENT", "Parcelamentos"],
    ]
      .map(
        ([k, l]) =>
          `<option value="${k}" ${f.source === k ? "selected" : ""}>${l}</option>`,
      )
      .join(
        "",
      )}</select></label><button type="submit" class="btn secondary">Aplicar filtros</button><button type="button" class="btn text" data-action="pending-reset">Limpar</button></form><div class="pending-totals" aria-label="Totais de todos os resultados filtrados"><div><span>A receber</span><strong class="positive">${money(p.totals.income)}</strong></div><div><span>A pagar</span><strong>${money(p.totals.expense)}</strong></div><p>${p.totals.count} pendência(s) · totais de todas as páginas<br>Valores previstos, ainda não realizados.</p></div>${entriesTable(p.content, { today: p.today, empty: pendingEmpty() })}${pager("pending", p, pendingState.page)}</section>`
  );
}
function pendingDashboard() {
  const p = state.dashboardPending;
  if (!p) return "";
  const overdue = p.windows.find((w) => w.group === "OVERDUE").totals;
  return `<section class="card dashboard-due"><div class="card-head"><div><h2>Hoje e nos próximos 7 dias</h2><p>Vencimentos atuais · independente do mês selecionado acima</p></div><button class="btn text small" data-nav="pending">Abrir central →</button></div>${overdue.count ? `<div class="due-alert"><span>${overdue.count} pendência(s) vencida(s) precisam de atenção.</span><button class="btn text small" data-action="pending-group" data-group="OVERDUE">Ver vencidas</button></div>` : ""}${p.content.length ? `<div class="dashboard-due-list">${p.content.map((row) => `<article><div><strong>${e(row.description)}</strong><small>${row.dueDate === p.today ? "Hoje" : dateLabel(row.dueDate)} · ${e(row.category)}${row.seriesId ? " · " + (row.seriesKind === "RECURRING" ? "Recorrência" : "Parcelamento") : ""}</small></div><strong class="${row.type === "INCOME" ? "positive" : "negative"}">${row.type === "INCOME" ? "+" : "−"} ${money(row.amount)}</strong><div class="row-actions"><button class="btn secondary small" data-action="details" data-id="${row.id}" aria-label="Detalhes de ${e(row.description)}">Detalhes</button>${canWrite() ? `<button class="btn soft small" data-action="pay" data-id="${row.id}">${row.type === "INCOME" ? "Receber" : "Pagar"}</button>` : ""}</div></article>`).join("")}</div>` : '<div class="due-empty">Nenhum vencimento entre hoje e os próximos 7 dias.</div>'}<p class="chart-caption">${p.totalElements} pendência(s) neste intervalo. ${p.totalElements > p.content.length ? "Exibindo as primeiras " + p.content.length + ". " : ""}Confira todas as datas na central.</p></section>`;
}
async function pendingAction(btn) {
  const action = btn.dataset.action;
  if (
    ![
      "pending-group",
      "pending-reset",
      "pending-prev",
      "pending-next",
    ].includes(action)
  )
    return false;
  if (action === "pending-group") {
    pendingState.filters.group = btn.dataset.group;
    pendingState.page = 0;
    state.page = "pending";
  }
  if (action === "pending-reset") resetPending();
  if (action === "pending-prev" || action === "pending-next")
    pendingState.page += action === "pending-next" ? 1 : -1;
  await safeLoad();
  return true;
}
