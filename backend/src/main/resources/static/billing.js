"use strict";
const billing = { page: 0, data: null, config: null };
function resetBilling() {
  billing.page = 0;
  billing.data = null;
  billing.config = null;
}
const canCollect = () => state.user?.permissions?.includes("WRITE_FINANCE");
const billingLabels = {
  ISSUING: "Emitindo",
  PENDING: "Aguardando pagamento",
  OVERDUE: "Vencida",
  CONFIRMED: "Aguardando crédito",
  RECEIVED: "Recebida",
  TEST_RECEIVED: "Recebida em teste",
  CANCELLED: "Cancelada",
  REVIEW: "Revisar no Asaas",
};
async function loadBilling(id) {
  const [config, data] = await Promise.all([
    api("/billing/connection"),
    api("/billing?page=" + billing.page),
  ]);
  if (id !== requestId || !state.user || state.page !== "billing") return;
  billing.config = config;
  billing.data = data;
  shell();
  const sandbox = config.environment !== "PRODUCTION";
  document.querySelector("#page-content").innerHTML =
    head(
      "Receber ficou mais simples.",
      "Pix e boleto vinculados às suas receitas, do envio à confirmação.",
      `${isAdmin() ? '<button class="btn secondary" data-action="billing-config">Conexão Asaas</button>' : ""}${canWrite() ? `<button class="btn" data-action="billing-new" ${!config.configured ? "disabled" : ""}>${icon("plus")} Nova cobrança</button>` : ""}`,
    ) +
    `<section class="card billing-hero"><div class="billing-hero-symbol" aria-hidden="true">${icon("wallet")}</div><div><span class="eyebrow">COBRANÇAS · ASAAS</span><h2>${!config.configured ? "Conecte sua conta para começar" : sandbox ? "Experimente antes de cobrar" : "Conta configurada para receber"}</h2><p>${!config.configured ? "O administrador pode conectar a empresa e escolher o ambiente de testes ou produção." : sandbox ? "Este é um ambiente de testes. Pagamentos simulados não alteram saldos, receitas ou tarifas." : "A baixa acontece quando o valor fica disponível no Asaas. A tarifa é registrada separadamente na mesma conta do lançamento."}</p></div><span class="badge ${sandbox ? "pending" : "paid"}">${!config.configured ? "Não conectado" : sandbox ? "Testes" : "Produção"}</span></section>` +
    `<section class="card billing-register"><div class="billing-register-head"><div><h2>Suas cobranças</h2><p class="muted">${data.totalElements} registro(s) · até 20 por página</p></div><button class="btn secondary small" data-action="billing-refresh">Atualizar lista</button></div><div class="billing-list">${data.content.map((c) => `<article class="billing-item"><div class="billing-item-main"><span class="billing-method">${c.method === "PIX" ? "Pix" : "Boleto"}</span><h3>${e(c.description)}</h3><small>Vencimento ${dateLabel(c.dueDate)} · ${c.environment === "SANDBOX" ? "Teste" : "Produção"}</small><span class="badge ${["RECEIVED", "TEST_RECEIVED"].includes(c.status) ? "paid" : ["REVIEW", "OVERDUE"].includes(c.status) ? "cancelled" : "pending"}">${e(billingLabels[c.status])}</span></div><div class="billing-item-value"><strong>${money(c.amount)}</strong>${c.netAmount != null ? `<small>Líquido ${money(c.netAmount)}</small>` : ""}<button class="btn secondary small" data-action="billing-details" data-id="${c.id}">Ver cobrança →</button></div>${c.message ? `<p class="billing-message">${e(c.message)}</p>` : ""}</article>`).join("") || '<div class="empty"><h3>Sua próxima receita começa aqui</h3><p>Cadastre uma receita pendente com cliente e CPF/CNPJ. Depois, emita Pix ou boleto por esta página.</p></div>'}</div><div class="pagination"><button class="btn secondary small" data-action="billing-prev" ${!data.number ? "disabled" : ""}>Anterior</button><span>${data.number + 1} / ${Math.max(1, data.totalPages)}</span><button class="btn secondary small" data-action="billing-next" ${data.number + 1 >= data.totalPages ? "disabled" : ""}>Próxima</button></div></section><p class="notice">Os links são compartilhados por você. O Fintry não envia avisos automáticos aos clientes. Para cobranças sem confirmação ou divergentes, use Consultar Asaas antes de qualquer nova tentativa.</p>`;
}
async function billingActionInternal(button) {
  const action = button.dataset.action;
  if (!action?.startsWith("billing-")) return false;
  const session = state.user?.id;
  const sameSession = () => {
    if (!session || state.user?.id !== session)
      throw new Error("Sua sessão mudou. Atualize a página.");
  };
  if (action === "billing-prev" || action === "billing-next") {
    billing.page = Math.max(
      0,
      billing.page + (action === "billing-prev" ? -1 : 1),
    );
    await safeLoad();
  }
  if (action === "billing-refresh") await safeLoad();
  if (action === "billing-config" && isAdmin()) {
    const c = billing.config;
    const frozen = c.locked && c.environment === "PRODUCTION";
    const webhook = c.webhookId
      ? location.origin + "/webhooks/asaas/" + c.webhookId
      : null;
    showDialog(
      "Conexão com o Asaas",
      "Cada empresa usa sua própria conta e suas próprias credenciais.",
      `<p class="notice">${c.available ? "As chaves serão protegidas no servidor e nunca aparecerão novamente neste formulário." : "O servidor precisa configurar BILLING_MASTER_KEY antes de conectar. Consulte o responsável pelo ambiente."}</p>${webhook ? `<label class="field">URL do webhook<input readonly value="${e(webhook)}"></label><p class="notice">Configure esta URL no painel Asaas com o mesmo token informado abaixo e os eventos de cobrança. Use HTTPS na instalação publicada.</p>` : ""}${frozen ? '<p class="notice">Esta conexão possui cobranças. Conta, ambiente e credenciais ficam preservados nesta versão para manter os vínculos existentes.</p>' : `<label class="field">Ambiente<select name="environment"><option value="SANDBOX" ${c.locked ? "disabled" : ""} ${!c.locked && c.environment !== "PRODUCTION" ? "selected" : ""}>Testes (Sandbox)</option><option value="PRODUCTION" ${c.locked || c.environment === "PRODUCTION" ? "selected" : ""}>Produção — cobranças reais</option></select></label>${field("Chave de API do Asaas", "apiKey", "password", 'required minlength="10" maxlength="2000" autocomplete="off"')}${field("Token exclusivo do webhook", "webhookToken", "password", 'required minlength="32" maxlength="200" pattern="[A-Za-z0-9_-]{32,200}" autocomplete="off"')}<p class="notice">Use de 32 a 200 letras, números, _ ou -. Este token deve ser diferente da chave da API. Guarde-o para configurar o webhook no Asaas.</p><label class="check"><input type="checkbox" name="consent" required> Entendo que Produção emite cobranças reais e vincula permanentemente esta conexão após a primeira emissão.</label>`}`,
      async (data) => {
        sameSession();
        if (frozen) return;
        if (!c.available)
          throw new Error(
            "Configure a chave de proteção no servidor primeiro.",
          );
        if (!data.consent) throw new Error("Confirme o ambiente escolhido.");
        await api("/billing/connection", {
          method: "PUT",
          body: JSON.stringify({
            environment: data.environment,
            apiKey: data.apiKey,
            webhookToken: data.webhookToken,
          }),
        });
        toast("Conexão salva. Abra novamente para copiar a URL do webhook.");
      },
      frozen ? "Concluir" : "Salvar conexão",
    );
  }
  if (action === "billing-new" && canWrite()) {
    const result = await api(
      "/finance/entries?type=INCOME&status=PENDING&size=100",
    );
    sameSession();
    const pending = result.content.filter((x) => x.contactId && !x.transferId);
    showDialog(
      "Nova cobrança",
      "Escolha a receita e a forma de pagamento.",
      `<p class="notice">${billing.config.environment === "PRODUCTION" ? "Produção: esta ação cria uma cobrança real na sua conta Asaas." : "Testes: a cobrança será simulada e não dará baixa na receita."} A conta do lançamento deve representar o saldo no Asaas, antes de uma transferência ao banco.</p><label class="field">Receita pendente<select name="entryId" required><option value="">Selecione uma receita</option>${pending.map((x) => `<option value="${x.id}">${e(x.description)} · ${money(x.amount)} · ${e(x.contactName)}</option>`).join("")}</select></label><label class="field">Forma de pagamento<select name="method"><option value="PIX">Pix</option><option value="BOLETO">Boleto</option></select></label><p class="notice">Mostrando até 100 receitas pendentes com cliente vinculado. O cliente precisa ter CPF/CNPJ e o vencimento deve ser hoje ou futuro. Há uma única emissão por lançamento nesta versão.</p>`,
      async (data) => {
        sameSession();
        const c = await send("/billing", data);
        toast(
          c.status === "REVIEW"
            ? "Emissão sem confirmação. Consulte a cobrança antes de continuar."
            : "Cobrança registrada.",
        );
      },
      "Emitir cobrança",
    );
  }
  if (action === "billing-details") {
    const c = billing.data.content.find((x) => x.id === button.dataset.id);
    if (!c) return true;
    showDialog(
      "Detalhes da cobrança",
      `${c.method === "PIX" ? "Pix" : "Boleto"} · ${billingLabels[c.status]}`,
      `<div class="billing-detail-amount">${money(c.amount)}<small>${e(c.description)}</small></div><dl class="billing-facts"><div><dt>Vencimento</dt><dd>${dateLabel(c.dueDate)}</dd></div><div><dt>Ambiente</dt><dd>${c.environment === "SANDBOX" ? "Testes" : "Produção"}</dd></div><div><dt>Crédito</dt><dd>${c.settledDate ? dateLabel(c.settledDate) : "Aguardando"}</dd></div><div><dt>Valor líquido</dt><dd>${c.netAmount != null ? money(c.netAmount) : "Aguardando"}</dd></div></dl>${c.message ? `<p class="notice">${e(c.message)}</p>` : ""}<div class="billing-detail-actions">${c.invoiceUrl ? `<a class="btn secondary" href="${e(c.invoiceUrl)}" target="_blank" rel="noopener noreferrer">Abrir fatura ↗</a>` : ""}${c.bankSlipUrl ? `<a class="btn secondary" href="${e(c.bankSlipUrl)}" target="_blank" rel="noopener noreferrer">Abrir boleto ↗</a>` : ""}<button type="button" class="btn text" data-action="billing-entry" data-id="${c.entryId}">Ver lançamento</button>${canCollect() && c.method === "PIX" && ["PENDING", "OVERDUE"].includes(c.status) ? `<button type="button" class="btn secondary" data-action="billing-pix" data-id="${c.id}">Pix copia e cola</button>` : ""}${canCollect() && ["PENDING", "OVERDUE"].includes(c.status) ? `<button type="button" class="btn text danger" data-action="billing-cancel" data-id="${c.id}">Cancelar cobrança</button>` : ""}</div>`,
      async () => {
        sameSession();
        if (canCollect()) {
          await send(`/billing/${c.id}/refresh`);
          toast("Consulta concluída.");
        }
      },
      canCollect() ? "Consultar Asaas" : "Concluir",
    );
  }
  if (action === "billing-entry") {
    modal.close();
    await openDetails(button.dataset.id);
  }
  if (action === "billing-cancel" && canCollect()) {
    modal.close();
    showDialog(
      "Cancelar cobrança?",
      "O cancelamento será solicitado ao Asaas.",
      "<p>O lançamento financeiro será preservado. A cobrança precisa estar pendente ou vencida; recebimentos confirmados exigem conferência no Asaas.</p>",
      async () => {
        sameSession();
        const c = await send(`/billing/${button.dataset.id}/cancel`);
        toast(
          c.status === "CANCELLED"
            ? "Cancelamento confirmado."
            : "Consulte a situação da cobrança.",
        );
      },
      "Cancelar no Asaas",
    );
  }
  if (action === "billing-pix" && canCollect()) {
    const result = await send(`/billing/${button.dataset.id}/pix`);
    sameSession();
    modal.close();
    showDialog(
      "Pix copia e cola",
      "Copie o código completo e compartilhe com o cliente.",
      `<label class="field">Código Pix<textarea readonly rows="5">${e(result.payload)}</textarea></label><p class="notice">O pagamento só será confirmado após consulta ao Asaas ou recebimento do webhook.</p>`,
      async () => {},
      "Concluir",
    );
  }
  return true;
}

document.addEventListener("click", async (event) => {
  const button = event.target.closest("#modal [data-action]");
  if (!button?.dataset.action?.startsWith("billing-")) return;
  button.disabled = true;
  try {
    await billingAction(button);
  } catch (error) {
    toast(error.message);
  } finally {
    if (button.isConnected) button.disabled = false;
  }
});

async function billingAction(button) {
  try {
    return await billingActionInternal(button);
  } catch (error) {
    toast(error.message);
    return true;
  }
}
