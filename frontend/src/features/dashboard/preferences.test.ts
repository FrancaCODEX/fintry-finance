import { test } from 'node:test';
import assert from 'node:assert/strict';
import { normalize } from './preferences';
test('malformed preferences cannot remove all series or select an unknown view', () => {
  const value = normalize({
    active: 'missing',
    current: { period: 99, type: 'invalid', series: ['oops'] },
    views: [null, { name: 'bad' }],
  });
  assert.equal(value.active, 'default');
  assert.equal(value.current.period, 6);
  assert.equal(value.current.type, 'bar');
  assert.deepEqual(value.current.series, ['income', 'expense']);
  assert.deepEqual(value.views, []);
});
test('legacy named views retain their period and series without trusting unbounded names', () => {
  const value = normalize({
    active: 'legacy',
    views: [
      {
        id: 'legacy',
        name: 'Nome'.repeat(20),
        period: 3,
        type: 'line',
        series: ['result', 'income', 'oops'],
      },
    ],
  });
  assert.equal(value.active, 'legacy');
  assert.equal(value.views[0].name.length, 32);
  assert.deepEqual(value.views[0].series, ['income', 'result']);
});
