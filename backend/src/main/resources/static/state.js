"use strict";
// Shared application state. Feature modules own their queries and view lifecycle.
function localDate() {
  return new Intl.DateTimeFormat("sv-SE", {
    timeZone: "America/Sao_Paulo",
  }).format(new Date());
}
function monthEnd(month) {
  const [y, m] = month.split("-").map(Number);
  return new Date(Date.UTC(y, m, 0)).toISOString().slice(0, 10);
}
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
  company: null,
  onboardingEntries: null,
  demoPack: null,
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
