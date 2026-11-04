"use strict";
// Incremental boundary: React owns its page DOM; the host owns session, shell and financial dialogs.
const workspaceReady = import("/workspace/fintry.js").then(() => true).catch(() => false);
function restoreWorkspaceLocation() {
  const params = new URLSearchParams(location.search);
  const allowed = new Set(navigationGroups().flatMap(([, pages]) => pages.map(([page]) => page)));
  const page = params.get("page");
  if (allowed.has(page)) state.page = page;
  const month = params.get("month");
  if (/^(20\d{2}|2100)-(0[1-9]|1[0-2])$/.test(month || "")) {
    state.month = month;
    if (state.page === "reports") state.reportFilters = {start:month+"-01",end:monthEnd(month),basis:"PAYMENT",status:"PAID",type:["INCOME","EXPENSE"].includes(params.get("type"))?params.get("type"):""};
  }
}
async function navigateWorkspace(page, month, type) {
  closeNavigation();
  navigation.query = "";
  if (state.page === "subscription" && page !== "subscription") subscription.welcome = false;
  state.page = page;
  if (month) state.month = month;
  state.entryPage = state.contactPage = state.seriesPage = 0;
  if (page === "reports" && month) {
    state.reportFilters = {start: month + "-01", end: monthEnd(month), basis: "PAYMENT", status: "PAID", type: type || ""};
    state.reportPage = 0;
  }
  const url = new URL(location.href);
  url.search = new URLSearchParams({page, month:state.month,...(page === "reports" && type ? {type} : {})}).toString();
  if (url.href !== location.href) history.pushState({}, "", url);
  window.scrollTo({top:0, behavior:"instant"});
  await safeLoad();
}
window.addEventListener("popstate", async () => {
  if (!state.user) return;
  window.FintryWorkspace?.unmount();
  window.FintryWorkspace?.closeFinance();
  modal.close();
  closeDetails();
  state.page = "dashboard";
  restoreWorkspaceLocation();
  await safeLoad();
});
async function mountWorkspace(id) {
  if (!await workspaceReady) {
    // Older views remain available in a source checkout while assets are being built.
    if (state.page === "scenarios") {
      document.querySelector("#page-content").innerHTML = '<section class="empty"><h1>Não foi possível abrir o simulador.</h1><p>Recarregue a página para tentar novamente.</p><button class="btn" data-action="reload">Tentar novamente</button></section>';
      return true;
    }
    return false;
  }
  if (id !== requestId || !state.user) return true;
  shell();
  const container = document.querySelector("#page-content");
  container.innerHTML = "";
  window.FintryWorkspace.mount(container, {
    page:state.page, month:state.month, user:state.user, canWrite:!!canWrite(), isAdmin:!!isAdmin(), request:api,
    canCollect: !!canCollect(), welcome: !!subscription.welcome, stockProduct: commerce.stockProduct,
    companyChanged: (name) => {
      state.user.companyName = name;
      const label = document.querySelector(".company-label > span");
      if (label) label.textContent = name;
    },
    auditLabel: (key) => auditActions[key] || key,
    auditValue,
    hostAction: async (name, args = {}, context = {}) => {
      try {
        if (context.state?.reportFilters) state.reportFilters = context.state.reportFilters;
        if (context.billing) Object.assign(billing, context.billing);
        if (context.subscription) Object.assign(subscription, context.subscription);
        const button = document.createElement("button");
        button.dataset.action = name;
        for (const [key, value] of Object.entries(args)) button.dataset[key] = value;
        await act(button);
      } catch (error) {
        toast(error.message);
      }
    },
    // Seed the existing dialogs with the exact page that React displayed.
    catalogAction: async (page, data, name, rowId) => {
      try {
        if (page !== state.page) return;
        if (page === "products" || page === "sales") commerce[page] = data;
        else if (page === "services" || page === "proposals") commercial[page] = data;
        else state[page] = page === "categories" || page === "accounts" ? data.content : data;
        if (name === "stock-history") {
          const row = data.content.find(item => item.id === rowId);
          if (!row) throw new Error("Produto não encontrado. Atualize a página.");
          commerce.stockProduct = { id: row.id, name: row.name };
          commerce.stockPage = 0;
          await navigateWorkspace("stock");
          return;
        }
        const button = document.createElement("button");
        button.dataset.action = name;
        if (rowId) button.dataset.id = rowId;
        await act(button);
      } catch (error) {
        toast(error.message);
      }
    },
    navigate:navigateWorkspace,
    details:async (entryId) => {try {await openDetails(entryId);}catch(error){toast(error.message);}},
    sync:(data) => Object.assign(state,data),
    action:async (name,row) => {
      try {
        if (["entry","edit-entry","transfer"].includes(name)) {
          [state.accounts,state.categories] = await Promise.all([api("/finance/accounts"),api("/categories")]);
        }
        if (row) state.entries = {...state.entries,content:[row]};
        const button = document.createElement("button");
        button.dataset.action = name;
        if (row) button.dataset.id = row.id;
        await act(button);
      } catch (error) {toast(error.message);}
    },
  });
  return true;
}
