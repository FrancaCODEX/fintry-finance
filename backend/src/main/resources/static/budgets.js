"use strict";
const planning = { month: localPlanningMonth(), result: null, sample: null };
function localPlanningMonth() {
  return new Intl.DateTimeFormat("sv-SE", {
    timeZone: "America/Sao_Paulo",
    year: "numeric",
    month: "2-digit",
  }).format(new Date());
}
function resetPlanning() {
  Object.assign(planning, {
    month: localPlanningMonth(),
    result: null,
    sample: null,
  });
}
async function loadPlanning(id) {
  const examples = state.page === "examples";
  const data = await Promise.all(
    examples
      ? [api("/finance/sample")]
      : [api("/finance/budgets?month=" + planning.month), api("/categories")],
  );
  if (id !== requestId || !state.user) return;
  if (examples) planning.sample = data[0];
  else {
    planning.result = data[0];
    state.categories = data[1];
  }
  shell();
  document.querySelector("#page-content").innerHTML = examples
    ? renderExamples()
    : renderBudgets();
  document
    .querySelector("#budget-filter")
    ?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      planning.month = ev.currentTarget.elements.month.value;
      await safeLoad();
    });
}
const budgetLabels = {
  OK: "Dentro do limite",
  WARNING: "Atenção ao limite",
  EXCEEDED: "Limite excedido",
};
function budgetCard(row) {
  const exceeded = Number(row.remaining) < 0;
  return `<article class="budget-card ${row.status.toLowerCase()}"><div class="budget-card-head"><div><h2>${e(row.category)}</h2>${!row.active ? '<small class="muted">Categoria inativa · histórico preservado</small>' : ""}</div><span class="badge ${row.status === "EXCEEDED" ? "overdue-badge" : row.status === "WARNING" ? "pending" : ""}">${budgetLabels[row.status]}</span></div><p class="budget-limit">Limite mensal <strong>${money(row.limitAmount)}</strong></p><div class="budget-progress"><label for="budget-progress-${row.id}">Comprometido <strong>${money(row.committed)} · ${Number(row.percentage).toLocaleString("pt-BR")} %</strong></label><progress id="budget-progress-${row.id}" max="100" value="${Math.min(100, Number(row.percentage))}" aria-valuetext="${e(Number(row.percentage).toLocaleString("pt-BR"))}% do limite comprometido">${row.percentage}%</progress></div><dl class="budget-breakdown"><div><dt>Pago</dt><dd>${money(row.paid)}</dd></div><div><dt>Pendente</dt><dd>${money(row.pending)}</dd></div><div><dt>${exceeded ? "Excedido em" : "Disponível após pendências"}</dt><dd class="${exceeded ? "negative" : "positive"}">${money(Math.abs(Number(row.remaining)))}</dd></div></dl><div class="budget-card-actions">${canWrite() ? `<button class="btn secondary small" data-action="budget-edit" data-id="${row.id}" aria-label="Editar orçamento de ${e(row.category)}">Editar limite</button><button class="btn text small" data-action="budget-delete" data-id="${row.id}" aria-label="Remover orçamento de ${e(row.category)}">Remover</button>` : ""}</div></article>`;
}
function budgetMetricMoney(value, tone = "") {
  const formatted = money(value);
  return `<strong class="${tone} ${formatted.length > 18 ? "money-long" : formatted.length > 12 ? "money-medium" : ""}">${formatted}</strong>`;
}
function renderBudgets() {
  const b = planning.result;
  return (
    head(
      "Planeje cada categoria.",
      "Defina limites mensais e acompanhe o valor pago e os compromissos pendentes.",
      canWrite()
        ? '<button class="btn secondary" data-action="budget-copy">Copiar mês anterior</button><button class="btn" data-action="budget-new">' +
            icon("plus") +
            "Novo orçamento</button>"
        : "",
    ) +
    `<form id="budget-filter" class="card budget-filter"><label class="field">Mês do orçamento<input name="month" type="month" required min="2000-01" max="2100-12" value="${planning.month}"></label><button class="btn secondary" type="submit">Consultar mês</button><p>Despesas pelo <strong>vencimento</strong> no mês. Transferências e cancelados ficam fora.</p></form><section class="budget-metrics" aria-label="Resumo dos orçamentos"><article class="card"><span>Limites definidos</span>${budgetMetricMoney(b.limitAmount)}<small>${b.content.length} categoria(s)</small></article><article class="card"><span>Pago nas categorias</span>${budgetMetricMoney(b.paid)}<small>Compromissos já realizados</small></article><article class="card"><span>Ainda pendente</span>${budgetMetricMoney(b.pending)}<small>Valores previstos no mês</small></article><article class="card"><span>Disponível após pendências</span>${budgetMetricMoney(b.remaining, Number(b.remaining) < 0 ? "negative" : "positive")}<small>${b.exceededCount} excedida(s) · ${b.warningCount} em atenção</small></article></section><p class="budget-rule">O limite considera pago + pendente. Atenção a partir de 80%; excedido acima de 100%. Um orçamento não bloqueia lançamentos nem reserva saldo.</p>${b.content.length ? `<section class="budget-grid" aria-label="Orçamentos por categoria">${b.content.map(budgetCard).join("")}</section>` : `<section class="card empty"><div class="empty-icon">${icon("wallet")}</div><h2>Um plano para o mês.</h2><p>Crie o primeiro limite para uma categoria de despesa ou copie os limites do mês anterior.</p>${canWrite() ? '<button class="btn" data-action="budget-new">Criar primeiro orçamento</button>' : "<p>Peça ao Financeiro ou Administrador para definir os limites.</p>"}</section>`}<section class="card budget-uncovered"><h2>Despesas sem orçamento</h2><p>Inclui categorias sem limite neste mês e lançamentos com categoria avulsa. Estes valores não entram nos indicadores dos orçamentos acima.</p><dl><div><dt>Pago</dt><dd>${money(b.unbudgetedPaid)}</dd></div><div><dt>Pendente</dt><dd>${money(b.unbudgetedPending)}</dd></div></dl></section>`
  );
}
function budgetDialog(row = null) {
  const available = state.categories.filter(
    (c) => c.kind !== "INCOME" && (c.active || c.id === row?.categoryId),
  );
  if (!available.length) {
    showDialog(
      "Crie uma categoria de despesa",
      "O orçamento acompanha os lançamentos vinculados à categoria.",
      '<p class="notice">Cadastre uma categoria ativa do tipo Despesa ou Mista em Categorias. Depois volte aqui para definir seu limite.</p>',
      async () => {
        state.page = "categories";
      },
      "Ir para Categorias",
    );
    return;
  }
  showDialog(
    row ? "Editar orçamento" : "Novo orçamento",
    "Use uma categoria cadastrada para relacionar as despesas ao limite.",
    `<div class="form-row">${field("Mês", "month", "month", 'required min="2000-01" max="2100-12"', planning.month)}${field("Limite mensal (R$)", "limitAmount", "number", 'required min="0.01" max="999999999999.99" step="0.01"', row?.limitAmount ?? "")}</div><label class="field">Categoria<select name="categoryId" required>${available.map((c) => `<option value="${c.id}" ${c.id === row?.categoryId ? "selected" : ""}>${e(c.name)}${!c.active ? " (inativa)" : ""}</option>`).join("")}</select></label><p class="notice">Pago e pendente são somados pela data de vencimento. Lançamentos com categoria avulsa precisam ser vinculados a esta categoria para entrar no acompanhamento.</p>`,
    async (data) => {
      await api("/finance/budgets" + (row ? "/" + row.id : ""), {
        method: row ? "PUT" : "POST",
        body: JSON.stringify(data),
      });
      planning.month = data.month;
      toast("Orçamento salvo.");
    },
    "Salvar orçamento",
  );
}
function renderExamples() {
  const loaded = planning.sample.loaded;
  const packVersion = planning.sample.packVersion || 1;
  const current = loaded && packVersion >= 3;
  const areas = [
    ["Financeiro", "4 contas, seis meses de receitas/despesas, transferências e compromissos de hoje até os próximos meses.", "dashboard", "Explorar dashboard"],
    ["Pendências e séries", "Atrasados, vencimentos próximos, recorrência mensal e compra parcelada.", "pending", "Ver pendências"],
    ["Clientes e fornecedores", "Contatos de exemplo, cadastros ativos e um registro arquivado.", "contacts", "Ver contatos"],
    ["Categorias e orçamentos", "Limites mensais com situações dentro do limite, em atenção e excedidas.", "budgets", "Ver orçamentos"],
    ["Produtos, estoque e vendas", "Estoque baixo/zerado, vendas em situações diferentes e propostas comerciais.", "sales", "Ver vendas"],
    ["Pagamentos parciais", "Recebimentos e pagamentos em etapas, saldo restante e histórico de baixas.", "entries", "Ver lançamentos"],
    ["Conciliação bancária", "Extrato OFX fictício, dois movimentos agrupados, vínculos individuais e uma linha em revisão.", "reconciliation", "Explorar conciliação"],
    ["Relatórios", "Pagamentos parciais discriminados pelas datas e valores efetivamente recebidos ou pagos.", "reports", "Abrir relatórios"],
  ];
  return (
    head(
      "Explore o Fintry completo.",
      "Um cenário de demonstração que percorre o financeiro da previsão à conciliação.",
    ) +
    `<section class="card examples-intro"><span class="eyebrow">DADOS FICTÍCIOS · ${current ? "PACOTE 3 ATUALIZADO" : "PACOTE 3 DISPONÍVEL"}</span><h2>${current ? "Seu ambiente de apresentação está pronto." : loaded ? "Complete os exemplos para apresentar o fluxo." : "Explore o Fintry com um cenário completo."}</h2><p>Os registros recebem a identificação Exemplo ou DEMO. A inclusão afeta dashboard e relatórios e preserva seus cadastros, exemplos e alterações atuais.</p>${current ? '<p class="notice success" role="status">Pacote 3 adicionado. As baixas e a conciliação são fictícias; nenhum banco real é acessado. Repetir a solicitação não duplica os registros.</p>' : canWrite() ? `<button class="btn" data-action="sample">${loaded ? "Adicionar exemplos de apresentação" : "Preparar ambiente de apresentação"}</button>` : '<p class="notice">Um Administrador ou membro do Financeiro pode preparar os exemplos.</p>'}</section><section class="examples-grid" aria-label="Conteúdo dos exemplos">${areas.map(([title, text, page, label]) => `<article class="card"><h2>${title}</h2><p>${text}</p><button class="btn text" data-nav="${page}">${label} →</button></article>`).join("")}</section><p class="notice">O pacote é adicionado somente quando você solicita, uma vez por empresa. Extratos, pagamentos e comprovantes são fictícios; nenhuma instituição financeira é acessada. Não cria usuários, senhas ou convites.</p>`
  );
}
function sampleDialog() {
  const upgrading = planning.sample.loaded;
  const current = upgrading && planning.sample.packVersion >= 3;
  showDialog(
    upgrading ? "Preparar exemplos de apresentação?" : "Preparar ambiente de apresentação?",
    "Registros fictícios para percorrer os principais fluxos do Fintry.",
    `<p class="notice">${current ? "O pacote de apresentação já está completo." : upgrading ? "Serão acrescentados lançamentos com baixas parciais e uma conciliação OFX fictícia: dois movimentos agrupados, dois vínculos individuais e um movimento em revisão. Seus dados atuais serão preservados; nenhum banco real será acessado." : "Serão adicionadas contas, categorias, contatos, lançamentos, séries, produtos, serviços, vendas, propostas, orçamentos, comprovantes e um extrato OFX fictícios."} Os valores recebidos/pagos aparecem no dashboard e nos relatórios.</p><p>A inclusão é feita uma única vez. Seus registros e alterações atuais serão preservados.</p>`,
    async () => {
      await send("/finance/sample");
      state.page = "examples";
      toast(
        upgrading
          ? "Exemplos de apresentação adicionados."
          : "Ambiente preenchido com dados de exemplo.",
      );
    },
    upgrading ? "Adicionar exemplos de apresentação" : "Preparar exemplos",
  );
}
async function planningAction(btn) {
  const action = btn.dataset.action;
  if (
    ![
      "sample",
      "budget-new",
      "budget-edit",
      "budget-delete",
      "budget-copy",
    ].includes(action)
  )
    return false;
  if (!canWrite()) {
    toast("Seu perfil permite somente consultas.");
    return true;
  }
  if (action === "sample") {
    sampleDialog();
    return true;
  }
  if (action === "budget-new") {
    budgetDialog();
    return true;
  }
  const row = planning.result?.content.find((r) => r.id === btn.dataset.id);
  if (action === "budget-edit" && row) budgetDialog(row);
  if (action === "budget-delete" && row)
    showDialog(
      "Remover este orçamento?",
      row.category,
      `<p class="notice">O limite de ${money(row.limitAmount)} de ${planning.month} será removido. As despesas e o histórico de alterações serão preservados.</p>`,
      async () => {
        await api("/finance/budgets/" + row.id, { method: "DELETE" });
        toast("Orçamento removido.");
      },
      "Remover orçamento",
    );
  if (action === "budget-copy")
    showDialog(
      "Copiar limites do mês anterior?",
      `Mês de destino: ${planning.month}`,
      "<p>Somente os limites serão copiados. Categorias inativas e categorias que já têm orçamento no destino serão ignoradas. Lançamentos não são copiados.</p>",
      async () => {
        const r = await send("/finance/budgets/copy", {
          month: planning.month,
        });
        toast(`${r.copied} orçamento(s) copiado(s); ${r.skipped} ignorado(s).`);
      },
      "Copiar limites",
    );
  return true;
}
