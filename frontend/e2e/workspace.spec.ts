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
  // Fail at startup instead of timing out separately on every missing control.
  await page.goto('/');
  await expect(
    page.getByRole('heading', {
      name: 'Seu caixa, com contexto.',
      exact: true,
    }),
  ).toBeVisible();
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
  await page
    .locator('#page-content')
    .getByRole('button', { name: /Novo lançamento/ })
    .click();
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
  await page
    .locator('#page-content')
    .getByRole('button', { name: /Novo lançamento/ })
    .click();
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

test('reconciliation explains a reusable rule and confirms the link explicitly', async ({
  page,
}) => {
  const accounts = await (
    await page.request.get('/api/v1/finance/accounts')
  ).json();
  const bank = accounts.find(
    (a: { name: string }) => a.name === '[Exemplo] Banco principal',
  );
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json();
  const date = new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'America/Sao_Paulo',
  }).format(new Date());
  const created = await page.request.post('/api/v1/finance/entries', {
    headers: { [csrf.headerName]: csrf.token },
    data: {
      accountId: bank.id,
      description: 'Assinatura internet',
      category: 'Internet',
      type: 'EXPENSE',
      amount: '312.55',
      dueDate: date,
      paid: false,
    },
  });
  expect(created.status()).toBe(201);
  await page.goto('/?page=reconciliation');
  await expect(
    page.getByRole('heading', {
      name: 'Encontre o vínculo. Confira os motivos.',
    }),
  ).toBeVisible();
  await page.locator('.workspace-rules>summary').click();
  await page.getByRole('button', { name: 'Criar regra', exact: true }).click();
  const dialog = page.locator('.workspace-form-dialog');
  await dialog.getByLabel('Nome da regra', { exact: true }).fill('Internet');
  await dialog
    .getByLabel('Texto que aparece no banco', { exact: true })
    .fill('COMPRA');
  await dialog
    .getByLabel('Texto que aparece no lançamento', { exact: true })
    .fill('Assinatura');
  await dialog
    .getByRole('button', { name: 'Salvar regra', exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  const movement = page
    .locator('.workspace-rec-movement')
    .filter({ hasText: 'COMPRA SEM LANCAMENTO PARA REVISAR' });
  await expect(movement.locator('.workspace-rec-suggestions')).toContainText(
    'Internet',
  );
  await movement
    .getByRole('button', { name: 'Revisar correspondências', exact: true })
    .click();
  await dialog.locator('input[name=entryId]').first().check();
  await dialog
    .getByRole('button', { name: 'Confirmar decisão', exact: true })
    .click();
  await expect(dialog).toHaveCount(0);
  await page.getByRole('button', { name: /Conciliados/ }).click();
  await expect(
    page
      .locator('.workspace-rec-movement')
      .filter({ hasText: 'COMPRA SEM LANCAMENTO PARA REVISAR' }),
  ).toContainText('Justificativas no momento da confirmação');
  await page.screenshot({
    path: 'test-results/reconciliation-assisted.png',
    fullPage: true,
  });
});

test('catalog filters survive reload and product actions retain the correct row', async ({
  page,
}) => {
  await page.goto('/?page=products');
  await expect(page.locator('.workspace-record').first()).toBeVisible();
  const product = page.locator('.workspace-record').first();
  const name = (await product.locator('h2').innerText()).trim();
  await page.getByLabel('Buscar por nome ou referência').fill(name);
  await page
    .getByRole('button', { name: 'Aplicar filtros', exact: true })
    .click();
  await expect(page).toHaveURL(/search=/);
  await page.reload();
  await expect(page.getByLabel('Buscar por nome ou referência')).toHaveValue(
    name,
  );
  await page
    .getByRole('button', { name: `Editar ${name}`, exact: true })
    .click();
  await expect(page.locator('#modal')).toBeVisible();
  await expect(page.locator('#modal [name=name]')).toHaveValue(name);
  await page
    .locator('#modal')
    .getByRole('button', { name: 'Fechar', exact: true })
    .click();
  await page
    .getByRole('button', { name: 'Histórico de estoque', exact: true })
    .first()
    .click();
  await expect(page).toHaveURL(/page=stock/);
  await page.goBack();
  await expect(page.getByLabel('Buscar por nome ou referência')).toHaveValue(
    name,
  );
});

test('commercial and administration pages render on mobile in both themes', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  for (const route of [
    'products',
    'services',
    'sales',
    'proposals',
    'contacts',
    'categories',
    'team',
    'accounts',
  ]) {
    await page.goto('/?page=' + route);
    await expect(page.locator('.workspace-record').first()).toBeVisible();
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBeTruthy();
    await page.evaluate(
      () => (document.documentElement.dataset.theme = 'dark'),
    );
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBeTruthy();
    await page.getByRole('button', { name: 'Abrir menu', exact: true }).click();
    await expect(page.locator('#navigation-dialog')).toBeVisible();
    await expect(
      page.locator('#navigation-dialog [aria-current=page]'),
    ).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.locator('#navigation-dialog')).not.toBeVisible();
  }
  await page.screenshot({
    path: 'test-results/catalog-mobile-dark.png',
    fullPage: true,
  });
});

