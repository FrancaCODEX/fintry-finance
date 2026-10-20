"use strict";
// Native scrolling and dialogs keep mouse, touch and keyboard behavior consistent.
const navigation = { collapsed: false, query: "", page: null, scrollFrame: 0 };
try {
  navigation.collapsed =
    localStorage.getItem("fintry-navigation-collapsed") === "true";
} catch {}
function navigationGroups() {
  return [
    [
      "Financeiro",
      [
        ["dashboard", "Visão geral", "dashboard"],
        ["entries", "Lançamentos", "entries"],
        ["pending", "Pendências", "calendar"],
        ["accounts", "Contas", "wallet"],
        ["budgets", "Orçamentos", "budget"],
        ["reports", "Relatórios", "chart"],
        ["forecast", "Fluxo projetado", "trend"],
        ["series", "Parcelas e recorrências", "repeat"],
        ...(canWrite() ? [["imports", "Importar CSV", "entries"]] : []),
      ],
    ],
    [
      "Comercial",
      [
        ["products", "Produtos", "box"],
        ["stock", "Estoque", "stock"],
        ["sales", "Vendas", "cart"],
      ],
    ],
    [
      "Cadastros",
      [
        ["contacts", "Clientes e fornecedores", "company"],
        ["categories", "Categorias", "tag"],
      ],
    ],
    [
      "Seu espaço",
      [
        ...(isAdmin()
          ? [
              ["team", "Equipe", "users"],
              ["audit", "Histórico", "history"],
            ]
          : []),
        ["security", "Minha segurança", "shield"],
        ["examples", "Dados de exemplo", "sparkles"],
      ],
    ],
  ];
}
function navigationMenu(prefix) {
  return `<nav class="navigation-list" id="${prefix}-navigation-list" aria-label="${prefix === "desktop" ? "Navegação principal" : "Menu do celular"}" tabindex="0">${navigationGroups()
    .map(
      ([group, pages], index) =>
        `<section class="navigation-group" aria-labelledby="${prefix}-group-${index}"><h2 id="${prefix}-group-${index}" class="navigation-group-label">${group}</h2>${pages.map(([page, label, ic]) => `<button type="button" class="nav-btn" data-nav="${page}" title="${label}" aria-label="${label}">${icon(ic)}<span class="nav-text">${label}</span><span class="nav-active-mark" aria-hidden="true"></span></button>`).join("")}</section>`,
    )
    .join(
      "",
    )}<div class="navigation-empty" hidden>Nenhuma página encontrada.<button type="button" data-action="navigation-clear">Limpar busca</button></div></nav>`;
}
function navigationSearch(prefix) {
  return `<div class="navigation-search">${icon("search")}<input type="search" maxlength="80" placeholder="Buscar no menu" aria-label="Buscar no menu" aria-controls="${prefix}-navigation-list" autocomplete="off" value="${e(navigation.query)}"><kbd aria-hidden="true">/</kbd></div><span class="sr-only navigation-feedback" role="status" aria-live="polite"></span>`;
}
function navigationUser(u) {
  return `<div class="user-block"><div class="avatar" aria-hidden="true">${e(u.name.slice(0, 1).toUpperCase())}</div><div class="user-info"><strong>${e(u.name)}</strong><small>${e(roles[u.role])}</small></div><button type="button" class="logout" data-action="logout" title="Sair da conta" aria-label="Sair da conta">${icon("exit")}</button></div>`;
}
function renderAppShell(u) {
  return `<div class="shell ${navigation.collapsed ? "is-collapsed" : ""}"><a class="skip-link" href="#page-content">Ir para o conteúdo</a><aside class="sidebar" aria-label="Menu lateral"><div class="sidebar-brand-row"><a class="brand" href="#" data-nav="dashboard" aria-label="Fintry — visão geral"><img src="/favicon.svg" alt=""><span class="brand-word">fintry<span>.</span></span></a><button type="button" class="sidebar-toggle" data-action="navigation-collapse" aria-controls="desktop-navigation-list">${icon("panel")}</button></div><div class="sidebar-search-slot">${navigationSearch("desktop")}</div><button type="button" class="sidebar-search-toggle" data-action="navigation-search" aria-label="Buscar no menu" title="Buscar no menu">${icon("search")}</button>${navigationMenu("desktop")}<div class="side-bottom">${navigationUser(u)}</div></aside><main class="main"><header class="topbar"><button type="button" class="mobile-menu-toggle" data-action="navigation-open" aria-label="Abrir menu" aria-controls="navigation-dialog" aria-haspopup="dialog" aria-expanded="false">${icon("menu")}</button><div class="topbar-context"><span class="topbar-section"></span><strong class="topbar-page"></strong></div><div class="company-label">${icon("company")}<span>${e(u.companyName)}</span></div><div class="topbar-actions">${themeToggle()}<button type="button" class="btn text small topbar-logout" data-action="logout">Sair ${icon("exit")}</button></div></header><div class="main-content">${!canWrite() ? '<p class="readonly-banner">' + icon("shield") + " Seu acesso é de somente leitura. Você pode consultar e exportar as informações da empresa.</p>" : ""}<div id="page-content" tabindex="-1"></div><footer class="footer"><span class="app-version"></span><a href="mailto:vfranca.dev@gmail.com">Criado por Vinicius Azeredo França</a></footer></div></main><dialog id="navigation-dialog" class="mobile-navigation" aria-labelledby="navigation-title"><div class="mobile-navigation-head"><div class="brand"><img src="/favicon.svg" alt=""><span id="navigation-title">fintry.</span></div><button type="button" class="navigation-close" data-action="navigation-close" aria-label="Fechar menu">${icon("close")}</button></div><p class="mobile-navigation-company">${e(u.companyName)}</p>${navigationSearch("mobile")}${navigationMenu("mobile")}<div class="mobile-navigation-footer">${navigationUser(u)}</div></dialog><button type="button" class="back-to-top" data-action="scroll-top" aria-label="Voltar ao topo" title="Voltar ao topo" hidden>${icon("arrowUp")}</button></div>`;
}
function navigationMount() {
  const dialog = document.querySelector("#navigation-dialog");
  dialog.addEventListener("keydown", (event) => {
    if (event.key !== "Tab") return;
    const controls = Array.from(
      dialog.querySelectorAll(
        "button,a[href],input,select,textarea,[tabindex]",
      ),
    ).filter(
      (el) =>
        !el.disabled && el.tabIndex >= 0 && el.getClientRects().length > 0,
    );
    const first = controls[0],
      last = controls[controls.length - 1];
    if (!first) {
      event.preventDefault();
      dialog.focus();
      return;
    }
    if (
      event.shiftKey &&
      (document.activeElement === first ||
        !dialog.contains(document.activeElement))
    ) {
      event.preventDefault();
      last.focus();
    } else if (
      !event.shiftKey &&
      (document.activeElement === last ||
        !dialog.contains(document.activeElement))
    ) {
      event.preventDefault();
      first.focus();
    }
  });
  dialog.addEventListener("close", () => {
    document.body.classList.remove("navigation-open");
    document
      .querySelector(".mobile-menu-toggle")
      ?.setAttribute("aria-expanded", "false");
  });
  dialog.addEventListener("click", (ev) => {
    if (ev.target !== dialog) return;
    const b = dialog.getBoundingClientRect();
    if (
      ev.clientX < b.left ||
      ev.clientX > b.right ||
      ev.clientY < b.top ||
      ev.clientY > b.bottom
    )
      closeNavigation();
  });
  document.querySelectorAll(".navigation-search input").forEach((input) => {
    input.addEventListener("input", () => {
      navigation.query = input.value;
      filterNavigation();
    });
    input.addEventListener("keydown", (ev) => {
      if (ev.key === "Escape" && input.value) {
        ev.preventDefault();
        ev.stopPropagation();
        navigation.query = "";
        filterNavigation();
      }
    });
  });
  navigation.page = null;
  syncNavigation();
}
function filterNavigation() {
  const normalize = (s) =>
    s
      .normalize("NFD")
      .replace(/[\u0300-\u036f]/g, "")
      .toLowerCase();
  const query = normalize(navigation.query.trim());
  document.querySelectorAll(".navigation-search input").forEach((el) => {
    if (el.value !== navigation.query) el.value = navigation.query;
  });
  document.querySelectorAll(".navigation-list").forEach((nav) => {
    let count = 0;
    for (const group of nav.querySelectorAll(".navigation-group")) {
      let visible = 0;
      for (const button of group.querySelectorAll("[data-nav]")) {
        button.hidden = !normalize(button.getAttribute("aria-label")).includes(
          query,
        );
        if (!button.hidden) visible++;
      }
      group.hidden = visible === 0;
      count += visible;
    }
    nav.querySelector(".navigation-empty").hidden = count !== 0;
    const feedback = nav.parentElement.querySelector(".navigation-feedback");
    if (feedback)
      feedback.textContent = query ? `${count} página(s) encontrada(s).` : "";
  });
}
function syncNavigation() {
  const shell = document.querySelector(".shell");
  if (!shell) return;
  shell.classList.toggle("is-collapsed", navigation.collapsed);
  const toggle = shell.querySelector(".sidebar-toggle");
  toggle.setAttribute(
    "aria-label",
    navigation.collapsed ? "Expandir menu" : "Recolher menu",
  );
  toggle.title = toggle.getAttribute("aria-label");
  toggle.setAttribute("aria-expanded", String(!navigation.collapsed));
  let current;
  for (const [group, pages] of navigationGroups())
    for (const [page, label] of pages)
      if (page === state.page) current = { group, label };
  shell.querySelector(".topbar-section").textContent =
    current?.group || "Fintry";
  shell.querySelector(".topbar-page").textContent =
    current?.label || "Visão geral";
  shell.querySelector(".app-version").textContent =
    `Fintry v${state.version} · © ${new Date().getFullYear()}`;
  document.title = `${current?.label || "Visão geral"} · Fintry`;
  for (const button of shell.querySelectorAll(".navigation-list [data-nav]")) {
    const active = button.dataset.nav === state.page;
    button.classList.toggle("active", active);
    if (active) button.setAttribute("aria-current", "page");
    else button.removeAttribute("aria-current");
  }
  filterNavigation();
  if (navigation.page !== state.page) {
    navigation.page = state.page;
    requestAnimationFrame(() => {
      const nav = shell.querySelector(".sidebar .navigation-list"),
        active = nav?.querySelector("[aria-current=page]");
      if (
        !nav ||
        !active ||
        active.hidden ||
        !matchMedia("(min-width:901px)").matches
      )
        return;
      const r = active.getBoundingClientRect(),
        b = nav.getBoundingClientRect();
      if (r.bottom > b.bottom) nav.scrollTop += r.bottom - b.bottom + 12;
      else if (r.top < b.top) nav.scrollTop -= b.top - r.top + 12;
    });
  }
  syncBackToTop();
}
function closeNavigation() {
  const dialog = document.querySelector("#navigation-dialog");
  if (dialog?.open) dialog.close();
  document.body.classList.remove("navigation-open");
}
function resetNavigation() {
  closeNavigation();
  navigation.query = "";
  navigation.page = null;
  document.title = "Fintry · Gestão financeira";
}
function navigationAction(button) {
  const action = button.dataset.action;
  if (
    ![
      "navigation-collapse",
      "navigation-search",
      "navigation-open",
      "navigation-close",
      "navigation-clear",
      "scroll-top",
    ].includes(action)
  )
    return false;
  if (action === "navigation-collapse") {
    navigation.collapsed = !navigation.collapsed;
    try {
      localStorage.setItem(
        "fintry-navigation-collapsed",
        String(navigation.collapsed),
      );
    } catch {}
    syncNavigation();
  }
  if (action === "navigation-search") {
    navigation.collapsed = false;
    try {
      localStorage.setItem("fintry-navigation-collapsed", "false");
    } catch {}
    syncNavigation();
    document.querySelector(".sidebar .navigation-search input")?.focus();
  }
  if (action === "navigation-open") {
    const dialog = document.querySelector("#navigation-dialog");
    if (dialog && !dialog.open) {
      document.body.classList.add("navigation-open");
      button.setAttribute("aria-expanded", "true");
      dialog.showModal();
      dialog.querySelector("[aria-current=page]")?.focus();
    }
  }
  if (action === "navigation-close") closeNavigation();
  if (action === "navigation-clear") {
    navigation.query = "";
    filterNavigation();
    button
      .closest("aside,dialog")
      ?.querySelector(".navigation-search input")
      ?.focus();
  }
  if (action === "scroll-top") {
    const target =
      document.querySelector("#page-content h1") ||
      document.querySelector("#page-content");
    if (target) {
      target.tabIndex = -1;
      target.focus({ preventScroll: true });
    }
    window.scrollTo({
      top: 0,
      behavior: matchMedia("(prefers-reduced-motion:reduce)").matches
        ? "instant"
        : "smooth",
    });
  }
  return true;
}
function syncBackToTop() {
  const button = document.querySelector(".back-to-top");
  if (button) button.hidden = window.scrollY < 420;
}
window.addEventListener(
  "scroll",
  () => {
    if (navigation.scrollFrame) return;
    navigation.scrollFrame = requestAnimationFrame(() => {
      navigation.scrollFrame = 0;
      syncBackToTop();
    });
  },
  { passive: true },
);
window.addEventListener("resize", () => {
  if (matchMedia("(min-width:901px)").matches) {
    const wasOpen = document.querySelector("#navigation-dialog")?.open;
    closeNavigation();
    if (wasOpen)
      document
        .querySelector(".sidebar [aria-current=page]")
        ?.focus({ preventScroll: true });
  }
  syncNavigation();
});
document.addEventListener("keydown", (ev) => {
  if (
    ev.key !== "/" ||
    ev.ctrlKey ||
    ev.altKey ||
    ev.metaKey ||
    ev.target.closest?.("input,select,textarea,[contenteditable=true]") ||
    document.querySelector("#modal[open],#details-drawer[open]") ||
    !document.querySelector(".shell")
  )
    return;
  ev.preventDefault();
  if (matchMedia("(max-width:900px)").matches)
    navigationAction(document.querySelector(".mobile-menu-toggle"));
  else if (navigation.collapsed)
    navigationAction(document.querySelector(".sidebar-search-toggle"));
  const input = document.querySelector(
    matchMedia("(max-width:900px)").matches
      ? "#navigation-dialog .navigation-search input"
      : ".sidebar .navigation-search input",
  );
  input?.focus();
});
