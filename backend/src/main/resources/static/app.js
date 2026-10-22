"use strict";
const root = document.querySelector("#root");
const modal = document.querySelector("#modal");
const state = {
  user: null,
  recoveryAvailable: false,
  team: null,
  teamPage: 0,
  teamFilters: {},
  audit: null,
  auditPage: 0,
  auditFilters: { start: localDate().slice(0, 7) + "-01", end: localDate() },
  report: null,
  reportPage: 0,
  reportFilters: {
    start: localDate().slice(0, 7) + "-01",
    end: monthEnd(localDate().slice(0, 7)),
    basis: "PAYMENT",
    status: "PAID",
  },
  forecast: null,
  forecastFilters: { days: "90", includeOverdue: "false" },
  version: "",
  series: null,
  seriesPage: 0,
  page: "dashboard",
  month: localDate().slice(0, 7),
  accounts: [],
  entries: null,
  summary: null,
  entryPage: 0,
  filters: {},
  contacts: null,
  categories: [],
  directoryFilters: { active: "true" },
  contactPage: 0,
  csrf: null,
};
const money = (value) =>
  Number(value).toLocaleString("pt-BR", { style: "currency", currency: "BRL" });
const escapeHtml = (value) =>
  String(value ?? "").replace(
    /[&<>"']/g,
    (c) =>
      ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[
        c
      ],
  );
const e = escapeHtml;
function localDate() {
  return new Intl.DateTimeFormat("sv-SE", {
    timeZone: "America/Sao_Paulo",
  }).format(new Date());
}
const dateLabel = (date) =>
  date
    ? new Intl.DateTimeFormat("pt-BR", { timeZone: "UTC" }).format(
        new Date(date + "T12:00:00Z"),
      )
    : "—";
const icons = {
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  close: '<path d="m6 6 12 12M18 6 6 18"/>',
  panel:
    '<rect x="3" y="4" width="18" height="16" rx="3"/><path d="M9 4v16m6-11-3 3 3 3"/>',
  search: '<circle cx="10.5" cy="10.5" r="6.5"/><path d="m16 16 4 4"/>',
  arrowUp: '<path d="m6 11 6-6 6 6M12 5v15"/>',
  budget:
    '<rect x="3" y="4" width="18" height="17" rx="3"/><path d="M3 10h18M8 2v4M16 2v4m-8 9 3 3 5-5"/>',
  chart: '<path d="M4 3v17h17M8 15v-4M13 15V7M18 15v-6"/>',
  repeat: '<path d="M4 8h15l-3-3m4 11H5l3 3M20 8v4M4 16v-4"/>',
  stock: '<path d="M3 21V8l9-5 9 5v13M7 21V11h10v10M7 15h10M7 18h10"/>',
  tag: '<path d="M20 13 11 22 2 13V3h10l8 8z"/><circle cx="7" cy="8" r="1"/>',
  users:
    '<circle cx="9" cy="8" r="3"/><path d="M3 21v-3a6 6 0 0 1 12 0v3M16 5a3 3 0 0 1 0 6m2 3a5 5 0 0 1 3 5v2"/>',
  history: '<path d="M3 5v5h5M3.5 10A9 9 0 1 1 4 17m8-10v5l3 2"/>',
  sparkles:
    '<path d="m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5zM20 3v4m-2-2h4"/>',
  calendar:
    '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18M8 15h2M14 15h2"/>',
  box: '<path d="m12 3 9 5v8l-9 5-9-5V8zM3 8l9 5 9-5M12 13v8M7 5.8l9 5"/>',
  cart: '<path d="M3 3h2l3 12h11l3-8H6M9 19h.01M18 19h.01"/><circle cx="9" cy="20" r="1"/><circle cx="18" cy="20" r="1"/>',
  shield:
    '<path d="M12 3 3 7v6c0 5 9 9 9 9s9-4 9-9V7z"/><path d="m8 12 3 3 5-6"/>',
  dashboard:
    '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  entries: '<path d="M8 3h11v18H5V6z"/><path d="M9 8h6M9 12h6M9 16h4"/>',
  wallet:
    '<path d="M4 6h14a2 2 0 0 1 2 2v12H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h13v2"/><path d="M20 11h-6v5h6"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  income: '<path d="M6 17L18 5M6 5h12v12"/>',
  expense: '<path d="M6 5l12 12M6 17h12V5"/>',
  trend: '<path d="m3 17 6-6 4 4 8-10M15 5h6v6"/>',
  exit: '<path d="M9 4H4v16h5M13 7l5 5-5 5M8 12h13"/>',
  company: '<path d="M4 21V3h11v18M15 10h5v11M8 7h3M8 11h3M8 15h3M2 21h20"/>',
  transfer: '<path d="M4 7h16m-4-4 4 4-4 4M20 17H4m4-4-4 4 4 4"/>',
};
const icon = (name) =>
  `<svg class="icon" viewBox="0 0 24 24" aria-hidden="true">${icons[name] || icons.wallet}</svg>`;
let toastTimer;
function toast(message) {
  const el = document.querySelector("#toast");
  el.textContent = message;
  el.classList.add("visible");
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove("visible"), 4500);
}
async function csrf() {
  const res = await fetch("/api/v1/auth/csrf");
  if (!res.ok) throw new Error("Não foi possível iniciar a sessão.");
  state.csrf = await res.json();
}
async function api(path, options = {}) {
  const method = options.method || "GET";
  if (method !== "GET" && !state.csrf) await csrf();
  const headers = { ...options.headers };
  if (options.body && !(options.body instanceof FormData))
    headers["Content-Type"] = "application/json";
  if (method !== "GET") headers[state.csrf.headerName] = state.csrf.token;
  let res;
  try {
    res = await fetch("/api/v1" + path, { ...options, headers });
  } catch {
    throw new Error("Sem conexão com o servidor. Tente novamente.");
  }
  let data = null;
  if (res.status !== 204) {
    const text = await res.text();
    if (text) {
      try {
        data = JSON.parse(text);
      } catch {
        data = {};
      }
    }
  }
  if (!res.ok) {
    if (res.status === 401 && state.user) {
      state.user = null;
      modal.close();
      closeDetails();
      state.csrf = null;
      renderAuth(
        "login",
        "Sua sessão foi encerrada. Entre novamente para continuar.",
      );
    }
    const error = new Error(
      data?.message ||
        (data?.errors
          ? Object.values(data.errors).join(" ")
          : "Não foi possível concluir a operação."),
    );
    error.fields = data?.errors || {};
    error.status = res.status;
    throw error;
  }
  return data;
}
const send = (path, body) =>
  api(path, { method: "POST", body: body ? JSON.stringify(body) : undefined });
function field(label, name, type = "text", extra = "", value = "") {
  return `<label class="field">${label}<input name="${name}" type="${type}" value="${e(value)}" ${extra}></label>`;
}
function formError(form, message, fields = {}) {
  uxErrors(form, message, fields);
}
async function submitForm(event, action) {
  event.preventDefault();
  const form = event.currentTarget;
  const button = form.querySelector("[type=submit]");
  const label = button.innerHTML;
  button.disabled = true;
  button.setAttribute("aria-busy", "true");
  button.textContent = "Processando…";
  formError(form, "");
  try {
    await action(Object.fromEntries(new FormData(form)), form);
  } catch (err) {
    if (form.isConnected)
      formError(
        form,
        err.fields && Object.keys(err.fields).length
          ? "Confira os campos destacados."
          : err.message,
        err.fields,
      );
    else toast(err.message);
  } finally {
    button.disabled = false;
    button.removeAttribute("aria-busy");
    button.innerHTML = label;
  }
}
const roles = {
  ADMIN: "Administrador",
  FINANCE: "Financeiro",
  VIEWER: "Somente leitura",
};
const canWrite = () =>
  state.user?.permissions?.includes("WRITE_FINANCE") &&
  state.subscriptionAccess?.writable !== false;
const isAdmin = () => state.user?.permissions?.includes("MANAGE_USERS");
const timestamp = (value) =>
  value
    ? new Intl.DateTimeFormat("pt-BR", {
        dateStyle: "short",
        timeStyle: "short",
        timeZone: "America/Sao_Paulo",
      }).format(new Date(value))
    : "—";
