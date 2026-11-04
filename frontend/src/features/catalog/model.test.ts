import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFilters } from './model';
function locationSearch(search: string) {
  Object.defineProperty(globalThis, 'location', {
    configurable: true,
    value: { search },
  });
}
test('an explicit cleared catalog query stays cleared after reload', () => {
  locationSearch('?page=products');
  assert.equal(readFilters('products').active, 'true');
  locationSearch('?page=products&index=0');
  assert.equal(readFilters('products').active, undefined);
  locationSearch('?page=products&index=0&active=false&search=Consultoria');
  assert.deepEqual(readFilters('products'), {
    active: 'false',
    search: 'Consultoria',
    index: '0',
  });
});
test('catalog URLs reject malformed page numbers and preserve the proposal API status', () => {
  locationSearch('?page=proposals&status=OPEN&index=-1');
  assert.equal(readFilters('proposals').status, 'OPEN');
  assert.equal(readFilters('proposals').index, '0');
  locationSearch('?page=proposals&status=INVALID&index=2.5');
  assert.equal(readFilters('proposals').status, undefined);
  assert.equal(readFilters('proposals').index, '0');
  locationSearch('?page=categories&index=20');
  assert.equal(readFilters('categories').index, '0');
});
