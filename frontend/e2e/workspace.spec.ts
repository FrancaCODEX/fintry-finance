import { test, expect } from '@playwright/test';
let sequence = 0;
test.beforeEach(async ({ page }) => {
  const email = `workspace-${Date.now()}-${sequence++}@example.com`;
  async function token() {
    const r = await page.request.get('/api/v1/auth/csrf');
    expect(r.ok()).toBeTruthy();
    return r.json();
  }
  let csrf = await token();
  const registered = await page.request.post('/api/v1/auth/register', {
    headers: { [csrf.headerName]: csrf.token },
    data: {
      name: 'Pessoa demonstração',
      companyName: 'Estúdio Aurora',
      document:
        String(Date.now()).padStart(14, '1').slice(0, 12) +
        String(sequence).padStart(2, '0'),
      email,
      password: 'Senha-segura-123',
    },
  });
  expect(registered.status()).toBe(201);
  csrf = await token();
  const logged = await page.request.post('/api/v1/auth/login', {
    headers: { [csrf.headerName]: csrf.token },
    data: { email, password: 'Senha-segura-123' },
  });
  expect(logged.ok()).toBeTruthy();
  csrf = await token();
  const sample = await page.request.post('/api/v1/finance/sample', {
    headers: { [csrf.headerName]: csrf.token },
  });
  expect(sample.status()).toBe(201);
});
test('React dashboard keeps line series visible and saves a named view', async ({
  page,
}) => {
  await page.goto('/?page=dashboard');
  await expect(
    page.getByRole('heading', { name: 'Seu caixa, com contexto.' }),
  ).toBeVisible();
  await page.getByRole('button', { name: 'Linhas', exact: true }).click();
  const path = page.locator('.workspace-cash-chart path').first();
  await expect(path).toHaveAttribute('fill', 'none');
  await page.locator('.workspace-months button').first().focus();
  await expect(path).toBeVisible();
  await page.getByText('Salvar visualização', { exact: true }).click();
  await page.getByLabel('Nome', { exact: true }).fill('Meu fluxo');
  await page.getByRole('button', { name: 'Salvar', exact: true }).click();
  await expect(
    page.getByLabel('Visualização', { exact: true }).locator('option:checked'),
  ).toHaveText('Meu fluxo');
  await page.reload();
  await expect(
    page.getByLabel('Visualização', { exact: true }).locator('option:checked'),
  ).toHaveText('Meu fluxo');
  await page.screenshot({
    path: 'test-results/dashboard-desktop.png',
    fullPage: true,
  });
});
test('entry filters survive reload and details preserve the current query', async ({
  page,
}) => {
  await page.goto('/?page=entries');
  await expect(
    page.getByRole('heading', { name: 'Cada movimento, no lugar.' }),
  ).toBeVisible();
  await page.getByLabel('Situação', { exact: true }).selectOption('PARTIAL');
  await page
    .getByRole('button', { name: 'Aplicar filtros', exact: true })
    .click();
  await expect(page).toHaveURL(/status=PARTIAL/);
  await expect(page.locator('.workspace-status').first()).toHaveText('Parcial');
  await page.reload();
  await expect(page.getByLabel('Situação', { exact: true })).toHaveValue(
    'PARTIAL',
  );
  await page
    .getByRole('button', { name: /Detalhes de/ })
    .first()
    .click();
  await expect(page.locator('#details-drawer')).toBeVisible();
  await page
    .getByRole('button', { name: 'Fechar detalhes', exact: true })
    .click();
  await expect(page).toHaveURL(/status=PARTIAL/);
  await page
    .getByRole('button', { name: 'Limpar filtros', exact: true })
    .click();
  await page.goBack();
  await expect(page.getByLabel('Situação', { exact: true })).toHaveValue(
    'PARTIAL',
  );
});
test('mobile entries remain within the viewport and session expiration returns to login', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/?page=entries');
  await expect(
    page.locator('.workspace-entry-table tbody tr').first(),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
  await page.screenshot({
    path: 'test-results/entries-mobile.png',
    fullPage: true,
  });
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json();
  await page.request.post('/api/v1/auth/logout', {
    headers: { [csrf.headerName]: csrf.token },
  });
  await page.reload();
  await expect(page.locator('#auth-form')).toBeVisible();
});
