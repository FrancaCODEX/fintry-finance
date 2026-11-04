import { test } from 'node:test';
import assert from 'node:assert/strict';
import { decimal, monthlySchedule } from './finance';
test('money input rejects rounding, signs and unsupported precision', () => {
  assert.equal(decimal(' 100,1 '), '100.10');
  assert.equal(decimal('999999999999,99'), '999999999999.99');
  for (const value of ['0', '-1', '1.001', '1e3', '1.000,00', ''])
    assert.throws(() => decimal(value));
});
test('installments preserve every cent and anchor the end of each month', () => {
  const rows = monthlySchedule('INSTALLMENT', 3, '100,00', '2028-01-31');
  assert.deepEqual(rows, [
    { date: '2028-01-31', amount: '33.34' },
    { date: '2028-02-29', amount: '33.33' },
    { date: '2028-03-31', amount: '33.33' },
  ]);
  assert.throws(() => monthlySchedule('INSTALLMENT', 3, '0.02', '2028-01-31'));
  assert.equal(
    monthlySchedule('RECURRING', 3, '10.20', '2027-01-31')[2].amount,
    '10.20',
  );
});