function passwordField(label, name, fresh = true) {
  return `<div class="field"><label for="password-${name}">${label}</label><span class="password-control"><input id="password-${name}" name="${name}" type="password" required ${fresh ? 'minlength="10"' : ""} maxlength="64" autocomplete="${fresh ? "new-password" : "current-password"}"><button type="button" class="password-toggle" data-password="${name}" aria-label="Mostrar ${label.toLowerCase()}" aria-pressed="false">Mostrar</button></span></div>`;
}
let accessLink = null;
function readAccessLink() {
  const params = new URLSearchParams(location.hash.slice(1));
  const kind = params.has("invite")
    ? "invite"
    : params.has("reset")
      ? "reset"
      : null;
  if (!kind) return false;
  accessLink = { kind, token: params.get(kind) };
  history.replaceState(null, "", location.pathname + location.search);
  closeDetails();
  modal.close();
  renderAuth(kind);
  return true;
}
window.addEventListener("hashchange", readAccessLink);
function renderAuth(mode = "login", notice = "") {
  if (!state.user) {
    resetCommerce();
    resetCommercial();
    resetPending();
    resetPlanning();
    resetEntryImport();
    resetReconciliation();
    resetBilling();
    resetSubscription();
    resetNavigation();
    state.dashboardPending = null;
  }
  if (typeof mode === "boolean") mode = mode ? "register" : "login";
  const register = mode === "register",
    invite = mode === "invite",
    reset = mode === "reset",
    forgot = mode === "forgot";
  const titles = {
    login: "Bom ter você por aqui.",
    register: "Comece pela sua empresa",
    forgot: "Vamos recuperar seu acesso.",
    reset: "Uma nova senha, um novo começo.",
    invite: "Seu lugar na equipe está pronto.",
  };
  const descriptions = {
    login: "Entre para acompanhar os números do seu negócio.",
    register: "Crie seu acesso e organize suas finanças.",
    forgot: "Informe o e-mail da sua conta. O link vale por 15 minutos.",
    reset: "Escolha uma senha de 10 a 64 caracteres.",
    invite: "Confira o convite e defina sua senha para começar.",
  };
  root.innerHTML = `<main class="auth"><section class="auth-story"><div class="brand"><img src="/favicon.svg" alt="">fintry<span>.</span></div><div><div class="eyebrow">Control today. Grow tomorrow.</div><h1>Seu financeiro.<br>Mais simples.<br><span>Mais claro.</span></h1><p>Organize o presente e enxergue os próximos passos da sua empresa. Tudo em um só lugar.</p><div class="preview-card"><span class="eyebrow">Clareza para crescer</span><strong>Decisões com direção.</strong><div class="mini-bars" aria-hidden="true"><i></i><i></i><i></i><i></i><i></i></div></div></div><footer>Feito para quem cuida de cada detalhe do negócio.</footer></section><section class="auth-panel"><div class="auth-form"><div class="auth-tools">${themeToggle()}</div><span class="eyebrow">${invite ? "CONVITE PARA A EQUIPE" : reset || forgot ? "SEGURANÇA DO ACESSO" : "BEM-VINDO AO FINTRY"}</span><h2>${titles[mode]}</h2><p class="muted">${descriptions[mode]}</p>${notice ? `<p class="notice success" role="status">${e(notice)}</p>` : ""}${invite ? '<div id="invite-info" class="notice" role="status">Conferindo convite…</div>' : ""}${register ? '<p class="auth-start-note">Depois do cadastro, conheça os planos e escolha como começar. Criar sua conta não gera cobrança.</p>' : ""}<form id="auth-form">${register ? field("Seu nome", "name", "text", 'required maxlength="120" autocomplete="name"') + field("Nome da empresa", "companyName", "text", 'required maxlength="255" autocomplete="organization"') + field("CNPJ (somente números)", "document", "text", 'required pattern="[0-9]{14}" maxlength="14" inputmode="numeric"') : ""}${invite ? field("Seu nome", "name", "text", 'required maxlength="120" autocomplete="name"') : ""}${!reset && !invite ? field("E-mail", "email", "email", 'required maxlength="255" autocomplete="username" placeholder="voce@empresa.com.br"') : ""}${!forgot ? passwordField(reset || invite ? "Nova senha" : "Senha", "password", mode !== "login") : ""}${register || reset || invite ? passwordField("Confirmar senha", "confirmation") + '<p class="field-help">Use uma senha exclusiva, com 10 a 64 caracteres.</p>' : ""}<div class="form-error" role="alert"></div><button class="btn submit" type="submit" ${invite ? "disabled" : ""}>${register ? "Criar minha conta" : forgot ? "Enviar link de recuperação" : reset ? "Salvar nova senha" : invite ? "Aceitar convite" : "Entrar no Fintry"} ${icon("income")}</button></form>${mode === "login" ? '<button class="auth-link" data-auth="forgot">Esqueci minha senha</button>' : ""}<div class="auth-switch">${mode === "login" ? 'Primeira vez por aqui? <button data-auth="register">Criar conta</button>' : '<button data-auth="login">Voltar para entrar</button>'}</div><p class="auth-note">Fintry v${e(state.version)} · Gestão financeira para pequenas empresas<br>Desenvolvido por Vinicius Azeredo França</p></div></section></main>`;
  const form = document.querySelector("#auth-form");
  if (forgot && !state.recoveryAvailable) {
    form.hidden = true;
    const help = document.createElement("p");
    help.className = "notice";
    help.textContent =
      "O envio de e-mails ainda não está configurado. Peça ao administrador da instalação para configurar o SMTP e habilitar a recuperação.";
    form.before(help);
  }
  if (invite) {
    const token = accessLink?.token;
    send("/auth/invitation", { token })
      .then((info) => {
        if (!form.isConnected) return;
        document.querySelector("#invite-info").innerHTML =
          `<strong>${e(info.companyName)}</strong><br>${e(info.email)} · ${e(roles[info.role])}<br><small>Válido até ${timestamp(info.expiresAt)}</small>`;
        form.elements.name.value = info.name;
        form.querySelector("[type=submit]").disabled = false;
      })
      .catch((err) => {
        if (!form.isConnected) return;
        document.querySelector("#invite-info").textContent =
          err.message + " Peça um novo convite ao administrador.";
        form.hidden = true;
      });
  }
  form.onsubmit = (ev) =>
    submitForm(ev, async (data) => {
      if (data.confirmation != null && data.password !== data.confirmation)
        throw uxFieldError("confirmation", "As senhas precisam ser iguais.");
      if (forgot) {
        const result = await send("/auth/forgot-password", {
          email: data.email,
        });
        form.hidden = true;
        const note = document.createElement("p");
        note.className = "notice success";
        note.setAttribute("role", "status");
        note.textContent = result.message + " Confira também a pasta de spam.";
        form.before(note);
        return;
      }
      if (reset || invite) {
        if (!accessLink?.token)
          throw new Error("Link inválido. Solicite outro link de acesso.");
        await send(invite ? "/auth/accept-invite" : "/auth/reset-password", {
          token: accessLink.token,
          password: data.password,
          ...(invite ? { name: data.name } : {}),
        });
        accessLink = null;
        state.user = null;
        state.csrf = null;
        await csrf();
        renderAuth(
          "login",
          invite
            ? "Convite aceito. Entre com seu e-mail e a senha que acabou de criar."
            : "Senha alterada. Entre novamente em seus dispositivos.",
        );
        return;
      }
      if (register) await send("/auth/register", data);
      resetCommerce();
      state.user = await send("/auth/login", {
        email: data.email,
        password: data.password,
      });
      await csrf();
      subscription.welcome = register;
      state.page = register ? "subscription" : "dashboard";
      state.entryPage =
        state.contactPage =
        state.seriesPage =
        state.reportPage =
        state.teamPage =
        state.auditPage =
          0;
      state.filters = {};
      state.directoryFilters = { active: "true" };
      state.month = localDate().slice(0, 7);
      await load();
    });
}
function applyPermissions(scope = document) {
  if (canWrite()) return;
  const mutations = [
    "entry",
    "account",
    "transfer",
    "sample",
    "pay",
    "cancel",
    "edit-entry",
    "stop-series",
    "contact",
    "category",
    "edit-contact",
    "edit-category",
    "toggle-contact",
    "toggle-category",
    "attachment-remove",
    "attachment-confirm",
  ];
  scope.querySelectorAll("[data-action]").forEach((el) => {
    if (
      mutations.includes(el.dataset.action) &&
      !(
        state.user?.permissions?.includes("WRITE_FINANCE") &&
        ["attachment-remove", "attachment-confirm"].includes(el.dataset.action)
      )
    )
      el.remove();
  });
  scope.querySelector("#attachment-form")?.remove();
  scope
    .querySelectorAll(".empty .actions")
    .forEach(
      (el) =>
        (el.innerHTML =
          state.subscriptionAccess?.writable === false
            ? '<span class="muted">Consulte Plano e assinatura para voltar a adicionar registros.</span>'
            : '<span class="muted">Um membro do Financeiro ou Administrador pode adicionar registros.</span>'),
    );
}
function pager(prefix, result, page) {
  return `<div class="pagination"><span>${result.totalElements} registro(s)</span><div class="row-actions"><button class="btn secondary small" data-action="${prefix}-prev" ${page === 0 ? "disabled" : ""}>Anterior</button><span>${page + 1} / ${Math.max(1, result.totalPages)}</span><button class="btn secondary small" data-action="${prefix}-next" ${page + 1 >= result.totalPages ? "disabled" : ""}>Próxima</button></div></div>`;
}
function roleOptions(selected = "FINANCE", all = false) {
  return `${all ? '<option value="">Todos os perfis</option>' : ""}${Object.entries(
    roles,
  )
    .map(
      ([key, label]) =>
        `<option value="${key}" ${selected === key ? "selected" : ""}>${label}</option>`,
    )
    .join("")}`;
}
function renderTeam() {
  const t = state.team;
  return (
    head(
      "Pessoas que fazem parte.",
      "Acesso à sua empresa, com a permissão certa para cada pessoa.",
      '<button class="btn" data-action="invite-user">' +
        icon("plus") +
        "Convidar pessoa</button>",
    ) +
    `<section class="access-summary"><article class="card"><span class="eyebrow">ACESSOS ATIVOS</span><strong>${t.active}</strong><p>Prontos para trabalhar</p></article><article class="card"><span class="eyebrow">AGUARDANDO ATIVAÇÃO</span><strong>${t.pending}</strong><p>Confira a validade dos convites</p></article><article class="card role-guide"><h2>Três perfis, um só negócio.</h2><p><strong>Administrador</strong> cuida de tudo, incluindo a equipe.<br><strong>Financeiro</strong> gerencia movimentos e comprovantes.<br><strong>Somente leitura</strong> consulta e exporta informações.</p></article></section><section class="card"><form id="team-filter" class="filters">${field("Buscar pessoa", "search", "search", 'maxlength="120" placeholder="Nome ou e-mail"', state.teamFilters.search || "")}<label class="field">Perfil<select name="role">${roleOptions(state.teamFilters.role || "", true)}</select></label><button class="btn secondary" type="submit">Filtrar equipe</button></form><div class="table-wrap"><table><thead><tr><th>PESSOA</th><th>PERFIL</th><th>ACESSO</th><th>AÇÕES</th></tr></thead><tbody>${t.content.map((u) => `<tr><td class="description"><strong>${e(u.name)} ${u.id === state.user.id ? '<small class="self-mark">Você</small>' : ""}</strong><small>${e(u.email)}</small></td><td><span class="role-pill">${e(roles[u.role])}</span></td><td><span class="badge ${u.invited ? "pending" : u.active ? "" : "cancelled"}">${u.invited ? "Aguardando ativação" : u.active ? "Ativo" : "Inativo"}</span>${u.invited ? `<br><small class="muted">${u.invitationExpiresAt ? (new Date(u.invitationExpiresAt) > new Date() ? "Válido até " : "Expirou em ") + timestamp(u.invitationExpiresAt) : "Convite revogado"}</small>` : ""}</td><td><div class="row-actions"><button class="btn secondary small" data-action="edit-user" data-id="${u.id}">Editar</button>${!u.active ? `<button class="btn soft small" data-action="renew-invite" data-id="${u.id}">Gerar novo convite</button>` : ""}${u.invited && u.invitationExpiresAt ? `<button class="btn text small" data-action="revoke-invite" data-id="${u.id}">Revogar convite</button>` : ""}</div></td></tr>`).join("") || '<tr><td colspan="4"><div class="empty"><h3>Nenhuma pessoa encontrada</h3><p>Ajuste a busca ou o perfil selecionado.</p></div></td></tr>'}</tbody></table></div>${pager("team", t, state.teamPage)}</section><p class="notice">Mudanças de perfil ou situação encerram as sessões dessa pessoa. Os registros financeiros e o histórico são preservados.</p>`
  );
}
const auditActions = {
  CREATE: "Criou",
  DELETE: "Removeu",
  SAMPLE_LOAD: "Adicionou dados de exemplo",
  UPDATE: "Alterou",
  PAY: "Registrou baixa",
  CANCEL: "Cancelou",
  TRANSFER: "Transferiu",
  SERIES_CREATE: "Criou série",
  SERIES_STOP: "Encerrou série",
  ATTACH_ADD: "Anexou comprovante",
  ATTACH_REMOVE: "Removeu comprovante",
  REGISTER: "Criou a empresa",
  LOGIN: "Entrou na conta",
  INVITE_CREATE: "Criou convite",
  INVITE_RENEW: "Renovou convite",
  INVITE_REVOKE: "Revogou convite",
  INVITE_ACCEPT: "Aceitou convite",
  PASSWORD_RESET: "Redefiniu a senha",
  PASSWORD_CHANGE: "Alterou a senha",
  RESET_REQUEST: "Solicitou recuperação",
  STOCK_MOVE: "Movimentou estoque",
  SALE_CREATE: "Criou venda",
  SALE_CONFIRM: "Confirmou venda",
  SALE_CANCEL: "Cancelou venda",
  SUBSCRIPTION: "Plano e assinatura",
  SUBSCRIPTION_SIMULATE: "Experimentou plano",
  SUBSCRIPTION_REQUEST: "Solicitou plano",
  SUBSCRIPTION_REQUEST_CANCEL: "Cancelou solicitação de plano",
  SUBSCRIPTION_RENEWAL: "Gerou mensalidade recorrente",
  SUBSCRIPTION_RENEWAL_ENABLE: "Autorizou recorrência",
  SUBSCRIPTION_RENEWAL_DISABLE: "Interrompeu emissões",
  SUBSCRIPTION_INVOICE: "Solicitou mensalidade",
  SUBSCRIPTION_PAYMENT: "Atualizou pagamento do Fintry",
  SUBSCRIPTION_ACTIVATE: "Ativou assinatura",
  SUBSCRIPTION_CANCEL: "Programou encerramento",
  SUBSCRIPTION_KEEP: "Manteve assinatura",
  BILLING_CONNECT: "Configurou conexão de cobrança",
  BILLING_ISSUE: "Emitiu cobrança",
  BILLING_STATUS: "Atualizou cobrança",
  OPERATIONS_MAIL_TEST: "Testou envio de e-mail",
  STATEMENT_IMPORT: "Importou movimento bancário",
  RECONCILE_LINK: "Conciliou lançamento",
  RECONCILE_CREATE: "Criou lançamento do extrato",
  RECONCILE_IGNORE: "Ignorou movimento bancário",
  RECONCILE_REOPEN: "Reabriu conciliação",
  PROPOSAL_CREATE: "Criou proposta",
  PROPOSAL_UPDATE: "Editou proposta",
  PROPOSAL_STATUS: "Atualizou negociação",
  PROPOSAL_APPROVE: "Aprovou proposta",
};
const resources = {
  ENTRY: "Lançamentos e comprovantes",
  ACCOUNT: "Contas",
  TRANSFER: "Transferências",
  SERIES: "Parcelas e recorrências",
  CONTACT: "Clientes e fornecedores",
  CATEGORY: "Categorias",
  COMPANY: "Empresa",
  USER: "Equipe",
  ACCESS: "Segurança do acesso",
  PRODUCT: "Produtos e serviços",
  PROPOSAL: "Propostas comerciais",
  RECONCILIATION: "Conciliação bancária",
  OPERATIONS: "Ambiente",
  BILLING: "Cobranças",
  SUBSCRIPTION: "Plano e assinatura",
  STOCK: "Estoque",
  SALE: "Vendas",
  BUDGET: "Orçamentos",
  SAMPLE: "Dados de exemplo",
};
function auditValue(v) {
  return (
    roles[v] ||
    {
      ESSENTIAL: "Essencial",
      PRO: "Profissional",
      BUSINESS: "Gestão",
      TRIAL: "Período de teste",
      LEGACY: "Acesso preservado",
      SIMULATED: "Simulação",
      ACTIVE: "Ativa",
      PAID: "Realizado",
      PENDING: "Pendente",
      LINKED: "Conciliado",
      IGNORED: "Ignorado",
      CANCELLED: "Cancelado",
      INCOME: "Receita",
      EXPENSE: "Despesa",
      BOTH: "Ambos",
      true: "Sim",
      false: "Não",
      RECURRING: "Recorrência",
      INSTALLMENT: "Parcelamento",
      DRAFT: "Rascunho",
      CONFIRMED: "Confirmada",
      INITIAL: "Estoque inicial",
      IN: "Entrada",
      OUT: "Saída",
      SALE: "Venda",
      RETURN: "Devolução",
    }[v] ||
    v ||
    "—"
  );
}
function renderAudit() {
  const a = state.audit;
  return (
    head(
      "Cada mudança tem uma história.",
      "Acompanhe quem fez o quê, quando e quais dados mudaram.",
      '<button class="btn secondary" data-action="reload">Atualizar histórico</button>',
    ) +
    `<section class="card"><form id="audit-filter" class="filters">${field("De", "start", "date", "required", state.auditFilters.start)}${field("Até", "end", "date", "required", state.auditFilters.end)}<label class="field">Área<select name="resource"><option value="">Todas as áreas</option>${Object.entries(
      resources,
    )
      .map(
        ([k, v]) =>
          `<option value="${k}" ${state.auditFilters.resource === k ? "selected" : ""}>${v}</option>`,
      )
      .join(
        "",
      )}</select></label>${field("Buscar no histórico", "search", "search", 'maxlength="120" placeholder="Pessoa ou registro"', state.auditFilters.search || "")}<button class="btn secondary" type="submit">Filtrar histórico</button></form><div class="audit-list">${a.content.map((item) => `<article class="audit-item"><div class="audit-marker" aria-hidden="true">${icon(item.resource === "ACCESS" ? "shield" : "entries")}</div><div class="audit-body"><div class="audit-title"><strong>${e(auditActions[item.action] || item.action)}</strong><time datetime="${e(item.occurredAt)}">${timestamp(item.occurredAt)}</time></div><p>${e(item.label)}</p><small>${e(item.actorName)} · ${e(resources[item.resource] || item.resource)}</small>${item.changes.length ? `<details><summary>Ver ${item.changes.length} campo(s) alterado(s)</summary><div class="audit-changes">${item.changes.map((c) => `<div><strong>${e(c.field)}</strong><span><small>Antes</small>${e(auditValue(c.before))}</span><span><small>Depois</small>${e(auditValue(c.after))}</span></div>`).join("")}</div></details>` : ""}${item.resource === "SALE" && item.resourceId ? `<button class="btn text small" data-action="sale-details" data-id="${item.resourceId}">Abrir venda →</button>` : ""}${item.resource === "ENTRY" && item.resourceId ? `<button class="btn text small" data-action="details" data-id="${item.resourceId}">Abrir lançamento →</button>` : ""}</div></article>`).join("") || '<div class="empty"><h3>Nenhuma alteração neste período</h3><p>Ajuste os filtros para consultar outros registros.</p></div>'}</div>${pager("audit", a, state.auditPage)}</section><p class="notice">Horários de Brasília. O histórico começa na versão 0.6.0. Senhas, links de acesso e conteúdo de comprovantes não são registrados. Consulte até 366 dias por vez.</p>`
  );
}
function renderSecurity() {
  return (
    head(
      "Seu acesso, bem cuidado.",
      "Mantenha uma senha exclusiva e confira suas permissões.",
      "",
    ) +
    `<section class="security-grid"><article class="card"><div class="card-head"><div><h2>Alterar minha senha</h2><p>As sessões abertas serão encerradas ao salvar.</p></div>${icon("shield")}</div><form id="security-form">${passwordField("Senha atual", "currentPassword", false)}${passwordField("Nova senha", "password")}${passwordField("Confirmar nova senha", "confirmation")}<p class="field-help">Use de 10 a 64 caracteres. A nova senha deve ser diferente da atual.</p><p class="form-error" role="alert"></p><button class="btn" type="submit">Salvar nova senha</button></form></article><aside class="card security-profile"><div class="avatar">${e(state.user.name.slice(0, 1).toUpperCase())}</div><h2>${e(state.user.name)}</h2><p>${e(state.user.email)}</p><span class="role-pill">${e(roles[state.user.role])}</span><hr><h3>O que seu perfil permite</h3><ul><li>Consultar finanças, relatórios e comprovantes</li>${canWrite() ? "<li>Gerenciar lançamentos, contas e cadastros</li>" : ""}${isAdmin() ? "<li>Gerenciar equipe e consultar o histórico</li>" : ""}<li>Alterar sua própria senha</li></ul><p class="notice">${isAdmin() ? "Gerencie os acessos na página Equipe." : "Para mudar suas permissões, fale com um Administrador da empresa."}</p></aside></section>`
  );
}
function bindAccessPages() {
  for (const prefix of ["team", "audit"]) {
    document
      .querySelector(`#${prefix}-filter`)
      ?.addEventListener("submit", async (ev) => {
        ev.preventDefault();
        state[prefix + "Filters"] = Object.fromEntries(
          new FormData(ev.currentTarget),
        );
        state[prefix + "Page"] = 0;
        await safeLoad();
      });
  }
  document.querySelector("#security-form")?.addEventListener("submit", (ev) =>
    submitForm(ev, async (data) => {
      if (data.password !== data.confirmation)
        throw uxFieldError("confirmation", "As senhas precisam ser iguais.");
      await send("/auth/change-password", {
        currentPassword: data.currentPassword,
        password: data.password,
      });
      state.user = null;
      state.csrf = null;
      await csrf();
      renderAuth("login", "Senha alterada. Entre novamente para continuar.");
    }),
  );
}
function inviteFields(u) {
  return (
    field("Nome", "name", "text", 'required maxlength="120"', u?.name || "") +
    (!u ? field("E-mail", "email", "email", 'required maxlength="255"') : "") +
    `<label class="field">Perfil<select name="role">${roleOptions(u?.role || "FINANCE")}</select></label><p class="notice">Administrador: acesso completo. Financeiro: gerencia finanças. Somente leitura: consulta e exporta dados.</p>`
  );
}
function showInviteLink() {
  const invitation = state.invitationToShow;
  state.invitationToShow = null;
  if (!invitation) return;
  showDialog(
    "Convite pronto para compartilhar",
    "Envie este link diretamente à pessoa convidada.",
    `<p class="notice">Válido até ${timestamp(invitation.expiresAt)}. O link será exibido somente agora. Gerar outro invalida o anterior.</p>${field("Link do convite", "link", "text", "readonly", invitation.link)}<button type="button" class="btn secondary" id="copy-invite">Copiar link</button><p id="copy-status" class="field-help" role="status"></p>`,
    async () => {},
    "Concluir",
  );
  const input = modal.querySelector("[name=link]");
  modal.querySelector("#copy-invite").onclick = async () => {
    try {
      await navigator.clipboard.writeText(invitation.link);
      modal.querySelector("#copy-status").textContent = "Link copiado.";
    } catch {
      input.focus();
      input.select();
      modal.querySelector("#copy-status").textContent =
        "Selecione e copie o link acima.";
    }
  };
}
async function accessAction(btn) {
  const action = btn.dataset.action;
  if (["team-prev", "team-next", "audit-prev", "audit-next"].includes(action)) {
    const prefix = action.split("-")[0];
    state[prefix + "Page"] += action.endsWith("next") ? 1 : -1;
    await safeLoad();
    return true;
  }
  if (
    !["invite-user", "edit-user", "renew-invite", "revoke-invite"].includes(
      action,
    )
  )
    return false;
  if (!isAdmin()) {
    toast("Seu perfil não permite esta ação.");
    return true;
  }
  const u = state.team?.content.find((x) => x.id === btn.dataset.id);
  if (action === "invite-user") {
    showDialog(
      "Convidar pessoa",
      "O convite dá acesso somente a esta empresa.",
      inviteFields(),
      async (data) => {
        state.invitationToShow = await send("/users", data);
      },
      "Gerar convite",
    );
  }
  if (action === "edit-user") {
    showDialog(
      "Editar acesso",
      e(u.email),
      inviteFields(u) +
        `<label class="field">Situação<select name="active" ${u.invited || u.id === state.user.id ? "disabled" : ""}><option value="true">Ativo</option><option value="false">${u.invited ? "Aguardando ativação" : "Inativo"}</option></select></label>${u.id === state.user.id ? '<p class="notice">Sua conta deve permanecer ativa como Administrador.</p>' : ""}`,
      async (data) => {
        await api("/users/" + u.id, {
          method: "PUT",
          body: JSON.stringify({
            ...data,
            active: u.invited
              ? false
              : u.id === state.user.id
                ? true
                : data.active === "true",
          }),
        });
        toast("Acesso atualizado.");
      },
    );
    modal.querySelector("[name=active]").value = String(u.active);
    if (u.id === state.user.id)
      modal.querySelector("[name=role]").disabled = true;
    const form = modal.querySelector("form");
    if (u.id === state.user.id) {
      const hidden = document.createElement("input");
      hidden.type = "hidden";
      hidden.name = "role";
      hidden.value = "ADMIN";
      form.append(hidden);
    }
  }
  if (action === "renew-invite" || action === "revoke-invite") {
    showDialog(
      action === "renew-invite" ? "Gerar novo convite?" : "Revogar convite?",
      e(u.name),
      '<p class="notice">O link anterior deixará de funcionar. Os registros dessa pessoa serão preservados.</p>',
      async () => {
        if (action === "renew-invite")
          state.invitationToShow = await send("/users/" + u.id + "/invitation");
        else {
          await api("/users/" + u.id + "/invitation", { method: "DELETE" });
          toast("Convite revogado.");
        }
      },
      "Confirmar",
    );
  }
  return true;
}

