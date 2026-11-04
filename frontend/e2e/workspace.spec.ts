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
  for (const metric of await page
    .locator('.workspace-kpi strong, .workspace-balance>strong')
    .all()) {
    expect(
      await metric.evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
    ).toBeTruthy();
  }
  await page.getByRole('button', { name: 'Linhas', exact: true }).click();
  const path = page.locator('.workspace-cash-chart path').first();
  await expect(path).toHaveAttribute('fill', 'none');
  await page.locator('.workspace-months button').first().focus();
  await expect(path).toBeVisible();
  await page.getByText('Salvar visualização', { exact: true }).click();
  await page.getByLabel('Nome', { exact: true }).fill('Meu fluxo');
  await page.getByRole('button', { name: 'Salvar', exact: true }).click();
  await expect(
    page
      .getByLabel('Visualização salva', { exact: true })
      .locator('option:checked'),
  ).toHaveText('Meu fluxo');
  await page.reload();
  await expect(
    page
      .getByLabel('Visualização salva', { exact: true })
      .locator('option:checked'),
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
    .first()
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
  expect(
    await page
      .locator('.workspace-entry-table')
      .evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
  ).toBeTruthy();
  const firstCard = page.locator('.workspace-entry-table tbody tr').first();
  for (const cell of await firstCard.locator('td').all()) {
    expect(
      await cell.evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
    ).toBeTruthy();
  }
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
test('scenario preview compares exact hypothetical expense without creating entries', async ({
  page,
}) => {
  const before = await (
    await page.request.get('/api/v1/finance/entries?size=1')
  ).json();
  await page.goto('/?page=scenarios');
  await expect(
    page.getByRole('heading', { name: 'E se o caixa mudasse?' }),
  ).toBeVisible();
  await page
    .getByRole('button', { name: '＋ Adicionar hipótese', exact: true })
    .click();
  await page
    .getByLabel('Descrição da hipótese', { exact: true })
    .fill('Despesa hipotética');
  await page.getByLabel('Valor da hipótese', { exact: true }).fill('100,10');
  await page
    .getByRole('button', { name: 'Comparar cenário', exact: true })
    .click();
  await expect(
    page.getByLabel('Diferença no saldo final', { exact: true }),
  ).toHaveText(/-R\$\s*100,10/);
  await expect(
    page
      .locator('.workspace-sources')
      .getByText('Despesa hipotética', { exact: true }),
  ).toBeVisible();
  const after = await (
    await page.request.get('/api/v1/finance/entries?size=1')
  ).json();
  expect(after.totalElements).toBe(before.totalElements);
  await page.screenshot({
    path: 'test-results/scenarios-desktop.png',
    fullPage: true,
  });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy();
});
test('dashboard explains a metric through its underlying payments', async ({
  page,
}) => {
  await page.goto('/?page=dashboard');
  await page.locator('.workspace-kpi.income').click();
  await expect(
    page.getByRole('heading', { name: 'De onde vêm as receitas' }),
  ).toBeVisible();
  await expect(page.locator('.workspace-explanation-value')).toBeVisible();
  await expect(
    page
      .locator('.workspace-explanation-dialog .workspace-sources>div')
      .first(),
  ).toBeVisible();
  await page
    .getByRole('button', { name: 'Fechar explicação', exact: true })
    .click();
  await expect(page.locator('.workspace-explanation-dialog')).toHaveCount(0);
});

test('React finance forms create, edit and partially settle a movement', async ({
  page,
}) => {
  await page.goto('/?page=entries');
  await page.getByRole('button', { name: /Novo lançamento/ }).click();
  const dialog = page.locator('.workspace-form-dialog');
  await dialog.getByLabel('Descrição', { exact: true }).fill('Contrato React');
  await dialog.getByLabel('Valor (R$)', { exact: true }).fill('100,10');
  await dialog
    .getByRole('button', { name: 'Salvar lançamento', exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  await page
    .getByRole('button', { name: 'Editar Contrato React', exact: true })
    .click();
  await dialog
    .getByLabel('Descrição', { exact: true })
    .fill('Contrato React revisado');
  await dialog
    .getByRole('button', { name: 'Salvar lançamento', exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  const row = page.locator('tr').filter({ hasText: 'Contrato React revisado' });
  await row.getByRole('button', { name: 'Receber', exact: true }).click();
  await dialog
    .getByLabel('Valor desta baixa (R$)', { exact: true })
    .fill('40,05');
  await dialog
    .getByRole('button', { name: 'Confirmar baixa', exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  await expect(
    page
      .locator('tr')
      .filter({ hasText: 'Contrato React revisado' })
      .locator('.workspace-status'),
  ).toHaveText('Parcial');
});
test('payment retry keeps its identity and does not lose the typed value', async ({
  page,
}) => {
  await page.goto('/?page=entries');
  await page
    .getByRole('button', {
      name: /Registrar recebimento|Registrar pagamento|Receber|Pagar/,
      exact: true,
    })
    .first()
    .click();
  const dialog = page.locator('.workspace-form-dialog');
  await dialog
    .getByLabel('Valor desta baixa (R$)', { exact: true })
    .fill('1,23');
  const keys: string[] = [];
  await page.route('**/api/v1/finance/entries/*/pay', async (route) => {
    keys.push(route.request().postDataJSON().requestId);
    if (keys.length === 1)
      await route.fulfill({
        status: 503,
        contentType: 'application/json',
        body: JSON.stringify({ message: 'Tente novamente.' }),
      });
    else await route.continue();
  });
  await dialog
    .getByRole('button', { name: 'Confirmar baixa', exact: true })
    .click();
  await expect(dialog.getByRole('alert')).toContainText('Tente novamente.');
  await expect(
    dialog.getByLabel('Valor desta baixa (R$)', { exact: true }),
  ).toHaveValue('1,23');
  await dialog
    .getByRole('button', { name: 'Confirmar baixa', exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  expect(keys).toHaveLength(2);
  expect(keys[0]).toBe(keys[1]);
});
test('installment form previews every cent and fits on a phone', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/?page=entries');
  await page.getByRole('button', { name: /Novo lançamento/ }).click();
  const dialog = page.locator('.workspace-form-dialog');
  await dialog
    .getByLabel('Formato', { exact: true })
    .selectOption('INSTALLMENT');
  await dialog.getByLabel('Valor total (R$)', { exact: true }).fill('100,00');
  await dialog
    .getByLabel('Quantidade de ocorrências', { exact: true })
    .fill('3');
  await expect(dialog.locator('.workspace-schedule')).toContainText('33,34');
  await expect(dialog.locator('.workspace-schedule')).toContainText('33,33');
  expect(
    await dialog.evaluate((el) => el.scrollWidth <= el.clientWidth + 1),
  ).toBeTruthy();
  await page.screenshot({ path: 'test-results/finance-form-mobile.png' });
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
});

test('saved planning survives reload, duplicates and keeps calculation history', async ({
  page,
}) => {
  await page.goto('/?page=scenarios');
  const planning = page.locator('.workspace-saved-planning');
  await planning.locator('summary').click();
  await planning
    .getByLabel('Nome do cenário', { exact: true })
    .fill('Plano de contratação');
  await planning
    .getByRole('button', { name: 'Salvar cenário e cálculo', exact: true })
    .click();
  await expect(planning.getByRole('status').first()).toContainText(
    'Cenário e cálculo salvos',
  );
  await expect(page).toHaveURL(/scenario=/);
  await page.reload();
  await expect(
    planning.getByLabel('Nome do cenário', { exact: true }),
  ).toHaveValue('Plano de contratação');
  await planning
    .getByRole('button', { name: 'Recalcular e registrar', exact: true })
    .click();
  await expect(planning.getByRole('status').first()).toContainText(
    'Novo cálculo salvo',
  );
  await expect(
    planning.locator('.workspace-run-history .workspace-saved-list>button'),
  ).toHaveCount(2);
  await planning
    .getByRole('button', { name: 'Duplicar cenário', exact: true })
    .click();
  await expect(
    planning.getByLabel('Nome do cenário', { exact: true }),
  ).toHaveValue('Plano de contratação — cópia');
});
