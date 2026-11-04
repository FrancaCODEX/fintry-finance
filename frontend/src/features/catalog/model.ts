export const catalogPages = [
  'products',
  'services',
  'sales',
  'proposals',
  'contacts',
  'categories',
  'team',
  'accounts',
] as const;
export type CatalogPage = (typeof catalogPages)[number];
export const isCatalogPage = (page: string): page is CatalogPage =>
  catalogPages.includes(page as CatalogPage);
export interface CatalogRow {
  id: string;
  name?: string;
  sku?: string;
  unit?: string;
  kind?: string;
  price?: number;
  cost?: number;
  quantity?: number;
  minimum?: number;
  lowStock?: boolean;
  active?: boolean;
  number?: number;
  title?: string;
  contactName?: string;
  status?: string;
  paymentStatus?: string;
  total?: number;
  validUntil?: string;
  expired?: boolean;
  saleNumber?: number;
  saleStatus?: string;
  document?: string;
  email?: string;
  phone?: string;
  role?: string;
  invited?: boolean;
  invitationExpiresAt?: string;
  balance?: number;
  initialBalance?: number;
}
export interface CatalogResult {
  content: CatalogRow[];
  totalElements: number;
  totalPages: number;
  number: number;
  activeCount?: number;
  lowStockCount?: number;
  stockValue?: number;
  active?: number;
  pending?: number;
  drafts?: number;
  negotiating?: number;
  approved?: number;
}
export type Filters = Record<string, string>;
export const labels: Record<string, string> = {
  DRAFT: 'Rascunho',
  OPEN: 'Em negociação',
  APPROVED: 'Aprovada',
  REJECTED: 'Recusada',
  CANCELLED: 'Cancelada',
  CONFIRMED: 'Confirmada',
  PAID: 'Recebida',
  PENDING: 'A receber',
  PARTIAL: 'Recebida parcialmente',
  CUSTOMER: 'Cliente',
  SUPPLIER: 'Fornecedor',
  BOTH: 'Cliente e fornecedor',
  INCOME: 'Receitas',
  EXPENSE: 'Despesas',
  ADMIN: 'Administrador',
  FINANCE: 'Financeiro',
  VIEWER: 'Somente leitura',
};
export const catalogConfig: Record<
  CatalogPage,
  {
    title: string;
    description: string;
    section: string;
    endpoint: string;
    create: string;
    createLabel: string;
    note: string;
  }
