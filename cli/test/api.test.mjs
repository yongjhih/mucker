import test from 'node:test';
import assert from 'node:assert';
import http from 'node:http';
import { MuckerClient } from '../lib/api.mjs';

test('MuckerClient REST API interactions', async (t) => {
  let rulesDb = [];
  let pausedHandled = null;

  // Mock Mucker Server
  const server = http.createServer((req, res) => {
    const url = new URL(req.url, `http://${req.headers.host}`);

    if (req.method === 'GET' && url.pathname === '/api/status') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ status: 'running', port: 8080, app: 'test.app' }));
      return;
    }

    if (req.method === 'GET' && url.pathname === '/api/rules') {
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(rulesDb));
      return;
    }

    if (req.method === 'POST' && url.pathname === '/api/rules') {
      let body = '';
      req.on('data', chunk => body += chunk);
      req.on('end', () => {
        const rule = JSON.parse(body);
        rule.id = rule.id || 'rule_test_1';
        rulesDb.push(rule);
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify(rule));
      });
      return;
    }

    if (req.method === 'DELETE' && url.pathname === '/api/rules/rule_test_1') {
      rulesDb = rulesDb.filter(r => r.id !== 'rule_test_1');
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true }));
      return;
    }

    if (req.method === 'DELETE' && url.pathname === '/api/rules') {
      rulesDb = [];
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true }));
      return;
    }

    if (req.method === 'POST' && url.pathname.includes('/fulfill')) {
      pausedHandled = 'fulfill';
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify({ success: true }));
      return;
    }

    res.writeHead(404);
    res.end();
  });

  await new Promise(resolve => server.listen(0, '127.0.0.1', resolve));
  const port = server.address().port;
  const client = new MuckerClient(`http://127.0.0.1:${port}`);

  try {
    // 1. Status
    const status = await client.getStatus();
    assert.strictEqual(status.status, 'running');
    assert.strictEqual(status.app, 'test.app');

    // 2. Add rule
    const newRule = await client.addRule({
      urlPattern: '/api/v1/test',
      method: 'GET',
      statusCode: 200,
      responseBody: '{"ok":true}'
    });
    assert.strictEqual(newRule.urlPattern, '/api/v1/test');

    // 3. Get rules
    const rules = await client.getRules();
    assert.strictEqual(rules.length, 1);
    assert.strictEqual(rules[0].urlPattern, '/api/v1/test');

    // 4. Fulfill paused
    await client.fulfillPaused('req_123', { statusCode: 200, responseBody: '{"mock":true}' });
    assert.strictEqual(pausedHandled, 'fulfill');

    // 5. Remove rule
    await client.removeRule('rule_test_1');
    const remaining = await client.getRules();
    assert.strictEqual(remaining.length, 0);

  } finally {
    server.close();
  }
});
