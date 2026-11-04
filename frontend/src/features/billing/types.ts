import type { Page } from '../planning/types';
export interface Connection {
  available: boolean;
  configured: boolean;
  environment: string;
  webhookId?: string;
  locked: boolean;
}
export interface Charge {
  id: string;
  entryId: string;
  description: string;
  amount: number;
  dueDate: string;
  method: string;
  status: string;
  environment: string;
  invoiceUrl?: string;
  bankSlipUrl?: string;
  netAmount?: number;
  settledDate?: string;
  message?: string;
}
export interface Webhook {
  pending: number;
  processing: number;
  failed: number;
  recent: {
    id: string;
    status: string;
    attempts: number;
    receivedAt: string;
    message?: string;
  }[];
}
export interface BillingData {
  config: Connection;
  data: Page<Charge>;
  operations: Webhook | null;
}
export const billingLabels: Record<string, string> = {
  ISSUING: 'Emitindo',
  PENDING: 'Aguardando pagamento',
  OVERDUE: 'Vencida',
  CONFIRMED: 'Aguardando crédito',
  RECEIVED: 'Recebida',
  TEST_RECEIVED: 'Recebida em teste',
  CANCELLED: 'Cancelada',
  REVIEW: 'Revisar no Asaas',
};
