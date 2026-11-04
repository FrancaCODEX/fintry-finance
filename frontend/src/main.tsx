import { Component, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { flushSync } from 'react-dom';
import type { Bridge } from './app/bridge';
import { Dashboard } from './features/dashboard/Dashboard';
import { Entries } from './features/entries/Entries';
import './workspace.css';
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
window.FintryWorkspace = {
  mount(element, bridge: Bridge) {
    root?.unmount();
    root = createRoot(element);
    flushSync(() =>
      root!.render(
        <Boundary>
          <div className="fintry-workspace">
            {bridge.page === 'dashboard' ? (
              <Dashboard bridge={bridge} />
            ) : (
              <Entries bridge={bridge} />
            )}
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
