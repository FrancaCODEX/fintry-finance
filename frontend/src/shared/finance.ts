// Decimal helpers are used for input and previews only; the server remains authoritative.
export function decimal(value: string, allowZero = false): string {
  const clean = value.trim().replace(',', '.');
  if (!/^\d{1,12}(\.\d{1,2})?$/.test(clean))
    throw new Error('Informe um valor com até duas casas decimais.');
  const [integer, fraction = ''] = clean.split('.');
  const cents = BigInt(integer) * 100n + BigInt(fraction.padEnd(2, '0'));
  if (!allowZero && cents === 0n)
    throw new Error('O valor deve ser maior que zero.');
  return `${BigInt(integer)}.${fraction.padEnd(2, '0')}`;
}
export function businessDate() {
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'America/Sao_Paulo',
  }).format(new Date());
}
export function monthlySchedule(
  mode: string,
  count: number,
  amount: string,
  first: string,
) {
  if (!Number.isInteger(count) || count < 2 || count > 120)
    throw new Error('Escolha de 2 a 120 ocorrências.');
  const normalized = decimal(amount),
    cents = BigInt(normalized.replace('.', ''));
  if (!/^\d{4}-\d{2}-\d{2}$/.test(first))
    throw new Error('Informe o primeiro vencimento.');
  const [year, month, day] = first.split('-').map(Number);
  const parsed = new Date(Date.UTC(year, month - 1, day));
  if (parsed.toISOString().slice(0, 10) !== first)
    throw new Error('Vencimento inválido.');
  if (mode === 'INSTALLMENT' && cents < BigInt(count))
    throw new Error('Cada parcela deve ter pelo menos um centavo.');
  return Array.from({ length: count }, (_, i) => {
    const last = new Date(Date.UTC(year, month + i, 0));
    const date = new Date(
      Date.UTC(
        last.getUTCFullYear(),
        last.getUTCMonth(),
        Math.min(day, last.getUTCDate()),
      ),
    )
      .toISOString()
      .slice(0, 10);
    const value =
      mode === 'INSTALLMENT'
        ? cents / BigInt(count) + (BigInt(i) < cents % BigInt(count) ? 1n : 0n)
        : cents;
    return {
      date,
      amount: `${value / 100n}.${String(value % 100n).padStart(2, '0')}`,
    };
  });
}