function shell() {
  const u = state.user,
    current = root.querySelector(".shell");
  const key = JSON.stringify([
    u.id,
    u.role,
    u.name,
    u.companyName,
    state.subscriptionAccess?.writable,
  ]);
  if (current?.dataset.session === key) {
    syncNavigation();
    return;
  }
  root.innerHTML = renderAppShell(u);
  root.querySelector(".shell").dataset.session = key;
  navigationMount();
}
function monthEnd(month) {
  const [y, m] = month.split("-").map(Number);
  return new Date(Date.UTC(y, m, 0)).toISOString().slice(0, 10);
}
function queryParams(values) {
  return new URLSearchParams(
    Object.fromEntries(
      Object.entries(values).filter(
        ([, value]) => value !== "" && value != null,
      ),
    ),
  );
}
function comparison(value, previous) {
  if (Number(previous) === 0)
    return Number(value) === 0
      ? "Sem movimentação no mês anterior"
      : "Sem base de comparação anterior";
  const delta =
    ((Number(value) - Number(previous)) / Math.abs(Number(previous))) * 100;
  return `${delta > 0 ? "+" : ""}${delta.toLocaleString("pt-BR", { maximumFractionDigits: 1 })}% vs. mês anterior`;
}
function categoryBreakdown(categories, total) {
  if (!categories.length)
    return '<p class="chart-caption">Nenhuma despesa realizada para os filtros selecionados.</p>';
  return `<div class="category-breakdown">${categories
    .slice(0, 5)
    .map((item) => {
      const percent = Number(total)
        ? (Number(item.amount) / Number(total)) * 100
        : 0;
      return `<div><div class="category-line"><span>${e(item.category)}</span><strong>${money(item.amount)}</strong></div><svg viewBox="0 0 100 5" preserveAspectRatio="none" role="img" aria-label="${e(item.category)}: ${percent.toFixed(1)}% das despesas realizadas"><rect width="100" height="5" rx="2.5" fill="var(--line)"/><rect width="${Math.min(100, percent)}" height="5" rx="2.5" fill="var(--green)"/></svg></div>`;
    })
    .join("")}</div>`;
}
function monthInput() {
  return `<label><span class="eyebrow" hidden>Período</span><input class="period" id="month" type="month" aria-label="Mês de referência" value="${state.month}" min="2000-01" max="2100-12"></label>`;
}
function head(title, subtitle, actions = "") {
  return `<div class="page-head"><div><h1>${title}</h1><p class="muted">${subtitle}</p></div><div class="head-actions">${actions}</div></div>`;
}
function metric(title, value, foot, ic, cls = "") {
  const formatted = money(value);
  return `<article class="metric ${cls} ${Number(value) < 0 ? "negative-value" : ""}"><div class="metric-top"><span>${title}</span><span class="metric-icon">${icon(ic)}</span></div><div class="metric-value ${formatted.length > 18 ? "money-long" : formatted.length > 12 ? "money-medium" : ""}">${formatted}</div><span class="metric-foot">${foot}</span></article>`;
}
function empty(title, text, action = "entry", button = "Novo lançamento") {
  return `<div class="empty"><div class="empty-icon">${icon("wallet")}</div><h3>${title}</h3><p>${text}</p><div class="actions"><button class="btn" data-action="${action}">${icon("plus")}${button}</button>${!state.accounts.length ? '<button class="btn secondary" data-action="sample">Explorar com dados de exemplo</button>' : ""}</div></div>`;
}
function chart(flow) {
  const max = Math.max(
    1,
    ...flow.flatMap((m) => [Number(m.income), Number(m.expense)]),
  );
  const compact = (v) =>
    Number(v).toLocaleString("pt-BR", {
      notation: "compact",
      maximumFractionDigits: 1,
    });
  return `<div class="chart"><svg role="img" aria-label="Receitas e despesas pagas nos últimos seis meses. Os valores também aparecem nas descrições das barras." viewBox="0 0 600 210">${[0, 1, 2, 3].map((i) => `<line x1="48" x2="588" y1="${20 + i * 47}" y2="${20 + i * 47}" stroke="var(--line)"/><text x="0" y="${24 + i * 47}">${compact(max * (1 - i / 3))}</text>`).join("")}${flow
    .map((m, i) => {
      const x = 62 + i * 88,
        hi = (Number(m.income) / max) * 141,
        he = (Number(m.expense) / max) * 141;
      const label = new Intl.DateTimeFormat("pt-BR", {
        month: "short",
        timeZone: "UTC",
      })
        .format(new Date(m.month + "-15T12:00:00Z"))
        .replace(".", "");
      return `<g><title>${label}: receitas ${money(m.income)}, despesas ${money(m.expense)}</title><rect x="${x}" y="${161 - hi}" width="23" height="${hi}" rx="4" fill="var(--green)"/><rect x="${x + 28}" y="${161 - he}" width="23" height="${he}" rx="4" fill="#b8c6b0"/><text x="${x + 25}" y="188" text-anchor="middle">${label}</text></g>`;
    })
    .join("")}</svg></div>`;
}
function entryStatus(row, today = localDate()) {
  const paid = row.status === "PAID",
    cancelled = row.status === "CANCELLED",
    overdue = !paid && !cancelled && row.dueDate < today;
  return {
    label: cancelled
      ? "Cancelado"
      : paid
        ? row.type === "INCOME"
          ? "Recebido"
          : "Pago"
        : overdue
          ? "Em atraso"
          : row.dueDate === today
            ? "Vence hoje"
            : "Pendente",
    className: cancelled
      ? "cancelled"
      : paid
        ? ""
        : overdue
          ? "pending overdue-badge"
          : "pending",
  };
}
function entryActions(row) {
  return `<div class="row-actions"><button class="btn secondary small" data-action="details" data-id="${row.id}" aria-label="Detalhes de ${e(row.description)}">Detalhes</button>${canWrite() && row.status === "PENDING" ? `<button class="btn soft small" data-action="pay" data-id="${row.id}">${row.type === "INCOME" ? "Receber" : "Pagar"}</button>${!row.seriesId && !row.saleId ? `<button class="btn secondary small" data-action="edit-entry" data-id="${row.id}" aria-label="Editar ${e(row.description)}">Editar</button>` : ""}` : ""}${canWrite() && row.status !== "CANCELLED" && !row.transferId && !row.saleId ? `<button class="btn text small" data-action="cancel" data-id="${row.id}" aria-label="Cancelar ${e(row.description)}">Cancelar</button>` : ""}</div>`;
}
function entryOrigin(row) {
  return row.seriesId
    ? `<span class="series-label">${row.seriesKind === "INSTALLMENT" ? "Parcela" : "Repetição"} ${row.sequenceNumber}/${row.seriesCount}</span>`
    : row.saleId
      ? '<span class="series-label">Venda vinculada</span>'
      : row.transferId
        ? '<span class="series-label">Transferência entre contas</span>'
        : "";
}
function entriesTable(rows, options = {}) {
  if (!rows.length)
    return (
      options.empty ||
      empty(
        "Nenhum lançamento por aqui",
        "Cadastre suas receitas e despesas para acompanhar cada movimento.",
      )
    );
  const names = new Map(state.accounts.map((a) => [a.id, a.name])),
    today = options.today || localDate();
  return `<div class="table-wrap entry-table" tabindex="0" role="region" aria-label="Lançamentos em tabela, com rolagem"><table><thead><tr>${["DESCRIÇÃO / CATEGORIA", "VENCIMENTO", "CONTA", "VALOR", "STATUS", "AÇÕES"].map((l) => `<th scope="col">${l}</th>`).join("")}</tr></thead><tbody>${rows
    .map((row) => {
      const status = entryStatus(row, today),
        income = row.type === "INCOME";
      return `<tr><td class="description"><strong>${e(row.description)}</strong><small>${e(row.category)}${row.contactName ? " · " + e(row.contactName) : ""}</small>${entryOrigin(row)}</td><td>${dateLabel(row.dueDate)}${row.paymentDate ? `<br><small class="muted">Baixa: ${dateLabel(row.paymentDate)}</small>` : ""}</td><td>${e(names.get(row.accountId) || "Conta")}</td><td class="${income ? "positive" : "negative"}"><strong>${income ? "+" : "−"} ${money(row.amount)}</strong></td><td><span class="badge ${status.className}">${status.label}</span></td><td>${entryActions(row)}</td></tr>`;
    })
    .join(
      "",
    )}</tbody></table></div><div class="entry-cards" role="list" aria-label="Lançamentos em cartões">${rows
    .map((row) => {
      const status = entryStatus(row, today),
        income = row.type === "INCOME";
      return `<article class="entry-card" role="listitem"><div class="entry-card-head"><span class="entry-direction ${income ? "positive" : "negative"}">${icon(income ? "income" : "expense")}${income ? "Receita" : "Despesa"}</span><span class="badge ${status.className}">${status.label}</span></div><h3>${e(row.description)}</h3><p>${e(row.category)}${row.contactName ? " · " + e(row.contactName) : ""}</p>${entryOrigin(row)}<strong class="entry-card-amount ${income ? "positive" : "negative"}">${income ? "+" : "−"} ${money(row.amount)}</strong><dl><div><dt>Vencimento</dt><dd>${dateLabel(row.dueDate)}</dd></div><div><dt>Conta</dt><dd>${e(names.get(row.accountId) || "Conta")}</dd></div>${row.paymentDate ? `<div><dt>Baixa</dt><dd>${dateLabel(row.paymentDate)}</dd></div>` : ""}</dl>${entryActions(row)}</article>`;
    })
    .join("")}</div>`;
}
function renderDashboard() {
  const s = state.summary;
  return (
    head(
      `Seu negócio, em perspectiva.`,
      `Olá, ${e(state.user.name.split(" ")[0])}. Acompanhe o que movimenta sua empresa.`,
      monthInput() +
        `<button class="btn" data-action="entry">${icon("plus")}Novo lançamento</button>`,
    ) +
    `<section class="metrics" aria-label="Indicadores financeiros">${metric("Saldo disponível", s.balance, "Saldo atual de todas as contas", "wallet", "primary")}${metric("Receitas do mês", s.income, comparison(s.income, s.previousIncome), "income")}${metric("Despesas do mês", s.expense, comparison(s.expense, s.previousExpense), "expense")}${metric("Resultado de caixa", s.result, comparison(s.result, s.previousResult), "trend")}</section>` +
    `<section class="grid-two"><article class="card"><div class="card-head"><div><h2>O ritmo do seu caixa</h2><p>Receitas e despesas realizadas · últimos 6 meses</p></div><div class="legend"><span><i class="dot"></i>Receitas</span><span><i class="dot expense"></i>Despesas</span></div></div>${chart(s.flow)}<div class="chart-caption">Valores em reais. Transferências entre contas não entram no resultado.</div></article><article class="card"><div class="card-head"><div><h2>No seu radar</h2><p>Vencimentos pendentes no mês selecionado</p></div>${icon("entries")}</div><div class="pending"><div class="pending-row"><div><p>A receber</p><small>Entradas previstas</small></div><strong class="positive">${money(s.pendingIncome)}</strong></div><div class="pending-row"><div><p>A pagar</p><small>Compromissos do período</small></div><strong>${money(s.pendingExpense)}</strong></div></div><div class="callout">Pendências entram no saldo quando você registra o pagamento ou recebimento.</div></article></section>` +
    pendingDashboard() +
    `<section class="card"><div class="card-head"><div><h2>Movimentações do período</h2><p>Organizadas pela data de vencimento</p></div><button class="btn text small" data-nav="entries">Ver todas →</button></div>${state.accounts.length ? entriesTable(state.entries.content.slice(0, 5)) : empty("Vamos organizar seu financeiro?", "Comece cadastrando uma conta ou explore um exemplo com dados fictícios.", "account", "Criar primeira conta")}</section>` +
    `<section class="grid-two dashboard-analysis"><article class="card"><div class="card-head"><div><h2>Maiores despesas por categoria</h2><p>Até 5 categorias · pagamentos do mês selecionado</p></div></div>${categoryBreakdown(s.categories || [], s.expense)}</article><article class="card"><div class="card-head"><div><h2>Olhe para os próximos meses</h2><p>Saldo atual e vencimentos cadastrados</p></div></div><div class="analysis-copy"><p>Explore o saldo estimado com os valores a receber e a pagar. Escolha a conta e como considerar as pendências vencidas.</p><button class="btn soft" data-nav="forecast">Ver fluxo projetado →</button><button class="btn text" data-nav="reports">Abrir relatórios</button></div></article></section>`
  );
}
function renderEntries() {
  const f = state.filters;
  return (
    head(
      "Cada movimento, no lugar.",
      "Registre entradas, saídas e acompanhe os vencimentos.",
      monthInput() +
        (canWrite()
          ? '<button class="btn secondary" data-nav="imports">Importar CSV</button>'
          : "") +
        `<button class="btn" data-action="entry">${icon("plus")}Novo lançamento</button>`,
    ) +
    `<section class="card"><form class="filters" id="filter-form"><input name="search" type="search" placeholder="Buscar pela descrição…" aria-label="Buscar lançamentos" maxlength="255" value="${e(f.search || "")}"><select name="type" aria-label="Tipo"><option value="">Todos os tipos</option><option value="INCOME" ${f.type === "INCOME" ? "selected" : ""}>Receitas</option><option value="EXPENSE" ${f.type === "EXPENSE" ? "selected" : ""}>Despesas</option></select><select name="status" aria-label="Status"><option value="">Todos os status</option>${[
      ["PENDING", "Pendentes"],
      ["PAID", "Realizados"],
      ["CANCELLED", "Cancelados"],
    ]
      .map(
        ([v, l]) =>
          `<option value="${v}" ${f.status === v ? "selected" : ""}>${l}</option>`,
      )
      .join(
        "",
      )}</select><button class="btn secondary" type="submit">Filtrar</button></form>${entriesTable(state.entries.content)}<div class="pagination"><span>${state.entries.totalElements} lançamento(s)</span><div class="row-actions"><button class="btn secondary small" data-action="prev" ${state.entryPage === 0 ? "disabled" : ""}>Anterior</button><span>${state.entryPage + 1} / ${Math.max(1, state.entries.totalPages)}</span><button class="btn secondary small" data-action="next" ${state.entryPage + 1 >= state.entries.totalPages ? "disabled" : ""}>Próxima</button></div></div></section>`
  );
}
function reportRows(rows) {
  if (!rows.length)
    return '<div class="empty"><h3>Nenhum lançamento encontrado</h3><p>Ajuste o período ou os filtros. Transferências não fazem parte deste relatório.</p></div>';
  return `<div class="table-wrap"><table><thead><tr><th>DESCRIÇÃO / CATEGORIA</th><th>CONTA</th><th>VENCIMENTO / BAIXA</th><th>VALOR</th><th>SITUAÇÃO</th></tr></thead><tbody>${rows.map((row) => `<tr><td class="description"><button class="entry-link" data-action="details" data-id="${row.id}" aria-label="Detalhes de ${e(row.description)}">${e(row.description)}</button><small>${e(row.category)}${row.contactName ? " · " + e(row.contactName) : ""}${row.sequenceNumber ? " · " + row.sequenceNumber + "/" + row.seriesCount : ""}</small></td><td>${e(row.accountName)}</td><td>${dateLabel(row.dueDate)}<br><small class="muted">Baixa: ${dateLabel(row.paymentDate)}</small></td><td class="${row.type === "INCOME" ? "positive" : "negative"}"><strong>${row.type === "INCOME" ? "+" : "−"} ${money(row.amount)}</strong></td><td><span class="badge ${row.status === "PENDING" ? "pending" : row.status === "CANCELLED" ? "cancelled" : ""}">${row.status === "PAID" ? "Realizado" : row.status === "PENDING" ? "Pendente" : "Cancelado"}</span></td></tr>`).join("")}</tbody></table></div>`;
}
function accountFilter(selected) {
  return `<label class="field">Conta<select name="accountId"><option value="">Todas as contas</option>${state.accounts.map((a) => `<option value="${a.id}" ${a.id === selected ? "selected" : ""}>${e(a.name)}</option>`).join("")}</select></label>`;
}
function renderReports() {
  const r = state.report,
    f = state.reportFilters,
    t = r.totals;
  return (
    head(
      "Os números, com contexto.",
      "Filtre, compare e exporte suas movimentações.",
      `<button class="btn secondary" data-action="report-export" data-format="csv">CSV</button><button class="btn secondary" data-action="report-export" data-format="xlsx">Excel</button><button class="btn" data-action="report-export" data-format="pdf">PDF</button>`,
    ) +
    `<section class="card"><form class="report-filters" id="report-form">${field("Data inicial", "start", "date", "required", f.start)}${field("Data final", "end", "date", "required", f.end)}<label class="field">Base do período<select name="basis" id="report-basis"><option value="PAYMENT" ${f.basis === "PAYMENT" ? "selected" : ""}>Data do pagamento / recebimento</option><option value="DUE" ${f.basis === "DUE" ? "selected" : ""}>Data do vencimento</option></select></label>${accountFilter(f.accountId)}<label class="field">Tipo<select name="type"><option value="">Receitas e despesas</option><option value="INCOME" ${f.type === "INCOME" ? "selected" : ""}>Receitas</option><option value="EXPENSE" ${f.type === "EXPENSE" ? "selected" : ""}>Despesas</option></select></label><label class="field">Situação<select name="status" ${f.basis === "PAYMENT" ? "disabled" : ""}><option value="" ${!f.status ? "selected" : ""}>Todos os ativos</option><option value="PAID" ${f.status === "PAID" ? "selected" : ""}>Realizados</option><option value="PENDING" ${f.status === "PENDING" ? "selected" : ""}>Pendentes</option><option value="CANCELLED" ${f.status === "CANCELLED" ? "selected" : ""}>Cancelados</option></select></label>${field("Categoria contém", "category", "search", 'maxlength="80" placeholder="Todas as categorias"', f.category || "")}${field("Cliente / fornecedor contém", "contact", "search", 'maxlength="120" placeholder="Todos os contatos"', f.contact || "")}<div class="filter-actions"><small class="muted">As exportações usam todos os registros dos filtros aplicados, até 5.000 lançamentos.</small><button type="submit" class="btn">Aplicar filtros</button></div></form></section>` +
    `<div class="report-context">${dateLabel(f.start)} a ${dateLabel(f.end)} · Por ${f.basis === "PAYMENT" ? "pagamento" : "vencimento"} · ${r.totalElements} lançamento(s)</div><section class="metrics">${metric("Receitas realizadas", t.income, "Nos filtros aplicados", "income")}${metric("Despesas realizadas", t.expense, "Nos filtros aplicados", "expense")}${metric("A receber", t.pendingIncome, "Pendências nos filtros aplicados", "income")}${metric("A pagar", t.pendingExpense, "Pendências nos filtros aplicados", "expense")}</section>` +
    `<section class="grid-two"><article class="card"><div class="card-head"><h2>Despesas realizadas por categoria</h2></div>${categoryBreakdown(r.categories, t.expense)}</article><article class="card"><div class="card-head"><h2>Resultado de caixa realizado</h2></div><div class="report-result"><strong class="${Number(t.result) < 0 ? "negative" : "positive"}">${money(t.result)}</strong><p>Receitas realizadas menos despesas realizadas nos filtros aplicados. Transferências e cancelados não compõem os totais. Este indicador não representa lucro contábil.</p></div></article></section><section class="card">${reportRows(r.content)}<div class="pagination"><span>${r.totalElements} lançamento(s)</span><div class="row-actions"><button class="btn secondary small" data-action="reports-prev" ${state.reportPage === 0 ? "disabled" : ""}>Anterior</button><span>${state.reportPage + 1} / ${Math.max(1, r.totalPages)}</span><button class="btn secondary small" data-action="reports-next" ${state.reportPage + 1 >= r.totalPages ? "disabled" : ""}>Próxima</button></div></div></section>`
  );
}
function forecastChart(f) {
  const values = [
    Number(f.openingBalance),
    ...f.days.map((d) => Number(d.balance)),
  ];
  let low = Math.min(0, ...values),
    high = Math.max(0, ...values);
  if (high === low) high = low + 1;
  const pad = (high - low) * 0.12;
  low -= pad;
  high += pad;
  const x = (i) => 48 + (i / (f.days.length - 1)) * 530,
    y = (v) => 20 + ((high - v) / (high - low)) * 160;
  const points = f.days
    .map((d, i) => `${x(i)},${y(Number(d.balance))}`)
    .join(" ");
  return `<div class="chart"><svg viewBox="0 0 600 220" role="img" aria-label="Evolução estimada do saldo até ${dateLabel(f.end)}"><line x1="48" x2="580" y1="${y(0)}" y2="${y(0)}" stroke="var(--line)" stroke-dasharray="4 4"/>${[high, (high + low) / 2, low].map((v) => `<text x="0" y="${y(v) + 4}">${v.toLocaleString("pt-BR", { notation: "compact", maximumFractionDigits: 1 })}</text>`).join("")}<polyline points="${points}" fill="none" stroke="var(--green)" stroke-width="2.5"/>${f.days
    .filter(
      (d, i) =>
        i === 0 || i === f.days.length - 1 || d.date === f.firstNegativeDate,
    )
    .map((d) => {
      const i = f.days.indexOf(d);
      return `<circle cx="${x(i)}" cy="${y(Number(d.balance))}" r="4" fill="${Number(d.balance) < 0 ? "#b54a49" : "#18785a"}"><title>${dateLabel(d.date)}: ${money(d.balance)}</title></circle>`;
    })
    .join(
      "",
    )}<text x="48" y="207">${dateLabel(f.start)}</text><text x="578" y="207" text-anchor="end">${dateLabel(f.end)}</text></svg></div>`;
}
function renderForecast() {
  const f = state.forecast,
    filters = state.forecastFilters;
  const days = f.days.filter(
    (d, i) =>
      i === 0 ||
      i === f.days.length - 1 ||
      Number(d.income) ||
      Number(d.expense),
  );
  return (
    head(
      "Enxergue o caminho do seu caixa.",
      "Projeção a partir do saldo atual e dos vencimentos cadastrados.",
      `<button class="btn secondary" data-nav="reports">Ver relatórios</button>`,
    ) +
    `<section class="card"><form class="forecast-filters" id="forecast-form"><label class="field">Horizonte<select name="days">${[30, 60, 90, 180, 366].map((n) => `<option value="${n}" ${String(n) === String(filters.days) ? "selected" : ""}>Até ${n} dias</option>`).join("")}</select></label>${accountFilter(filters.accountId)}<label class="check"><input type="checkbox" name="includeOverdue" ${filters.includeOverdue === "true" ? "checked" : ""}>Considerar vencidas como previstas para hoje</label><button type="submit" class="btn">Atualizar projeção</button></form></section>` +
    `<div class="report-context">${dateLabel(f.start)} a ${dateLabel(f.end)} · Valores previstos, sem registrar baixas</div><section class="metrics">${metric("Saldo atual", f.openingBalance, "Ponto de partida da projeção", "wallet", "primary")}${metric("Saldo estimado ao final", f.closingBalance, "Considerando os vencimentos previstos", "trend")}${metric("A receber no horizonte", f.pendingIncome, "Entradas pendentes consideradas", "income")}${metric("A pagar no horizonte", f.pendingExpense, "Saídas pendentes consideradas", "expense")}</section>` +
    `<section class="grid-two"><article class="card"><div class="card-head"><div><h2>Evolução estimada do saldo</h2><p>Valores em reais · saldo acumulado por dia</p></div></div>${forecastChart(f)}</article><article class="card"><div class="card-head"><h2>No radar da projeção</h2></div><div class="report-result"><span class="eyebrow">Menor saldo estimado</span><strong class="${Number(f.minimumBalance) < 0 ? "negative" : "positive"}">${money(f.minimumBalance)}</strong><p>${f.firstNegativeDate ? "Primeiro saldo negativo estimado em " + dateLabel(f.firstNegativeDate) + "." : "Nenhum saldo negativo no horizonte considerado."}</p><p>Vencidos a receber: <b>${money(f.overdueIncome)}</b><br>Vencidos a pagar: <b>${money(f.overdueExpense)}</b></p></div></article></section>` +
    `<p class="notice">A estimativa considera somente as contas e os lançamentos cadastrados, sem alterar saldos ou registrar pagamentos. ${f.includeOverdue ? "Pendências vencidas foram somadas ao dia de hoje." : "Pendências vencidas não foram incluídas no saldo futuro."} Os totais são líquidos por dia; não representam horários de entrada e saída.</p><section class="card"><div class="card-head"><div><h2>Vencimentos e saldo estimado</h2><p>Datas com movimentação, além do início e fim do horizonte</p></div></div><div class="table-wrap"><table><thead><tr><th>DATA</th><th>A RECEBER</th><th>A PAGAR</th><th>SALDO ESTIMADO</th></tr></thead><tbody>${days.map((d) => `<tr><td>${dateLabel(d.date)}</td><td class="positive">${money(d.income)}</td><td class="negative">${money(d.expense)}</td><td class="${Number(d.balance) < 0 ? "negative" : ""}"><strong>${money(d.balance)}</strong></td></tr>`).join("")}</tbody></table></div></section>`
  );
}
async function downloadReport(format, button) {
  button.disabled = true;
  try {
    const response = await fetch(
      "/api/v1/reports/export?" +
        queryParams({ ...state.reportFilters, format }),
    );
    if (!response.ok) {
      const data = await response.json();
      if (response.status === 401) {
        state.user = null;
        renderAuth();
      }
      throw new Error(data.message || "Não foi possível exportar o relatório.");
    }
    const blob = await response.blob(),
      url = URL.createObjectURL(blob),
      link = document.createElement("a");
    link.href = url;
    link.download = `fintry-relatorio-${state.reportFilters.start}-${state.reportFilters.end}.${format}`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 30000);
    toast("Arquivo preparado com os filtros aplicados.");
  } catch (err) {
    toast(err.message);
  } finally {
    button.disabled = false;
  }
}
function renderAccounts() {
  return (
    head(
      "Onde seu dinheiro está.",
      "Contas organizadas para acompanhar o saldo do seu negócio.",
      `<button class="btn secondary" data-action="transfer">${icon("transfer")}Transferir</button><button class="btn" data-action="account">${icon("plus")}Nova conta</button>`,
    ) +
    (state.accounts.length
      ? `<section class="account-grid">${state.accounts.map((a) => `<article class="card account-card"><div class="metric-icon">${icon("wallet")}</div><h2>${e(a.name)}</h2><span class="eyebrow">Saldo atual</span><strong>${money(a.balance)}</strong><p>Saldo inicial: ${money(a.initialBalance)}</p></article>`).join("")}</section><p class="notice">O saldo é calculado pelo valor inicial mais os lançamentos realizados. Transferências movimentam as contas sem alterar o resultado da empresa.</p>`
      : `<section class="card">${empty("Sua primeira conta começa aqui", "Cadastre uma conta bancária ou o caixa da empresa.", "account", "Criar conta")}</section>`)
  );
}
let requestId = 0;
async function load() {
  const id = ++requestId,
    page = state.page;
  const focused = document.activeElement?.closest("#page-content")
    ? document.activeElement.name
    : null;
  shell();
  let container = document.querySelector("#page-content");
  container.setAttribute("aria-busy", "true");
  container.innerHTML = uxLoading(page);
  try {
    await loadData(id);
    if (id !== requestId || !state.user) return;
    container = document.querySelector("#page-content");
    container?.setAttribute("aria-busy", "false");
    if (!modal.open && !drawer.open) {
      const restored = focused
        ? Array.from(container?.querySelectorAll("[name]") || []).find(
            (x) => x.name === focused,
          )
        : null;
      const target = restored || container?.querySelector(".page-head h1");
      if (target) {
        if (!restored) target.tabIndex = -1;
        target.focus({ preventScroll: true });
      }
    }
  } catch (err) {
    if (id !== requestId || !state.user) return;
    container = document.querySelector("#page-content");
    if (container) {
      container.setAttribute("aria-busy", "false");
      container.innerHTML = uxLoadError(err.message);
      container.querySelector("h2")?.focus({ preventScroll: true });
    }
    throw err;
  }
}
async function loadData(id) {
  const access = await api("/subscription/access");
  if (id !== requestId || !state.user) return;
  state.subscriptionAccess = access;
  if (!root.querySelector(".shell")) shell();
  if (["services", "proposals"].includes(state.page)) return loadCommercial(id);
  if (state.page === "imports") return loadImports(id);
  if (state.page === "subscription") return loadSubscription(id);
  if (state.page === "billing") return loadBilling(id);
  if (state.page === "operations") return loadOperations(id);
  if (state.page === "reconciliation") return loadReconciliation(id);
  if (["budgets", "examples"].includes(state.page)) return loadPlanning(id);
  if (state.page === "pending") return loadPending(id);
  if (["products", "stock", "sales"].includes(state.page))
    return loadCommerce(id);
  if (["team", "audit", "security"].includes(state.page)) {
    if (state.page !== "security" && !isAdmin()) {
      state.page = "dashboard";
      return load();
    }
    const page = state.page;
    if (page === "team")
      state.team = await api(
        "/users?" + queryParams({ ...state.teamFilters, page: state.teamPage }),
      );
    if (page === "audit")
      state.audit = await api(
        "/audit?" +
          queryParams({ ...state.auditFilters, page: state.auditPage }),
      );
    if (id !== requestId || !state.user) return;
    shell();
    document.querySelector("#page-content").innerHTML =
      page === "team"
        ? renderTeam()
        : page === "audit"
          ? renderAudit()
          : renderSecurity();
    bindAccessPages();
    return;
  }
  const [year, month] = state.month.split("-").map(Number);
  const end = new Date(Date.UTC(year, month, 0)).toISOString().slice(0, 10);
  const query = new URLSearchParams({
    start: state.month + "-01",
    end,
    page: String(state.page === "dashboard" ? 0 : state.entryPage),
    size: state.page === "dashboard" ? "5" : "20",
    ...(state.page === "entries" ? state.filters : {}),
  });
  const [
    accounts,
    entries,
    summary,
    categories,
    contacts,
    series,
    report,
    forecast,
    dashboardPending,
  ] = await Promise.all([
    api("/finance/accounts"),
    api("/finance/entries?" + query),
    api("/finance/summary?month=" + state.month),
    api("/categories"),
    state.page === "contacts"
      ? api(
          "/contacts?" +
            new URLSearchParams({
              ...state.directoryFilters,
              page: String(state.contactPage),
              size: "20",
            }),
        )
      : Promise.resolve(null),
    state.page === "series"
      ? api("/finance/series?page=" + state.seriesPage)
      : Promise.resolve(null),
    state.page === "reports"
      ? api(
          "/reports?" +
            queryParams({
              ...state.reportFilters,
              page: String(state.reportPage),
            }),
        )
      : Promise.resolve(null),
    state.page === "forecast"
      ? api("/reports/forecast?" + queryParams(state.forecastFilters))
      : Promise.resolve(null),
    state.page === "dashboard"
      ? api("/finance/pending?group=UPCOMING&size=5")
      : Promise.resolve(null),
  ]);
  if (id !== requestId || !state.user) return;
  if (
    state.page === "entries" &&
    state.entryPage > 0 &&
    state.entryPage >= entries.totalPages
  ) {
    state.entryPage = Math.max(0, entries.totalPages - 1);
    return load();
  }
  state.dashboardPending = dashboardPending;
  state.accounts = accounts;
  state.entries = entries;
  state.summary = summary;
  state.series = series;
  state.report = report;
  state.forecast = forecast;
  state.categories = categories;
  state.contacts = contacts;
  shell();
  document.querySelector("#page-content").innerHTML =
    state.page === "reports"
      ? renderReports()
      : state.page === "forecast"
        ? renderForecast()
        : state.page === "dashboard"
          ? renderDashboard()
          : state.page === "entries"
            ? renderEntries()
            : state.page === "series"
              ? renderSeries()
              : state.page === "contacts"
                ? renderContacts()
                : state.page === "categories"
                  ? renderCategories()
                  : renderAccounts();
  applyPermissions(root);
  document
    .querySelector("#directory-filter")
    ?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      state.directoryFilters = Object.fromEntries(
        new FormData(ev.currentTarget),
      );
      state.contactPage = 0;
      state.seriesPage = 0;
      await safeLoad();
    });
  document.querySelector("#month")?.addEventListener("change", async (ev) => {
    if (!/^\d{4}-\d{2}$/.test(ev.target.value)) return;
    state.month = ev.target.value;
    state.entryPage = 0;
    state.contactPage = 0;
    state.seriesPage = 0;
    await safeLoad();
  });
  document
    .querySelector("#report-form")
    ?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      state.reportFilters = Object.fromEntries(new FormData(ev.currentTarget));
      if (state.reportFilters.basis === "PAYMENT")
        state.reportFilters.status = "PAID";
      state.reportPage = 0;
      await safeLoad();
    });
  document.querySelector("#report-basis")?.addEventListener("change", (ev) => {
    const control = document.querySelector("#report-form [name=status]");
    control.disabled = ev.target.value === "PAYMENT";
    control.value = control.disabled ? "PAID" : "";
  });
  document
    .querySelector("#forecast-form")
    ?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      const values = Object.fromEntries(new FormData(ev.currentTarget));
      values.includeOverdue = values.includeOverdue === "on" ? "true" : "false";
      state.forecastFilters = values;
      await safeLoad();
    });
  document
    .querySelector("#filter-form")
    ?.addEventListener("submit", async (ev) => {
      ev.preventDefault();
      state.filters = Object.fromEntries(new FormData(ev.currentTarget));
      state.entryPage = 0;
      await safeLoad();
    });
}
async function safeLoad() {
  try {
    await load();
  } catch (err) {
    if (!document.querySelector(".load-error")) toast(err.message);
    if (
      state.user &&
      !document.querySelector("#page-content")?.children.length
    ) {
      document.querySelector("#page-content").innerHTML =
        '<div class="empty"><h2>Não foi possível carregar os dados.</h2><button class="btn" data-action="reload">Tentar novamente</button></div>';
    }
  }
}
function showDialog(title, subtitle, fields, action, submit = "Salvar") {
  modal.innerHTML = `<div class="modal-head"><div><h2 id="modal-title">${title}</h2><p>${subtitle}</p></div><button class="close" data-action="close" aria-label="Fechar">×</button></div><form id="modal-form">${fields}<div class="form-error" role="alert"></div><div class="dialog-actions"><button type="button" class="btn secondary" data-action="close">Voltar</button><button class="btn" type="submit">${submit}</button></div></form>`;
  modal.showModal();
  modal.querySelector("form").onsubmit = (ev) =>
    submitForm(ev, async (data, form) => {
      await action(data, form);
      modal.close();
      await safeLoad();
      if (state.invitationToShow) showInviteLink();
      if (commercial.pendingProposal) {
        const id = commercial.pendingProposal;
        commercial.pendingProposal = null;
        await proposalDetails(id);
      }
      if (commerce.pendingSale) {
        const id = commerce.pendingSale;
        commerce.pendingSale = null;
        await saleDetails(id);
      }
      if (drawer.open) {
        await loadDetails();
        drawer.querySelector("#drawer-title")?.focus();
      }
    });
}
function accountOptions(name, label) {
  return `<label class="field">${label}<select name="${name}" required>${state.accounts.map((a) => `<option value="${a.id}">${e(a.name)}</option>`).join("")}</select></label>`;
}
function accountDialog() {
  showDialog(
    "Nova conta financeira",
    "Um lugar para acompanhar suas movimentações.",
    field(
      "Nome da conta",
      "name",
      "text",
      'required maxlength="100" placeholder="Ex.: Banco principal, Caixa da loja"',
    ) +
      field(
        "Saldo inicial (R$)",
        "initialBalance",
        "number",
        'required step="0.01" min="-999999999999.99" max="999999999999.99"',
        "0",
      ) +
      '<p class="notice">Informe o saldo anterior aos lançamentos que você vai cadastrar. Esse valor não conta como receita.</p>',
    async (data) => {
      await send("/finance/accounts", data);
      toast("Conta criada com sucesso.");
    },
    "Criar conta",
  );
}
function monthlyPlan(kind, count, value, first) {
  const cents = Math.round(Number(value) * 100);
  if (
    !Number.isInteger(count) ||
    count < 2 ||
    count > 120 ||
    !Number.isSafeInteger(cents) ||
    cents < 1 ||
    !/^\d{4}-\d{2}-\d{2}$/.test(first)
  )
    throw new Error(
      "Informe valor, quantidade de 2 a 120 e o primeiro vencimento.",
    );
  const [year, month, day] = first.split("-").map(Number);
  const base = Math.floor(cents / count),
    remainder = cents % count;
  if (kind === "INSTALLMENT" && base < 1)
    throw new Error("Cada parcela deve ter ao menos R$ 0,01.");
  return Array.from({ length: count }, (_, i) => {
    const target = new Date(Date.UTC(year, month - 1 + i, 1));
    const last = new Date(
      Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0),
    ).getUTCDate();
    target.setUTCDate(Math.min(day, last));
    return {
      date: target.toISOString().slice(0, 10),
      cents: kind === "INSTALLMENT" ? base + (i < remainder ? 1 : 0) : cents,
    };
  });
}
function renderSeries() {
  const result = state.series;
  return (
    head(
      "Organize os próximos meses.",
      "Parcelamentos e repetições mensais com quantidade definida.",
      `<button class="btn" data-action="entry">${icon("plus")}Nova série ou lançamento</button>`,
    ) +
    `<section class="card">${result.content.length ? `<div class="table-wrap"><table><thead><tr><th>SÉRIE</th><th>PERÍODO</th><th>VALOR TOTAL</th><th>ACOMPANHAMENTO</th><th>AÇÕES</th></tr></thead><tbody>${result.content.map((item) => `<tr><td class="description"><strong>${e(item.description)}</strong><small>${item.kind === "INSTALLMENT" ? "Parcelamento" : "Recorrência mensal"} · ${item.occurrences} ocorrências</small></td><td>${dateLabel(item.firstDueDate)}<br><small class="muted">até ${dateLabel(item.lastDueDate)}</small></td><td><strong>${money(item.totalAmount)}</strong></td><td class="description"><span class="badge ${item.stopped ? "cancelled" : item.pending ? "pending" : ""}">${item.stopped ? "Encerrada" : item.pending ? "Em andamento" : item.paid === item.occurrences ? "Concluída" : item.cancelled === item.occurrences ? "Cancelada" : "Encerrada"}</span><br><small>${item.paid} realizadas · ${item.pending} pendentes · ${item.cancelled} canceladas</small></td><td><div class="row-actions"><button class="btn secondary small" data-action="series-items" data-id="${item.id}">Ver lançamentos</button>${item.pending ? `<button class="btn text small" data-action="stop-series" data-id="${item.id}">Cancelar pendências</button>` : ""}</div></td></tr>`).join("")}</tbody></table></div>` : `<div class="empty"><h3>Planeje seus próximos vencimentos</h3><p>Divida um valor total em parcelas ou repita um valor mensalmente. Confira a prévia antes de registrar.</p><button class="btn" data-action="entry">Criar primeira série</button></div>`}<div class="pagination"><span>${result.totalElements} série(s)</span><div class="row-actions"><button class="btn secondary small" data-action="series-prev" ${state.seriesPage === 0 ? "disabled" : ""}>Anterior</button><span>${state.seriesPage + 1} / ${Math.max(1, result.totalPages)}</span><button class="btn secondary small" data-action="series-next" ${state.seriesPage + 1 >= result.totalPages ? "disabled" : ""}>Próxima</button></div></div></section><p class="notice">Os valores entram no saldo ao registrar cada baixa. Cancelar pendências da série preserva pagamentos e recebimentos realizados. Recorrências desta versão têm prazo definido, sem renovação automática.</p>`
  );
}
async function showSeriesItems(id) {
  const selected = state.series.content.find((item) => item.id === id);
  showDialog(
    "Lançamentos da série",
    e(selected.description),
    '<div class="series-items-content">Carregando…</div>',
    async () => {},
    "Fechar",
  );
  modal.querySelector("[type=submit]").hidden = true;
  const form = modal.querySelector("form");
  try {
    const parts = await Promise.all([
      api("/finance/entries?seriesId=" + id + "&size=100"),
      api("/finance/entries?seriesId=" + id + "&size=100&page=1"),
    ]);
    if (!form.isConnected) return;
    const rows = parts
      .flatMap((part) => part.content)
      .sort((a, b) => a.sequenceNumber - b.sequenceNumber);
    form.querySelector(".series-items-content").innerHTML =
      `<div class="schedule-rows">${rows.map((item) => `<div><span>${item.sequenceNumber}/${item.seriesCount} · ${dateLabel(item.dueDate)}<br><small>${item.status === "PAID" ? "Realizado" : item.status === "CANCELLED" ? "Cancelado" : "Pendente"}</small></span><strong>${money(item.amount)}</strong></div>`).join("")}</div><p class="notice">Para registrar uma baixa, abra Lançamentos e selecione o mês do vencimento.</p>`;
  } catch (err) {
    if (form.isConnected)
      form.querySelector(".series-items-content").textContent = err.message;
  }
}
function categoryOptions(type, row) {
  const options = state.categories.filter(
    (c) =>
      (c.active && (c.kind === type || c.kind === "BOTH")) ||
      c.id === row?.categoryId,
  );
  return `<label class="field">Categoria cadastrada<select name="categoryId" id="entry-category"><option value="">Usar categoria avulsa</option>${options.map((c) => `<option value="${c.id}" ${c.id === row?.categoryId ? "selected" : ""}>${e(c.name)}${c.active ? "" : " (inativa)"}</option>`).join("")}</select></label>`;
}
function entryDialog(row = null) {
  if (!state.accounts.length) {
    accountDialog();
    return;
  }
  let requestId = crypto.randomUUID(),
    lastPayload = null;
  showDialog(
    row ? "Editar lançamento pendente" : "Novo lançamento",
    row
      ? "Ajuste os dados antes de registrar a baixa."
      : "Organize cada movimento e seus vínculos.",
    (row
      ? ""
      : `<div class="entry-steps"><span class="eyebrow">1 · COMO LANÇAR</span><label class="field">Formato<select name="mode"><option value="SINGLE">Lançamento único</option><option value="INSTALLMENT">Parcelar um valor total</option><option value="RECURRING">Repetir valor mensalmente</option></select></label><div id="occurrences-field" hidden>${field("Quantidade de parcelas / repetições", "occurrences", "number", 'min="2" max="120" step="1"', "12")}</div></div>`) +
      `<div class="form-row"><label class="field">Tipo<select name="type"><option value="INCOME">Receita</option><option value="EXPENSE">Despesa</option></select></label>${field("Valor (R$)", "amount", "number", 'required min="0.01" max="999999999999.99" step="0.01"', row?.amount ?? "")}</div>` +
      field(
        "Descrição",
        "description",
        "text",
        'required maxlength="255"',
        row?.description ?? "",
      ) +
      accountOptions("accountId", "Conta") +
      `<div id="category-select">${categoryOptions(row?.type ?? "INCOME", row)}</div>` +
      `<div id="legacy-category">${field("Categoria avulsa", "category", "text", 'maxlength="80" placeholder="Ex.: Serviços"', row?.category ?? "Outros")}</div>` +
      field(
        "Buscar cliente ou fornecedor (opcional)",
        "contactSearch",
        "search",
        'placeholder="Digite para buscar um cadastro" autocomplete="off"',
      ) +
      `<label class="field">Cliente / fornecedor<select name="contactId"><option value="">Sem vínculo</option>${row?.contactId ? `<option value="${row.contactId}" selected>${e(row.contactName)}</option>` : ""}</select><small id="contact-hint" aria-live="polite">Buscando cadastros ativos…</small></label>` +
      field(
        "Vencimento",
        "dueDate",
        "date",
        "required",
        row?.dueDate ?? localDate(),
      ) +
      (row
        ? ""
        : `<label class="check"><input type="checkbox" name="paid" id="paid">Já foi pago / recebido</label><div id="payment-field" hidden>${field("Data do pagamento / recebimento", "paymentDate", "date", `max="${localDate()}"`, localDate())}</div><div id="schedule-preview" class="schedule-preview" aria-live="polite" hidden></div>`),
    async (data) => {
      const mode = data.mode || "SINGLE",
        count = Number(data.occurrences);
      delete data.mode;
      delete data.occurrences;
      delete data.contactSearch;
      data.paid = data.paid === "on";
      if (!data.paid) data.paymentDate = null;
      data.categoryId = data.categoryId || null;
      data.contactId = data.contactId || null;
      if (row)
        await api("/finance/entries/" + row.id, {
          method: "PUT",
          body: JSON.stringify(data),
        });
      else if (mode !== "SINGLE") {
        const payload = JSON.stringify({
          kind: mode,
          occurrences: count,
          entry: data,
        });
        if (lastPayload !== null && lastPayload !== payload)
          requestId = crypto.randomUUID();
        lastPayload = payload;
        await send("/finance/series", {
          requestId,
          kind: mode,
          occurrences: count,
          entry: data,
        });
      } else await send("/finance/entries", data);
      toast(
        row
          ? "Lançamento atualizado."
          : mode === "SINGLE"
            ? "Lançamento registrado."
            : "Série criada. Confira os próximos vencimentos em Parcelas e recorrências.",
      );
    },
    row ? "Salvar alterações" : "Registrar lançamento",
  );
  const form = modal.querySelector("form");
  form.elements.type.value = row?.type ?? "INCOME";
  if (row) form.elements.accountId.value = row.accountId;
  const toggleCategory = () => {
    const linked = Boolean(form.elements.categoryId.value);
    form.querySelector("#legacy-category").hidden = linked;
    form.elements.category.required = !linked;
  };
  form
    .querySelector("#category-select")
    .addEventListener("change", toggleCategory);
  toggleCategory();
  let contactRequest = 0,
    timer;
  async function lookupContacts() {
    const n = ++contactRequest;
    try {
      const query = new URLSearchParams({
        kind: form.elements.type.value === "INCOME" ? "CUSTOMER" : "SUPPLIER",
        active: "true",
        size: "100",
        search: form.elements.contactSearch.value,
      });
      const result = await api("/contacts?" + query);
      if (n !== contactRequest || !form.isConnected) return;
      const selected = form.elements.contactId.value;
      const selectedName =
        form.elements.contactId.selectedOptions[0]?.textContent;
      let opts = result.content;
      if (selected && !opts.some((c) => c.id === selected))
        opts = [
          {
            id: selected,
            name: selectedName || row?.contactName || "Vínculo anterior",
          },
          ...opts,
        ];
      form.elements.contactId.innerHTML =
        '<option value="">Sem vínculo</option>' +
        opts
          .map((c) => `<option value="${c.id}">${e(c.name)}</option>`)
          .join("");
      if (opts.some((c) => c.id === selected))
        form.elements.contactId.value = selected;
      form.querySelector("#contact-hint").textContent =
        result.totalElements > 100
          ? "Mostrando os primeiros 100. Refine a busca."
          : "Selecione um cadastro ou deixe sem vínculo.";
    } catch (err) {
      if (form.isConnected)
        form.querySelector("#contact-hint").textContent = err.message;
    }
  }
  form.elements.contactSearch.oninput = () => {
    clearTimeout(timer);
    timer = setTimeout(() => lookupContacts(), 250);
  };
  form.elements.type.onchange = () => {
    form.querySelector("#category-select").innerHTML = categoryOptions(
      form.elements.type.value,
      null,
    );
    toggleCategory();
    form.elements.contactId.innerHTML = '<option value="">Sem vínculo</option>';
    form.elements.contactSearch.value = "";
    lookupContacts();
  };
  if (!row) {
    const mode = form.elements.mode,
      count = form.elements.occurrences;
    const label = form.elements.amount.closest("label").firstChild;
    const paidLabel = form.querySelector("#paid").parentElement;
    function preview() {
      const series = mode.value !== "SINGLE";
      form.querySelector("#occurrences-field").hidden = !series;
      count.required = series;
      count.disabled = !series;
      label.textContent =
        mode.value === "INSTALLMENT"
          ? "Valor total (R$)"
          : mode.value === "RECURRING"
            ? "Valor mensal (R$)"
            : "Valor (R$)";
      paidLabel.lastChild.textContent = series
        ? "Primeira ocorrência já paga / recebida"
        : "Já foi pago / recebido";
      const box = form.querySelector("#schedule-preview");
      box.hidden = !series;
      if (!series) return;
      try {
        const rows = monthlyPlan(
          mode.value,
          Number(count.value),
          form.elements.amount.value,
          form.elements.dueDate.value,
        );
        const total = rows.reduce((sum, item) => sum + item.cents, 0);
        box.innerHTML = `<strong>Confira antes de registrar</strong><p>${rows.length} ${mode.value === "INSTALLMENT" ? "parcelas" : "repetições mensais"} · Total: ${money(total / 100)}</p><div class="schedule-rows">${rows.map((item, i) => `<div><span>${i + 1}/${rows.length} · ${dateLabel(item.date)}</span><strong>${money(item.cents / 100)}</strong></div>`).join("")}</div><small>Somente a primeira pode ser marcada como realizada agora. As demais ficam pendentes.</small>`;
      } catch (err) {
        box.textContent = err.message;
      }
    }
    for (const input of [
      mode,
      count,
      form.elements.amount,
      form.elements.dueDate,
    ])
      input.addEventListener("input", preview);
    form.querySelector("#paid").onchange = (ev) => {
      form.querySelector("#payment-field").hidden = !ev.target.checked;
      form.elements.paymentDate.required = ev.target.checked;
    };
    preview();
  }
  lookupContacts();
}
const contactKindLabel = (kind) =>
  ({
    CUSTOMER: "Cliente",
    SUPPLIER: "Fornecedor",
    BOTH: "Cliente e fornecedor",
  })[kind];
const categoryKindLabel = (kind) =>
  ({ INCOME: "Receitas", EXPENSE: "Despesas", BOTH: "Receitas e despesas" })[
    kind
  ];
function directoryEmpty(label, action) {
  return `<div class="empty"><div class="empty-icon">${icon("company")}</div><h3>Nenhum cadastro encontrado</h3><p>Cadastre ${label} ou ajuste os filtros para encontrar outros resultados.</p><button class="btn" data-action="${action}">${icon("plus")}Novo cadastro</button></div>`;
}
function renderContacts() {
  const f = state.directoryFilters,
    result = state.contacts;
  return (
    head(
      "Quem faz parte do seu negócio.",
      "Clientes e fornecedores próximos das suas movimentações.",
      `<button class="btn" data-action="contact">${icon("plus")}Novo contato</button>`,
    ) +
    `<section class="card"><form class="filters" id="directory-filter"><input name="search" type="search" aria-label="Buscar contatos" placeholder="Buscar por nome ou documento…" value="${e(f.search ?? "")}"><select name="kind" aria-label="Tipo de contato"><option value="">Clientes e fornecedores</option><option value="CUSTOMER" ${f.kind === "CUSTOMER" ? "selected" : ""}>Clientes</option><option value="SUPPLIER" ${f.kind === "SUPPLIER" ? "selected" : ""}>Fornecedores</option></select><select name="active" aria-label="Situação do contato"><option value="true" ${f.active === "true" ? "selected" : ""}>Ativos</option><option value="false" ${f.active === "false" ? "selected" : ""}>Inativos</option><option value="" ${f.active === "" ? "selected" : ""}>Todos</option></select><button type="submit" class="btn secondary">Filtrar</button></form>` +
    (result.content.length
      ? `<div class="table-wrap"><table><thead><tr><th>NOME / DOCUMENTO</th><th>TIPO</th><th>CONTATO</th><th>SITUAÇÃO</th><th>AÇÕES</th></tr></thead><tbody>${result.content.map((c) => `<tr><td class="description"><strong>${e(c.name)}</strong><small>${e(c.document ?? "Documento não informado")}</small></td><td>${contactKindLabel(c.kind)}</td><td class="description">${e(c.email ?? "—")}<br><small>${e(c.phone ?? "")}</small></td><td><span class="badge ${c.active ? "" : "cancelled"}">${c.active ? "Ativo" : "Inativo"}</span></td><td><div class="row-actions"><button class="btn secondary small" data-action="edit-contact" data-id="${c.id}" aria-label="Editar ${e(c.name)}">Editar</button><button class="btn text small" data-action="toggle-contact" data-id="${c.id}">${c.active ? "Inativar" : "Reativar"}</button></div></td></tr>`).join("")}</tbody></table></div>`
      : directoryEmpty("seus clientes e fornecedores", "contact")) +
    `<div class="pagination"><span>${result.totalElements} contato(s)</span><div class="row-actions"><button class="btn secondary small" data-action="contacts-prev" ${state.contactPage === 0 ? "disabled" : ""}>Anterior</button><span>${state.contactPage + 1} / ${Math.max(1, result.totalPages)}</span><button class="btn secondary small" data-action="contacts-next" ${state.contactPage + 1 >= result.totalPages ? "disabled" : ""}>Próxima</button></div></div></section>`
  );
}
function renderCategories() {
  return (
    head(
      "Categorias com a sua organização.",
      "Separe receitas e despesas de um jeito que faça sentido para sua empresa.",
      `<button class="btn" data-action="category">${icon("plus")}Nova categoria</button>`,
    ) +
    `<section class="card">${state.categories.length ? `<div class="table-wrap"><table><thead><tr><th>CATEGORIA</th><th>APLICAÇÃO</th><th>SITUAÇÃO</th><th>AÇÕES</th></tr></thead><tbody>${state.categories.map((c) => `<tr><td class="description"><strong>${e(c.name)}</strong></td><td>${categoryKindLabel(c.kind)}</td><td><span class="badge ${c.active ? "" : "cancelled"}">${c.active ? "Ativa" : "Inativa"}</span></td><td><div class="row-actions"><button class="btn secondary small" data-action="edit-category" data-id="${c.id}" aria-label="Editar ${e(c.name)}">Editar</button><button class="btn text small" data-action="toggle-category" data-id="${c.id}">${c.active ? "Inativar" : "Reativar"}</button></div></td></tr>`).join("")}</tbody></table></div>` : directoryEmpty("categorias financeiras", "category")}</section><p class="notice">Categorias e contatos inativos continuam no histórico. Os lançamentos antigos com categoria avulsa permanecem disponíveis.</p>`
  );
}
function contactDialog(row = null) {
  showDialog(
    row ? "Editar contato" : "Novo contato",
    "Organize clientes e fornecedores da sua empresa.",
    field("Nome", "name", "text", 'required maxlength="120"', row?.name ?? "") +
      `<label class="field">Tipo de contato<select name="kind"><option value="CUSTOMER">Cliente</option><option value="SUPPLIER">Fornecedor</option><option value="BOTH">Cliente e fornecedor</option></select></label>` +
      field(
        "CPF / CNPJ (opcional)",
        "document",
        "text",
        'maxlength="18" inputmode="numeric"',
        row?.document ?? "",
      ) +
      field(
        "E-mail (opcional)",
        "email",
        "email",
        'maxlength="255"',
        row?.email ?? "",
      ) +
      field(
        "Telefone (opcional)",
        "phone",
        "tel",
        'maxlength="24"',
        row?.phone ?? "",
      ) +
      `<label class="field">Observações<textarea name="notes" maxlength="1000" rows="3">${e(row?.notes ?? "")}</textarea></label>` +
      (row
        ? `<label class="field">Situação<select name="active"><option value="true">Ativo</option><option value="false">Inativo</option></select></label>`
        : ""),
    async (data) => {
      data.active = row ? data.active === "true" : true;
      await api("/contacts" + (row ? "/" + row.id : ""), {
        method: row ? "PUT" : "POST",
        body: JSON.stringify(data),
      });
      toast("Contato salvo.");
    },
  );
  modal.querySelector("[name=kind]").value = row?.kind ?? "CUSTOMER";
  if (row) modal.querySelector("[name=active]").value = String(row.active);
}
function categoryDialog(row = null) {
  showDialog(
    row ? "Editar categoria" : "Nova categoria",
    "Uma classificação para suas movimentações.",
    field(
      "Nome da categoria",
      "name",
      "text",
      'required maxlength="80"',
      row?.name ?? "",
    ) +
      `<label class="field">Aplicação<select name="kind"><option value="INCOME">Receitas</option><option value="EXPENSE">Despesas</option><option value="BOTH">Receitas e despesas</option></select></label>` +
      (row
        ? `<label class="field">Situação<select name="active"><option value="true">Ativa</option><option value="false">Inativa</option></select></label>`
        : ""),
    async (data) => {
      data.active = row ? data.active === "true" : true;
      await api("/categories" + (row ? "/" + row.id : ""), {
        method: row ? "PUT" : "POST",
        body: JSON.stringify(data),
      });
      toast("Categoria salva.");
    },
  );
  modal.querySelector("[name=kind]").value = row?.kind ?? "INCOME";
  if (row) modal.querySelector("[name=active]").value = String(row.active);
}
function toggleDirectory(row, category) {
  showDialog(
    `${row.active ? "Inativar" : "Reativar"} ${category ? "categoria" : "contato"}?`,
    e(row.name),
    '<p class="notice">O histórico será preservado. Cadastros inativos deixam de aparecer para novos vínculos.</p>',
    async () => {
      const body = category
        ? { name: row.name, kind: row.kind, active: !row.active }
        : {
            name: row.name,
            kind: row.kind,
            document: row.document,
            email: row.email,
            phone: row.phone,
            notes: row.notes,
            active: !row.active,
          };
      await api((category ? "/categories/" : "/contacts/") + row.id, {
        method: "PUT",
        body: JSON.stringify(body),
      });
      toast("Cadastro atualizado.");
    },
    "Confirmar",
  );
}
function transferDialog() {
  if (state.accounts.length < 2) {
    toast("Cadastre pelo menos duas contas para transferir.");
    return;
  }
  showDialog(
    "Transferir entre contas",
    "Sem alterar as receitas e despesas da empresa.",
    accountOptions("sourceId", "Conta de origem") +
      accountOptions("destinationId", "Conta de destino") +
      `<div class="form-row">${field("Valor (R$)", "amount", "number", 'required min="0.01" max="999999999999.99" step="0.01"')}${field("Data", "date", "date", `required max="${localDate()}"`, localDate())}</div>` +
      field(
        "Descrição",
        "description",
        "text",
        'required maxlength="255"',
        "Transferência entre contas",
      ),
    async (data) => {
      await send("/finance/transfers", data);
      toast("Transferência realizada.");
    },
    "Transferir",
  );
  modal.querySelector("[name=destinationId]").selectedIndex = 1;
}
document.addEventListener("click", (ev) => {
  const toggle = ev.target.closest("[data-password]");
  if (toggle) {
    const input = toggle
      .closest("form")
      .querySelector(`[name="${toggle.dataset.password}"]`);
    const visible = input.type === "password";
    input.type = visible ? "text" : "password";
    toggle.textContent = visible ? "Ocultar" : "Mostrar";
    toggle.setAttribute("aria-pressed", String(visible));
    toggle.setAttribute(
      "aria-label",
      `${visible ? "Ocultar" : "Mostrar"} senha`,
    );
  }
  const auth = ev.target.closest("[data-auth]");
  if (auth) {
    accessLink = null;
    renderAuth(auth.dataset.auth);
  }
});
root.addEventListener("click", async (ev) => {
  const nav = ev.target.closest("[data-nav]");
  if (nav) {
    ev.preventDefault();
    closeNavigation();
    navigation.query = "";
    window.scrollTo({ top: 0, behavior: "instant" });
    if (state.page === "subscription" && nav.dataset.nav !== "subscription")
      subscription.welcome = false;
    state.page = nav.dataset.nav;
    state.entryPage = 0;
    state.contactPage = 0;
    state.seriesPage = 0;
    await safeLoad();
    return;
  }
  const btn = ev.target.closest("[data-action]");
  if (!btn) return;
  await act(btn);
});
modal.addEventListener("click", (ev) => {
  if (ev.target.closest("[data-action=close]")) modal.close();
});
async function act(btn) {
  const action = btn.dataset.action;
  if (navigationAction(btn)) return;
  if (action === "theme") return window.fintryTheme.toggle();
  if (await importAction(btn)) return;
  if (await subscriptionAction(btn)) return;
  if (await billingAction(btn)) return;
  if (await operationsAction(btn)) return;
  if (await reconciliationAction(btn)) return;
  if (await planningAction(btn)) return;
  if (await pendingAction(btn)) return;
  if (await commercialAction(btn)) return;
  if (await commerceAction(btn)) return;
  if (await accessAction(btn)) return;
  if (
    !canWrite() &&
    [
      "entry",
      "account",
      "transfer",
      "sample",
      "pay",
      "cancel",
      "edit-entry",
      "stop-series",
      "contact",
      "category",
      "edit-contact",
      "edit-category",
      "toggle-contact",
      "toggle-category",
    ].includes(action)
  ) {
    toast("Seu perfil permite somente consultas.");
    return;
  }
  if (action === "details") return openDetails(btn.dataset.id);
  if (action === "entry") return entryDialog();
  if (action === "report-export")
    return downloadReport(btn.dataset.format, btn);
  if (action === "reports-prev" || action === "reports-next") {
    state.reportPage += action === "reports-next" ? 1 : -1;
    return safeLoad();
  }
  if (action === "series-prev" || action === "series-next") {
    state.seriesPage += action === "series-next" ? 1 : -1;
    return safeLoad();
  }
  if (action === "series-items") {
    try {
      await showSeriesItems(btn.dataset.id);
    } catch (err) {
      toast(err.message);
    }
    return;
  }
  if (action === "stop-series") {
    showDialog(
      "Cancelar pendências da série?",
      "Os pagamentos e recebimentos realizados serão mantidos.",
      '<p class="notice">Todas as ocorrências ainda pendentes desta série serão canceladas, inclusive vencidas. O histórico será preservado.</p>',
      async () => {
        await send("/finance/series/" + btn.dataset.id + "/stop");
        toast("Pendências canceladas. Registros realizados preservados.");
      },
      "Confirmar cancelamento",
    );
    return;
  }
  if (action === "edit-entry")
    return entryDialog(
      state.entries.content.find((r) => r.id === btn.dataset.id) ||
        (detailData?.entry.id === btn.dataset.id ? detailData.entry : null),
    );
  if (action === "contact") return contactDialog();
  if (action === "category") return categoryDialog();
  if (action === "edit-contact")
    return contactDialog(
      state.contacts.content.find((r) => r.id === btn.dataset.id),
    );
  if (action === "edit-category")
    return categoryDialog(
      state.categories.find((r) => r.id === btn.dataset.id),
    );
  if (action === "toggle-contact")
    return toggleDirectory(
      state.contacts.content.find((r) => r.id === btn.dataset.id),
      false,
    );
  if (action === "toggle-category")
    return toggleDirectory(
      state.categories.find((r) => r.id === btn.dataset.id),
      true,
    );
  if (action === "contacts-prev" || action === "contacts-next") {
    state.contactPage += action === "contacts-next" ? 1 : -1;
    return safeLoad();
  }
  if (action === "account") return accountDialog();
  if (action === "transfer") return transferDialog();
  if (action === "prev" || action === "next") {
    state.entryPage += action === "next" ? 1 : -1;
    return safeLoad();
  }
  if (action === "reload") return safeLoad();
  if (action === "pay") {
    showDialog(
      "Registrar baixa",
      "O saldo será atualizado ao confirmar.",
      field(
        "Data do pagamento / recebimento",
        "paymentDate",
        "date",
        `required max="${localDate()}"`,
        localDate(),
      ),
      async (data) => {
        await send(`/finance/entries/${btn.dataset.id}/pay`, data);
        toast("Baixa registrada.");
      },
      "Confirmar baixa",
    );
    return;
  }
  if (action === "cancel") {
    showDialog(
      "Cancelar lançamento?",
      "O registro ficará no histórico como cancelado.",
      '<p class="notice">Se já estiver pago, o valor deixará de compor o saldo. Lançamentos pendentes também podem ser corrigidos pela opção Editar.</p>',
      async () => {
        await send(`/finance/entries/${btn.dataset.id}/cancel`);
        toast("Lançamento cancelado.");
      },
      "Confirmar cancelamento",
    );
    return;
  }
  btn.disabled = true;
  try {
    if (action === "logout") {
      await send("/auth/logout");
      closeDetails();
      state.user = null;
      state.csrf = null;
      state.filters = {};
      state.directoryFilters = { active: "true" };
      state.reportPage = 0;
      state.reportFilters = {
        start: localDate().slice(0, 7) + "-01",
        end: monthEnd(localDate().slice(0, 7)),
        basis: "PAYMENT",
        status: "PAID",
      };
      state.forecastFilters = { days: "90", includeOverdue: "false" };
      state.contactPage = 0;
      state.seriesPage = 0;
      await csrf();
      renderAuth();
    }
  } catch (err) {
    toast(err.message);
  } finally {
    btn.disabled = false;
  }
}
function themeToggle() {
  const dark = document.documentElement.dataset.theme === "dark";
  return `<button type="button" class="btn secondary small theme-toggle" data-action="theme" aria-pressed="${dark}" aria-label="Ativar modo ${dark ? "claro" : "escuro"}">${dark ? "☀ Claro" : "☾ Escuro"}</button>`;
}
window.addEventListener("fintrythemechange", () => {
  const dark = document.documentElement.dataset.theme === "dark";
  document.querySelectorAll("[data-action=theme]").forEach((button) => {
    button.textContent = dark ? "☀ Claro" : "☾ Escuro";
    button.setAttribute("aria-pressed", String(dark));
    button.setAttribute(
      "aria-label",
      `Ativar modo ${dark ? "claro" : "escuro"}`,
    );
  });
});
const drawer = document.querySelector("#details-drawer");
let detailId = null,
  detailData = null,
  detailFiles = [],
  detailRequest = 0,
  removalId = null;
function closeDetails() {
  detailRequest++;
  detailId = null;
  detailData = null;
  detailFiles = [];
  if (drawer.open) drawer.close();
}
drawer.addEventListener("close", () => {
  detailId = null;
  detailData = null;
  detailFiles = [];
  detailRequest++;
  if (!document.activeElement || document.activeElement === document.body)
    document.querySelector(".nav-btn.active")?.focus();
});
const fileSize = (n) =>
  n < 1024 * 1024
    ? `${(n / 1024).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} KB`
    : `${(n / (1024 * 1024)).toLocaleString("pt-BR", { maximumFractionDigits: 1 })} MB`;
async function openDetails(id) {
  detailId = id;
  removalId = null;
  drawer.innerHTML =
    '<div class="drawer-header"><h2 id="drawer-title" tabindex="-1">Detalhes do lançamento</h2><button class="close" data-action="drawer-close" aria-label="Fechar detalhes">×</button></div><p class="muted" role="status">Carregando lançamento…</p>';
  if (!drawer.open) drawer.showModal();
  await loadDetails();
  if (drawer.open && detailId === id)
    drawer.querySelector("[data-action=drawer-close]")?.focus();
}
async function loadDetails() {
  const id = detailId,
    request = ++detailRequest;
  if (!id) return;
  try {
    const [data, files] = await Promise.all([
      api(`/finance/entries/${id}`),
      api(`/finance/entries/${id}/attachments`),
    ]);
    if (request !== detailRequest || !drawer.open || !state.user) return;
    detailData = data;
    detailFiles = files;
    renderDetails();
  } catch (error) {
    if (request !== detailRequest || !drawer.open) return;
    drawer.innerHTML =
      '<div class="drawer-header"><h2 id="drawer-title" tabindex="-1">Detalhes do lançamento</h2><button class="close" data-action="drawer-close" aria-label="Fechar detalhes">×</button></div><p role="alert">' +
      e(error.message) +
      '</p><button class="btn secondary" data-action="drawer-retry">Tentar novamente</button>';
  }
}
function renderDetails() {
  const data = detailData,
    row = data.entry;
  const status =
    row.status === "PAID"
      ? row.type === "INCOME"
        ? "Recebido"
        : "Pago"
      : row.status === "CANCELLED"
        ? "Cancelado"
        : row.dueDate < localDate()
          ? "Em atraso"
          : "Pendente";
  const item = (label, value) =>
    `<div><dt>${label}</dt><dd>${e(value || "—")}</dd></div>`;
  drawer.innerHTML = `<div class="drawer-header"><div><span class="eyebrow">${row.transferId ? "Transferência entre contas" : row.type === "INCOME" ? "Receita" : "Despesa"}</span><h2 id="drawer-title" tabindex="-1">${e(row.description)}</h2></div><button class="close" data-action="drawer-close" aria-label="Fechar detalhes">×</button></div><div class="drawer-amount ${row.type === "INCOME" ? "positive" : "negative"}">${money(row.amount)}</div><span class="badge ${row.status === "PENDING" ? "pending" : row.status === "CANCELLED" ? "cancelled" : ""}">${status}</span><dl class="detail-grid">${item("Conta", data.accountName)}${item("Categoria", row.category)}${item("Cliente / fornecedor", row.contactName)}${item("Vencimento", dateLabel(row.dueDate))}${item("Pagamento / recebimento", dateLabel(row.paymentDate))}${item("Registrado em", dateLabel(data.createdAt?.slice(0, 10)))}${item("Última alteração", dateLabel(data.updatedAt?.slice(0, 10)))}${row.seriesId ? item(row.seriesKind === "INSTALLMENT" ? "Parcela" : "Repetição mensal", row.sequenceNumber + " de " + row.seriesCount) : ""}</dl><div class="drawer-actions">${row.status === "PENDING" ? `<button class="btn" data-action="pay" data-id="${row.id}">${row.type === "INCOME" ? "Receber" : "Pagar"}</button>${!row.seriesId && !row.transferId && !row.saleId ? `<button class="btn secondary" data-action="edit-entry" data-id="${row.id}">Editar lançamento</button>` : ""}` : ""}${row.status !== "CANCELLED" && !row.transferId && !row.saleId ? `<button class="btn text" data-action="cancel" data-id="${row.id}">Cancelar lançamento</button>` : ""}</div>${row.saleId ? `<p class="notice">Receita vinculada a uma venda. Para devolver itens e estornar a receita, cancele pela página Vendas.</p><button class="btn secondary" data-action="sale-details" data-id="${row.saleId}">Abrir venda vinculada</button>` : ""}<section class="attachments-section" aria-labelledby="attachments-title"><div class="card-head"><div><h3 id="attachments-title">Comprovantes</h3><p>${detailFiles.length} de 10 arquivos · PDF, PNG ou JPEG</p></div></div><p class="muted attachment-hint">Os arquivos ficam vinculados somente a este lançamento, inclusive após a baixa ou o cancelamento.</p><div class="attachment-list">${detailFiles.length ? detailFiles.map((file) => `<article class="attachment-item"><div class="attachment-type" aria-hidden="true">${file.contentType === "application/pdf" ? "PDF" : "IMG"}</div><div class="attachment-info"><strong>${e(file.filename)}</strong><small>${fileSize(file.byteCount)} · ${dateLabel(file.createdAt?.slice(0, 10))}<br>Enviado por ${e(file.uploadedByName)}</small></div><div class="attachment-actions"><button class="btn secondary small" data-action="attachment-download" data-file="${file.id}" aria-label="Baixar ${e(file.filename)}">Baixar</button><button class="btn text small" data-action="attachment-remove" data-file="${file.id}" aria-label="Remover ${e(file.filename)}">Remover</button></div>${removalId === file.id ? `<div class="remove-confirm" role="alert"><p>Remover este comprovante? O arquivo será excluído. O lançamento será preservado.</p><button class="btn danger small" data-action="attachment-confirm" data-file="${file.id}">Confirmar remoção</button><button class="btn secondary small" data-action="attachment-keep">Manter arquivo</button></div>` : ""}</article>`).join("") : '<div class="attachment-empty">Nenhum comprovante anexado. Adicione notas, recibos ou comprovantes de pagamento.</div>'}</div>${detailFiles.length < 10 ? `<form id="attachment-form"><label class="field">Adicionar comprovante<input name="file" type="file" required accept="application/pdf,image/png,image/jpeg,.pdf,.png,.jpg,.jpeg" aria-describedby="attachment-help"></label><p id="attachment-help" class="muted attachment-hint">Até 5 MB por arquivo e 10 arquivos por lançamento.</p><p id="attachment-error" class="form-error" role="alert"></p><button type="submit" class="btn">Anexar comprovante</button></form>` : '<p class="notice">Limite de 10 comprovantes atingido. Remova um arquivo para anexar outro.</p>'}</section><div class="drawer-footer">${themeToggle()}<button class="btn secondary" data-action="drawer-close">Fechar detalhes</button></div>`;
  applyPermissions(drawer);
  drawer
    .querySelector("#attachment-form")
    ?.addEventListener("submit", async (event) => {
      event.preventDefault();
      const form = event.currentTarget,
        file = form.elements.file.files[0],
        button = form.querySelector("button"),
        error = form.querySelector("#attachment-error"),
        id = detailId;
      error.textContent = "";
      if (!file) return;
      if (file.size > 5 * 1024 * 1024) {
        error.textContent = "Cada comprovante pode ter até 5 MB.";
        return;
      }
      button.disabled = true;
      button.textContent = "Enviando…";
      try {
        await api(`/finance/entries/${id}/attachments`, {
          method: "POST",
          body: new FormData(form),
        });
        if (id === detailId && drawer.open) {
          await loadDetails();
          drawer.querySelector("#attachment-form button")?.focus();
        }
        toast("Comprovante anexado.");
      } catch (err) {
        if (form.isConnected) error.textContent = err.message;
      } finally {
        button.disabled = false;
        button.textContent = "Anexar comprovante";
      }
    });
}
drawer.addEventListener("click", async (event) => {
  const button = event.target.closest("[data-action]");
  if (!button) return;
  const action = button.dataset.action;
  if (action === "drawer-close") return closeDetails();
  if (action === "drawer-retry") return loadDetails();
  if (action === "attachment-remove" || action === "attachment-keep") {
    removalId = action === "attachment-remove" ? button.dataset.file : null;
    renderDetails();
    drawer
      .querySelector(
        removalId
          ? "[data-action=attachment-confirm]"
          : "#attachment-form button",
      )
      ?.focus();
    return;
  }
  if (action === "attachment-confirm") {
    const id = detailId;
    button.disabled = true;
    try {
      await api(`/finance/entries/${id}/attachments/${button.dataset.file}`, {
        method: "DELETE",
      });
      removalId = null;
      if (id === detailId && drawer.open) {
        await loadDetails();
        (
          drawer.querySelector("#attachment-form button") ||
          drawer.querySelector("[data-action=drawer-close]")
        )?.focus();
      }
      toast("Comprovante removido. Lançamento preservado.");
    } catch (error) {
      toast(error.message);
    } finally {
      button.disabled = false;
    }
    return;
  }
  if (action === "attachment-download") {
    const id = detailId,
      file = detailFiles.find((f) => f.id === button.dataset.file);
    button.disabled = true;
    try {
      const response = await fetch(
        `/api/v1/finance/entries/${id}/attachments/${file.id}`,
      );
      if (!response.ok) {
        if (response.status === 401) {
          closeDetails();
          state.user = null;
          renderAuth();
        }
        const data = await response.json();
        throw new Error(data.message || "Não foi possível baixar o arquivo.");
      }
      const url = URL.createObjectURL(await response.blob()),
        link = document.createElement("a");
      link.href = url;
      link.download = file.filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      setTimeout(() => URL.revokeObjectURL(url), 30000);
    } catch (error) {
      toast(error.message);
    } finally {
      button.disabled = false;
    }
    return;
  }
  await act(button);
});

(async () => {
  try {
    await csrf();
    const app = await api("/app");
    state.version = app.version;
    state.recoveryAvailable = app.passwordRecoveryAvailable;
    if (readAccessLink()) return;
    try {
      state.user = await api("/auth/me");
    } catch {}
    if (state.user) await safeLoad();
    else renderAuth();
  } catch (err) {
    root.innerHTML =
      '<div class="empty"><h1>Vamos tentar de novo?</h1><p>Não foi possível conectar ao Fintry. Recarregue a página para tentar novamente.</p></div>';
    toast(err.message);
  }
})();
