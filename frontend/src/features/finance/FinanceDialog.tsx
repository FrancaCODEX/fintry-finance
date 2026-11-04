import { useEffect, useRef, useState } from 'react';
import type { FinanceBridge } from '../../app/bridge';
import type {
  EntryDetails,
  EntryInput,
  Category,
  ContactPage,
} from '../../api/client';
import { query } from '../../api/client';
import { Dialog, FieldError } from '../../shared/Dialog';
import { QueryState } from '../../shared/components';
import { useResource } from '../../shared/useResource';
import { decimal, businessDate, monthlySchedule } from '../../shared/finance';
import { money, dateLabel } from '../../shared/format';
type Action = 'entry' | 'edit-entry' | 'pay' | 'cancel';
export function FinanceDialog({
  action,
  id,
  bridge,
  close,
}: {
  action: Action;
  id?: string;
  bridge: FinanceBridge;
  close: () => void;
}) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [errors, setErrors] = useState<Record<string, string>>({});
  const lock = useRef(false),
    identity = useRef({ key: crypto.randomUUID(), payload: '' });
  const [form, setForm] = useState({
    accountId: '',
    description: '',
    type: 'INCOME' as 'INCOME' | 'EXPENSE',
    amount: '',
    categoryId: '',
    category: 'Outros',
    contactId: '',
    dueDate: businessDate(),
    paid: false,
    paymentDate: businessDate(),
    mode: 'SINGLE',
    occurrences: 12,
  });
  const [search, setSearch] = useState(''),
    [term, setTerm] = useState('');
  const data = useResource('finance-dialog:' + action + id, async (signal) => {
    const [accounts, categories, details] = await Promise.all([
      bridge.request<import('../../api/client').Account[]>(
        '/finance/accounts',
        { signal },
      ),
      bridge.request<Category[]>('/categories', { signal }),
      id
        ? bridge.request<EntryDetails>('/finance/entries/' + id, { signal })
        : undefined,
    ]);
    return { accounts, categories, details };
  });
  useEffect(() => {
    if (!data.data) return;
    const row = data.data.details?.entry;
    setForm((f) => ({
      ...f,
      accountId: row?.accountId || data.data!.accounts[0]?.id || '',
      ...(row
        ? {
            description: row.description,
            type: row.type,
            amount: String(action === 'pay' ? row.remainingAmount : row.amount),
            categoryId: row.categoryId || '',
            category: row.category || 'Outros',
            contactId: row.contactId || '',
            dueDate: row.dueDate,
          }
        : {}),
    }));
  }, [data.data]);
  useEffect(() => {
    const timer = setTimeout(() => setTerm(search), 250);
    return () => clearTimeout(timer);
  }, [search]);
  const contacts = useResource('contacts:' + form.type + term, (signal) =>
    bridge.request<ContactPage>(
      '/contacts?' +
        query({
          kind: form.type === 'INCOME' ? 'CUSTOMER' : 'SUPPLIER',
          active: true,
          size: 50,
          search: term,
        }),
      { signal },
    ),
  );
  useEffect(() => {
    if (Object.values(errors).some(Boolean))
      document
        .querySelector<HTMLElement>(
          '.workspace-form-dialog [aria-invalid="true"]',
        )
        ?.focus();
  }, [errors]);
  const row = data.data?.details?.entry;
  const title =
    action === 'pay'
      ? 'Registrar baixa'
      : action === 'cancel'
        ? 'Cancelar lançamento?'
        : action === 'edit-entry'
          ? 'Editar lançamento'
          : 'Novo lançamento';
  const description =
    action === 'pay'
      ? 'Confira o valor desta baixa e o saldo que ficará em aberto.'
      : action === 'cancel'
        ? 'O registro continuará disponível no histórico.'
        : 'Preencha os dados e confira os valores antes de confirmar.';
  const blocked =
    !!row &&
    ((action === 'pay' && !['PENDING', 'PARTIAL'].includes(row.status)) ||
      (action === 'edit-entry' &&
        (row.status !== 'PENDING' ||
          !!row.seriesId ||
          !!row.saleId ||
          !!row.transferId)) ||
      (action === 'cancel' &&
        (row.status === 'CANCELLED' || !!row.saleId || !!row.transferId)));
  const categories =
    data.data?.categories.filter(
      (c) =>
        (c.active && (c.kind === form.type || c.kind === 'BOTH')) ||
        c.id === form.categoryId,
    ) || [];
  const field = (name: string) => ({
    'aria-invalid': !!errors[name],
    'aria-describedby': errors[name] ? name + '-error' : undefined,
  });
  function update<K extends keyof typeof form>(
    key: K,
    value: (typeof form)[K],
  ) {
    setForm((f) => ({ ...f, [key]: value }));
    setErrors((e) => ({ ...e, [key]: '' }));
    setError('');
  }
  let schedule: ReturnType<typeof monthlySchedule> = [],
    scheduleError = '';
  if (form.mode !== 'SINGLE' && form.amount)
    try {
      schedule = monthlySchedule(
        form.mode,
        form.occurrences,
        form.amount,
        form.dueDate,
      );
    } catch (e) {
      scheduleError = (e as Error).message;
    }
  async function submit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault();
    if (lock.current || blocked || !bridge.canWrite) return;
    const nextErrors: Record<string, string> = {};
    let amount = '';
    if (action !== 'cancel')
      try {
        amount = decimal(form.amount);
      } catch (err) {
        nextErrors.amount = (err as Error).message;
      }
    if (action === 'pay' && form.paymentDate > businessDate())
      nextErrors.paymentDate = 'A baixa não pode ter data futura.';
    if (form.paid && form.paymentDate > businessDate())
      nextErrors.paymentDate = 'A baixa não pode ter data futura.';
    if (action === 'entry' || action === 'edit-entry') {
      if (!form.accountId) nextErrors.accountId = 'Selecione uma conta.';
      if (!form.description.trim())
        nextErrors.description = 'Informe a descrição.';
      if (!form.categoryId && !form.category.trim())
        nextErrors.category = 'Informe a categoria.';
      if (form.mode !== 'SINGLE')
        try {
          monthlySchedule(
            form.mode,
            form.occurrences,
            form.amount,
            form.dueDate,
          );
        } catch (err) {
          nextErrors.occurrences = (err as Error).message;
        }
    }
    setErrors(nextErrors);
    if (Object.keys(nextErrors).length) {
      setError('Confira os campos indicados.');
      return;
    }
    lock.current = true;
    setBusy(true);
    setError('');
    try {
      if (action === 'cancel')
        await bridge.request('/finance/entries/' + id + '/cancel', {
          method: 'POST',
        });
      else if (action === 'pay') {
        const payload = JSON.stringify({
          amount,
          paymentDate: form.paymentDate,
        });
        if (identity.current.payload && identity.current.payload !== payload)
          identity.current.key = crypto.randomUUID();
        identity.current.payload = payload;
        await bridge.request('/finance/entries/' + id + '/pay', {
          method: 'POST',
          body: JSON.stringify({
            amount,
            paymentDate: form.paymentDate,
            requestId: identity.current.key,
          }),
        });
      } else {
        const input: EntryInput = {
          accountId: form.accountId,
          description: form.description.trim(),
          type: form.type,
          amount,
          categoryId: form.categoryId || null,
          category: form.categoryId ? null : form.category.trim(),
          contactId: form.contactId || null,
          dueDate: form.dueDate,
          paid: action === 'entry' && form.paid,
          paymentDate:
            action === 'entry' && form.paid ? form.paymentDate : null,
        };
        if (form.mode !== 'SINGLE') {
          const payload = JSON.stringify({
            kind: form.mode,
            occurrences: form.occurrences,
            entry: input,
          });
          if (identity.current.payload && identity.current.payload !== payload)
            identity.current.key = crypto.randomUUID();
          identity.current.payload = payload;
          await bridge.request('/finance/series', {
            method: 'POST',
            body: JSON.stringify({
              ...JSON.parse(payload),
              requestId: identity.current.key,
            }),
          });
        } else
          await bridge.request('/finance/entries' + (id ? '/' + id : ''), {
            method: id ? 'PUT' : 'POST',
            body: JSON.stringify(input),
          });
      }
      close();
      bridge.notify(
        action === 'pay'
          ? 'Baixa registrada.'
          : action === 'cancel'
            ? 'Lançamento cancelado.'
            : 'Lançamento salvo.',
      );
      await bridge.refresh();
    } catch (err) {
      const failure = err as Error & { fields?: Record<string, string> };
      setError(failure.message);
      setErrors(
        Object.fromEntries(
          Object.entries(failure.fields || {}).map(([k, v]) => [
            k.replace(/^entry\./, ''),
            v,
          ]),
        ),
      );
    } finally {
      lock.current = false;
      setBusy(false);
    }
  }
  return (
    <Dialog title={title} description={description} busy={busy} close={close}>
      <form className="workspace-finance-form" onSubmit={submit}>
        <QueryState {...data} />
        {data.data && (
          <>
            {!data.data.accounts.length ? (
              <div className="workspace-message">
                <p>Cadastre uma conta para começar.</p>
                <button
                  type="button"
                  className="btn"
                  onClick={() => {
                    close();
                    bridge.createAccount();
                  }}
                >
                  Criar conta
                </button>
              </div>
            ) : blocked ? (
              <p role="alert" className="workspace-message">
                Este lançamento mudou e não permite esta operação. Atualize a
                consulta.
              </p>
            ) : (
              <>
                {row && (
                  <div className="workspace-form-context">
                    <strong>{row.description}</strong>
                    <span>
                      {money(row.amount)} · {dateLabel(row.dueDate)}
                    </span>
                  </div>
                )}
                {action === 'cancel' ? (
                  <p className="workspace-scenario-note">
                    {row?.receivedAmount
                      ? 'As baixas deste lançamento deixarão de compor o saldo.'
                      : 'O lançamento deixará de compor as pendências.'}{' '}
                    Confira antes de confirmar.
                  </p>
                ) : (
                  <>
                    {(action === 'entry' || action === 'edit-entry') && (
                      <>
                        {action === 'entry' && (
                          <label className="field">
                            Formato
                            <select
                              value={form.mode}
                              onChange={(e) => update('mode', e.target.value)}
                            >
                              <option value="SINGLE">Lançamento único</option>
                              <option value="INSTALLMENT">
                                Parcelar um valor total
                              </option>
                              <option value="RECURRING">
                                Repetir valor mensalmente
                              </option>
                            </select>
                          </label>
                        )}
                        <div className="workspace-form-grid">
                          <label className="field">
                            Tipo
                            <select
                              value={form.type}
                              onChange={(e) => {
                                setForm((f) => ({
                                  ...f,
                                  type: e.target.value as 'INCOME' | 'EXPENSE',
                                  categoryId: '',
                                  contactId: '',
                                }));
                                setSearch('');
                              }}
                            >
                              <option value="INCOME">Receita</option>
                              <option value="EXPENSE">Despesa</option>
                            </select>
                          </label>
                          <label className="field">
                            {form.mode === 'INSTALLMENT'
                              ? 'Valor total (R$)'
                              : form.mode === 'RECURRING'
                                ? 'Valor mensal (R$)'
                                : 'Valor (R$)'}
                            <input
                              name="amount"
                              inputMode="decimal"
                              required
                              value={form.amount}
                              onChange={(e) => update('amount', e.target.value)}
                              {...field('amount')}
                            />
                            <FieldError
                              id="amount-error"
                              error={errors.amount}
                            />
                          </label>
                        </div>
                        <label className="field">
                          Descrição
                          <input
                            name="description"
                            autoFocus
                            required
                            maxLength={255}
                            value={form.description}
                            onChange={(e) =>
                              update('description', e.target.value)
                            }
                            {...field('description')}
                          />
                          <FieldError
                            id="description-error"
                            error={errors.description}
                          />
                        </label>
                        <label className="field">
                          Conta
                          <select
                            name="accountId"
                            required
                            value={form.accountId}
                            onChange={(e) =>
                              update('accountId', e.target.value)
                            }
                            {...field('accountId')}
                          >
                            {data.data.accounts.map((a) => (
                              <option key={a.id} value={a.id}>
                                {a.name}
                              </option>
                            ))}
                          </select>
                          <FieldError
                            id="accountId-error"
                            error={errors.accountId}
                          />
                        </label>
                        <label className="field">
                          Categoria cadastrada
                          <select
                            value={form.categoryId}
                            onChange={(e) =>
                              update('categoryId', e.target.value)
                            }
                          >
                            <option value="">Usar categoria avulsa</option>
                            {categories.map((c) => (
                              <option key={c.id} value={c.id}>
                                {c.name}
                                {c.active ? '' : ' (inativa)'}
                              </option>
                            ))}
                          </select>
                        </label>
                        {!form.categoryId && (
                          <label className="field">
                            Categoria avulsa
                            <input
                              required
                              maxLength={80}
                              value={form.category}
                              onChange={(e) =>
                                update('category', e.target.value)
                              }
                              {...field('category')}
                            />
                            <FieldError
                              id="category-error"
                              error={errors.category}
                            />
                          </label>
                        )}
                        <label className="field">
                          Buscar cliente ou fornecedor
                          <input
                            type="search"
                            value={search}
                            onChange={(e) => setSearch(e.target.value)}
                            maxLength={255}
                          />
                        </label>
                        <label className="field">
                          Cliente / fornecedor
                          <select
                            value={form.contactId}
                            onChange={(e) =>
                              update('contactId', e.target.value)
                            }
                          >
                            <option value="">Sem vínculo</option>
                            {form.contactId &&
                              !contacts.data?.content.some(
                                (c) => c.id === form.contactId,
                              ) && (
                                <option value={form.contactId}>
                                  {row?.contactName || 'Vínculo atual'}
                                </option>
                              )}
                            {contacts.data?.content.map((c) => (
                              <option key={c.id} value={c.id}>
                                {c.name}
                              </option>
                            ))}
                          </select>
                          <small>
                            {contacts.error ||
                              (contacts.loading
                                ? 'Buscando cadastros…'
                                : contacts.data!.totalElements > 50
                                  ? 'Mostrando os primeiros 50. Refine a busca.'
                                  : 'Vínculo opcional.')}
                          </small>
                        </label>
                        <label className="field">
                          {form.mode === 'SINGLE'
                            ? 'Vencimento'
                            : 'Primeiro vencimento'}
                          <input
                            type="date"
                            required
                            value={form.dueDate}
                            onChange={(e) => update('dueDate', e.target.value)}
                            {...field('dueDate')}
                          />
                          <FieldError
                            id="dueDate-error"
                            error={errors.dueDate}
                          />
                        </label>
                        {form.mode !== 'SINGLE' && (
                          <>
                            <label className="field">
                              Quantidade de ocorrências
                              <input
                                type="number"
                                min={2}
                                max={120}
                                required
                                value={form.occurrences}
                                onChange={(e) =>
                                  update('occurrences', Number(e.target.value))
                                }
                                {...field('occurrences')}
                              />
                              <FieldError
                                id="occurrences-error"
                                error={errors.occurrences}
                              />
                            </label>
                            <section
                              className="workspace-schedule"
                              aria-live="polite"
                            >
                              <h3>Confira os vencimentos</h3>
                              {scheduleError ? (
                                <p>{scheduleError}</p>
                              ) : (
                                schedule.map((s, i) => (
                                  <div key={s.date}>
                                    <span>
                                      {i + 1}/{schedule.length} ·{' '}
                                      {dateLabel(s.date)}
                                    </span>
                                    <strong>{money(s.amount)}</strong>
                                  </div>
                                ))
                              )}
                              <small>
                                Prévia. O servidor valida os valores ao
                                confirmar.
                              </small>
                            </section>
                          </>
                        )}
                        {action === 'entry' && (
                          <label className="check">
                            <input
                              type="checkbox"
                              checked={form.paid}
                              onChange={(e) => update('paid', e.target.checked)}
                            />
                            {form.mode === 'SINGLE'
                              ? 'Já foi pago / recebido'
                              : 'Primeira ocorrência já paga / recebida'}
                          </label>
                        )}
                      </>
                    )}
                    {action === 'pay' && (
                      <>
                        <div className="workspace-form-context">
                          <span>Saldo restante</span>
                          <strong>{money(row!.remainingAmount)}</strong>
                        </div>
                        <label className="field">
                          Valor desta baixa (R$)
                          <input
                            autoFocus
                            required
                            inputMode="decimal"
                            value={form.amount}
                            onChange={(e) => update('amount', e.target.value)}
                            {...field('amount')}
                          />
                          <FieldError id="amount-error" error={errors.amount} />
                        </label>
                        <p className="workspace-caption">
                          Pode ser uma baixa parcial. A operação é identificada
                          para evitar duplicação ao repetir a mesma tentativa.
                        </p>
                      </>
                    )}
                    {(action === 'pay' || form.paid) && (
                      <label className="field">
                        Data do pagamento / recebimento
                        <input
                          type="date"
                          required
                          max={businessDate()}
                          value={form.paymentDate}
                          onChange={(e) =>
                            update('paymentDate', e.target.value)
                          }
                          {...field('paymentDate')}
                        />
                        <FieldError
                          id="paymentDate-error"
                          error={errors.paymentDate}
                        />
                      </label>
                    )}
                  </>
                )}
                <div
                  className="workspace-form-feedback"
                  role="alert"
                  tabIndex={-1}
                >
                  {error}
                </div>
                <footer className="workspace-form-actions">
                  <button
                    type="button"
                    className="btn secondary"
                    disabled={busy}
                    onClick={close}
                  >
                    Voltar
                  </button>
                  <button
                    className={'btn' + (action === 'cancel' ? ' danger' : '')}
                    disabled={busy}
                    type="submit"
                  >
                    {busy
                      ? 'Salvando…'
                      : action === 'pay'
                        ? 'Confirmar baixa'
                        : action === 'cancel'
                          ? 'Confirmar cancelamento'
                          : 'Salvar lançamento'}
                  </button>
                </footer>
              </>
            )}
          </>
        )}
      </form>
    </Dialog>
  );
}
