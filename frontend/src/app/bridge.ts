import type { CatalogPage, CatalogResult } from '../features/catalog/model';
import type {
  Account,
  Entry,
  EntryPage,
  Summary,
  Transport,
} from '../api/client';
export type Page =
  | 'dashboard'
  | 'entries'
  | 'scenarios'
  | 'reconciliation'
  | CatalogPage
  | 'pending'
  | 'reports'
  | 'forecast'
  | 'series'
  | 'budgets'
  | 'imports'
  | 'stock'
  | 'audit'
  | 'company'
  | 'security'
  | 'operations'
  | 'examples'
  | 'billing'
  | 'subscription';
export interface Bridge {
  page: Page;
  month: string;
  user: { id: string; name: string; companyId: string; email: string };
  canCollect: boolean;
  welcome: boolean;
  stockProduct?: { id: string; name: string };
  hostAction: (
    name: string,
    args?: Record<string, string>,
    context?: {
      state?: Record<string, unknown>;
      billing?: Record<string, unknown>;
      subscription?: Record<string, unknown>;
    },
  ) => Promise<void>;
  companyChanged: (name: string) => void;
  auditLabel: (key: string) => string;
  auditValue: (value: string) => string;
  canWrite: boolean;
  isAdmin: boolean;
  catalogAction: (
    page: CatalogPage,
    data: CatalogResult,
    name: string,
    id?: string,
  ) => Promise<void>;
  request: Transport;
  action: (name: string, row?: Entry) => Promise<void>;
  details: (id: string) => Promise<void>;
  navigate: (page: string, month?: string, type?: string) => void;
  sync: (data: {
    accounts?: Account[];
    entries?: EntryPage;
    summary?: Summary;
    month?: string;
  }) => void;
}
declare global {
  interface Window {
    FintryWorkspace?: {
      mount: (element: HTMLElement, bridge: Bridge) => void;
      unmount: () => void;
      openFinance: (
        action: 'entry' | 'edit-entry' | 'pay' | 'cancel',
        id: string | undefined,
        bridge: FinanceBridge,
      ) => void;
      closeFinance: () => void;
    };
  }
}

export interface FinanceBridge {
  request: Transport;
  canWrite: boolean;
  notify: (message: string) => void;
  refresh: () => Promise<void>;
  createAccount: () => void;
}
