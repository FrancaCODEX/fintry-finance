import type {
  Account,
  Entry,
  EntryPage,
  Summary,
  Transport,
} from '../api/client';
export type Page = 'dashboard' | 'entries' | 'scenarios';
export interface Bridge {
  page: Page;
  month: string;
  user: { id: string; name: string; companyId: string };
  canWrite: boolean;
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
