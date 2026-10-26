"use strict";
let uxFieldSequence = 0;
function uxSummary(form) {
  let box = form.querySelector(".form-error");
  if (!box) {
    box = document.createElement("div");
    box.className = "form-error";
    box.setAttribute("role", "alert");
    form.append(box);
  }
  return box;
}
function uxDecorate() {
  for (const form of document.forms) {
    form.noValidate = true;
    for (const control of form.elements) {
      if (
        !control.matches?.("input,select,textarea") ||
        control.dataset.uxReady
      )
        continue;
      control.dataset.uxReady = "true";
      if (!control.hasAttribute("aria-label") && control.labels?.length) {
        const label = control.labels[0].cloneNode(true);
        label
          .querySelectorAll(
            "input,select,textarea,button,small,.field-error,.password-control",
          )
          .forEach((el) => el.remove());
        const text = label.textContent.trim();
        if (text) control.setAttribute("aria-label", text);
      }
    }
  }
}
function uxClearControl(control) {
  control.removeAttribute("aria-invalid");
  const id = control.dataset.uxError;
  if (id) {
    const el = document.getElementById(id);
    if (el) {
      el.textContent = "";
      el.hidden = true;
    }
    const refs = (control.getAttribute("aria-describedby") || "")
      .split(/\s+/)
      .filter((x) => x && x !== id);
    if (refs.length) control.setAttribute("aria-describedby", refs.join(" "));
    else control.removeAttribute("aria-describedby");
  }
}
function uxControlError(control, message) {
  if (!control || control.type === "hidden" || control.disabled) return false;
  let id = control.dataset.uxError,
    span = id ? document.getElementById(id) : null;
  if (!span) {
    id = "field-error-" + ++uxFieldSequence;
    span = document.createElement("small");
    span.id = id;
    span.className = "field-error";
    control.dataset.uxError = id;
    const field = control.closest(".field");
    if (field) field.append(span);
    else control.after(span);
  }
  span.textContent = message;
  span.hidden = false;
  control.setAttribute("aria-invalid", "true");
  const refs = new Set(
    (control.getAttribute("aria-describedby") || "")
      .split(/\s+/)
      .filter(Boolean),
  );
  refs.add(id);
  control.setAttribute("aria-describedby", Array.from(refs).join(" "));
  return true;
}
function uxErrors(form, message, fields = {}) {
  for (const control of form.elements)
    if (control.matches?.("input,select,textarea")) uxClearControl(control);
  const missing = [];
  let first;
  for (const [name, text] of Object.entries(fields)) {
    const control = Array.from(form.elements).find(
      (el) => el.name === name && !el.disabled && el.type !== "hidden",
    );
    if (control && uxControlError(control, text)) {
      first ||= control;
    } else missing.push(text);
  }
  uxSummary(form).textContent = missing.length
    ? [message, ...missing].filter(Boolean).join(" ")
    : message;
  if (first) first.focus();
}
function uxFieldError(name, message) {
  const err = new Error(message);
  err.fields = { [name]: message };
  return err;
}
function uxNativeMessage(control) {
  const v = control.validity;
  if (v.valueMissing)
    return control.matches("select")
      ? "Selecione uma opção."
      : control.type === "checkbox"
        ? "Marque esta opção."
        : "Preencha este campo.";
  if (v.typeMismatch && control.type === "email")
    return "Informe um e-mail válido.";
  if (v.tooShort) return `Use pelo menos ${control.minLength} caracteres.`;
  if (v.tooLong) return `Use no máximo ${control.maxLength} caracteres.`;
  if (v.rangeUnderflow)
    return control.type === "date"
      ? `Escolha uma data a partir de ${dateLabel(control.min)}.`
      : `Use um valor igual ou maior que ${control.min.replace(".", ",")}.`;
  if (v.rangeOverflow)
    return control.type === "date"
      ? `Escolha uma data até ${dateLabel(control.max)}.`
      : `Use um valor igual ou menor que ${control.max.replace(".", ",")}.`;
  if (v.stepMismatch)
    return "Confira as casas decimais e o intervalo permitido.";
  if (v.patternMismatch) return control.title || "Confira o formato informado.";
  if (v.badInput) return "Informe um valor válido.";
  return control.validationMessage || "Confira este campo.";
}
document.addEventListener(
  "invalid",
  (ev) => {
    const control = ev.target;
    if (!control.form) return;
    ev.preventDefault();
    uxControlError(control, uxNativeMessage(control));
    uxSummary(control.form).textContent = "Confira os campos destacados.";
  },
  true,
);
document.addEventListener(
  "submit",
  (ev) => {
    const form = ev.target;
    if (!(form instanceof HTMLFormElement)) return;
    uxDecorate();
    if (!form.checkValidity()) {
      ev.preventDefault();
      ev.stopImmediatePropagation();
      Array.from(form.elements)
        .find((el) => el.willValidate && !el.validity.valid)
        ?.focus();
    }
  },
  true,
);
document.addEventListener("input", (ev) => {
  const control = ev.target;
  if (control.form && control.matches("input,textarea,select")) {
    uxClearControl(control);
    if (!control.form.querySelector("[aria-invalid=true]")) {
      const summary = control.form.querySelector(".form-error");
      if (summary) summary.textContent = "";
    }
  }
});
document.addEventListener("change", (ev) => {
  if (ev.target.matches("select"))
    ev.target.dispatchEvent(new Event("input", { bubbles: true }));
});
new MutationObserver(uxDecorate).observe(document.body, {
  childList: true,
  subtree: true,
});
uxDecorate();
const uxPageNames = {
  dashboard: "Visão geral",
  entries: "Lançamentos",
  pending: "Central de pendências",
  budgets: "Orçamentos",
  imports: "Importar CSV",
  operations: "Ambiente",
  reconciliation: "Conciliação bancária",
  billing: "Cobranças",
  subscription: "Plano e assinatura",
  examples: "Dados de exemplo",
  accounts: "Contas",
  contacts: "Clientes e fornecedores",
  categories: "Categorias",
  series: "Parcelas e recorrências",
  reports: "Relatórios",
  forecast: "Fluxo projetado",
  team: "Equipe",
  audit: "Histórico",
  security: "Minha segurança",
  company: "Empresa",
  products: "Produtos",
  services: "Serviços",
  proposals: "Propostas",
  stock: "Estoque",
  sales: "Vendas",
};
function uxLoading(page) {
  return `<section class="page-loading" role="status" aria-label="Carregando ${uxPageNames[page] || "informações"}"><p class="loading-caption">Carregando ${uxPageNames[page] || "informações"}…</p><div aria-hidden="true"><div class="skeleton skeleton-title"></div><div class="skeleton skeleton-subtitle"></div><div class="skeleton-metrics">${Array.from({ length: 3 }, () => '<div class="skeleton skeleton-metric"></div>').join("")}</div><div class="card skeleton-list">${Array.from({ length: 5 }, () => '<div class="skeleton skeleton-row"></div>').join("")}</div></div></section>`;
}
function uxLoadError(message) {
  return `<section class="load-error card" role="alert"><div class="empty-icon">${icon("entries")}</div><h2 tabindex="-1">Não foi possível carregar esta página</h2><p>${e(message)}</p><button class="btn" data-action="reload">Tentar novamente</button></section>`;
}
