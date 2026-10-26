"use strict";
const reconciliation = {
  accountId: "",
  status: "OPEN",
  page: 0,
  data: null,
  categories: [],
  uploading: false,
  ofx: "",
  filename: "",
  preview: null,
  selected: new Set(),
  groupSelection: new Set(),
  error: "",
  notice: "",
  busy: false,
  generation: 0,
};
function resetReconciliation() {
  Object.assign(reconciliation, {
    accountId: "",
    status: "OPEN",
    page: 0,
    data: null,
    categories: [],
    uploading: false,
    ofx: "",
    filename: "",
    preview: null,
    selected: new Set(),
    groupSelection: new Set(),
    error: "",
    notice: "",
    busy: false,
    generation: reconciliation.generation + 1,
  });
}
async function loadReconciliation(id) {
  const session = reconciliation.generation;
  const [accounts, categories] = await Promise.all([
    api("/finance/accounts"),
    api("/categories"),
  ]);
  if (id !== requestId || !state.user || session !== reconciliation.generation)
    return;
  state.accounts = accounts;
  reconciliation.categories = categories;
  if (!accounts.some((a) => a.id === reconciliation.accountId))
    reconciliation.accountId = accounts[0]?.id || "";
  const data = reconciliation.accountId
    ? await api(
        "/finance/reconciliation?" +
          queryParams({
            accountId: reconciliation.accountId,
            status: reconciliation.status,
            page: reconciliation.page,
            size: 25,
          }),
      )
    : null;
  if (id !== requestId || !state.user || session !== reconciliation.generation)
    return;
  reconciliation.data = data;
  shell();
  mountReconciliation();
}
const recMoney = (value) => importMoney(String(value).replace(/^[-+]/, ""));
const recLabel = (status) =>
  ({ OPEN: "Em revisão", LINKED: "Conciliado", IGNORED: "Ignorado" })[status];
