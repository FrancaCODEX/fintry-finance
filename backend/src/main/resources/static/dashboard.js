"use strict";
function comparison(value, previous) {
  if (Number(previous) === 0)
    return Number(value) === 0
      ? "Sem movimentação no mês anterior"
      : "Sem base de comparação anterior";
  const delta =
    ((Number(value) - Number(previous)) / Math.abs(Number(previous))) * 100;
  return `${delta > 0 ? "+" : ""}${delta.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}% vs. mês anterior`;
}

function dashboardMonth(month, short = false) {
  return new Intl.DateTimeFormat("pt-BR", {
    month: short ? "short" : "long",
    ...(short ? {} : { year: "numeric" }),
    timeZone: "UTC",
  })
    .format(new Date(month + "-15T12:00:00Z"))
    .replace(".", "");
}
function dashboardComparison(value, previous) {
  // Negative previous results do not have an intuitive percentage base.
  if (Number(previous) < 0) {
    const delta = Number(value) - Number(previous);
    return `${delta > 0 ? "+ " : delta < 0 ? "− " : ""}${money(Math.abs(delta))} vs. mês anterior`;
  }
  return comparison(value, previous);
}
function dashboardSparkline(values, label) {
  const points = values.map(Number);
  const low = Math.min(0, ...points), high = Math.max(1, ...points);
  const spread = high - low || 1;
  const coords = points.map((value, index) => ({
    x: points.length <= 1 ? 50 : (index / (points.length - 1)) * 100,
    y: 26 - ((value - low) / spread) * 20,
  }));
  const line = coords.map((point, index) => `${index ? "L" : "M"}${point.x.toFixed(1)},${point.y.toFixed(1)}`).join(" ");
  const area = `${line} L100,30 L0,30 Z`;
  return `<svg class="dashboard-sparkline" viewBox="0 0 100 32" preserveAspectRatio="none" role="img" aria-label="${e(label)}"><path class="sparkline-area" d="${area}"/><path class="sparkline-line" d="${line}"/>${coords.length ? `<circle class="sparkline-dot" cx="${coords.at(-1).x}" cy="${coords.at(-1).y}" r="2.3"/>` : ""}</svg>`;
}
function dashboardKpi(title, value, previous, series, iconName, tone, explanation) {
  const formatted = money(value);
  const label = `${title} nos últimos seis meses`;
  return `<article class="dashboard-kpi ${tone}"><div class="dashboard-kpi-top"><span>${e(title)}</span><span class="dashboard-kpi-icon">${icon(iconName)}</span></div><strong class="dashboard-kpi-value ${formatted.length > 15 ? "money-long" : ""}">${formatted}</strong><div class="dashboard-kpi-bottom"><span class="dashboard-kpi-comparison">${dashboardComparison(value, previous)}</span><span class="dashboard-kpi-note">${explanation}</span></div>${dashboardSparkline(series, label)}</article>`;
}
function dashboardShortcuts() {
  const actions = [
    ...(canWrite()
      ? [
          ["entry", "Novo lançamento", "Receita ou despesa", "plus", false],
          ["transfer", "Transferir", "Entre suas contas", "transfer", false],
        ]
      : [
          ["accounts", "Suas contas", "Consulte os saldos", "wallet", true],
          ["pending", "Pendências", "Confira vencimentos", "calendar", true],
        ]),
    ["reports", "Relatórios", "Analise e exporte", "chart", true],
    ["reconciliation", "Conciliação", "Confira o extrato", "entries", true],
  ];
  return `<nav class="dashboard-shortcuts" aria-label="Atalhos do financeiro">${actions.map(([action, title, text, ic, nav]) => `<button type="button" ${nav ? "data-nav" : "data-action"}="${action}"><span class="shortcut-icon">${icon(ic)}</span><span><strong>${title}</strong><small>${text}</small></span><span class="shortcut-arrow" aria-hidden="true">↗</span></button>`).join("")}</nav>`;
}
function dashboardOnboarding() {
  const steps = [
    {
      done: state.accounts.length > 0,
      title: "Cadastre uma conta",
      description: "Banco, carteira ou caixa da empresa.",
      page: "accounts",
    },
    {
      done: state.categories.length > 0,
      title: "Organize as categorias",
      description: "Separe receitas e despesas por assunto.",
      page: "categories",
    },
    {
      done: Number(state.onboardingEntries?.totalElements) > 0,
      title: "Registre um lançamento",
      description: "Comece a acompanhar o movimento financeiro.",
      page: "entries",
    },
  ];
  const completed = steps.filter((step) => step.done).length;
  if (completed === steps.length) return "";
  return `<section class="card dashboard-onboarding" aria-labelledby="onboarding-title"><div class="onboarding-heading"><div><span class="eyebrow">PRIMEIROS PASSOS</span><h2 id="onboarding-title">Prepare seu espaço financeiro</h2><p>Conclua estas etapas para começar a acompanhar sua empresa.</p></div><div class="onboarding-progress"><strong>${completed} de ${steps.length}</strong><progress max="${steps.length}" value="${completed}" aria-label="${completed} de ${steps.length} etapas concluídas"></progress></div></div><div class="onboarding-steps">${steps.map((step, index) => `<button class="onboarding-step ${step.done ? "is-done" : ""}" data-nav="${step.page}" ${step.done ? 'aria-label="Concluído: ' + e(step.title) + '"' : ""}><span class="onboarding-step-number">${step.done ? "✓" : index + 1}</span><span><strong>${e(step.title)}</strong><small>${e(step.description)}</small></span>${icon("income")}</button>`).join("")}</div><div class="onboarding-footer"><span>Já tem planilhas ou quer explorar tudo de uma vez?</span><button class="btn text small" data-nav="examples">Conhecer dados de exemplo →</button></div></section>`;
}
function dashboardAttention() {
  const p = state.dashboardPending;
  if (!p) return "";
  const count = p.windows.find((w) => w.group === "OVERDUE")?.totals.count || 0;
  return `<section class="dashboard-attention ${count ? "needs-attention" : ""}" aria-label="Situação dos vencimentos atuais"><div class="attention-icon">${icon("calendar")}</div><div><span class="eyebrow">VENCIMENTOS ATUAIS · ${dateLabel(p.today)}</span><p>${count ? `<strong>${count} pendência(s) vencida(s).</strong> Confira o que precisa de atenção.` : "Nenhum atraso cadastrado. Confira os próximos vencimentos."}</p></div><button class="btn secondary small" ${count ? 'data-action="pending-group" data-group="OVERDUE"' : 'data-nav="pending"'}>${count ? "Ver vencidas" : "Ver pendências"}</button></section>`;
}
function chart(flow) {
  if (!flow.some((m) => Number(m.income) || Number(m.expense)))
    return `<div class="dashboard-chart-empty">${icon("chart")}<strong>Seu histórico de caixa começa aqui</strong><p>As barras aparecem ao registrar pagamentos e recebimentos nos últimos seis meses do período selecionado.</p></div>`;
  const max = Math.max(
    1,
    ...flow.flatMap((m) => [Number(m.income), Number(m.expense)]),
  );
  const compact = (v) =>
    v.toLocaleString("pt-BR", {
      notation: "compact",
      maximumFractionDigits: 1,
    });
  // Focusable SVG groups expose the same values shown on hover and touch.
  return `<div class="chart dashboard-chart" tabindex="0" role="region" aria-label="Gráfico com rolagem horizontal; selecione um mês para consultar valores"><svg role="group" aria-label="Receitas e despesas realizadas nos últimos seis meses. Selecione um mês para consultar os valores." viewBox="0 0 640 235">${[0, 1, 2, 3].map((i) => `<line x1="54" x2="626" y1="${24 + i * 50}" y2="${24 + i * 50}" stroke="var(--line)" stroke-dasharray="3 5"/><text x="0" y="${28 + i * 50}">${compact(max * (1 - i / 3))}</text>`).join("")}${flow
    .map((m, i) => {
      const x = 68 + i * 93,
        hi = (Number(m.income) / max) * 150,
        he = (Number(m.expense) / max) * 150;
      return `<g role="button" tabindex="0" data-dashboard-index="${i}" aria-pressed="${i === flow.length - 1}" aria-label="Consultar ${e(dashboardMonth(m.month))}: receitas ${money(m.income)}, despesas ${money(m.expense)}" class="dashboard-chart-month ${i === flow.length - 1 ? "is-selected" : ""}"><title>${e(dashboardMonth(m.month))}: receitas ${money(m.income)}, despesas ${money(m.expense)}</title><rect class="chart-hit" x="${x - 7}" y="12" width="73" height="204" rx="10"/><rect class="chart-income" x="${x}" y="${174 - hi}" width="24" height="${hi}" rx="5"/><rect class="chart-expense" x="${x + 30}" y="${174 - he}" width="24" height="${he}" rx="5"/><text x="${x + 27}" y="201" text-anchor="middle">${dashboardMonth(m.month, true)}</text></g>`;
    })
    .join("")}</svg></div>`;
}
function dashboardChartValues(m) {
  return `<span class="chart-detail-month">${e(dashboardMonth(m.month))}</span><dl><div><dt>Receitas</dt><dd class="positive">${money(m.income)}</dd></div><div><dt>Despesas</dt><dd>${money(m.expense)}</dd></div><div><dt>Resultado de caixa</dt><dd class="${Number(m.income) - Number(m.expense) < 0 ? "negative" : "positive"}">${money(Number(m.income) - Number(m.expense))}</dd></div></dl>`;
}
function dashboardChart(s) {
  const last = s.flow.at(-1);
  return `<article class="card dashboard-cash"><div class="card-head"><div><span class="eyebrow">HISTÓRICO REALIZADO</span><h2>O ritmo do seu caixa</h2><p>Últimos 6 meses até ${e(dashboardMonth(state.month))}</p></div><div class="legend"><span><i class="dot"></i>Receitas</span><span><i class="dot expense"></i>Despesas</span></div></div>${chart(s.flow)}${last ? `<div class="dashboard-chart-detail" aria-live="polite" aria-atomic="true">${dashboardChartValues(last)}</div>` : ""}<details class="dashboard-chart-data"><summary>Ver valores em tabela</summary><div class="table-wrap" tabindex="0" role="region" aria-label="Valores do gráfico por mês"><table><thead><tr><th scope="col">MÊS</th><th scope="col">RECEITAS</th><th scope="col">DESPESAS</th></tr></thead><tbody>${s.flow.map((m) => `<tr><th scope="row">${e(dashboardMonth(m.month))}</th><td>${money(m.income)}</td><td>${money(m.expense)}</td></tr>`).join("")}</tbody></table></div></details><p class="chart-caption">Valores em reais, pela data da baixa. Transferências não compõem o resultado de caixa.</p></article>`;
}
function renderDashboard() {
  const s = state.summary;
  return (
    `<div class="dashboard-page">` +
    head(
      "Seu negócio, em perspectiva.",
      `Olá, ${e(state.user.name.split(" ")[0])}. Seu financeiro, do saldo de hoje aos próximos compromissos.`,
      monthInput() +
        `<button class="btn secondary" data-dashboard-today>Ir para mês atual</button>`,
    ) +
    `<div class="dashboard-overview"><article class="card dashboard-balance"><div class="balance-heading"><span class="eyebrow">SALDO DISPONÍVEL</span><span class="balance-symbol">${icon("wallet")}</span></div><strong class="balance-number ${money(s.balance).length > 18 ? "balance-long" : ""}">${money(s.balance)}</strong><p>Saldo atual de todas as contas.<br>Inclui saldos iniciais e movimentos realizados.</p><button class="balance-link" data-nav="accounts">Consultar contas ${icon("income")}</button><span class="balance-scope">Agora · independente do mês selecionado</span></article><section class="dashboard-monthly" aria-label="Indicadores do mês selecionado"><div class="dashboard-period"><span class="eyebrow">MÊS EM ANÁLISE</span><strong>${e(dashboardMonth(state.month))}</strong><span>Valores realizados pela data do pagamento ou recebimento</span></div><div class="dashboard-kpis">${dashboardKpi("Receitas realizadas", s.income, s.previousIncome, s.flow.map((m) => m.income), "income", "income", "Entradas confirmadas")}${dashboardKpi("Despesas realizadas", s.expense, s.previousExpense, s.flow.map((m) => m.expense), "expense", "expense", "Saídas confirmadas")}${dashboardKpi("Resultado de caixa", s.result, s.previousResult, s.flow.map((m) => Number(m.income) - Number(m.expense)), "trend", "result", "Receitas menos despesas")}</div><p class="dashboard-result-note">Valores pela data da baixa. O resultado de caixa não representa lucro contábil.</p></section></div>` +
    dashboardOnboarding() +
    dashboardPresentationTour() +
    dashboardShortcuts() +
    dashboardAttention() +
    `<section class="grid-two dashboard-main-grid">${dashboardChart(s)}<article class="card dashboard-radar"><div class="card-head"><div><span class="eyebrow">PREVISÕES DO MÊS</span><h2>No seu radar</h2><p>Pendentes por vencimento · ${e(dashboardMonth(state.month))}</p></div></div><div class="pending"><div class="pending-row"><span class="radar-icon income">${icon("income")}</span><div><p>A receber</p><small>Entradas previstas</small></div><strong class="positive">${money(s.pendingIncome)}</strong></div><div class="pending-row"><span class="radar-icon">${icon("expense")}</span><div><p>A pagar</p><small>Compromissos do período</small></div><strong>${money(s.pendingExpense)}</strong></div></div><div class="radar-bottom"><p>Pendências só alteram o saldo quando você registra a baixa.</p><button class="btn soft" data-nav="forecast">Ver fluxo projetado ${icon("income")}</button></div></article></section>` +
    `<section class="grid-two dashboard-secondary-grid"><article class="card dashboard-categories"><div class="card-head"><div><span class="eyebrow">PARA ONDE FOI O DINHEIRO</span><h2>Maiores despesas por categoria</h2><p>Até 5 categorias · pagamentos de ${e(dashboardMonth(state.month))}</p></div></div>${categoryBreakdown(s.categories || [], s.expense)}<div class="dashboard-card-link"><button class="btn text small" data-nav="reports">Analisar nos relatórios →</button></div></article><article class="card dashboard-next"><span class="eyebrow">PLANEJE O PRÓXIMO PASSO</span><div class="next-icon">${icon("trend")}</div><h2>Mais clareza para decidir.</h2><p>Veja como os vencimentos cadastrados podem impactar o saldo. A projeção considera o que já foi registrado na sua empresa.</p><button class="btn" data-nav="forecast">Explorar fluxo projetado ${icon("income")}</button><span>Previsão não é garantia de recebimento.</span></article></section>` +
    pendingDashboard() +
    `<section class="card dashboard-transactions"><div class="card-head"><div><span class="eyebrow">LANÇAMENTOS</span><h2>Movimentações do período</h2><p>Até 5 lançamentos · pela data de vencimento</p></div><button class="btn text small" data-nav="entries">Ver todas →</button></div>${state.accounts.length ? entriesTable(state.entries.content.slice(0, 5)) : empty("Vamos organizar seu financeiro?", "Comece cadastrando uma conta ou explore um exemplo com dados fictícios.", "account", "Criar primeira conta")}</section></div>`
  );
}
function dashboardPresentationTour() {
  if (!state.demoPack?.loaded || Number(state.demoPack.packVersion) < 3) return "";
  const steps = [
    ["01", "Lançamentos", "Veja recebimentos e pagamentos feitos em etapas.", "entries"],
    ["02", "Relatórios", "Acompanhe cada baixa pela data e pelo valor realizado.", "reports"],
    ["03", "Conciliação", "Confira vínculos, agrupamento e um movimento em revisão.", "reconciliation"],
  ];
  return `<section class="dashboard-tour" aria-labelledby="dashboard-tour-title"><div class="dashboard-tour-heading"><div><span class="eyebrow">ROTEIRO DE APRESENTAÇÃO</span><h2 id="dashboard-tour-title">Veja o fluxo completo do Fintry</h2><p>Um percurso curto pelos dados de demonstração.</p></div><span class="demo-label">${icon("sparkles")} Dados fictícios</span></div><div class="dashboard-tour-steps">${steps.map(([number, title, description, page]) => `<button class="dashboard-tour-step" data-nav="${page}"><span class="tour-number">${number}</span><span class="tour-copy"><strong>${title}</strong><small>${description}</small></span><span class="tour-arrow" aria-hidden="true">→</span></button>`).join("")}</div><button class="dashboard-tour-all" data-nav="examples">Ver todos os exemplos →</button></section>`;
}
function bindDashboard() {
  const page = document.querySelector(".dashboard-page");
  if (!page) return;
  page.querySelector("[data-dashboard-today]").onclick = async () => {
    state.month = localDate().slice(0, 7);
    await safeLoad();
  };
  const select = (event) => {
    const point = event.target.closest?.("[data-dashboard-index]");
    if (!point) return;
    const month = state.summary.flow[Number(point.dataset.dashboardIndex)];
    if (!month) return;
    const detail = page.querySelector(".dashboard-chart-detail");
    if (point.classList.contains("is-selected")) return;
    page.querySelectorAll("[data-dashboard-index]").forEach((el) => {
      const selected = el === point;
      el.classList.toggle("is-selected", selected);
      el.setAttribute("aria-pressed", String(selected));
    });
    detail.innerHTML = dashboardChartValues(month);
  };
  const svg = page.querySelector(".dashboard-chart svg");
  if (!svg) return;
  svg.addEventListener("mouseover", select);
  svg.addEventListener("focusin", select);
  svg.addEventListener("click", select);
  svg.addEventListener("keydown", (event) => {
    const point = event.target.closest?.("[data-dashboard-index]");
    if (!point) return;
    if (["Enter", " "].includes(event.key)) {
      event.preventDefault();
      select(event);
    }
    if (["ArrowLeft", "ArrowRight"].includes(event.key)) {
      event.preventDefault();
      const points = [...svg.querySelectorAll("[data-dashboard-index]")];
      const next =
        (points.indexOf(point) +
          (event.key === "ArrowLeft" ? -1 : 1) +
          points.length) %
        points.length;
      points[next].focus();
    }
  });
}