test('proposal status uses the backend enum and filtered empty states can recover', async ({
  page,
}) => {
  await page.goto('/?page=proposals&status=OPEN');
  await expect(page.getByLabel('Situação', { exact: true })).toHaveValue(
    'OPEN',
  );
  await expect(page.locator('.workspace-record').first()).toBeVisible();
  await expect(
    page.locator('.workspace-record .workspace-status').first(),
  ).toHaveText('Em negociação');
  await page.getByLabel('Buscar por nome ou referência').fill('no-match-0-39');
  await page
    .getByRole('button', { name: 'Aplicar filtros', exact: true })
    .click();
  await expect(
    page.getByRole('heading', { name: 'Nenhum registro encontrado' }),
  ).toBeVisible();
  await page
    .getByRole('button', { name: 'Limpar filtros', exact: true })
    .click();
  await expect(page.locator('.workspace-record').first()).toBeVisible();
});

test('every menu page is owned by React and loads without query errors', async ({
  page,
}) => {
  test.setTimeout(180000);
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const routes = [
    'dashboard',
    'entries',
    'pending',
    'accounts',
    'reconciliation',
    'billing',
    'budgets',
    'reports',
    'forecast',
    'scenarios',
    'series',
    'imports',
    'products',
    'services',
    'proposals',
    'stock',
    'sales',
    'contacts',
    'categories',
    'company',
    'team',
    'audit',
    'operations',
    'subscription',
    'security',
    'examples',
  ];
  for (const route of routes) {
    await page.goto('/?page=' + route);
    const root = page.locator('#page-content > .fintry-workspace');
    await expect(root).toBeVisible();
    await expect(root.locator('h1')).toBeVisible();
    await expect(root.locator('.workspace-loading')).toHaveCount(0);
    await expect(root.locator('.workspace-message[role=alert]')).toHaveCount(0);
  }
  expect(errors).toEqual([]);
});

test('remaining pages fit on a phone including reports, audit and plan usage', async ({
  page,
}) => {
  test.setTimeout(180000);
  await page.setViewportSize({ width: 390, height: 844 });
  for (const route of [
    'pending',
    'reports',
    'forecast',
    'series',
    'budgets',
    'imports',
    'stock',
    'audit',
    'company',
    'security',
    'operations',
    'examples',
    'billing',
    'subscription',
  ]) {
    await page.goto('/?page=' + route);
    await expect(page.locator('#page-content .fintry-workspace')).toBeVisible();
    await expect(page.locator('.workspace-loading')).toHaveCount(0);
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
      route,
    ).toBeTruthy();
  }
  await page.screenshot({
    path: 'test-results/subscription-mobile.png',
    fullPage: true,
  });
});

test('CSV preview and confirmation preserve selection and avoid duplicate imports', async ({
  page,
}) => {
  await page.goto('/?page=imports');
  await expect(page.getByLabel('Arquivo CSV', { exact: true })).toBeVisible();
  const csv =
    'descricao;tipo;valor;vencimento;categoria\nImportação React 039;receita;123,45;2026-12-20;Outros\n';
  await page.getByLabel('Arquivo CSV', { exact: true }).setInputFiles({
    name: 'financeiro.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(csv, 'utf8'),
  });
  await page.getByRole('button', { name: 'Gerar prévia', exact: true }).click();
  await expect(
    page.getByRole('heading', {
      name: 'Confira antes de importar',
      exact: true,
    }),
  ).toBeVisible();
  await expect(
    page.getByLabel('Selecionar registro 2', { exact: true }),
  ).toBeChecked();
  await page
    .getByRole('button', { name: 'Revisar e importar', exact: true })
    .click();
  await page
    .getByRole('button', { name: 'Confirmar importação', exact: true })
    .click();
  await expect(
    page.getByRole('heading', {
      name: '1 lançamento(s) importado(s).',
      exact: true,
    }),
  ).toBeVisible();
  await page
    .getByRole('button', { name: 'Importar outro arquivo', exact: true })
    .click();
  await page.getByLabel('Arquivo CSV', { exact: true }).setInputFiles({
    name: 'financeiro.csv',
    mimeType: 'text/csv',
    buffer: Buffer.from(csv, 'utf8'),
  });
  await page.getByRole('button', { name: 'Gerar prévia', exact: true }).click();
  await expect(
    page.getByLabel('Selecionar registro 2', { exact: true }),
  ).toBeDisabled();
  await expect(
    page.getByRole('button', { name: 'Revisar e importar', exact: true }),
  ).toBeDisabled();
});

