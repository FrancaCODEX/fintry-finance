"use strict";
(() => {
  const system = matchMedia("(prefers-color-scheme: dark)");
  let preference;
  try {
    preference = localStorage.getItem("fintry-theme");
  } catch {}
  const apply = () => {
    const dark =
      preference === "dark" || (preference !== "light" && system.matches);
    document.documentElement.dataset.theme = dark ? "dark" : "light";
    window.dispatchEvent(new Event("fintrythemechange"));
  };
  window.fintryTheme = {
    toggle() {
      preference =
        document.documentElement.dataset.theme === "dark" ? "light" : "dark";
      try {
        localStorage.setItem("fintry-theme", preference);
      } catch {}
      apply();
    },
  };
  system.addEventListener("change", apply);
  window.addEventListener("storage", (event) => {
    if (event.key === "fintry-theme" || event.key === null) {
      preference = event.newValue;
      apply();
    }
  });
  apply();
})();
