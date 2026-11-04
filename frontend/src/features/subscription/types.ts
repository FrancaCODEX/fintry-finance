export interface Plan {
  code: string;
  name: string;
  description: string;
  members: number;
  accounts: number;
  storageBytes: number;
  monthlyPrice: number | null;
}
export interface Subscription {
  companyId: string;
  mode: string;
  plan: string;
  status: string;
  writable: boolean;
  limited: boolean;
  trialEndsAt?: string;
  paidUntil?: string;
  cancelAtPeriodEnd: boolean;
  revision: number;
  usage: { members: number; accounts: number; storageBytes: number };
  plans: Plan[];
  requests: {
    id: string;
    plan: string;
    status: string;
    createdAt: string;
    activeUntil?: string;
  }[];
}
export interface Invoice {
  id: string;
  requestId: string;
  plan: string;
  amount: number;
  dueDate: string;
  method: string;
  status: string;
  environment: string;
  invoiceUrl?: string;
  message?: string;
  activeUntil?: string;
  createdAt: string;
}
export interface Payments {
  enabled: boolean;
  environment: string;
  periodDays: number;
  availability: string;
  pricedPlans: string[];
  invoices: Invoice[];
}
export interface Renewal {
  enabled: boolean;
  revision: number;
  status: string;
  amount: number | null;
  plan: string;
  method: string;
  nextDueAt?: string;
  generationAt?: string;
  invoiceId?: string;
  sourceInvoiceId?: string;
  offeredPrice: number;
  canEnable: boolean;
  schedulerEnabled: boolean;
}
export const statusLabels: Record<string, string> = {
  EVALUATION: 'Avaliação',
  SIMULATED: 'Plano em simulação',
  LEGACY: 'Acesso preservado',
  TRIAL: 'Período de teste',
  ACTIVE: 'Assinatura ativa',
  EXPIRED: 'Período encerrado',
  CANCELLED: 'Assinatura encerrada',
  INACTIVE: 'Ativação necessária',
};
export const invoiceLabels: Record<string, string> = {
  ISSUING: 'Emitindo',
  PENDING: 'Aguardando pagamento',
  CONFIRMED: 'Aguardando recebimento',
  OVERDUE: 'Vencida',
  PAID: 'Acesso liberado',
  TEST_PAID: 'Teste recebido',
  CANCELLING: 'Cancelamento em confirmação',
  CANCELLED: 'Cancelada',
  REVIEW: 'Conferência necessária',
};
export const renewalLabels: Record<string, string> = {
  DISABLED: 'Emissão manual',
  AUTHORIZED: 'Recorrência autorizada',
  PRICE_CHANGED: 'Novo preço exige autorização',
  CONFIGURATION: 'Configuração em conferência',
  PLAN_CHANGED: 'Plano em conferência',
  REVIEW: 'Pagamento em conferência',
  PENDING: 'Aguardando resolução da solicitação',
  CYCLE_CLOSED: 'Emissão deste ciclo encerrada',
  CAPACITY: 'Capacidade em conferência',
};
export function checkoutReason(
  data: Subscription,
  payments: Payments,
  plan: Plan,
) {
  if (data.mode === 'EVALUATION')
    return 'Neste ambiente, escolher um plano apenas simula seus limites. Não gera mensalidade ou link de pagamento.';
  if (!payments.enabled)
    return 'A emissão de mensalidades está desabilitada no servidor. A solicitação fica registrada, sem gerar cobrança ou link.';
  if (!payments.pricedPlans.includes(plan.code))
    return 'O preço deste plano ainda não foi configurado para emissão integrada.';
  if (['LEGACY', 'INACTIVE'].includes(data.status))
    return 'Este acesso exige conferência comercial antes da primeira mensalidade.';
  if (data.status === 'ACTIVE' && data.plan !== plan.code)
    return 'Uma mudança de plano durante o período pago exige atendimento comercial. Você pode renovar o plano atual.';
  return '';
}
export function externalInvoiceUrl(value?: string) {
  if (!value) return undefined;
  try {
    const url = new URL(value);
    return url.protocol === 'https:' ? url.href : undefined;
  } catch {
    return undefined;
  }
}
