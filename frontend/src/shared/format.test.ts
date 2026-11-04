import { test } from 'node:test';
import assert from 'node:assert/strict';
import { money, validMonth, bounds } from './format';
test('decimal strings preserve every cent even above the safe integer range', () => {
  assert.equal(
    money('100000000000000.01').replace(/\s/g, ' '),
    'R$ 100.000.000.000.000,01',
  );
  assert.equal(money('-0.05').replace(/\s/g, ' '), '-R$ 0,05');
  assert.equal(money('0.3').replace(/\s/g, ' '), 'R$ 0,30');
  assert.equal(money('invalid'), '—');
});
test('month filters reject invalid months and honor leap years', () => {
  assert.equal(validMonth('2026-13'), false);
  assert.equal(validMonth('2026-00'), false);
  assert.equal(validMonth('2026-11'), true);
  assert.equal(bounds('2028-02').end, '2028-02-29');
});
