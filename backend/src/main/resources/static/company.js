"use strict";
function renderCompany(data) {
  const disabled = canWrite() ? "" : "disabled";
  return (
    head(
      "Os dados da sua empresa.",
      "Mantenha o nome e os contatos que aparecem no ambiente do Fintry.",
    ) +
    `<section class="company-profile-grid"><article class="card company-profile-card"><div class="card-head"><div><span class="eyebrow">PERFIL DA EMPRESA</span><h2>Identificação e contato</h2><p>Estas informações pertencem à empresa e ficam disponíveis para os relatórios.</p></div>${icon("company")}</div><form id="company-form">${field("Nome fantasia", "tradeName", "text", `required maxlength="255" autocomplete="organization" ${disabled}`, data.tradeName || "")}${field("Razão social", "legalName", "text", `required maxlength="255" ${disabled}`, data.legalName || "")}<div class="form-row">${field("CNPJ ou documento", "document", "text", `required maxlength="20" inputmode="numeric" ${disabled}`, data.document || "")}${field("Telefone", "phone", "tel", `required maxlength="20" autocomplete="tel" ${disabled}`, data.phone || "")}</div>${field("E-mail da empresa", "email", "email", `required maxlength="255" autocomplete="email" ${disabled}`, data.email || "")}<p class="form-error" role="alert"></p>${canWrite() ? '<button class="btn" type="submit">Salvar dados da empresa</button>' : '<p class="notice">Seu acesso permite consultar as informações. Para editá-las, regularize o plano e entre novamente.</p>'}</form></article><aside class="card company-profile-aside"><span class="eyebrow">IDENTIDADE NO FINTRY</span><div class="company-profile-mark">${icon("company")}</div><h2>${e(data.tradeName || data.legalName)}</h2><p>${e(data.legalName)}</p><dl><div><dt>Documento</dt><dd>${e(data.document)}</dd></div><div><dt>Cadastro</dt><dd>${timestamp(data.createdAt)}</dd></div></dl><p class="muted">O nome fantasia também aparece no topo da aplicação. Alterações ficam registradas no histórico da empresa.</p></aside></section>`
  );
}

function bindCompany() {
  const form = document.querySelector("#company-form");
  if (!form || !canWrite()) return;
  form.addEventListener("submit", (event) =>
    submitForm(event, async (data) => {
      const updated = await api("/companies/me", {
        method: "PUT",
        body: JSON.stringify(data),
      });
      state.company = updated;
      state.user.companyName = updated.tradeName || updated.legalName;
      toast("Dados da empresa atualizados.");
      await safeLoad();
    }),
  );
}
