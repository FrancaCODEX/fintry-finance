import { useEffect, useRef, useState } from 'react';
import type { Bridge } from '../../app/bridge';
import type { Account } from '../../api/client';
import { useResource } from '../../shared/useResource';
import { PageHeading, Panel, QueryState } from '../../shared/components';
import { Field } from '../../shared/Field';
import {
  Metrics,
  Table,
  useCommand,
  Feedback,
  Confirm,
  mutation,
} from '../../shared/workspace';
import { money, dateLabel } from '../../shared/format';
interface Row {
  number: number;
  description: string;
  type: string;
  amount: string;
  dueDate: string;
  category: string;
  status: string;
  paymentDate?: string;
  state: string;
  message: string;
}
interface Preview {
  accountName: string;
  total: number;
  ready: number;
  duplicates: number;
  invalid: number;
  rows: Row[];
}
interface Result {
  imported: number;
  skippedDuplicates: number;
  income: string;
  expense: string;
}
const stateLabels: Record<string, string> = {
  READY: 'Pronto para importar',
  DUPLICATE: 'Possível duplicidade',
  INVALID: 'Precisa de correção',
};
export function Imports({ bridge }: { bridge: Bridge }) {
  const accounts = useResource('import-accounts', (signal) =>
    bridge.request<Account[]>('/finance/accounts', { signal }),
  );
  const [account, setAccount] = useState(''),
    [csv, setCsv] = useState(''),
    [filename, setFilename] = useState(''),
    [preview, setPreview] = useState<Preview>(),
    [selected, setSelected] = useState(new Set<number>()),
    [filter, setFilter] = useState('ALL'),
    [result, setResult] = useState<Result>(),
    [confirm, setConfirm] = useState(false);
  const cmd = useCommand(),
    generation = useRef(0);
  useEffect(
    () => () => {
      generation.current++;
    },
    [],
  );
  const accountId = account || accounts.data?.[0]?.id || '';
  function reset() {
    generation.current++;
    setPreview(undefined);
    setSelected(new Set());
    setResult(undefined);
    setConfirm(false);
  }
  const rows =
    preview?.rows.filter(
      (r) => r.state === 'READY' && selected.has(r.number),
    ) || [];
  async function read(file?: File) {
    if (!file) return;
    reset();
    setCsv('');
    setFilename(file.name);
    const id = generation.current;
    await cmd.run(async () => {
      if (file.size > 1048576) throw new Error('O CSV deve ter até 1 MB.');
      if (!/\.csv$/i.test(file.name))
        throw new Error('Selecione um arquivo com extensão .csv.');
      let text: string;
      try {
        text = new TextDecoder('utf-8', { fatal: true }).decode(
          await file.arrayBuffer(),
        );
      } catch {
        throw new Error('Salve o arquivo como CSV UTF-8 antes de importar.');
      }
      if (!text.trim())
        throw new Error(
          'O arquivo está vazio. Use um CSV com cabeçalho e lançamentos.',
        );
      if (id === generation.current) setCsv(text);
    });
  }
  async function generate() {
    const id = ++generation.current;
    setPreview(undefined);
    setSelected(new Set());
    setFilter('ALL');
    await cmd.run(async () => {
      if (!csv)
        throw new Error(
          'Selecione um arquivo CSV válido antes de gerar a prévia.',
        );
      const data = await bridge.request<Preview>(
        '/finance/imports/preview',
        mutation('POST', { accountId, csv }),
      );
      if (id !== generation.current) return;
      setPreview(data);
      setSelected(
        new Set(
          data.rows.filter((r) => r.state === 'READY').map((r) => r.number),
        ),
      );
    });
  }
  return (
    <>
      <PageHeading
        title="Importar CSV"
        description="Traga receitas e despesas com revisão antes de salvar."
      >
        <a
          className="btn secondary"
          href="/api/v1/finance/imports/template"
          download
        >
          Baixar modelo CSV
        </a>
      </PageHeading>
      <QueryState {...accounts} />
      {!bridge.canWrite ? (
        <p className="workspace-empty">
          Importação disponível para Administrador e Financeiro com acesso de
          escrita.
        </p>
      ) : accounts.data && !accounts.data.length ? (
        <Panel title="Primeiro, uma conta de destino">
          <div className="workspace-empty">
            <p>
              Cadastre um banco, carteira ou caixa para receber os registros.
            </p>
            <button className="btn" onClick={() => bridge.action('account')}>
              Criar conta
            </button>
          </div>
        </Panel>
      ) : (
        accounts.data && (
          <>
            <ol
              className="workspace-import-steps"
              aria-label="Etapas da importação"
            >
              {['Arquivo e conta', 'Revisão dos registros', 'Confirmação'].map(
                (label, i) => (
                  <li
                    key={label}
                    aria-current={
                      (result ? 2 : preview ? 1 : 0) === i ? 'step' : undefined
                    }
                  >
                    {i + 1} · {label}
                  </li>
                ),
              )}
            </ol>
            {result ? (
              <Panel title="Importação concluída">
                <div className="workspace-empty" role="status">
                  <h2>{result.imported} lançamento(s) importado(s).</h2>
                  <p>
                    {result.skippedDuplicates} registro(s) ignorado(s) por
                    possível duplicidade na validação final.
                  </p>
                  <p>
                    Receitas: {money(result.income)} · Despesas:{' '}
                    {money(result.expense)}
                  </p>
                  <p>
                    Totais incluem previstos e realizados. Só realizados alteram
                    o saldo atual.
                  </p>
                  <div className="head-actions">
                    <button
                      className="btn"
                      onClick={() => bridge.navigate('entries')}
                    >
                      Ver lançamentos
                    </button>
                    <button
                      className="btn secondary"
                      onClick={() => {
                        reset();
                        setCsv('');
                        setFilename('');
                      }}
                    >
                      Importar outro arquivo
                    </button>
                  </div>
                </div>
              </Panel>
            ) : (
              <>
                <Panel title="Arquivo e conta de destino">
                  <div className="workspace-finance-form">
                    <Field label="Conta de destino">
                      <select
                        value={accountId}
                        disabled={cmd.busy}
                        onChange={(e) => {
                          reset();
                          setAccount(e.target.value);
                        }}
                      >
                        {accounts.data.map((a) => (
                          <option key={a.id} value={a.id}>
                            {a.name}
                          </option>
                        ))}
                      </select>
                    </Field>
                    <Field label="Arquivo CSV">
                      <input
                        type="file"
                        accept=".csv,text/csv"
                        disabled={cmd.busy}
                        onChange={(e) => void read(e.target.files?.[0])}
                      />
                    </Field>
                    <p className="workspace-caption">
                      CSV UTF-8 de até 1 MB e 500 lançamentos. {filename}
                    </p>
                    <Feedback error={cmd.error} />
                    <button
                      className="btn"
                      disabled={cmd.busy || !csv || !accountId}
                      onClick={() => void generate()}
                    >
                      {cmd.busy ? 'Validando arquivo…' : 'Gerar prévia'}
                    </button>
                  </div>
                </Panel>
                <details className="workspace-panel workspace-help">
                  <summary>
                    Como preparar a planilha e conferir duplicidades
                  </summary>
                  <p>
                    Obrigatórias: descricao, tipo, valor, vencimento e
                    categoria. Opcionais: situacao e data_pagamento. Separador:
                    vírgula ou ponto e vírgula. Datas: DD/MM/AAAA ou AAAA-MM-DD
                    entre 1900 e 2100. Valores positivos com até duas casas
                    decimais, sem R$. Tipos: receita ou despesa. Situação:
                    pendente ou realizado; realizado exige data_pagamento até
                    hoje.
                  </p>
                  <p>
                    Categorias existentes são vinculadas; novas ficam avulsas.
                    Categorias inativas ou incompatíveis geram erro. Mesma
                    conta, descrição, tipo, valor e vencimento indicam possível
                    duplicidade, inclusive repetições no arquivo. Cancelados e
                    transferências ficam fora da comparação. Registros legítimos
                    iguais podem ser conferidos e cadastrados manualmente.
                  </p>
                </details>
                {preview && (
                  <Panel title="Confira antes de importar">
                    <p className="workspace-caption">
                      {preview.total} registro(s) para {preview.accountName}. A
                      prévia não altera seus dados.
                    </p>
                    <Metrics
                      items={[
                        ['Prontos', preview.ready],
                        ['Possíveis duplicidades', preview.duplicates],
                        ['Com erro', preview.invalid],
                      ]}
                    />
                    <div className="workspace-chart-controls">
                      <Field label="Filtrar registros da prévia">
                        <select
                          value={filter}
                          onChange={(e) => setFilter(e.target.value)}
                        >
                          {[
                            ['ALL', 'Todos'],
                            ['READY', 'Prontos'],
                            ['DUPLICATE', 'Duplicidades'],
                            ['INVALID', 'Com erro'],
                          ].map(([key, label]) => (
                            <option key={key} value={key}>
                              {label}
                            </option>
                          ))}
                        </select>
                      </Field>
                      <button
                        className="btn secondary small"
                        onClick={() =>
                          setSelected(
                            new Set(
                              preview.rows
                                .filter((r) => r.state === 'READY')
                                .map((r) => r.number),
                            ),
                          )
                        }
                      >
                        Selecionar prontos
                      </button>
                      <button
                        className="btn text small"
                        onClick={() => setSelected(new Set())}
                      >
                        Limpar seleção
                      </button>
                    </div>
                    <Table
                      rows={preview.rows.filter(
                        (r) => filter === 'ALL' || r.state === filter,
                      )}
                      keyOf={(r) => String(r.number)}
                      columns={[
                        {
                          label: 'Seleção',
                          render: (r) => (
                            <input
                              type="checkbox"
                              aria-label={`Selecionar registro ${r.number}`}
                              disabled={r.state !== 'READY'}
                              checked={selected.has(r.number)}
                              onChange={(e) =>
                                setSelected((previous) => {
                                  const next = new Set(previous);
                                  if (e.target.checked) next.add(r.number);
                                  else next.delete(r.number);
                                  return next;
                                })
                              }
                            />
                          ),
                        },
                        {
                          label: 'Registro',
                          render: (r) => (
                            <>
                              <strong>
                                #{r.number} · {r.description || 'Sem descrição'}
                              </strong>
                              <small>{r.category}</small>
                            </>
                          ),
                        },
                        {
                          label: 'Tipo / situação',
                          render: (r) => (
                            <>
                              {r.type === 'INCOME'
                                ? 'Receita'
                                : r.type === 'EXPENSE'
                                  ? 'Despesa'
                                  : r.type}
                              <small>
                                {r.status === 'PAID'
                                  ? 'Realizado'
                                  : r.status === 'PENDING'
                                    ? 'Pendente'
                                    : r.status}{' '}
                                {r.paymentDate &&
                                  (r.state === 'INVALID'
                                    ? r.paymentDate
                                    : dateLabel(r.paymentDate))}
                              </small>
                            </>
                          ),
                        },
                        {
                          label: 'Vencimento',
                          render: (r) =>
                            r.state === 'INVALID'
                              ? r.dueDate
                              : dateLabel(r.dueDate),
                        },
                        {
                          label: 'Valor',
                          render: (r) =>
                            r.state === 'INVALID' ? r.amount : money(r.amount),
                        },
                        {
                          label: 'Validação',
                          render: (r) => (
                            <>
                              {stateLabels[r.state]}
                              <small>{r.message}</small>
                            </>
                          ),
                        },
                      ]}
                    />
                    <div className="workspace-finance-form">
                      <p role="status">
                        {rows.length} registro(s) selecionado(s) · Receitas{' '}
                        {selectedTotal(rows, 'INCOME')} · Despesas{' '}
                        {selectedTotal(rows, 'EXPENSE')}
                      </p>
                      <button
                        className="btn"
                        disabled={!rows.length || cmd.busy}
                        onClick={() => setConfirm(true)}
                      >
                        Revisar e importar
                      </button>
                    </div>
                  </Panel>
                )}
              </>
            )}
            {confirm && preview && (
              <Confirm
                title="Confirmar importação"
                description={`${rows.length} lançamento(s) selecionado(s) para ${preview.accountName}. Realizados alteram o saldo; pendentes entram nas previsões. Possíveis duplicidades serão ignoradas na validação final. Nada será substituído.`}
                close={() => setConfirm(false)}
                label="Confirmar importação"
                confirm={async () => {
                  const id = generation.current;
                  const data = await bridge.request<Result>(
                    '/finance/imports/confirm',
                    mutation('POST', {
                      accountId,
                      csv,
                      selectedRows: rows.map((r) => r.number),
                    }),
                  );
                  if (id !== generation.current)
                    throw new Error(
                      'O arquivo foi alterado. Gere outra prévia.',
                    );
                  setResult(data);
                  setPreview(undefined);
                  setCsv('');
                  setSelected(new Set());
                }}
              />
            )}
          </>
        )
      )}
    </>
  );
}
function selectedTotal(rows: Row[], type: string) {
  const total = rows
    .filter((r) => r.type === type)
    .reduce((sum, r) => {
      const [whole, fraction = ''] = r.amount.split('.');
      return sum + BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
    }, 0n);
  return money(`${total / 100n}.${String(total % 100n).padStart(2, '0')}`);
}