test('report filters persist, company editing saves and pending payments use React forms', async ({
  page,
}) => {
  await page.goto('/?page=reports');
  await page.getByLabel('Base do período', { exact: true }).selectOption('DUE');
  await page.getByLabel('Situação', { exact: true }).selectOption('PENDING');
  await page
    .getByRole('button', { name: 'Aplicar filtros', exact: true })
    .click();
  await expect(page).toHaveURL(/basis=DUE/);
  await page.reload();
  await expect(page.getByLabel('Situação', { exact: true })).toHaveValue(
    'PENDING',
  );
  await page.goto('/?page=company');
  await page
    .getByLabel('Nome fantasia', { exact: true })
    .fill('Estúdio Aurora React');
  await page
    .getByLabel('Razão social', { exact: true })
    .fill('Estúdio Aurora Ltda');
  await page
    .getByLabel('CNPJ ou documento', { exact: true })
    .fill('12345678000199');
  await page.getByLabel('Telefone', { exact: true }).fill('11999998888');
  await page
    .getByLabel('E-mail da empresa', { exact: true })
    .fill('empresa@example.com');
  await page
    .getByRole('button', { name: 'Salvar dados da empresa', exact: true })
    .click();
  await expect(
    page.getByText('Dados da empresa atualizados.', { exact: true }),
  ).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Nome fantasia', { exact: true })).toHaveValue(
    'Estúdio Aurora React',
  );
  await page.goto('/?page=pending');
  await page
    .locator('#page-content')
    .getByRole('button', { name: /^(Pagar|Receber)$/ })
    .first()
    .click();
  await expect(page.locator('.workspace-form-dialog')).toBeVisible();
});

test('React budget editor creates, changes and removes only the selected limit', async ({
  page,
}) => {
  const csrf = await (await page.request.get('/api/v1/auth/csrf')).json();
  const created = await page.request.post('/api/v1/categories', {
    headers: { [csrf.headerName]: csrf.token },
    data: {
      name: 'Categoria orçamento React 039',
      kind: 'EXPENSE',
      active: true,
    },
  });
  expect(created.ok()).toBeTruthy();
  const category = await created.json();
  await page.goto('/?page=budgets');
  await page
    .getByRole('button', { name: 'Novo orçamento', exact: true })
    .click();
  await page.getByLabel('Categoria', { exact: true }).selectOption(category.id);
  await page.getByLabel('Limite mensal (R$)', { exact: true }).fill('250.00');
  await page
    .getByRole('button', { name: 'Salvar orçamento', exact: true })
    .click();
  const card = page.locator('.workspace-record').filter({
    has: page.getByRole('heading', {
      name: 'Categoria orçamento React 039',
      exact: true,
    }),
  });
  await expect(card).toContainText('250,00');
  await card
    .getByRole('button', { name: 'Editar limite', exact: true })
    .click();
  await page.getByLabel('Limite mensal (R$)', { exact: true }).fill('125.00');
  await page
    .getByRole('button', { name: 'Salvar orçamento', exact: true })
    .click();
  await expect(card).toContainText('125,00');
  await card.getByRole('button', { name: 'Remover', exact: true }).click();
  await page.getByRole('button', { name: 'Confirmar', exact: true }).click();
  await expect(card).toHaveCount(0);
});

test('read-only access can consult migrated pages without mutation controls', async ({
  page,
}) => {
  let csrf = await (await page.request.get('/api/v1/auth/csrf')).json();
  const email = `reader-${Date.now()}@example.com`;
  const invite = await page.request.post('/api/v1/users', {
    headers: { [csrf.headerName]: csrf.token },
    data: { name: 'Pessoa consulta', email, role: 'VIEWER' },
  });
  expect(invite.ok()).toBeTruthy();
  const link = (await invite.json()).link;
  const token = new URLSearchParams(new URL(link).hash.slice(1)).get('invite');
  const accepted = await page.request.post('/api/v1/auth/accept-invite', {
    headers: { [csrf.headerName]: csrf.token },
    data: { token, name: 'Pessoa consulta', password: 'Senha-consulta-123' },
  });
  expect(accepted.status()).toBe(204);
  csrf = await (await page.request.get('/api/v1/auth/csrf')).json();
  const login = await page.request.post('/api/v1/auth/login', {
    headers: { [csrf.headerName]: csrf.token },
    data: { email, password: 'Senha-consulta-123' },
  });
  expect(login.ok()).toBeTruthy();
  await page.goto('/?page=products');
  await expect(page.locator('.workspace-record').first()).toBeVisible();
  await expect(
    page
      .locator('#page-content')
      .getByRole('button', { name: 'Novo produto', exact: true }),
  ).toHaveCount(0);
  await expect(
    page.locator('#page-content').getByRole('button', { name: /^Editar / }),
  ).toHaveCount(0);
  await page.goto('/?page=pending');
  await expect(page.locator('.workspace-loading')).toHaveCount(0);
  await expect(
    page
      .locator('#page-content')
      .getByRole('button', { name: /^(Pagar|Receber)$/ }),
  ).toHaveCount(0);
  await page.goto('/?page=team');
  await expect(
    page.getByRole('heading', {
      name: 'Seu caixa, com contexto.',
      exact: true,
    }),
  ).toBeVisible();
  csrf = await (await page.request.get('/api/v1/auth/csrf')).json();
  const blocked = await page.request.post('/api/v1/finance/accounts', {
    headers: { [csrf.headerName]: csrf.token },
    data: { name: 'Conta bloqueada', initialBalance: 0 },
  });
  expect(blocked.status()).toBe(403);
});
