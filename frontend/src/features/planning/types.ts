import type { Entry, EntryPage } from '../../api/client';
export interface Page<T> {
  content: T[];
  number: number;
  totalElements: number;
  totalPages: number;
}
export interface Pending extends Page<Entry> {
  today: string;
  group: string;
  totals: { count: number; income: number; expense: number };
  windows: {
    group: string;
    totals: { count: number; income: number; expense: number };
  }[];
}
export interface ReportRow {
  id: string;
  description: string;
  category: string;
  contactName: string;
  accountName: string;
  type: string;
  status: string;
  amount: number;
  dueDate: string;
  paymentDate?: string;
  sequenceNumber?: number;
  seriesCount?: number;
  paymentId?: string;
}
export interface Report extends Page<ReportRow> {
  totals: {
    income: number;
    expense: number;
    result: number;
    pendingIncome: number;
    pendingExpense: number;
  };
  categories: { category: string; amount: number }[];
}
export interface Series {
  id: string;
  description: string;
  kind: string;
  type: string;
  occurrences: number;
  totalAmount: number;
  firstDueDate: string;
  lastDueDate: string;
  paid: number;
  partial: number;
  pending: number;
  pausedOccurrences: number;
  cancelled: number;
  paused: boolean;
  stopped: boolean;
}
export interface Budget {
  id: string;
  categoryId: string;
  category: string;
  active: boolean;
  status: string;
  limitAmount: number;
  committed: number;
  percentage: number;
  paid: number;
  pending: number;
  remaining: number;
}
export interface Budgets {
  content: Budget[];
  limitAmount: number;
  paid: number;
  pending: number;
  remaining: number;
  exceededCount: number;
  warningCount: number;
  unbudgetedPaid: number;
  unbudgetedPending: number;
}
export interface Stock {
  id: string;
  productId: string;
  saleId?: string;
  sku: string;
  productName: string;
  unit: string;
  kind: string;
  delta: number;
  balance: number;
  reason: string;
  actorName: string;
  createdAt: string;
}
export type SeriesEntries = EntryPage;
