"use strict";
const entryImport = {
  csv: "",
  filename: "",
  accountId: "",
  preview: null,
  selected: new Set(),
  result: null,
  filter: "ALL",
  busy: false,
  error: "",
  generation: 0,
};
function resetEntryImport() {
  Object.assign(entryImport, {
    csv: "",
    filename: "",
    accountId: "",
    preview: null,
    selected: new Set(),
    result: null,
    filter: "ALL",
    busy: false,
    error: "",
    generation: entryImport.generation + 1,
  });
}
async function loadImports(id) {
  const accounts = await api("/finance/accounts");
  if (id !== requestId || !state.user) return;
  state.accounts = accounts;
  if (!accounts.some((a) => a.id === entryImport.accountId)) {
    entryImport.accountId = accounts[0]?.id || "";
    entryImport.preview = null;
    entryImport.selected.clear();
  }
  shell();
  mountImports();
}
function importSteps() {
  const step = entryImport.result ? 3 : entryImport.preview ? 2 : 1;
  return `<ol class="import-steps" aria-label="Etapas da importação">${["Arquivo e conta", "Revisão dos registros", "Confirmação"].map((label, i) => `<li class="${i + 1 === step ? "current" : i + 1 < step ? "complete" : ""}" ${i + 1 === step ? 'aria-current="step"' : ""}><span aria-hidden="true">${i + 1}</span>${label}</li>`).join("")}</ol>`;
}
function importRow(row) {
  const ready = row.state === "READY";
  const label = {
    READY: "Pronto para importar",
    DUPLICATE: "Possível duplicidade",
    INVALID: "Precisa de correção",
  }[row.state];
  const valid = row.state !== "INVALID";
  return `<tr class="import-row ${row.state.toLowerCase()}" data-import-state="${row.state}" ${entryImport.filter !== "ALL" && row.state !== entryImport.filter ? "hidden" : ""}><td class="import-select" data-label="Selecionar"><input type="checkbox" data-import-row="${row.number}" aria-label="Selecionar registro ${row.number}" ${ready ? (entryImport.selected.has(row.number) ? "checked" : "") : "disabled"}></td><td data-label="Registro"><span class="import-record-number">#${row.number}</span><strong>${e(row.description || "Sem descrição")}</strong><small>${e(row.category || "Sem categoria")}</small></td><td data-label="Tipo / situação">${e(valid ? (row.type === "INCOME" ? "Receita" : "Despesa") : row.type)}<small>${e(valid ? (row.status === "PAID" ? "Realizado" : "Pendente") : row.status || "Pendente")}${row.paymentDate ? ` · ${e(valid ? dateLabel(row.paymentDate) : row.paymentDate)}` : ""}</small></td><td data-label="Vencimento">${e(valid ? dateLabel(row.dueDate) : row.dueDate)}</td><td class="import-amount" data-label="Valor">${e(valid ? importMoney(row.amount) : row.amount)}</td><td data-label="Validação"><span class="badge ${row.state === "INVALID" ? "overdue-badge" : row.state === "DUPLICATE" ? "pending" : "paid"}">${label}</span><small>${e(row.message)}</small></td></tr>`;
}
function renderImports() {
  const p = entryImport.preview,
    r = entryImport.result;
  const intro = head(
    "Traga seu financeiro para cá.",
    "Importe receitas e despesas com uma revisão antes de salvar.",
    '<a class="btn secondary" href="/api/v1/finance/imports/template" download>Baixar modelo CSV</a>',
  );
  if (!canWrite())
    return (
      intro +
      '<section class="card empty"><h2>Importação para Administrador e Financeiro.</h2><p>Seu perfil permite consultar os lançamentos. Peça a um responsável para importar os registros.</p></section>'
    );
  if (!state.accounts.length)
    return (
      intro +
      '<section class="card empty"><h2>Primeiro, uma conta de destino.</h2><p>Cadastre um banco, carteira ou caixa para receber os lançamentos do arquivo.</p><button class="btn" data-action="account">Criar conta</button></section>'
    );
  return (
    intro +
    importSteps() +
    (r
      ? `<section class="card import-success" role="status"><div class="empty-icon">${icon("entries")}</div><span class="eyebrow">IMPORTAÇÃO CONCLUÍDA</span><h2>${r.imported} lançamento(s) importado(s).</h2><p>${r.skippedDuplicates ? `${r.skippedDuplicates} registro(s) da seleção foram ignorados por possível duplicidade na validação final.` : "Os lançamentos selecionados foram salvos nesta empresa."}</p><dl><div><dt>Receitas importadas</dt><dd>${importMoney(r.income)}</dd></div><div><dt>Despesas importadas</dt><dd>${importMoney(r.expense)}</dd></div></dl><p class="muted">Totais incluem previstos e realizados. Apenas realizados alteram o saldo atual.</p><div class="head-actions"><button class="btn" data-nav="entries">Ver lançamentos</button><button class="btn secondary" data-action="import-new">Importar outro arquivo</button></div></section>`
      : `<section class="card import-upload"><div><span class="eyebrow">01 · PREPARAR</span><h2>Escolha a origem dos registros.</h2><p>CSV UTF-8 de até 1 MB, com no máximo 500 lançamentos. A conta abaixo será usada para todo o arquivo.</p><label class="field">Conta de destino<select id="import-account" ${entryImport.busy ? "disabled" : ""}>${state.accounts.map((a) => `<option value="${a.id}" ${a.id === entryImport.accountId ? "selected" : ""}>${e(a.name)}</option>`).join("")}</select></label></div><div class="import-file-box"><div class="import-file-heading">Arquivo CSV</div><label class="btn secondary import-file-trigger">Escolher arquivo<input id="import-file" type="file" class="sr-only" aria-label="Arquivo CSV" accept=".csv,text/csv" aria-describedby="import-file-note" ${entryImport.busy ? "disabled" : ""}></label><p id="import-file-note" aria-live="polite">${entryImport.filename ? `<strong>${e(entryImport.filename)}</strong><span>Arquivo carregado. Escolha outro para substituir.</span>` : "Use o modelo do Fintry ou adapte o cabeçalho da sua planilha."}</p><button class="btn" data-action="import-preview" ${entryImport.busy ? 'disabled aria-busy="true"' : ""}>${entryImport.busy ? "Validando arquivo…" : "Gerar prévia"}</button></div></section><p class="form-error import-error" id="import-error" role="alert" tabindex="-1" ${entryImport.error ? "" : "hidden"}>${e(entryImport.error)}</p><details class="card import-help"><summary>Como preparar a planilha e conferir duplicidades</summary><div><p><strong>Colunas obrigatórias:</strong> descricao, tipo, valor, vencimento e categoria. Opcionais: situacao e data_pagamento. Use ponto e vírgula ou vírgula como separador; campos com separador ou quebra de linha precisam estar entre aspas.</p><p><strong>Valores:</strong> 1234,56, 1.234,56 ou 1234.56, sem R$, negativos ou mais de duas casas decimais. <strong>Datas:</strong> DD/MM/AAAA ou AAAA-MM-DD, entre 1900 e 2100. Tipo: receita ou despesa. Situação: pendente (padrão) ou realizado; realizados exigem data_pagamento até hoje.</p><p><strong>Categorias:</strong> nomes já cadastrados são vinculados, sem diferenciar maiúsculas e minúsculas. Categorias novas permanecem avulsas; não são criadas no cadastro. Categorias inativas ou incompatíveis geram erro.</p><p><strong>Possível duplicidade:</strong> mesma conta, descrição (ignorando maiúsculas), tipo, valor e vencimento, mesmo com categoria/situação diferentes. Cancelados e transferências ficam fora da comparação. Repetições no arquivo também são ignoradas. Se dois registros iguais forem legítimos, confira e cadastre o segundo manualmente.</p><p>A numeração começa no registro 2, após o cabeçalho; linhas vazias são ignoradas. Não há importação de contas, contatos, séries, vendas, transferências ou comprovantes nesta etapa.</p></div></details>${
          p
            ? `<section class="card import-review" aria-labelledby="import-review-title"><div class="card-head"><div><span class="eyebrow">02 · REVISAR</span><h2 id="import-review-title" tabindex="-1">Confira antes de importar.</h2><p>${p.total} registro(s) para ${e(p.accountName)}. A prévia não altera seus dados.</p></div></div><div class="import-counts" aria-label="Resultado da validação"><div><strong>${p.ready}</strong><span>Prontos</span></div><div><strong>${p.duplicates}</strong><span>Possíveis duplicidades</span></div><div><strong>${p.invalid}</strong><span>Com erro</span></div></div><div class="import-toolbar"><div class="import-filters" role="group" aria-label="Filtrar registros da prévia">${[
                ["ALL", "Todos"],
                ["READY", "Prontos"],
                ["DUPLICATE", "Duplicidades"],
                ["INVALID", "Com erro"],
              ]
                .map(
                  ([key, label]) =>
                    `<button class="btn secondary small" data-action="import-filter" data-filter="${key}" aria-pressed="${entryImport.filter === key}">${label}</button>`,
                )
                .join(
                  "",
                )}</div><div class="row-actions"><button class="btn text small" data-action="import-select">Selecionar prontos</button><button class="btn text small" data-action="import-clear">Limpar seleção</button></div></div><div class="import-table-wrap" role="region" aria-label="Prévia dos lançamentos" tabindex="0"><table class="import-table"><thead><tr><th scope="col">Seleção</th><th scope="col">Registro / categoria</th><th scope="col">Tipo / situação</th><th scope="col">Vencimento</th><th scope="col">Valor</th><th scope="col">Validação</th></tr></thead><tbody>${p.rows.map(importRow).join("")}</tbody></table><p class="import-no-results" hidden>Nenhum registro neste filtro.</p></div><p class="import-review-note">Corrija os erros na planilha e gere outra prévia, ou importe somente os registros prontos selecionados. Possíveis duplicidades ficam fora da seleção.</p></section><section class="card import-confirm-bar" aria-label="Confirmar seleção"><div><strong id="import-selection-count" aria-live="polite"></strong><span id="import-selection-totals"></span></div><button class="btn" data-action="import-confirm">Revisar e importar</button></section>`
            : ""
        }`)
  );
}
function mountImports() {
  if (state.page !== "imports" || !state.user) return;
  const container = document.querySelector("#page-content");
  container.innerHTML = renderImports();
  container
    .querySelector("#import-file")
    ?.addEventListener("change", importFileChanged);
  container
    .querySelector("#import-account")
    ?.addEventListener("change", (ev) => {
      entryImport.accountId = ev.target.value;
      entryImport.preview = null;
      entryImport.selected.clear();
      entryImport.error = "";
      entryImport.generation++;
      mountImports();
    });
  container.querySelectorAll("[data-import-row]").forEach((control) =>
    control.addEventListener("change", () => {
      const n = Number(control.dataset.importRow);
      if (control.checked) entryImport.selected.add(n);
      else entryImport.selected.delete(n);
      updateImportSelection();
    }),
  );
  updateImportSelection();
}
function selectedImportRows() {
  return (
    entryImport.preview?.rows.filter(
      (r) => r.state === "READY" && entryImport.selected.has(r.number),
    ) || []
  );
}
function importCents(value) {
  const [whole, fraction = ""] = String(value).split(".");
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
}
function formatImportCents(cents) {
  return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" })
    .formatToParts(cents / 100n)
    .map((part) =>
      part.type === "fraction"
        ? String(cents % 100n).padStart(2, "0")
        : part.value,
    )
    .join("");
}
function importMoney(value) {
  return formatImportCents(importCents(value));
}
function updateImportSelection() {
  const rows = selectedImportRows();
  const count = document.querySelector("#import-selection-count");
  if (!count) return;
  count.textContent = `${rows.length} registro(s) selecionado(s)`;
  const total = (type) =>
    rows
      .filter((r) => r.type === type)
      .reduce((sum, r) => sum + importCents(r.amount), 0n);
  document.querySelector("#import-selection-totals").textContent =
    `Receitas: ${formatImportCents(total("INCOME"))} · Despesas: ${formatImportCents(total("EXPENSE"))}`;
  const btn = document.querySelector('[data-action="import-confirm"]');
  btn.disabled = !rows.length || entryImport.busy;
}
async function importFileChanged(ev) {
  const file = ev.target.files[0];
  if (!file) return;
  const generation = ++entryImport.generation;
  entryImport.preview = null;
  entryImport.result = null;
  entryImport.csv = "";
  entryImport.selected.clear();
  entryImport.filename = file.name;
  entryImport.error = "";
  entryImport.busy = true;
  mountImports();
  try {
    if (file.size > 1048576) throw new Error("O CSV deve ter até 1 MB.");
    if (!/\.csv$/i.test(file.name))
      throw new Error("Selecione um arquivo com extensão .csv.");
    const csv = new TextDecoder("utf-8", { fatal: true }).decode(
      await file.arrayBuffer(),
    );
    if (generation !== entryImport.generation) return;
    if (!csv.trim())
      throw new Error(
        "O arquivo está vazio. Use um CSV com cabeçalho e lançamentos.",
      );
    entryImport.csv = csv;
  } catch (err) {
    if (generation !== entryImport.generation) return;
    entryImport.error =
      err instanceof TypeError
        ? "Salve o arquivo como CSV UTF-8 antes de importar."
        : err.message;
  } finally {
    if (generation === entryImport.generation) {
      entryImport.busy = false;
      mountImports();
    }
  }
}
async function importAction(btn) {
  const action = btn.dataset.action;
  if (!action.startsWith("import-")) return false;
  if (!canWrite() || entryImport.busy) return true;
  if (action === "import-new") {
    resetEntryImport();
    entryImport.accountId = state.accounts[0]?.id || "";
    mountImports();
  }
  if (action === "import-filter") {
    entryImport.filter = btn.dataset.filter;
    document
      .querySelectorAll("[data-import-state]")
      .forEach(
        (el) =>
          (el.hidden =
            entryImport.filter !== "ALL" &&
            el.dataset.importState !== entryImport.filter),
      );
    document
      .querySelectorAll('[data-action="import-filter"]')
      .forEach((el) => el.setAttribute("aria-pressed", el === btn));
    document.querySelector(".import-no-results").hidden =
      !!document.querySelector("[data-import-state]:not([hidden])");
  }
  if (["import-select", "import-clear"].includes(action)) {
    entryImport.selected = new Set(
      action === "import-clear"
        ? []
        : entryImport.preview.rows
            .filter((r) => r.state === "READY")
            .map((r) => r.number),
    );
    document
      .querySelectorAll("[data-import-row]:not(:disabled)")
      .forEach(
        (el) =>
          (el.checked = entryImport.selected.has(Number(el.dataset.importRow))),
      );
    updateImportSelection();
  }
  if (action === "import-preview") {
    if (!entryImport.csv) {
      entryImport.error =
        "Selecione um arquivo CSV válido antes de gerar a prévia.";
      mountImports();
      document.querySelector("#import-error")?.focus();
      return true;
    }
    const generation = ++entryImport.generation;
    entryImport.busy = true;
    entryImport.error = "";
    entryImport.preview = null;
    entryImport.filter = "ALL";
    mountImports();
    try {
      const preview = await send("/finance/imports/preview", {
        accountId: entryImport.accountId,
        csv: entryImport.csv,
      });
      if (generation !== entryImport.generation) return true;
      entryImport.preview = preview;
      entryImport.selected = new Set(
        preview.rows.filter((r) => r.state === "READY").map((r) => r.number),
      );
    } catch (err) {
      if (generation === entryImport.generation)
        entryImport.error = err.message;
    } finally {
      if (generation === entryImport.generation) {
        entryImport.busy = false;
        mountImports();
        document
          .querySelector(
            entryImport.error ? "#import-error" : "#import-review-title",
          )
          ?.focus();
      }
    }
  }
  if (action === "import-confirm") {
    const rows = selectedImportRows();
    if (!rows.length) return true;
    const snapshot = {
      accountId: entryImport.accountId,
      csv: entryImport.csv,
      selectedRows: rows.map((r) => r.number),
    };
    const generation = entryImport.generation;
    showDialog(
      "Confirmar importação",
      "Apenas os registros selecionados serão adicionados.",
      `<p class="notice">Você selecionou <strong>${rows.length} lançamento(s)</strong> para <strong>${e(entryImport.preview.accountName)}</strong>. Registros realizados alteram o saldo; pendentes entram nas previsões. A validação final ignora possíveis duplicidades encontradas agora.</p><p>Os registros criados aparecerão no histórico de alterações. Nada será substituído.</p>`,
      async () => {
        if (generation !== entryImport.generation)
          throw new Error("O arquivo foi alterado. Gere outra prévia.");
        entryImport.busy = true;
        try {
          const result = await send("/finance/imports/confirm", snapshot);
          if (generation === entryImport.generation) {
            entryImport.result = result;
            entryImport.preview = null;
            entryImport.csv = "";
            entryImport.selected.clear();
          }
        } finally {
          if (generation === entryImport.generation) entryImport.busy = false;
        }
      },
      "Confirmar importação",
    );
  }
  return true;
}
