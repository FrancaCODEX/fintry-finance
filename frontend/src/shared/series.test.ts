import { test } from 'node:test';
import assert from 'node:assert/strict';
import type { Series } from '../features/planning/types';
import { seriesLabel, canPauseSeries } from './series';
const series: Series = {
  id: 'series',
  description: 'Assinatura',
  kind: 'RECURRING',
  type: 'EXPENSE',
  occurrences: 2,
  totalAmount: 200,
  firstDueDate: '2026-11-01',
  lastDueDate: '2026-12-01',
  paid: 1,
  partial: 1,
  pending: 0,
  pausedOccurrences: 0,
  cancelled: 0,
  paused: false,
  stopped: false,
};
test('a partially settled series stays in progress and a cancelled series is not completed', () => {
  assert.equal(seriesLabel(series), 'Em andamento');
  assert.equal(
    seriesLabel({ ...series, partial: 0, paid: 0, cancelled: 2 }),
    'Cancelada',
  );
  assert.equal(seriesLabel({ ...series, partial: 0, paid: 2 }), 'Concluída');
});
test('pausing requires a pending future recurring occurrence', () => {
  assert.equal(canPauseSeries(series, '2026-11-04'), false);
  assert.equal(
    canPauseSeries({ ...series, partial: 0, pending: 1 }, '2026-11-04'),
    true,
  );
  assert.equal(canPauseSeries({ ...series, pending: 1 }, '2026-12-01'), false);
  assert.equal(
    canPauseSeries({ ...series, pending: 1, paused: true }, '2026-11-04'),
    false,
  );
});