> = {
  products: {
    title: 'Produtos',
    description: 'Seu catálogo, preços e disponibilidade, lado a lado.',
    section: 'Comercial',
    endpoint: '/products',
    create: 'product-new',
    createLabel: 'Novo produto',
    note: 'O custo usa o valor atual cadastrado. Compras e pagamentos são registrados separadamente em Lançamentos.',
  },
  services: {
    title: 'Serviços',
    description: 'Transforme seu conhecimento em propostas e vendas.',
    section: 'Comercial',
    endpoint: '/products',
    create: 'service-new',
    createLabel: 'Novo serviço',
    note: 'Serviços não possuem estoque. Custos são referências; despesas são registradas no financeiro.',
  },
  sales: {
    title: 'Vendas',
    description: 'Do rascunho ao recebimento, acompanhe cada venda.',
    section: 'Comercial',
    endpoint: '/sales',
    create: 'sale-new',
    createLabel: 'Nova venda',
    note: 'A confirmação movimenta estoque e cria a receita vinculada. O recebimento é registrado no lançamento financeiro.',
  },
  proposals: {
    title: 'Propostas',
    description: 'Organize negociações e acompanhe o próximo passo.',
    section: 'Comercial',
    endpoint: '/proposals',
    create: 'proposal-new',
    createLabel: 'Nova proposta',
    note: 'Propostas não reservam estoque nem entram no fluxo previsto antes da aprovação. A negociação é um registro interno, sem envio automático de e-mail.',
  },
  contacts: {
    title: 'Clientes e fornecedores',
    description: 'As pessoas e empresas que fazem parte do seu negócio.',
    section: 'Cadastros',
    endpoint: '/contacts',
    create: 'contact',
    createLabel: 'Novo contato',
    note: 'Cadastros inativos continuam vinculados ao histórico financeiro.',
  },
  categories: {
    title: 'Categorias',
    description:
      'Uma organização que faz sentido para suas receitas e despesas.',
    section: 'Cadastros',
    endpoint: '/categories',
    create: 'category',
    createLabel: 'Nova categoria',
    note: 'Categorias inativas e categorias avulsas dos lançamentos antigos continuam no histórico.',
  },
  team: {
    title: 'Equipe',
    description: 'Cada pessoa com o acesso certo à sua empresa.',
    section: 'Seu espaço',
    endpoint: '/users',
    create: 'invite-user',
    createLabel: 'Convidar pessoa',
    note: 'Administrador gerencia tudo; Financeiro gerencia movimentos; Somente leitura consulta e exporta. Alterar perfil ou situação encerra as sessões da pessoa.',
  },
  accounts: {
    title: 'Contas',
    description: 'Veja onde está o dinheiro da sua empresa.',
    section: 'Financeiro',
    endpoint: '/finance/accounts',
    create: 'account',
    createLabel: 'Nova conta',
    note: 'O saldo considera o valor inicial e as baixas realizadas. Transferências não alteram o resultado da empresa.',
  },
};
export function readFilters(page: CatalogPage): Filters {
  const params = new URLSearchParams(location.search);
  const defaults: Filters =
    !params.has('index') && ['products', 'services', 'contacts'].includes(page)
      ? { active: 'true' }
      : {};
  const fields = filterFields(page);
  for (const field of fields) {
    const value = params.get(field.key);
    if (
      value !== null &&
      (!field.options || field.options.some(([key]) => key === value))
    )
      defaults[field.key] = value.slice(0, 120);
  }
  const index = ['categories', 'accounts'].includes(page)
    ? 0
    : Number(params.get('index') || 0);
  return {
    ...defaults,
    index:
      Number.isSafeInteger(index) && index >= 0 && index <= 100000
        ? String(index)
        : '0',
  };
}
export interface FilterField {
  key: string;
  label: string;
  options?: [string, string][];
}
export function filterFields(page: CatalogPage): FilterField[] {
  const fields: FilterField[] = [
    { key: 'search', label: 'Buscar por nome ou referência' },
  ];
  if (['products', 'services', 'contacts', 'categories'].includes(page))
    fields.push({
      key: 'active',
      label: 'Situação',
      options: [
        ['', 'Todos'],
        ['true', 'Ativos'],
        ['false', 'Inativos'],
      ],
    });
  if (page === 'products')
    fields.push({
      key: 'low',
      label: 'Disponibilidade',
      options: [
        ['', 'Todo o estoque'],
        ['true', 'Abaixo ou no mínimo'],
      ],
    });
  if (page === 'sales' || page === 'proposals')
    fields.push({
      key: 'status',
      label: 'Situação',
      options: [
        ['', 'Todas'],
        ...(page === 'sales'
          ? ['DRAFT', 'CONFIRMED', 'CANCELLED']
          : ['DRAFT', 'OPEN', 'APPROVED', 'REJECTED', 'CANCELLED']
        ).map((key): [string, string] => [key, labels[key]]),
      ],
    });
  if (page === 'proposals')
    fields.push({
      key: 'expired',
      label: 'Validade',
      options: [
        ['', 'Todas as validades'],
        ['true', 'Expiradas em aberto'],
      ],
    });
  if (page === 'contacts')
    fields.push({
      key: 'kind',
      label: 'Tipo de contato',
      options: [
        ['', 'Todos'],
        ['CUSTOMER', 'Clientes'],
        ['SUPPLIER', 'Fornecedores'],
      ],
    });
  if (page === 'team')
    fields.push({
      key: 'role',
      label: 'Perfil',
      options: [
        ['', 'Todos'],
        ...['ADMIN', 'FINANCE', 'VIEWER'].map((key): [string, string] => [
          key,
          labels[key],
        ]),
      ],
    });
  return fields;
}
