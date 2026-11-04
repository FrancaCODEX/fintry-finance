import { sameContract } from './contract-format.mjs';
import { readFile, mkdtemp, rm } from 'node:fs/promises';
import { execFileSync } from 'node:child_process';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
const dir = await mkdtemp(join(tmpdir(), 'fintry-contract-'));
try {
  const generated = join(dir, 'schema.d.ts');
  execFileSync(process.execPath, ['node_modules/openapi-typescript/bin/cli.js', '../api/workspace.openapi.json', '-o', generated]);
  if (!sameContract(await readFile(generated, 'utf8'), await readFile('src/api/schema.d.ts', 'utf8'))) throw new Error('Execute npm run contracts e versione schema.d.ts.');
} finally { await rm(dir, { recursive: true, force: true }); }
