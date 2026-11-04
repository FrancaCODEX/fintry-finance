import { Pending } from './features/planning/Pending';
import { Reports } from './features/planning/Reports';
import { Forecast } from './features/planning/Forecast';
import { Series } from './features/planning/Series';
import { Budgets } from './features/planning/Budgets';
import { Stock } from './features/management/Stock';
import { Audit } from './features/management/Audit';
import { Company } from './features/management/Company';
import { Security } from './features/management/Security';
import { Operations } from './features/management/Operations';
import { Examples } from './features/management/Examples';
import { Imports } from './features/imports/Imports';
import { Billing } from './features/billing/Billing';
import { Subscription } from './features/subscription/Subscription';
import { Catalog } from './features/catalog/Catalog';
import { isCatalogPage, type CatalogPage } from './features/catalog/model';
import { Component, type ReactNode, type ComponentType } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { flushSync } from 'react-dom';
import type { Bridge, Page } from './app/bridge';
import { Dashboard } from './features/dashboard/Dashboard';
import { Scenario } from './features/scenarios/Scenario';
import { Entries } from './features/entries/Entries';
import './workspace.css';
import { Reconciliation } from './features/reconciliation/Reconciliation';
import { FinanceDialog } from './features/finance/FinanceDialog';
const views = {
  dashboard: Dashboard,
  entries: Entries,
  scenarios: Scenario,
  reconciliation: Reconciliation,
  pending: Pending,
  reports: Reports,
  forecast: Forecast,
  series: Series,
  budgets: Budgets,
  stock: Stock,
  audit: Audit,
  company: Company,
  security: Security,
  operations: Operations,
  examples: Examples,
  imports: Imports,
  billing: Billing,
  subscription: Subscription,
} satisfies Record<
  Exclude<Page, CatalogPage>,
  ComponentType<{ bridge: Bridge }>
>;
function WorkspaceContent({ bridge }: { bridge: Bridge }) {
  const page = bridge.page;
  if (isCatalogPage(page))
    return <Catalog key={page} page={page} bridge={bridge} />;
  const View = views[page];
  return <View bridge={bridge} />;
}
class Boundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() {
    return { failed: true };
  }
  render() {
    return this.state.failed ? (
      <section className="workspace-message" role="alert">
        <h1>Não foi possível abrir esta página.</h1>
        <button className="btn" onClick={() => location.reload()}>
          Recarregar
        </button>
      </section>
    ) : (
      this.props.children
    );
  }
}
let root: Root | null = null;
let financeRoot: Root | null = null;
let financeContainer: HTMLElement | null = null;
function closeFinance() {
  // Close is scheduled outside an event render so React can finish the current update.
  queueMicrotask(() => {
    financeRoot?.unmount();
    financeRoot = null;
    financeContainer?.remove();
    financeContainer = null;
  });
}
window.addEventListener('fintry:session-expired', closeFinance);
window.FintryWorkspace = {
  closeFinance,
  openFinance(action, id, bridge) {
    financeRoot?.unmount();
    financeContainer?.remove();
    financeContainer = document.createElement('div');
    financeContainer.className = 'fintry-workspace';
    document.body.append(financeContainer);
    financeRoot = createRoot(financeContainer);
    financeRoot.render(
      <Boundary>
        <FinanceDialog
          action={action}
          id={id}
          bridge={bridge}
          close={closeFinance}
        />
      </Boundary>,
    );
  },
  mount(element, bridge: Bridge) {
    root?.unmount();
    root = createRoot(element);
    flushSync(() =>
      root!.render(
        <Boundary>
          <div className="fintry-workspace">
            <WorkspaceContent bridge={bridge} />
          </div>
        </Boundary>,
      ),
    );
  },
  unmount() {
    root?.unmount();
    root = null;
  },
};
