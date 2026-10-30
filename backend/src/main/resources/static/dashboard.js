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
function dashboardChartPrefs() {
  const defaults = {
    active: "default",
    current: { title: "Fluxo de caixa", period: 6, type: "bar", series: ["income", "expense"] },
    views: [],
  };
  try {
    const raw = localStorage.getItem(`fintry:dashboard-charts:${state.user?.id || "local"}`);
    if (!raw) return defaults;
    const value = JSON.parse(raw);
    return {
      ...defaults,
      ...value,
      current: { ...defaults.current, ...(value.current || {}) },
      views: Array.isArray(value.views) ? value.views.slice(0, 10) : [],
    };
  } catch {
    return defaults;
  }
}
function dashboardChartConfig() {
  const prefs = dashboardChartPrefs();
  const view = prefs.views.find((item) => item.id === prefs.active);
  return view ? { ...view, title: view.name } : prefs.current;
}
function saveDashboardChartPrefs(prefs) {
  try {
    localStorage.setItem(`fintry:dashboard-charts:${state.user?.id || "local"}`, JSON.stringify(prefs));
  } catch {
    toast("Não foi possível salvar as visualizações neste navegador.");
  }
}
function dashboardChartValue(month, key) {
  if (key === "income") return Number(month.income);
  if (key === "expense") return Number(month.expense);
  return Number(month.income) - Number(month.expense);
}
const dashboardChartSeries = {
  income: { label: "Receitas", className: "income" },
  expense: { label: "Despesas", className: "expense" },
  result: { label: "Resultado", className: "result" },
};
function chart(flow, config) {
  const series = config.series.filter((key) => dashboardChartSeries[key]);
  if (!flow.some((m) => Number(m.income) || Number(m.expense)))
    return `<div class="dashboard-chart-empty">${icon("chart")}<strong>Seu histórico de caixa começa aqui</strong><p>As visualizações usam os pagamentos e recebimentos dos últimos meses do período selecionado.</p></div>`;
  const values = flow.flatMap((m) => series.map((key) => dashboardChartValue(m, key)));
  const min = Math.min(0, ...values), max = Math.max(1, ...values), span = max - min || 1;
  const y = (value) => 174 - ((value - min) / span) * 150;
  const zero = y(0);
  const compact = (v) => v.toLocaleString("pt-BR", { notation: "compact", maximumFractionDigits: 1 });
  const step = 540 / flow.length;
  const xAt = (index) => 68 + step * (index + .5);
  const lines = config.type === "line" ? series.map((key, sIndex) => {
    const pts = flow.map((m, i) => `${xAt(i).toFixed(1)},${y(dashboardChartValue(m, key)).toFixed(1)}`).join(" ");
    return `<polyline aria-hidden="true" class="chart-series-line series-${dashboardChartSeries[key].className}" points="${pts}"/>${flow.map((m, i) => `<circle aria-hidden="true" class="chart-point series-${dashboardChartSeries[key].className}" cx="${xAt(i).toFixed(1)}" cy="${y(dashboardChartValue(m, key)).toFixed(1)}" r="4"/>`).join("")}`;
  }).join("") : "";
  return `<div class="chart dashboard-chart" tabindex="0" role="region" aria-label="Gráfico interativo. Use as setas para percorrer os meses; selecione um mês para consultar os valores."><svg role="group" aria-label="${config.type === "line" ? "Linhas" : "Barras"} de ${series.map((key) => dashboardChartSeries[key].label.toLowerCase()).join(", ")} nos últimos ${flow.length} meses." viewBox="0 0 640 235">${[0,1,2,3].map((i) => {
    const value = max - (span * i / 3), yy = y(value);
    return `<line x1="54" x2="626" y1="${yy}" y2="${yy}" stroke="var(--line)" stroke-dasharray="3 5"/><text x="0" y="${yy + 4}">${compact(value)}</text>`;
  }).join("")}<line class="chart-zero-line" x1="54" x2="626" y1="${zero}" y2="${zero}"/>${lines}${flow.map((m, i) => {
    const x = xAt(i), groupWidth = Math.min(74, step - 8), barWidth = Math.min(18, (groupWidth - 8) / series.length);
    const bars = config.type === "bar" ? series.map((key, sIndex) => {
      const value = dashboardChartValue(m, key), yy = y(value), left = x - groupWidth / 2 + 4 + sIndex * (barWidth + 3);
      return `<rect class="chart-${dashboardChartSeries[key].className}" x="${left}" y="${Math.min(yy, zero)}" width="${barWidth}" height="${Math.max(1, Math.abs(zero - yy))}" rx="4"/>`;
    }).join("") : "";
    return `<g role="button" tabindex="0" data-dashboard-index="${i}" aria-pressed="${i === flow.length - 1}" aria-label="Consultar ${e(dashboardMonth(m.month))}: ${series.map((key) => dashboardChartSeries[key].label + " " + money(dashboardChartValue(m, key))).join(", ")}" class="dashboard-chart-month ${i === flow.length - 1 ? "is-selected" : ""}"><title>${e(dashboardMonth(m.month))}</title><rect class="chart-hit" x="${x - step / 2}" y="12" width="${step}" height="204" rx="10"/>${bars}<text x="${x}" y="201" text-anchor="middle">${dashboardMonth(m.month, true)}</text></g>`;
  }).join("")}</svg></div>`;
}
function dashboardChartValues(m, series) {
  return `<span class="chart-detail-month">${e(dashboardMonth(m.month))}</span><dl>${series.map((key) => `<div><dt>${dashboardChartSeries[key].label}</dt><dd class="${key === "income" || (key === "result" && dashboardChartValue(m, key) >= 0) ? "positive" : key === "result" ? "negative" : ""}">${money(dashboardChartValue(m, key))}</dd></div>`).join("")}</dl>`;
}
function dashboardChart(s) {
  const prefs = dashboardChartPrefs(), config = dashboardChartConfig();
  const flow = s.flow.slice(-Math.min(Number(config.period) || 6, s.flow.length));
  const last = flow.at(-1);
  const options = [{id:"default", name:"Fluxo de caixa"}, ...prefs.views].map((view) => `<option value="${e(view.id)}" ${prefs.active === view.id ? "selected" : ""}>${e(view.name)}</option>`).join("");
  const series = ["income", "expense", "result"];
  return `<article class="card dashboard-cash"><div class="card-head"><div><span class="eyebrow">HISTÓRICO REALIZADO</span><h2>${e(config.title || "Fluxo de caixa")}</h2><p>Valores realizados até ${e(dashboardMonth(state.month))}</p></div></div><div class="chart-studio" aria-label="Controles do gráfico"><div class="chart-studio-row"><label class="chart-view-select">Visualização<select data-chart-view aria-label="Visualização salva">${options}</select></label><div class="chart-control"><span class="chart-control-label">Período</span><div class="segmented-control" aria-label="Período do gráfico">${[3,6].map((period) => `<button type="button" data-chart-period="${period}" aria-pressed="${Number(config.period) === period}">${period} meses</button>`).join("")}</div></div><div class="chart-control"><span class="chart-control-label">Formato</span><div class="segmented-control" aria-label="Formato do gráfico">${[["bar","Barras"],["line","Linhas"]].map(([type,label]) => `<button type="button" data-chart-type="${type}" aria-pressed="${config.type === type}">${label}</button>`).join("")}</div></div><button type="button" class="btn secondary small chart-delete" data-chart-delete ${prefs.active === "default" ? "disabled" : ""} aria-label="Excluir visualização selecionada">Excluir</button></div><div class="chart-series-controls" aria-label="Informações exibidas">${series.map((key) => `<button type="button" class="chart-series-toggle ${dashboardChartSeries[key].className}" data-chart-series="${key}" aria-pressed="${config.series.includes(key)}"><i aria-hidden="true"></i>${dashboardChartSeries[key].label}</button>`).join("")}<details class="chart-save-details"><summary>Salvar como nova visualização</summary><form data-chart-save-form><label class="field">Nome da visualização<input name="name" maxlength="32" required placeholder="Ex.: Receitas e resultado"></label><button class="btn small" type="submit">Criar visualização</button></form></details></div></div><div class="chart-legend" aria-label="Legenda do gráfico">${config.series.map((key) => `<span><i class="series-${dashboardChartSeries[key].className}"></i>${dashboardChartSeries[key].label}</span>`).join("")}</div><div class="dashboard-chart-plot">${chart(flow, config)}${last ? `<div class="dashboard-chart-detail" aria-live="polite" aria-atomic="true">${dashboardChartValues(last, config.series)}</div>` : ""}</div><details class="dashboard-chart-data"><summary>Consultar valores em tabela</summary><div class="table-wrap" tabindex="0" role="region" aria-label="Valores exibidos no gráfico por mês"><table><thead><tr><th scope="col">MÊS</th>${config.series.map((key) => `<th scope="col">${dashboardChartSeries[key].label.toLocaleUpperCase("pt-BR")}</th>`).join("")}</tr></thead><tbody>${flow.map((m) => `<tr><th scope="row">${e(dashboardMonth(m.month))}</th>${config.series.map((key) => `<td>${money(dashboardChartValue(m, key))}</td>`).join("")}</tr>`).join("")}</tbody></table></div></details><p class="chart-caption">Valores em reais, pela data da baixa. Transferências não compõem o resultado de caixa. Preferências salvas neste navegador.</p></article>`;
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
    `<section class="grid-two dashboard-main-grid"><div class="dashboard-chart-slot">${dashboardChart(s)}</div><article class="card dashboard-radar"><div class="card-head"><div><span class="eyebrow">PREVISÕES DO MÊS</span><h2>No seu radar</h2><p>Pendentes por vencimento · ${e(dashboardMonth(state.month))}</p></div></div><div class="pending"><div class="pending-row"><span class="radar-icon income">${icon("income")}</span><div><p>A receber</p><small>Entradas previstas</small></div><strong class="positive">${money(s.pendingIncome)}</strong></div><div class="pending-row"><span class="radar-icon">${icon("expense")}</span><div><p>A pagar</p><small>Compromissos do período</small></div><strong>${money(s.pendingExpense)}</strong></div></div><div class="radar-bottom"><p>Pendências só alteram o saldo quando você registra a baixa.</p><button class="btn soft" data-nav="forecast">Ver fluxo projetado ${icon("income")}</button></div></article></section>` +
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
function bindDashboardChart(page) {
  const slot = page.querySelector(".dashboard-chart-slot");
  if (!slot) return;
  const render = () => {
    slot.innerHTML = dashboardChart(state.summary);
    bindDashboardChart(page);
  };
  const persist = (config) => {
    const next = dashboardChartPrefs();
    next.current = { ...config };
    const view = next.views.find((item) => item.id === next.active);
    if (view) Object.assign(view, config);
    saveDashboardChartPrefs(next);
  };
  slot.querySelector("[data-chart-view]")?.addEventListener("change", (event) => {
    const next = dashboardChartPrefs(), selected = event.target.value;
    next.active = selected;
    const view = next.views.find((item) => item.id === selected);
    next.current = view ? { title: view.name, period: view.period, type: view.type, series: [...view.series] } : { title: "Fluxo de caixa", period: 6, type: "bar", series: ["income", "expense"] };
    saveDashboardChartPrefs(next);
    render();
  });
  slot.querySelectorAll("[data-chart-period], [data-chart-type], [data-chart-series]").forEach((button) => {
    button.addEventListener("click", () => {
      const config = { ...dashboardChartConfig(), series: [...dashboardChartConfig().series] };
      if (button.hasAttribute("data-chart-period")) config.period = Number(button.dataset.chartPeriod);
      if (button.hasAttribute("data-chart-type")) config.type = button.dataset.chartType;
      if (button.hasAttribute("data-chart-series")) {
        const key = button.dataset.chartSeries;
        if (config.series.includes(key)) {
          if (config.series.length === 1) return;
          config.series = config.series.filter((item) => item !== key);
        } else config.series.push(key);
      }
      persist(config);
      render();
    });
  });
  slot.querySelector("[data-chart-save-form]")?.addEventListener("submit", (event) => {
    event.preventDefault();
    const name = new FormData(event.currentTarget).get("name").trim();
    if (!name) return;
    const next = dashboardChartPrefs();
    if (next.views.length >= 10) {
      toast("Você pode salvar até 10 visualizações.");
      return;
    }
    const id = `view-${Date.now().toString(36)}`;
    next.views.push({ id, name, ...dashboardChartConfig(), series: [...dashboardChartConfig().series] });
    next.active = id;
    next.current = { ...next.views.at(-1), title: name };
    saveDashboardChartPrefs(next);
    render();
    toast("Visualização salva neste navegador.");
  });
  slot.querySelector("[data-chart-delete]")?.addEventListener("click", () => {
    const next = dashboardChartPrefs();
    if (next.active === "default") return;
    next.views = next.views.filter((view) => view.id !== next.active);
    next.active = "default";
    next.current = { title: "Fluxo de caixa", period: 6, type: "bar", series: ["income", "expense"] };
    saveDashboardChartPrefs(next);
    render();
    toast("Visualização excluída.");
  });
  const svg = slot.querySelector(".dashboard-chart svg");
  if (!svg) return;
  const select = (event) => {
    const point = event.target.closest?.("[data-dashboard-index]");
    if (!point) return;
    const config = dashboardChartConfig();
    const flow = state.summary.flow.slice(-Math.min(Number(config.period) || 6, state.summary.flow.length));
    const month = flow[Number(point.dataset.dashboardIndex)];
    if (!month) return;
    slot.querySelectorAll("[data-dashboard-index]").forEach((el) => {
      const selected = el === point;
      el.classList.toggle("is-selected", selected);
      el.setAttribute("aria-pressed", String(selected));
    });
    slot.querySelector(".dashboard-chart-detail").innerHTML = dashboardChartValues(month, config.series);
  };
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
      const next = (points.indexOf(point) + (event.key === "ArrowLeft" ? -1 : 1) + points.length) % points.length;
      points[next].focus();
    }
  });
}
function bindDashboard() {
  const page = document.querySelector(".dashboard-page");
  if (!page) return;
  page.querySelector("[data-dashboard-today]").onclick = async () => {
    state.month = localDate().slice(0, 7);
    await safeLoad();
  };
  bindDashboardChart(page);
}
