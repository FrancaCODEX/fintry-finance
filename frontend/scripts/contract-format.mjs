// Git on Windows may check text out as CRLF. Only line endings are presentation;
// every other byte remains part of the generated-contract drift check.
export function sameContract(actual, expected) {
  return actual.replace(/\r\n/g, '\n') === expected.replace(/\r\n/g, '\n');
}
