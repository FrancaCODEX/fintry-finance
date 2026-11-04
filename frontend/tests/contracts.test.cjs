const { test } = require('node:test');
const assert = require('node:assert/strict');
test('contract comparison tolerates CRLF while rejecting actual type drift', async () => {
  const { sameContract } = await import('../scripts/contract-format.mjs');
  const lf = 'export type Amount = string;\nexport type Count = number;\n';
  assert.equal(sameContract(lf, lf.replaceAll('\n', '\r\n')), true);
  assert.equal(sameContract(lf.replaceAll('\n', '\r\n'), lf), true);
  assert.equal(sameContract(lf, lf.replace('Amount = string', 'Amount = number')), false);
  assert.equal(sameContract(lf, lf + 'export type Extra = string;\n'), false);
});
