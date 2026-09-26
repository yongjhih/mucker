import test from 'node:test';
import assert from 'node:assert';
import { c } from '../lib/colors.mjs';

test('colors helper formats text or returns string', () => {
  const result = c.green('Success');
  assert.ok(result.includes('Success'));

  const bold = c.bold('Important');
  assert.ok(bold.includes('Important'));

  const red = c.red('Error');
  assert.ok(red.includes('Error'));
});
