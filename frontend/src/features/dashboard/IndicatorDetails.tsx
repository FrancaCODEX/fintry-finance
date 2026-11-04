import { useEffect, useRef, useState } from 'react';
import type { Bridge } from '../../app/bridge';
import {
  query,
  type IndicatorExplanation,
  type IndicatorKind,
} from '../../api/client';
import { money, dateLabel } from '../../shared/format';
import { QueryState } from '../../shared/components';
import { useResource } from '../../shared/useResource';
const titles = {
  BALANCE: 'Como este saldo é formado',
  INCOME: 'De onde vêm as receitas',
  EXPENSE: 'De onde vêm as despesas',
  RESULT: 'Como calculamos o resultado',
};
export function IndicatorDetails({
  kind,
  month,
  bridge,
  onClose,
}: {
  kind: IndicatorKind;
  month: string;
  bridge: Bridge;
  onClose: () => void;
}) {
  const dialog = useRef<HTMLDialogElement>(null),
    [page, setPage] = useState(0);
  const result = useResource('indicator:' + kind + month + page, (signal) =>
    bridge.request<IndicatorExplanation>(
      '/reports/explanation?' + query({ kind, month, page }),
      { signal },
    ),
  );
  useEffect(() => {
    const element = dialog.current!;
    element.showModal();
    return () => {
      if (element.open) element.close();
    };
  }, []);
  const info = result.data;
  return (
    <dialog
      className="workspace-explanation-dialog"
      ref={dialog}
      aria-labelledby="indicator-explanation-title"
      onCancel={onClose}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div className="workspace-panel-head">
        <div>
          <span className="eyebrow">NÚMEROS COM ORIGEM</span>
          <h2 id="indicator-explanation-title">{titles[kind]}</h2>
        </div>
        <button
          type="button"
          autoFocus
          className="btn secondary small"
          onClick={onClose}
        >
          Fechar explicação
        </button>
      </div>
      <div className="workspace-explanation-content">
        <QueryState {...result} />
        {info && (
          <>
            <strong className="workspace-explanation-value">
              {money(info.amount)}
            </strong>
            <p>{info.formula}</p>
            <p className="workspace-caption">{info.scope}</p>
            <div className="workspace-explanation-steps">
              {info.steps.map((step, i) => (
                <div key={i}>
                  <span>{step.label}</span>
                  <b>{money(step.amount)}</b>
                </div>
              ))}
            </div>
            {info.sources.length > 0 && (
              <>
                <h3>Baixas que compõem este valor</h3>
                <div className="workspace-sources">
                  {info.sources.map((source, i) => (
                    <div key={source.paymentId || i}>
                      <div>
                        <button
                          className="workspace-row-name"
                          onClick={() => {
                            onClose();
                            setTimeout(
                              () => void bridge.details(source.entryId),
                              0,
                            );
                          }}
                        >
                          {source.description}
                        </button>
                        <small>Baixa em {dateLabel(source.paymentDate)}</small>
                      </div>
                      <strong
                        className={
                          source.type === 'INCOME'
                            ? 'workspace-income'
                            : 'workspace-expense'
                        }
                      >
                        {source.type === 'INCOME' ? '+' : '−'}{' '}
                        {money(source.amount)}
                      </strong>
                    </div>
                  ))}
                </div>
              </>
            )}
            {kind === 'BALANCE' ? (
              <button
                className="btn secondary"
                onClick={() => bridge.navigate('accounts')}
              >
                Consultar contas
              </button>
            ) : (
              <nav
                className="workspace-pagination"
                aria-label="Paginação das baixas"
              >
                <span>
                  {info.totalElements} baixa(s) · página {page + 1} de{' '}
                  {Math.max(1, info.totalPages)}
                </span>
                <div>
                  <button
                    className="btn secondary small"
                    disabled={!page}
                    onClick={() => setPage((p) => p - 1)}
                  >
                    Anterior
                  </button>
                  <button
                    className="btn secondary small"
                    disabled={page + 1 >= info.totalPages}
                    onClick={() => setPage((p) => p + 1)}
                  >
                    Próxima
                  </button>
                </div>
              </nav>
            )}
            <p className="workspace-caption">
              Consulta atualizada em{' '}
              {new Date(info.capturedAt).toLocaleString('pt-BR')}. Os totais
              incluem todas as páginas.
            </p>
          </>
        )}
      </div>
    </dialog>
  );
}
