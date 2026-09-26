import test from 'node:test';
import assert from 'node:assert';
import { execSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const cliPath = path.resolve(__dirname, '../bin/mucker.mjs');

test('CLI prints banner and help menu', () => {
  const output = execSync(`node "${cliPath}" --help`, { encoding: 'utf-8' });
  assert.ok(output.toLowerCase().includes('mucker'));
  assert.ok(output.includes('COMMANDS:'));
  assert.ok(output.includes('forward'));
  assert.ok(output.includes('rules'));
  assert.ok(output.includes('listen'));
});

test('CLI prints version', () => {
  const output = execSync(`node "${cliPath}" --version`, { encoding: 'utf-8' });
  assert.ok(output.trim().startsWith('v1.0.0'));
});

test('CLI devices command runs without crashing', () => {
  const output = execSync(`node "${cliPath}" devices`, { encoding: 'utf-8' });
  assert.ok(output.length > 0);
});
