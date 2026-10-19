"use strict";
const commerce = {
  products: null,
  productsPage: 0,
  productFilters: { active: "true" },
  movements: null,
  stockPage: 0,
  stockProduct: null,
  sales: null,
  salesPage: 0,
  saleFilters: {},
  pendingSale: null,
};
function resetCommerce() {
  Object.assign(commerce, {
    products: null,
    productsPage: 0,
    productFilters: { active: "true" },
    movements: null,
    stockPage: 0,
    stockProduct: null,
    sales: null,
    salesPage: 0,
    saleFilters: {},
    pendingSale: null,
  });
}
const quantityLabel = (value) =>
  Number(value).toLocaleString("pt-BR", { maximumFractionDigits: 3 });
const saleStates = {
  DRAFT: "Rascunho",
  CONFIRMED: "Confirmada",
  CANCELLED: "Cancelada",
};
const movementKinds = {
  INITIAL: "Estoque inicial",
  IN: "Entrada manual",
  OUT: "Saída manual",
  SALE: "Venda",
  RETURN: "Devolução da venda",
};
function commerceBadge(status) {
  return `<span class="badge ${status === "DRAFT" ? "pending" : status === "CANCELLED" ? "cancelled" : ""}">${saleStates[status]}</span>`;
}
function commerceEmpty(title, text, action, label) {
  return `<div class="empty"><div class="empty-icon">${icon("box")}</div><h3>${title}</h3><p>${text}</p>${canWrite() ? `<button class="btn" data-action="${action}">${icon("plus")}${label}</button>` : '<p class="muted">Um membro do Financeiro ou Administrador pode adicionar registros.</p>'}</div>`;
}
async function loadCommerce(id) {
  const page = state.page;
  const result = await (page === "products"
    ? api(
        "/products?" +
          queryParams({
            ...commerce.productFilters,
            page: commerce.productsPage,
          }),
      )
    : page === "stock"
      ? api(
          "/stock/movements?" +
            queryParams({
              productId: commerce.stockProduct?.id,
              page: commerce.stockPage,
            }),
        )
      : api(
          "/sales?" +
            queryParams({ ...commerce.saleFilters, page: commerce.salesPage }),
        ));
  if (id !== requestId || !state.user) return;
  commerce[
    page === "products" ? "products" : page === "stock" ? "movements" : "sales"
  ] = result;
  shell();
  document.querySelector("#page-content").innerHTML =
    page === "products"
      ? renderProducts()
      : page === "stock"
        ? renderStock()
        : renderSales();
  for (const [formName, key, pageName] of [
    ["product-filter", "productFilters", "productsPage"],
    ["sale-filter", "saleFilters", "salesPage"],
  ])
    document
      .querySelector("#" + formName)
      ?.addEventListener("submit", async (ev) => {
        ev.preventDefault();
        commerce[key] = Object.fromEntries(new FormData(ev.currentTarget));
        commerce[pageName] = 0;
        await safeLoad();
      });
}
function renderProducts() {
  const data = commerce.products;
  return (
    head(
      "Produtos que movem seu negócio.",
      "Catálogo, preços e disponibilidade em um só lugar.",
      canWrite()
        ? '<button class="btn" data-action="product-new">' +
            icon("plus") +
            "Novo produto</button>"
        : "",
    ) +
    `<section class="access-summary product-summary"><article class="card"><span class="eyebrow">PRODUTOS ATIVOS</span><strong>${data.activeCount}</strong><p>Disponíveis no catálogo</p></article><article class="card"><span class="eyebrow">ATENÇÃO AO ESTOQUE</span><strong>${data.lowStockCount}</strong><p>Saldo igual ou abaixo do mínimo</p></article><article class="card"><span class="eyebrow">ESTOQUE A CUSTO ATUAL</span><strong class="stock-value">${money(data.stockValue)}</strong><p>Produtos ativos · valor de referência</p></article></section><section class="card"><form id="product-filter" class="commerce-filters">${field("Buscar produto", "search", "search", 'maxlength="120" placeholder="Nome ou SKU"', commerce.productFilters.search || "")}<label class="field">Situação<select name="active"><option value="true" ${commerce.productFilters.active === "true" ? "selected" : ""}>Ativos</option><option value="false" ${commerce.productFilters.active === "false" ? "selected" : ""}>Inativos</option><option value="" ${commerce.productFilters.active === "" ? "selected" : ""}>Todos</option></select></label><label class="field">Disponibilidade<select name="low"><option value="false">Todo o estoque</option><option value="true" ${commerce.productFilters.low === "true" ? "selected" : ""}>Abaixo ou no mínimo</option></select></label><button type="submit" class="btn secondary">Filtrar produtos</button></form>${data.content.length ? `<div class="commerce-list product-list">${data.content.map((p) => `<article class="product-tile"><div class="product-tile-head"><div class="product-symbol" aria-hidden="true">${icon("box")}</div><div><span class="eyebrow">${e(p.sku)}</span><h2>${e(p.name)}</h2></div><span class="badge ${!p.active ? "cancelled" : p.lowStock ? "pending" : ""}">${!p.active ? "Inativo" : p.lowStock ? "Estoque baixo" : "Disponível"}</span></div><dl class="product-values"><div><dt>Preço de venda</dt><dd>${money(p.price)}</dd></div><div><dt>Em estoque</dt><dd>${quantityLabel(p.quantity)} <small>${e(p.unit)}</small></dd></div><div><dt>Custo unitário</dt><dd>${money(p.cost)}</dd></div><div><dt>Mínimo</dt><dd>${quantityLabel(p.minimum)} ${e(p.unit)}</dd></div></dl><div class="row-actions">${canWrite() ? `<button class="btn secondary small" data-action="product-edit" data-id="${p.id}" aria-label="Editar ${e(p.name)}">Editar</button>${p.active ? `<button class="btn soft small" data-action="stock-new" data-id="${p.id}">Movimentar estoque</button>` : ""}` : ""}<button class="btn text small" data-action="stock-history" data-id="${p.id}">Histórico de estoque</button></div></article>`).join("")}</div>` : commerceEmpty("Nenhum produto encontrado", "Cadastre seu primeiro produto ou ajuste os filtros.", "product-new", "Cadastrar produto")}${pager("products", data, commerce.productsPage)}</section><p class="notice">O custo de referência usa o custo atual cadastrado. Compras e pagamentos são registrados separadamente em Lançamentos.</p>`
  );
}
function renderStock() {
  const data = commerce.movements;
  return (
    head(
      "Cada unidade tem um caminho.",
      commerce.stockProduct
        ? "Movimentações de " + e(commerce.stockProduct.name)
        : "Entradas, saídas e vendas com saldo após cada movimento.",
      `${commerce.stockProduct ? '<button class="btn secondary" data-action="stock-clear">Ver todos os produtos</button>' : ""}${canWrite() ? '<button class="btn" data-action="stock-new">' + icon("plus") + "Movimentar estoque</button>" : ""}`,
    ) +
    `<section class="card"><div class="stock-timeline">${data.content.length ? data.content.map((m) => `<article class="stock-event"><div class="stock-direction ${Number(m.delta) > 0 ? "stock-in" : "stock-out"}" aria-hidden="true">${icon(Number(m.delta) > 0 ? "income" : "expense")}</div><div class="stock-event-body"><div class="audit-title"><strong>${e(m.productName)}</strong><time>${timestamp(m.createdAt)}</time></div><p>${movementKinds[m.kind]} · ${e(m.reason)}</p><small>${e(m.sku)} · Por ${e(m.actorName)}</small>${m.saleId ? `<button class="btn text small" data-action="sale-details" data-id="${m.saleId}">Abrir venda →</button>` : ""}</div><div class="stock-event-balance"><strong class="${Number(m.delta) > 0 ? "positive" : "negative"}">${Number(m.delta) > 0 ? "+" : ""}${quantityLabel(m.delta)} ${e(m.unit)}</strong><small>Saldo: ${quantityLabel(m.balance)} ${e(m.unit)}</small></div></article>`).join("") : commerceEmpty("O histórico começa na primeira entrada", "Cadastre produtos com estoque inicial ou registre uma movimentação.", "stock-new", "Movimentar estoque")}</div>${pager("stock", data, commerce.stockPage)}</section><p class="notice">Movimentos são preservados no histórico. Para corrigir, registre uma entrada ou saída com o motivo. Vendas confirmadas e canceladas atualizam o estoque automaticamente.</p>`
  );
}
function renderSales() {
  const data = commerce.sales;
  return (
    head(
      "Da venda ao seu caixa.",
      "Confira os itens, confirme o estoque e acompanhe o recebimento.",
      canWrite()
        ? '<button class="btn" data-action="sale-new">' +
            icon("plus") +
            "Nova venda</button>"
        : "",
    ) +
    `<section class="sale-workflow" aria-label="Etapas da venda"><div><span>1</span><strong>Prepare</strong><p>Escolha cliente, produtos e preços.</p></div><div><span>2</span><strong>Confirme</strong><p>Baixa no estoque e receita vinculada.</p></div><div><span>3</span><strong>Acompanhe</strong><p>Receba pelo lançamento financeiro.</p></div></section><section class="card"><form id="sale-filter" class="commerce-filters">${field("Buscar venda", "search", "search", 'maxlength="120" placeholder="Cliente ou observação"', commerce.saleFilters.search || "")}<label class="field">Situação<select name="status"><option value="">Todas as vendas</option>${Object.entries(
      saleStates,
    )
      .map(
        ([k, v]) =>
          `<option value="${k}" ${commerce.saleFilters.status === k ? "selected" : ""}>${v}</option>`,
      )
      .join(
        "",
      )}</select></label><button class="btn secondary" type="submit">Filtrar vendas</button></form><div class="commerce-list sale-list">${data.content.length ? data.content.map((s) => `<article class="sale-tile"><div class="sale-tile-title"><div><span class="eyebrow">VENDA #${s.number}</span><h2>${e(s.contactName || "Consumidor final")}</h2><small>${dateLabel(s.createdAt?.slice(0, 10))}</small></div>${commerceBadge(s.status)}</div><div class="sale-tile-total"><strong>${money(s.total)}</strong><span class="muted">${s.status === "DRAFT" ? "Estoque e caixa ainda não alterados" : s.status === "CANCELLED" ? "Registros preservados" : s.paymentStatus === "PAID" ? "Recebida" : "A receber"}</span></div><button class="btn secondary small" data-action="sale-details" data-id="${s.id}">Detalhes da venda #${s.number}</button></article>`).join("") : commerceEmpty("Sua próxima venda começa aqui", "Crie um rascunho e confira tudo antes de confirmar.", "sale-new", "Criar primeira venda")}</div>${pager("sales", data, commerce.salesPage)}</section>`
  );
}
function productDialog(p = null) {
  showDialog(
    p ? "Editar produto" : "Novo produto",
    "Identifique o produto e defina os valores de referência.",
    `<div class="form-row">${field("SKU / código", "sku", "text", 'required pattern="[A-Za-z0-9._-]{1,40}" maxlength="40" placeholder="Ex.: CAM-001"', p?.sku || "")}<label class="field">Unidade<select name="unit" ${p ? "disabled" : ""}>${["UN", "KG", "L", "M", "CX"].map((u) => `<option value="${u}" ${p?.unit === u ? "selected" : ""}>${u}</option>`).join("")}</select></label></div>${field("Nome do produto", "name", "text", 'required maxlength="120"', p?.name || "")}<div class="form-row">${field("Preço de venda (R$)", "price", "number", 'required min="0" max="999999999999.99" step="0.01"', p?.price ?? "")}${field("Custo unitário (R$)", "cost", "number", 'required min="0" max="999999999999.99" step="0.01"', p?.cost ?? "0")}</div><div class="form-row">${!p ? field("Estoque inicial", "initialQuantity", "number", 'required min="0" max="999999999.999" step="0.001"', "0") : ""}${field("Estoque mínimo", "minimum", "number", 'required min="0" max="999999999.999" step="0.001"', p?.minimum ?? "0")}</div>${p ? `<label class="field">Situação<select name="active"><option value="true" ${p.active ? "selected" : ""}>Ativo</option><option value="false" ${!p.active ? "selected" : ""}>Inativo</option></select></label><p class="notice">A unidade e os movimentos anteriores são preservados. Para alterar o saldo, use Movimentar estoque.</p>` : '<p class="notice">O estoque inicial será registrado no histórico. O SKU é único na sua empresa; use letras, números, ponto, traço ou sublinhado.</p>'}`,
    async (data) => {
      await api("/products" + (p ? "/" + p.id : ""), {
        method: p ? "PUT" : "POST",
        body: JSON.stringify({
          ...data,
          unit: p?.unit || data.unit,
          active: p ? data.active === "true" : true,
        }),
      });
      toast("Produto salvo.");
    },
  );
}
function productPicker(selected = null) {
  return `${field("Buscar produto no catálogo", "productSearch", "search", 'maxlength="120" placeholder="Nome ou SKU" autocomplete="off"')}<label class="field">Produto<select name="productId" required><option value="">Carregando produtos…</option>${selected ? `<option value="${selected.id}" selected>${e(selected.sku)} · ${e(selected.name)}</option>` : ""}</select><small class="picker-help" role="status">Busque para encontrar outros produtos. Até 20 resultados por busca.</small></label>`;
}
function bindProductPicker(form, selected = null) {
  let rows = selected ? [selected] : [],
    seq = 0;
  const select = form.elements.productId;
  async function search() {
    const id = ++seq;
    const result = await api(
      "/products?" +
        queryParams({
          active: "true",
          size: 20,
          search: form.elements.productSearch.value,
        }),
    );
    if (!form.isConnected || id !== seq) return;
    rows = result.content;
    if (selected && !rows.some((x) => x.id === selected.id))
      rows.unshift(selected);
    const old = select.value;
    select.innerHTML =
      '<option value="">Escolha um produto</option>' +
      rows
        .map(
          (p) =>
            `<option value="${p.id}">${e(p.sku)} · ${e(p.name)} (${quantityLabel(p.quantity)} ${e(p.unit)})</option>`,
        )
        .join("");
    if (rows.some((p) => p.id === old)) select.value = old;
    else if (selected) select.value = selected.id;
  }
  let timer;
  form.elements.productSearch.oninput = () => {
    clearTimeout(timer);
    timer = setTimeout(
      () => search().catch((err) => formError(form, err.message)),
      250,
    );
  };
  search().catch((err) => formError(form, err.message));
  return () => rows.find((p) => p.id === select.value);
}
function stockDialog(p = null) {
  const requestId = crypto.randomUUID();
  showDialog(
    "Movimentar estoque",
    "Registre a quantidade e o motivo para manter o histórico claro.",
    productPicker(p) +
      `<div class="form-row"><label class="field">Movimento<select name="kind"><option value="IN">Entrada</option><option value="OUT">Saída</option></select></label>${field("Quantidade", "quantity", "number", 'required min="0.001" max="999999999.999" step="0.001"')}</div>${field("Motivo", "reason", "text", 'required maxlength="255" placeholder="Ex.: Compra recebida, perda, correção de contagem"')}<p class="notice">Esta ação ajusta apenas o estoque. Registre pagamentos de compras em Lançamentos. O saldo não pode ficar negativo.</p>`,
    async (data) => {
      await send("/stock/movements", {
        requestId,
        productId: data.productId,
        kind: data.kind,
        quantity: data.quantity,
        reason: data.reason,
      });
      toast("Movimentação registrada.");
    },
    "Registrar movimento",
  );
  bindProductPicker(modal.querySelector("form"), p);
}
function scaledInteger(value, digits) {
  value = String(value).replace(/^\./, "0.");
  if (!/^\d+(\.\d+)?$/.test(String(value))) return null;
  const [a, b = ""] = String(value).split(".");
  if (b.length > digits) return null;
  return BigInt(a + b.padEnd(digits, "0"));
}
function lineCents(quantity, price) {
  const q = scaledInteger(quantity, 3),
    p = scaledInteger(price, 2);
  return q === null || p === null ? null : (q * p + 500n) / 1000n;
}
function saleDialog() {
  const requestId = crypto.randomUUID(),
    lines = [];
  showDialog(
    "Nova venda",
    "Prepare os itens. O estoque só muda depois de confirmar a venda.",
    `${field("Buscar cliente (opcional)", "customerSearch", "search", 'maxlength="120" placeholder="Nome ou documento" autocomplete="off"')}<label class="field">Cliente<select name="contactId"><option value="">Consumidor final</option></select></label><section class="sale-builder"><h3>Adicionar produto</h3>${productPicker()}<button type="button" class="btn secondary" id="add-sale-item">Adicionar aos itens</button></section><div id="sale-lines" class="sale-lines"></div><div id="sale-preview" class="sale-preview" aria-live="polite"></div>${field("Observações (opcional)", "notes", "text", 'maxlength="500"')}<p class="notice">Rascunhos não reservam estoque nem geram receita. Preços e nomes dos itens ficam registrados nesta venda.</p>`,
    async (data) => {
      if (!lines.length) throw new Error("Adicione pelo menos um produto.");
      if (
        lines.some((l) => !l.quantity || (!l.unitPrice && l.unitPrice !== "0"))
      )
        throw new Error("Confira as quantidades e os preços dos itens.");
      if (lines.some((l) => lineCents(l.quantity, l.unitPrice) === null))
        throw new Error(
          "Use até três casas na quantidade e duas no preço, sem notação científica.",
        );
      const created = await send("/sales", {
        requestId,
        contactId: data.contactId || null,
        notes: data.notes,
        items: lines.map((l) => ({
          productId: l.productId,
          quantity: l.quantity,
          unitPrice: l.unitPrice,
        })),
      });
      commerce.pendingSale = created.id;
      state.page = "sales";
      commerce.saleFilters = {};
      commerce.salesPage = 0;
      toast("Rascunho criado. Confira os dados e confirme a venda.");
    },
    "Salvar rascunho",
  );
  const form = modal.querySelector("form"),
    selected = bindProductPicker(form);
  form.elements.productId.required = false;
  function preview() {
    const cents = lines.map((l) => lineCents(l.quantity, l.unitPrice));
    const total = cents.some((x) => x === null)
      ? null
      : cents.reduce((a, b) => a + b, 0n);
    form.querySelector("#sale-preview").innerHTML =
      `<span>${lines.length} produto(s)</span><strong>Total ${total === null ? "—" : money(Number(total) / 100)}</strong>`;
  }
  function renderLines() {
    form.querySelector("#sale-lines").innerHTML = lines
      .map(
        (l, i) =>
          `<article class="sale-line"><div class="sale-line-heading"><div><strong>${e(l.name)}</strong><small>${e(l.sku)} · Saldo atual: ${quantityLabel(l.available)} ${e(l.unit)}</small></div><button class="btn text small" type="button" data-remove-line="${i}" aria-label="Remover ${e(l.name)}">Remover</button></div><div class="form-row">${field("Quantidade (" + e(l.unit) + ")", "qty-" + i, "number", `required min="0.001" max="999999999.999" step="0.001" data-line="${i}" data-prop="quantity"`, l.quantity)}${field("Preço unitário (R$)", "price-" + i, "number", `required min="0" max="999999999999.99" step="0.01" data-line="${i}" data-prop="unitPrice"`, l.unitPrice)}</div></article>`,
      )
      .join("");
    preview();
  }
  form.querySelector("#add-sale-item").onclick = () => {
    const p = selected();
    if (!p) {
      formError(form, "Escolha um produto para adicionar.");
      return;
    }
    if (lines.some((l) => l.productId === p.id)) {
      toast("Este produto já está nos itens. Ajuste a quantidade.");
      return;
    }
    if (lines.length >= 100) {
      toast("Cada venda aceita até 100 produtos.");
      return;
    }
    lines.push({
      productId: p.id,
      name: p.name,
      sku: p.sku,
      unit: p.unit,
      available: p.quantity,
      quantity: "1",
      unitPrice: String(p.price),
    });
    formError(form, "");
    renderLines();
  };
  form.addEventListener("input", (ev) => {
    const input = ev.target.closest("[data-line]");
    if (input) {
      lines[Number(input.dataset.line)][input.dataset.prop] = input.value;
      preview();
    }
  });
  form.addEventListener("click", (ev) => {
    const btn = ev.target.closest("[data-remove-line]");
    if (btn) {
      lines.splice(Number(btn.dataset.removeLine), 1);
      renderLines();
    }
  });
  let customerSeq = 0,
    timer;
  form.elements.customerSearch.oninput = () => {
    clearTimeout(timer);
    timer = setTimeout(async () => {
      const id = ++customerSeq;
      try {
        const data = await api(
          "/contacts?" +
            queryParams({
              kind: "CUSTOMER",
              active: "true",
              search: form.elements.customerSearch.value,
            }),
        );
        if (!form.isConnected || id !== customerSeq) return;
        form.elements.contactId.innerHTML =
          '<option value="">Consumidor final</option>' +
          data.content
            .map((c) => `<option value="${c.id}">${e(c.name)}</option>`)
            .join("");
      } catch (err) {
        if (form.isConnected) formError(form, err.message);
      }
    }, 250);
  };
  preview();
}
async function saleDetails(id) {
  closeDetails();
  const data = await api("/sales/" + id);
  showDialog(
    "Venda #" + data.number,
    e(data.contactName || "Consumidor final"),
    `<div class="sale-details-summary">${commerceBadge(data.status)}<strong>${money(data.total)}</strong><p>${data.status === "DRAFT" ? "Estoque e receita ainda não foram movimentados." : data.status === "CANCELLED" ? "Estoque devolvido e receita cancelada, quando houve confirmação." : data.paymentStatus === "PAID" ? "Recebimento registrado no financeiro." : "Receita registrada como pendente no financeiro."}</p></div><div class="sale-detail-items">${data.items.map((i) => `<article><div><strong>${e(i.productName)}</strong><small>${e(i.sku)} · ${quantityLabel(i.quantity)} ${e(i.unit)} × ${money(i.unitPrice)}</small></div><strong>${money(i.total)}</strong></article>`).join("")}</div>${data.notes ? `<p class="notice">${e(data.notes)}</p>` : ""}<dl class="detail-grid"><div><dt>Criada em</dt><dd>${dateLabel(data.createdAt?.slice(0, 10))}</dd></div>${data.confirmedAt ? `<div><dt>Confirmada em</dt><dd>${timestamp(data.confirmedAt)}</dd></div>` : ""}${data.cancelledAt ? `<div><dt>Cancelada em</dt><dd>${timestamp(data.cancelledAt)}</dd></div>` : ""}</dl><div class="sale-detail-actions">${canWrite() && data.status === "DRAFT" ? `<button type="button" class="btn" data-commerce-action="sale-confirm" data-id="${data.id}">Confirmar venda</button>` : ""}${data.entryId ? `<button type="button" class="btn secondary" data-commerce-action="sale-entry" data-id="${data.entryId}">Ver lançamento financeiro</button>` : ""}${canWrite() && data.status !== "CANCELLED" ? `<button type="button" class="btn text" data-commerce-action="sale-cancel" data-id="${data.id}">Cancelar venda</button>` : ""}</div>`,
    async () => {},
    "Fechar",
  );
  modal.querySelector("[data-action=close]").focus();
}
async function confirmSaleDialog(id) {
  const accounts = await api("/finance/accounts");
  if (!accounts.length) {
    toast("Cadastre uma conta em Contas antes de confirmar.");
    return;
  }
  state.accounts = accounts;
  showDialog(
    "Confirmar venda",
    "A confirmação movimenta estoque e cria uma receita vinculada.",
    accountOptions("accountId", "Conta de recebimento") +
      field("Vencimento", "dueDate", "date", "required", localDate()) +
      '<label class="check"><input type="checkbox" name="paid"> Marcar como recebida</label><div id="sale-payment-date" hidden>' +
      field(
        "Data do recebimento",
        "paymentDate",
        "date",
        `max="${localDate()}"`,
        localDate(),
      ) +
      '</div><p class="notice">O servidor confere o estoque novamente. Se algum item estiver indisponível, a confirmação inteira será desfeita.</p>',
    async (data) => {
      await send("/sales/" + id + "/confirm", {
        accountId: data.accountId,
        dueDate: data.dueDate,
        paid: data.paid === "on",
        paymentDate: data.paid === "on" ? data.paymentDate : null,
      });
      commerce.pendingSale = id;
      toast("Venda confirmada. Estoque e receita registrados.");
    },
    "Confirmar venda",
  );
  const form = modal.querySelector("form");
  form.elements.paid.onchange = () => {
    form.querySelector("#sale-payment-date").hidden =
      !form.elements.paid.checked;
    form.elements.paymentDate.required = form.elements.paid.checked;
  };
}
function cancelSaleDialog(id) {
  showDialog(
    "Cancelar venda?",
    "Os registros serão preservados para consulta.",
    '<p class="notice">Se a venda foi confirmada, os itens voltarão ao estoque e a receita será cancelada. Se já foi recebida, o valor sairá do saldo financeiro; esta ação não realiza devolução bancária.</p>',
    async () => {
      await send("/sales/" + id + "/cancel");
      commerce.pendingSale = id;
      toast("Venda cancelada. Histórico preservado.");
    },
    "Confirmar cancelamento",
  );
}
async function commerceAction(btn) {
  const action = btn.dataset.action || btn.dataset.commerceAction;
  if (
    [
      "products-prev",
      "products-next",
      "stock-prev",
      "stock-next",
      "sales-prev",
      "sales-next",
    ].includes(action)
  ) {
    const key = action.split("-")[0] + "Page";
    commerce[key] += action.endsWith("next") ? 1 : -1;
    await safeLoad();
    return true;
  }
  if (
    ![
      "product-new",
      "product-edit",
      "stock-new",
      "stock-history",
      "stock-clear",
      "sale-new",
      "sale-details",
      "sale-confirm",
      "sale-cancel",
      "sale-entry",
    ].includes(action)
  )
    return false;
  if (
    [
      "product-new",
      "product-edit",
      "stock-new",
      "sale-new",
      "sale-confirm",
      "sale-cancel",
    ].includes(action) &&
    !canWrite()
  ) {
    toast("Seu perfil permite somente consultas.");
    return true;
  }
  const p = commerce.products?.content.find((p) => p.id === btn.dataset.id);
  try {
    if (action === "product-new") productDialog();
    if (action === "product-edit") productDialog(p);
    if (action === "stock-new") stockDialog(p);
    if (action === "stock-history") {
      commerce.stockProduct = { id: p.id, name: p.name };
      commerce.stockPage = 0;
      state.page = "stock";
      await safeLoad();
    }
    if (action === "stock-clear") {
      commerce.stockProduct = null;
      commerce.stockPage = 0;
      await safeLoad();
    }
    if (action === "sale-new") saleDialog();
    if (action === "sale-details") await saleDetails(btn.dataset.id);
    if (action === "sale-confirm") await confirmSaleDialog(btn.dataset.id);
    if (action === "sale-cancel") cancelSaleDialog(btn.dataset.id);
    if (action === "sale-entry") {
      modal.close();
      await openDetails(btn.dataset.id);
    }
  } catch (err) {
    toast(err.message);
  }
  return true;
}
document.addEventListener("click", async (ev) => {
  const btn = ev.target.closest("[data-commerce-action]");
  if (btn) {
    btn.disabled = true;
    try {
      await commerceAction(btn);
    } finally {
      btn.disabled = false;
    }
  }
});
