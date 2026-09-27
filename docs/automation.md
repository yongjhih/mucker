---
layout: doc
title: CDP & E2E Automation
subtitle: Automating mobile mock data injection in CI/CD using Puppeteer, Playwright, Python, and AI Agents.
---

Because Mucker exposes standard Chrome DevTools Protocol (CDP) discovery and WebSocket endpoints, external test runners can control network responses on real Android devices or emulators without touching the host app code.

## 1. Discovery Endpoints

When Mucker is running, it exposes standard CDP discovery endpoints:

* **Version**: `GET http://localhost:8080/json/version`
* **Target List**: `GET http://localhost:8080/json/list`
* **WebSocket Endpoint**: `ws://localhost:8080/devtools/page`

---

## 2. Node.js & WebSocket Integration

You can connect directly to the WebSocket and drive mock responses using the `Fetch` domain:

```javascript
import WebSocket from 'ws';

const ws = new WebSocket('ws://localhost:8080/devtools/page');

ws.on('open', () => {
  console.log('Connected to Mucker CDP WebSocket');

  // 1. Enable request pausing for all API requests
  ws.send(JSON.stringify({
    id: 1,
    method: 'Fetch.enable',
    params: {
      patterns: [{ urlPattern: '*/api/*' }]
    }
  }));
});

ws.on('message', (data) => {
  const msg = JSON.parse(data.toString());

  // 2. Intercept paused requests
  if (msg.method === 'Fetch.requestPaused') {
    const { requestId, request } = msg.params;
    console.log(`[CDP Intercepted] ${request.method} ${request.url}`);

    if (request.url.includes('/checkout')) {
      // Mock failure
      ws.send(JSON.stringify({
        id: 2,
        method: 'Fetch.fulfillRequest',
        params: {
          requestId,
          responseCode: 500,
          responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
          body: Buffer.from(JSON.stringify({ error: 'CardDeclined' })).toString('base64')
        }
      }));
    } else {
      // Allow other requests to proceed
      ws.send(JSON.stringify({
        id: 3,
        method: 'Fetch.continueRequest',
        params: { requestId }
      }));
    }
  }
});
```

---

## 3. Python Integration

```python
import asyncio
import json
import base64
import websockets

async def run_mucker_test():
    uri = "ws://localhost:8080/devtools/page"
    async with websockets.connect(uri) as ws:
        # Enable Fetch domain
        await ws.send(json.dumps({
            "id": 1,
            "method": "Fetch.enable",
            "params": {"patterns": [{"urlPattern": "*"}]}
        }))

        async for message in ws:
            event = json.loads(message)
            if event.get("method") == "Fetch.requestPaused":
                req_id = event["params"]["requestId"]
                url = event["params"]["request"]["url"]
                print(f"Intercepted {url}")

                # Fulfill with mock
                mock_body = base64.b64encode(b'{"mock": true}').decode('utf-8')
                await ws.send(json.dumps({
                    "id": 2,
                    "method": "Fetch.fulfillRequest",
                    "params": {
                        "requestId": req_id,
                        "responseCode": 200,
                        "responseHeaders": [{"name": "Content-Type", "value": "application/json"}],
                        "body": mock_body
                    }
                }))

asyncio.run(run_mucker_test())
```

---

## 4. Playwright & Puppeteer Chaos Engineering in CI/CD

Mucker provides ready-to-run chaos injection scripts (`examples/chaos-ci/`) to test app fault-tolerance directly inside GitHub Actions or GitLab CI.

### Puppeteer CDP Session Chaos Injection

```javascript
import puppeteer from 'puppeteer';

// Connect Puppeteer directly to Mucker's CDP WebSocket on the Android device
const browser = await puppeteer.connect({
  browserWSEndpoint: 'ws://localhost:8080/devtools/page'
});

const pages = await browser.pages();
const client = await pages[0].target().createCDPSession();

// Enable network interception
await client.send('Fetch.enable', {
  patterns: [{ urlPattern: '*' }]
});

// Randomly inject 500 errors and artificial latency
client.on('Fetch.requestPaused', async (event) => {
  const isChaos = Math.random() < 0.35; // 35% error rate

  if (isChaos) {
    console.log(`[Chaos] Injecting HTTP 500 into ${event.request.url}`);
    await client.send('Fetch.fulfillRequest', {
      requestId: event.requestId,
      responseCode: 500,
      responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
      body: Buffer.from(JSON.stringify({ error: 'Chaos Injected Failure' })).toString('base64')
    });
  } else {
    await client.send('Fetch.continueRequest', { requestId: event.requestId });
  }
});
```

### One-Command Chaos Execution
Run the zero-dependency built-in chaos injector against your connected Android app or emulator:

```bash
npm run chaos
# or
node examples/chaos-ci/chaos-injector.mjs
```

---

## 5. AI Agent Skill Integration

Mucker includes an agent skill definition at `skills/mucker/SKILL.md`. When using AI coding assistants (such as Google Antigravity, Cursor, or Claude Code), the agent can:
1. Automatically run `mucker forward` to bridge ADB.
2. Inject edge-case mock responses (401 Unauthorized, 503 Maintenance, empty response lists) during interactive debugging.
3. Validate Android app resilience without requiring backend services to be deployed.

