// Display only. Financial arithmetic and authoritative totals belong to the server.
export const money = (value: number | string) => {
  if (typeof value === 'number')
    return Number.isFinite(value)
      ? new Intl.NumberFormat('pt-BR', {
          style: 'currency',
          currency: 'BRL',
        }).format(value)
      : '—';
  if (!/^-?\d+(?:\.\d{1,2})?$/.test(value)) return '—';
  const negative = value.startsWith('-');
  const [whole, fraction = ''] = value.replace('-', '').split('.');
  const parts = new Intl.NumberFormat('pt-BR', {
    style: 'currency',
    currency: 'BRL',
  }).formatToParts(BigInt(whole));
  return (
    (negative ? '-' : '') +
    parts
      .map((part) =>
        part.type === 'fraction' ? fraction.padEnd(2, '0') : part.value,
      )
      .join('')
  );
};
export const dateLabel = (value: string) =>
  new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' }).format(
    new Date(value + 'T12:00:00Z'),
  );
export const monthLabel = (value: string, short = false) =>
  new Intl.DateTimeFormat('pt-BR', {
    timeZone: 'UTC',
    month: short ? 'short' : 'long',
    ...(short ? {} : { year: 'numeric' }),
  }).format(new Date(value + '-15T12:00:00Z'));
export const validMonth = (value: string | null): value is string =>
  !!value && /^(20\d{2}|2100)-(0[1-9]|1[0-2])$/.test(value);
export function bounds(month: string) {
  const [year, m] = month.split('-').map(Number);
  return {
    start: month + '-01',
    end: new Date(Date.UTC(year, m, 0)).toISOString().slice(0, 10),
  };
}
export function currentMonth() {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'America/Sao_Paulo',
    year: 'numeric',
    month: '2-digit',
  }).format(new Date());
}