function recCard(r) {
  const income = !r.amount.startsWith("-");
  return `<article class="card rec-movement" data-rec-id="${r.id}"><div class="rec-movement-top">${canWrite() && r.status === "OPEN" ? `<label class="rec-group-check"><input type="checkbox" data-rec-group-select="${r.id}" ${reconciliation.groupSelection.has(r.id) ? "checked" : ""}> Agrupar</label>` : ""}<span class="badge ${r.status === "LINKED" ? "paid" : "pending"}">${recLabel(r.status)}</span><time datetime="${r.date}">${dateLabel(r.date)}</time></div><div class="rec-movement-main"><div class="rec-direction ${income ? "rec-income" : "rec-expense"}" aria-label="${income ? "Entrada" : "Saída"}">${income ? "↙" : "↗"}</div><div><h2>${e(r.description)}</h2><small>Identificador bancário: ${e(r.fitId)}</small></div><strong class="rec-value">${income ? "+" : "−"} ${recMoney(r.amount)}</strong></div>${r.status === "OPEN" ? `<div class="rec-suggestion"><strong>${r.suggestions.length ? `${r.suggestions.length} sugestão(ões) para revisar` : "Nenhuma sugestão próxima"}</strong><p>${r.suggestions.length ? e(r.suggestions[0].entry.description) + " · " + recMoney(r.suggestions[0].entry.amount) : "Busque lançamentos com o mesmo valor ou crie um novo após conferir o financeiro."}</p><small>Sugestões por valor, tipo e datas próximas. A decisão é sua.</small></div>` : r.entry ? `<div class="rec-suggestion"><strong>${r.batchId ? `Grupo conciliado · ${r.batchMovements} movimentos` : `Vinculado a ${e(r.entry.description)}`}</strong><p>${e(r.entry.category)} · Movimento em ${dateLabel(r.date)}</p></div>` : ""}${r.reason ? `<p class="rec-reason">${e(r.reason)}</p>` : ""}<div class="rec-card-actions">${r.entryId ? `<button class="btn text small" data-action="details" data-id="${r.entryId}">Ver lançamento →</button>` : ""}${canWrite() ? (r.status === "OPEN" ? `<button class="btn secondary small" data-action="rec-match" data-id="${r.id}">Buscar correspondência</button><button class="btn small" data-action="rec-create" data-id="${r.id}">Criar lançamento</button><button class="btn text small" data-action="rec-ignore" data-id="${r.id}">Ignorar</button>` : r.batchId ? "" : `<button class="btn secondary small" data-action="rec-reopen" data-id="${r.id}">Reabrir revisão</button>`) : ""}</div></article>`;
}
function recUpload() {
  const p = reconciliation.preview;
  return `<section class="card rec-upload"><div class="card-head"><div><span class="eyebrow">IMPORTAR EXTRATO</span><h2>Primeiro confira. Depois concilie.</h2><p>OFX bancário em reais, até 1 MB e 500 movimentos. Importar para revisão não altera o saldo.</p></div><button class="btn text small" data-action="rec-upload-close">Fechar importação</button></div><div class="rec-upload-controls"><label class="btn secondary import-file-trigger">Escolher OFX<input class="sr-only" id="rec-file" type="file" accept=".ofx" aria-label="Arquivo OFX" ${reconciliation.busy ? "disabled" : ""}></label><span class="rec-filename">${e(reconciliation.filename || "Exporte o extrato no internet banking.")}</span><button class="btn" data-action="rec-preview" ${reconciliation.busy ? "disabled" : ""}>${reconciliation.busy ? "Lendo extrato…" : "Gerar prévia OFX"}</button></div><p><a href="/api/v1/finance/reconciliation/example" download>Baixar OFX fictício para uma conta de avaliação</a></p><p class="muted">OFX 1.x/2.x em UTF-8 ou Windows-1252. Uma conta por arquivo; cartões, investimentos e correções bancárias ficam fora desta etapa.</p>${p ? `<div class="rec-source"><span>Banco <strong>${e(p.bank)}</strong> · Conta <strong>${e(p.accountHint)}</strong></span><span>Destino: <strong>${e(p.accountName)}</strong></span></div><p class="notice">Confira o banco e o final da conta. Esta identificação bancária ficará vinculada ao destino escolhido no Fintry.</p><div class="import-counts"><div><strong>${p.ready}</strong><span>Novos</span></div><div><strong>${p.duplicates}</strong><span>Repetidos</span></div><div><strong>${p.invalid}</strong><span>Com erro</span></div></div><div class="rec-preview-scroll"><table class="rec-preview-table"><thead><tr><th>Seleção</th><th>Movimento</th><th>Data</th><th>Valor</th><th>Validação</th></tr></thead><tbody>${p.rows.map((r) => `<tr><td><input type="checkbox" data-rec-row="${r.number}" aria-label="Selecionar movimento ${r.number}" ${r.state !== "READY" ? "disabled" : reconciliation.selected.has(r.number) ? "checked" : ""}></td><td><strong>${e(r.description || "Registro inválido")}</strong><small>${e(r.fitId)}</small></td><td>${r.date ? dateLabel(r.date) : "—"}</td><td>${r.amount ? (r.amount.startsWith("-") ? "− " : "+ ") + recMoney(r.amount) : "—"}</td><td><span class="badge ${r.state === "READY" ? "paid" : r.state === "INVALID" ? "overdue-badge" : "pending"}">${{ READY: "Novo", DUPLICATE: "Repetido", INVALID: "Com erro" }[r.state]}</span><small>${e(r.message)}</small></td></tr>`).join("")}</tbody></table></div><div class="rec-preview-confirm"><label class="check"><input id="rec-origin-confirm" type="checkbox"> Conferi o banco e o final da conta de destino.</label><button class="btn" data-action="rec-import">Adicionar selecionados à revisão</button></div>` : ""}</section>`;
}
function renderReconciliation() {
  const d = reconciliation.data;
  return (
    head(
      "Seu extrato, em equilíbrio.",
      "Compare os movimentos do banco com o financeiro e resolva cada diferença com clareza.",
      canWrite()
        ? '<button class="btn" data-action="rec-upload">Importar OFX</button>'
        : "",
    ) +
    `<section class="rec-account-bar card"><label class="field">Conta do Fintry<select id="rec-account" ${reconciliation.busy ? "disabled" : ""}>${state.accounts.map((a) => `<option value="${a.id}" ${a.id === reconciliation.accountId ? "selected" : ""}>${e(a.name)}</option>`).join("")}</select></label><p>Vincular um realizado preserva o saldo. Vincular um pendente registra a baixa na data do banco.</p></section>${reconciliation.notice ? `<p class="notice" role="status">${e(reconciliation.notice)}</p>` : ""}<p class="form-error" id="rec-error" role="alert" tabindex="-1" ${reconciliation.error ? "" : "hidden"}>${e(reconciliation.error)}</p>${
      !state.accounts.length
        ? '<section class="card empty"><h2>Cadastre uma conta para começar.</h2><p>Ela será o destino dos movimentos bancários.</p></section>'
        : `${canWrite() && reconciliation.uploading ? recUpload() : ""}<div class="rec-metrics" aria-label="Situação da conciliação">${[
            ["OPEN", d?.open || 0, "Em revisão", "Confira as correspondências"],
            [
              "LINKED",
              d?.linked || 0,
              "Conciliados",
              "Extrato e financeiro vinculados",
            ],
            [
              "IGNORED",
              d?.ignored || 0,
              "Ignorados",
              "Decisões com motivo registrado",
            ],
          ]
            .map(
              ([key, count, label, detail]) =>
                `<button class="card rec-metric" data-action="rec-status" data-status="${key}" aria-pressed="${reconciliation.status === key}"><span>${label}</span><strong>${count}</strong><small>${detail}</small></button>`,
            )
            .join(
              "",
            )}</div>${canWrite() ? recGroupControl() : ""}<div class="rec-list-heading"><div><h2>${recLabel(reconciliation.status) || "Todos os movimentos"}</h2><p>${d?.totalElements || 0} movimento(s) neste filtro</p></div><button class="btn text small" data-action="rec-status" data-status="" aria-pressed="${reconciliation.status === ""}">Ver todos</button><button class="btn secondary small" data-action="rec-refresh">Atualizar</button></div><div class="rec-grid">${d?.content.map(recCard).join("") || '<section class="card empty"><div class="empty-icon">' + icon("wallet") + "</div><h2>Nenhum movimento neste filtro.</h2><p>Importe um extrato OFX ou consulte outra situação.</p></section>"}</div>${d && d.totalPages > 1 ? `<div class="rec-pagination"><button class="btn secondary" data-action="rec-page" data-page="${d.number - 1}" ${d.number === 0 ? "disabled" : ""}>Anterior</button><span>Página ${d.number + 1} de ${d.totalPages}</span><button class="btn secondary" data-action="rec-page" data-page="${d.number + 1}" ${d.number + 1 >= d.totalPages ? "disabled" : ""}>Próxima</button></div>` : ""}`
    }`
  );
}
function recGroupControl() {
  const ids = [...reconciliation.groupSelection];
  return ids.length >= 2
    ? `<section class="card rec-group-control"><div><strong>${ids.length} movimentos selecionados</strong><small>Selecione movimentos do mesmo tipo e com saldo suficiente no lançamento.</small></div><button class="btn" data-action="rec-group">Conciliar em grupo</button><button class="btn text small" data-action="rec-group-clear">Limpar seleção</button></section>`
    : "";
}
function mountReconciliation() {
  if (state.page !== "reconciliation" || !state.user) return;
  document.querySelector("#page-content").innerHTML = renderReconciliation();
  document
    .querySelector("#rec-account")
    ?.addEventListener("change", async (ev) => {
      reconciliation.accountId = ev.target.value;
      reconciliation.groupSelection.clear();
      reconciliation.page = 0;
      reconciliation.preview = null;
      reconciliation.selected.clear();
      reconciliation.error = "";
      reconciliation.notice = "";
      reconciliation.generation++;
      await safeLoad();
    });
  document
    .querySelector("#rec-file")
    ?.addEventListener("change", recFileChanged);
  document.querySelectorAll("[data-rec-group-select]").forEach((input) =>
    input.addEventListener("change", () => {
      const id = input.dataset.recGroupSelect;
      if (input.checked) reconciliation.groupSelection.add(id);
      else reconciliation.groupSelection.delete(id);
      mountReconciliation();
    }),
  );
  document.querySelectorAll("[data-rec-row]").forEach((input) =>
    input.addEventListener("change", () => {
      const n = Number(input.dataset.recRow);
      if (input.checked) reconciliation.selected.add(n);
      else reconciliation.selected.delete(n);
    }),
  );
}
async function recFileChanged(ev) {
  const file = ev.target.files[0];
  if (!file) return;
  const generation = ++reconciliation.generation;
  Object.assign(reconciliation, {
    ofx: "",
    filename: file.name,
    preview: null,
    selected: new Set(),
    error: "",
    busy: true,
  });
  mountReconciliation();
  try {
    if (file.size > 1048576 || !/\.ofx$/i.test(file.name))
      throw new Error("Escolha um arquivo .ofx de até 1 MB.");
    const bytes = await file.arrayBuffer();
    let content;
    try {
      content = new TextDecoder("utf-8", { fatal: true }).decode(bytes);
    } catch {
      content = new TextDecoder("windows-1252", { fatal: true }).decode(bytes);
    }
    if (generation !== reconciliation.generation) return;
    if (!content.trim()) throw new Error("O arquivo OFX está vazio.");
    reconciliation.ofx = content;
  } catch (err) {
    if (generation === reconciliation.generation)
      reconciliation.error = err.message;
  } finally {
    if (generation === reconciliation.generation) {
      reconciliation.busy = false;
      mountReconciliation();
    }
  }
}
async function reconciliationAction(btn) {
  const action = btn.dataset.action;
  if (!action?.startsWith("rec-")) return false;
  if (reconciliation.busy) return true;
  if (action === "rec-group-clear") {
    reconciliation.groupSelection.clear();
    mountReconciliation();
    return true;
  }
  if (action === "rec-group") {
    if (!canWrite()) return true;
    const ids = [...reconciliation.groupSelection];
    const generation = reconciliation.generation;
    btn.disabled = true;
    try {
      const candidates = await send(
        "/finance/reconciliation/group/candidates",
        {
          accountId: reconciliation.accountId,
          movementIds: ids,
        },
      );
      if (generation !== reconciliation.generation || !state.user || modal.open)
        return true;
      const selected =
        reconciliation.data?.content.filter((r) => ids.includes(r.id)) || [];
      const total = selected.reduce(
        (sum, r) => sum + Number(String(r.amount).replace(",", ".")),
        0,
      );
      const kind = total < 0 ? "Saídas" : "Entradas";
      showDialog(
        "Conciliar movimentos em grupo",
        "As baixas serão registradas separadamente, cada uma na data do extrato.",
        `<p class="notice">${ids.length} ${kind.toLowerCase()} selecionadas · Total ${recMoney(total.toFixed(2))}. O grupo não pode exceder o saldo restante do lançamento.</p><label class="field">Lançamento de destino<select name="entryId" required><option value="">Escolha um lançamento</option>${candidates.map((r) => `<option value="${r.id}">${e(r.description)} · ${e(r.status === "PARTIAL" ? "Parcial" : "Pendente")} · restante ${recMoney(r.remainingAmount)}</option>`).join("")}</select></label><label class="field">Motivo e detalhes de divergência<input name="reason" required maxlength="255" placeholder="Ex.: repasse dividido em vários movimentos"></label><p class="muted">Tarifas são conciliadas como despesas separadas. O motivo fica registrado no histórico de cada movimento e do grupo.</p>`,
        async (data) => {
          if (!data.entryId) throw new Error("Escolha um lançamento.");
          await send("/finance/reconciliation/group", {
            accountId: reconciliation.accountId,
            movementIds: ids,
            entryId: data.entryId,
            reason: data.reason,
          });
          reconciliation.groupSelection.clear();
          reconciliation.notice =
            "Grupo conciliado. Cada movimento foi registrado no histórico de pagamentos.";
          reconciliation.status = "OPEN";
          reconciliation.page = 0;
        },
        "Confirmar grupo",
      );
    } catch (err) {
      reconciliation.error = err.message;
      mountReconciliation();
    } finally {
      btn.disabled = false;
    }
    return true;
  }
  if (action === "rec-status") {
    reconciliation.groupSelection.clear();
    reconciliation.status = btn.dataset.status;
    reconciliation.page = 0;
    await safeLoad();
    return true;
  }
  if (action === "rec-page") {
    reconciliation.groupSelection.clear();
    reconciliation.page = Number(btn.dataset.page);
    await safeLoad();
    return true;
  }
  if (action === "rec-refresh") {
    await safeLoad();
    return true;
  }
  if (!canWrite()) return true;
  if (action === "rec-upload" || action === "rec-upload-close") {
    reconciliation.uploading = action === "rec-upload";
    mountReconciliation();
    return true;
  }
  if (action === "rec-preview") {
    const generation = ++reconciliation.generation;
    reconciliation.busy = true;
    reconciliation.error = "";
    mountReconciliation();
    try {
      if (!reconciliation.ofx)
        throw new Error("Escolha um arquivo OFX antes de gerar a prévia.");
      const p = await send("/finance/reconciliation/preview", {
        accountId: reconciliation.accountId,
        ofx: reconciliation.ofx,
      });
      if (generation === reconciliation.generation) {
        reconciliation.preview = p;
        reconciliation.selected = new Set(
          p.rows.filter((r) => r.state === "READY").map((r) => r.number),
        );
      }
    } catch (err) {
      if (generation === reconciliation.generation) {
        reconciliation.error = err.message;
        reconciliation.preview = null;
      }
    } finally {
      if (generation === reconciliation.generation) {
        reconciliation.busy = false;
        mountReconciliation();
        document
          .querySelector(reconciliation.error ? "#rec-error" : ".rec-source")
          ?.scrollIntoView({ block: "nearest" });
      }
    }
    return true;
  }
  if (action === "rec-import") {
    if (
      !reconciliation.selected.size ||
      !document.querySelector("#rec-origin-confirm")?.checked
    ) {
      reconciliation.error =
        "Selecione movimentos novos e confirme a conta de destino.";
      const box = document.querySelector("#rec-error");
      box.textContent = reconciliation.error;
      box.hidden = false;
      box.focus();
      return true;
    }
    reconciliation.error = "";
    const generation = reconciliation.generation,
      snapshot = {
        accountId: reconciliation.accountId,
        ofx: reconciliation.ofx,
        selectedRows: [...reconciliation.selected],
      };
    showDialog(
      "Adicionar extrato à revisão",
      "O saldo não será alterado nesta etapa.",
      `<p class="notice">${snapshot.selectedRows.length} movimento(s) do banco ${e(reconciliation.preview.bank)}, conta ${e(reconciliation.preview.accountHint)}, para ${e(reconciliation.preview.accountName)}.</p><p>Depois, escolha uma correspondência, crie um lançamento ou ignore cada movimento com um motivo.</p>`,
      async () => {
        if (generation !== reconciliation.generation)
          throw new Error("O arquivo ou a conta mudou. Gere outra prévia.");
        const result = await send("/finance/reconciliation/import", snapshot);
        if (generation !== reconciliation.generation) return;
        Object.assign(reconciliation, {
          uploading: false,
          error: "",
          ofx: "",
          filename: "",
          preview: null,
          notice: `${result.imported} movimento(s) adicionado(s) à revisão. ${result.skipped} repetido(s) ignorado(s). Seu saldo foi preservado.`,
          status: "OPEN",
          page: 0,
        });
      },
      "Adicionar à revisão",
    );
    return true;
  }
  const row = reconciliation.data?.content.find((r) => r.id === btn.dataset.id);
  if (!row) return true;
  const generation = reconciliation.generation;
  const resolve = async (payload) => {
    if (generation !== reconciliation.generation || !state.user)
      throw new Error("A sessão ou a conta mudou. Atualize a página.");
    await send(`/finance/reconciliation/${row.id}/resolve`, {
      revision: row.revision,
      ...payload,
    });
    if (generation === reconciliation.generation) {
      reconciliation.error = "";
      reconciliation.notice =
        payload.action === "REOPEN"
          ? "Revisão reaberta. O pagamento foi mantido; confira o financeiro antes de corrigir."
          : "Decisão registrada na conciliação e no histórico.";
    }
  };
  const summary = `<div class="rec-dialog-summary"><strong>${e(row.description)}</strong><span>${dateLabel(row.date)} · ${row.amount.startsWith("-") ? "Saída" : "Entrada"} · ${recMoney(row.amount)}</span></div>`;
  if (action === "rec-match") {
    btn.disabled = true;
    try {
      const candidates = await api(
        `/finance/reconciliation/${row.id}/candidates`,
      );
      if (
        generation !== reconciliation.generation ||
        state.page !== "reconciliation" ||
        !state.user ||
        modal.open
      )
        return true;
      showDialog(
        "Vincular lançamento",
        "Confira os dados antes de conciliar.",
        summary +
          `<label class="field">Buscar descrição<input id="rec-candidate-search" type="search" maxlength="120" placeholder="Buscar entre valores iguais"></label><p class="muted">Até 100 resultados por busca, da mesma conta, tipo e valor. Realizados precisam ter a mesma data; pendentes serão baixados em ${dateLabel(row.date)}.</p><div id="rec-candidates" class="rec-candidates">${recCandidateOptions(candidates)}</div><p class="notice">Vincular um realizado preserva o saldo. Vincular um pendente registra o pagamento na data do banco.</p>`,
        async (data) => {
          if (!data.entryId) throw new Error("Escolha uma correspondência.");
          await resolve({ action: "LINK", entryId: data.entryId });
        },
        "Confirmar vínculo",
      );
      let timer,
        sequence = 0;
      document
        .querySelector("#rec-candidate-search")
        .addEventListener("input", (ev) => {
          clearTimeout(timer);
          const search = ev.target.value,
            version = ++sequence,
            form = modal.querySelector("form");
          timer = setTimeout(async () => {
            try {
              const rows = await api(
                `/finance/reconciliation/${row.id}/candidates?` +
                  queryParams({ search }),
              );
              if (
                version === sequence &&
                form.isConnected &&
                generation === reconciliation.generation
              ) {
                const selected = form.querySelector(
                  '[name="entryId"]:checked',
                )?.value;
                form.querySelector("#rec-candidates").innerHTML =
                  recCandidateOptions(rows);
                const preserved = Array.from(
                  form.querySelectorAll('[name="entryId"]'),
                ).find((input) => input.value === selected);
                if (preserved) preserved.checked = true;
              }
            } catch (err) {
              if (form.isConnected)
                form.querySelector(".form-error").textContent = err.message;
            }
          }, 250);
        });
    } catch (err) {
      reconciliation.error = err.message;
      mountReconciliation();
    } finally {
      btn.disabled = false;
    }
    return true;
  }
  if (action === "rec-create") {
    const kind = row.amount.startsWith("-") ? "EXPENSE" : "INCOME",
      cats = reconciliation.categories.filter(
        (c) => c.active && (c.kind === kind || c.kind === "BOTH"),
      );
    showDialog(
      "Criar lançamento do extrato",
      "Será registrado como realizado na data do banco.",
      summary +
        `<label class="field">Categoria cadastrada<select name="categoryId"><option value="">Usar categoria avulsa abaixo</option>${cats.map((c) => `<option value="${c.id}">${e(c.name)}</option>`).join("")}</select></label><label class="field">Categoria avulsa<input name="category" maxlength="80" value="Movimentos bancários"></label><label class="field">Motivo, se houver outro lançamento com o mesmo valor<input name="reason" maxlength="255" placeholder="Explique por que este movimento é distinto"></label><p class="notice">Confira as correspondências para evitar duplicidades. Criar uma ${kind === "INCOME" ? "receita aumentará" : "despesa reduzirá"} o saldo em ${recMoney(row.amount)}.</p><label class="check"><input type="checkbox" required> Conferi o financeiro e quero criar um novo lançamento.</label>`,
      (data) =>
        resolve({
          action: "CREATE",
          categoryId: data.categoryId || null,
          category: data.category,
          reason: data.reason,
        }),
      "Criar e conciliar",
    );
    return true;
  }
  if (action === "rec-ignore" || action === "rec-reopen") {
    const reopen = action === "rec-reopen";
    showDialog(
      reopen ? "Reabrir revisão" : "Ignorar movimento",
      reopen
        ? "O vínculo será removido; o pagamento não será estornado."
        : "O movimento continuará registrado, sem criar lançamento.",
      summary +
        `<label class="field">Motivo<input name="reason" required maxlength="255"></label>${reopen ? '<p class="notice">Lançamentos criados ou baixados pela conciliação permanecem realizados. Após reabrir, consulte e corrija o financeiro pelas ações existentes.</p>' : ""}`,
      (data) =>
        resolve({ action: reopen ? "REOPEN" : "IGNORE", reason: data.reason }),
      reopen ? "Reabrir revisão" : "Confirmar decisão",
    );
    return true;
  }
  return true;
}
function recCandidateOptions(rows) {
  return rows.length
    ? rows
        .map(
          ({ entry: r, explanation }) =>
            `<label class="rec-candidate"><input type="radio" name="entryId" value="${r.id}"><span><strong>${e(r.description)}</strong><small>${e(r.category)} · ${r.status === "PAID" ? "Realizado" : "Pendente"} · ${dateLabel(r.paymentDate || r.dueDate)}</small><small>${e(explanation)}</small>${r.saleId ? "<small>Receita de venda</small>" : r.transferId ? "<small>Transferência entre contas</small>" : ""}</span><strong>${recMoney(r.amount)}</strong></label>`,
        )
        .join("")
    : '<p class="empty">Nenhum lançamento disponível. Refine a busca ou volte para criar um lançamento após conferir o financeiro.</p>';
}
