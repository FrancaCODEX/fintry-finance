"use strict";
async function loadOperations(id) {
  if (!isAdmin()) {
    state.page = "dashboard";
    return load();
  }
  const result = await api("/operations");
  if (
    id !== requestId ||
    !state.user ||
    !isAdmin() ||
    state.page !== "operations"
  )
    return;
  shell();
  document.querySelector("#page-content").innerHTML =
    head(
      "Tudo pronto para trabalhar.",
      "Acompanhe a disponibilidade do ambiente e confira o envio de e-mail.",
      '<button class="btn secondary" data-action="operations-refresh">Atualizar diagnóstico</button>',
    ) +
    `<section class="card operations-banner"><div class="operations-status-dot ${result.ready ? "is-up" : ""}" aria-hidden="true"></div><div><span class="eyebrow">${result.production ? "AMBIENTE DE PRODUÇÃO" : "AMBIENTE DE AVALIAÇÃO / DESENVOLVIMENTO"}</span><h2>${result.ready ? "Aplicação disponível" : "O ambiente precisa de atenção"}</h2><p>${result.ready ? "O Fintry está pronto e conectado ao banco de dados." : "Peça ao responsável pelo servidor para conferir a disponibilidade do serviço."}</p></div><span class="badge ${result.ready ? "paid" : "pending"}">${result.ready ? "Disponível" : "Verificar"}</span></section><div class="operations-grid"><section class="card operations-card"><div class="operations-card-icon">${icon("wallet")}</div><h2>Dados da empresa</h2><strong>${result.database === "UP" ? "Conexão disponível" : "Conexão indisponível"}</strong><p>Este diagnóstico verifica a conexão. As cópias de segurança e sua restauração são administradas pelo responsável pelo servidor.</p></section><section class="card operations-card"><div class="operations-card-icon">${icon("shield")}</div><h2>Acesso</h2><strong>${result.secureCookies ? "Sessão protegida por HTTPS" : "Configuração local"}</strong><p>${result.secureCookies ? "O cookie de sessão exige uma conexão segura." : "Este ambiente permite acesso HTTP local. A publicação exige HTTPS e cookies seguros."}</p></section><section class="card operations-card"><div class="operations-card-icon">${icon("users")}</div><h2>E-mail</h2><strong>${{ DISABLED: "Envio desabilitado", LOCAL: "Caixa de testes local", SMTP: "SMTP configurado" }[result.mailMode]}</strong><p>${result.mailMode === "LOCAL" ? "As mensagens ficam no capturador local; não são enviadas para a internet." : result.mailMode === "DISABLED" ? "A recuperação por e-mail exige configurar o envio neste ambiente." : "Envie um teste ao seu próprio e-mail para conferir a entrega. Configuração válida não garante que a mensagem chegou."}</p><button class="btn secondary" data-action="operations-mail-test" ${result.mailMode === "DISABLED" ? "disabled" : ""}>Testar meu e-mail</button></section></div><section class="card operations-version"><div><span class="eyebrow">FINTRY</span><h2>Versão ${e(result.version)}</h2></div><p>Processo iniciado em <strong>${timestamp(result.startedAt)}</strong></p></section><p class="notice">Os testes de e-mail são limitados a três por pessoa a cada dez minutos. Este painel não executa backups nem informa que uma cópia foi restaurada.</p>`;
}
async function operationsAction(btn) {
  const action = btn.dataset.action;
  if (!action?.startsWith("operations-")) return false;
  if (!isAdmin()) return true;
  if (action === "operations-refresh") {
    await safeLoad();
    return true;
  }
  if (action === "operations-mail-test") {
    const userId = state.user.id;
    showDialog(
      "Testar seu e-mail",
      "A mensagem será enviada somente para o e-mail do seu acesso.",
      `<p class="notice">Destino: <strong>${e(state.user.email)}</strong></p><p>O teste não altera senha nem dados financeiros. Depois do envio, confira a caixa de entrada e o spam; o aceite do servidor SMTP não confirma a entrega.</p>`,
      async () => {
        if (!state.user || state.user.id !== userId || !isAdmin())
          throw new Error("Sua sessão mudou. Atualize o painel.");
        const result = await send("/operations/mail-test", {});
        if (state.user?.id === userId) toast(result.message);
      },
      "Enviar teste para mim",
    );
  }
  return true;
}
