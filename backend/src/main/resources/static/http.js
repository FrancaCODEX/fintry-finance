"use strict";
// Transport shared by the existing views and React. No DOM rendering belongs here.
let csrfRequest = null;
async function csrf() {
  if (!csrfRequest) {
    csrfRequest = (async () => {
      const response = await fetch("/api/v1/auth/csrf", { credentials: "same-origin", cache: "no-store" });
      if (!response.ok) throw new Error("Não foi possível iniciar a sessão.");
      const token = await response.json();
      if (!token?.headerName || !token?.token) throw new Error("Token de sessão inválido.");
      state.csrf = token;
    })().finally(() => { csrfRequest = null; });
  }
  return csrfRequest;
}
async function api(path, options = {}) {
  if (!path.startsWith("/") || path.startsWith("//")) throw new Error("Caminho de API inválido.");
  const method = (options.method || "GET").toUpperCase();
  const writes = !["GET", "HEAD", "OPTIONS"].includes(method);
  if (writes && !state.csrf) await csrf();
  const headers = { ...options.headers, Accept: "application/json" };
  if (options.body && !(options.body instanceof FormData)) headers["Content-Type"] = "application/json";
  if (writes) headers[state.csrf.headerName] = state.csrf.token;
  let response;
  try {
    response = await fetch("/api/v1" + path, { cache: "no-store", ...options, method, headers, credentials: "same-origin" });
  } catch (error) {
    if (error.name === "AbortError") throw error;
    throw new Error("Sem conexão com o servidor. Tente novamente.");
  }
  let data = null;
  const text = response.status === 204 ? "" : await response.text();
  if (text) {
    try { data = JSON.parse(text); }
    catch { if (response.ok) throw new Error("Resposta inválida do servidor. Tente novamente."); }
  }
  if (!response.ok) {
    if (response.status === 401) {
      state.csrf = null;
      window.dispatchEvent(new CustomEvent("fintry:session-expired"));
    }
    const error = new Error(data?.message || (data?.errors ? Object.values(data.errors).join(" ") : "Não foi possível concluir a operação."));
    error.fields = data?.errors || {};
    error.status = response.status;
    throw error;
  }
  return data;
}
const send = (path, body) => api(path, { method: "POST", body: body ? JSON.stringify(body) : undefined });
