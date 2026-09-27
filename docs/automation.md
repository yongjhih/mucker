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

Mucker provides ready-to-run chaos injection scripts in `examples/chaos-ci/` tailored specifically for **QA Engineers, SDETs, and Mobile DevOps** to test app fault-tolerance directly inside GitHub Actions, GitLab CI, or local test suites.

### Why QA & Automation Teams Choose Mucker over Traditional Proxies

| Feature | Charles / Proxyman / Mitmproxy | Mucker (CDP OkHttp Engine) |
|---|---|---|
| **Root CA Certificate** | Mandatory (Fails on Android 7+ without custom XML) | **Zero CA Certificates Required** |
| **Network Security Config** | Requires modifying `res/xml/network_security_config.xml` | **Zero App XML Changes** |
| **SSL Pinning Conflict** | Breaks pinning or requires risky bypasses | **Preserves native SSL verification** |
| **Automation Protocol** | Proprietary CLI or separate proxy daemon | **Standard Chrome DevTools Protocol (CDP)** |
| **Scripting Ecosystem** | Custom addons / Python proxies | **Playwright, Puppeteer, Node.js, Python** |
| **CI/CD Flakiness** | High (Proxy port collisions, cert trust failures) | **Zero (Runs embedded inside debug APK)** |

---

### Playwright CDP Chaos Injection (`playwright-chaos.mjs`)

QA teams can use Playwright's `chromium.connectOverCDP` or `CDPSession` to intercept Android OkHttp calls in automated test pipelines:

```javascript
import { chromium } from 'playwright-core';

// Connect Playwright directly to Mucker on the Android device
const browser = await chromium.connectOverCDP('http://127.0.0.1:8080');
const context = browser.contexts()[0];
const page = context.pages()[0] || (await context.newPage());
const client = await context.newCDPSession(page);

// Enable CDP Fetch domain for all network routes
await client.send('Fetch.enable', {
  patterns: [{ urlPattern: '*' }]
});

// Intercept requests and randomly inject 500s or simulated timeouts
client.on('Fetch.requestPaused', async (event) => {
  const { requestId, request } = event;
  const isChaos500 = Math.random() < 0.25; // 25% HTTP 500
  const isTimeout = Math.random() < 0.15;  // 15% Gateway Timeout

  if (isChaos500) {
    console.log(`[Playwright Chaos] Injected 500 into ${request.method} ${request.url}`);
    await client.send('Fetch.fulfillRequest', {
      requestId,
      responseCode: 500,
      responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
      body: Buffer.from(JSON.stringify({ error: 'Playwright Injected Server Error' })).toString('base64')
    });
  } else if (isTimeout) {
    console.log(`[Playwright Chaos] Delaying ${request.url} by 4000ms (Simulated Timeout)`);
    setTimeout(async () => {
      await client.send('Fetch.fulfillRequest', {
        requestId,
        responseCode: 504,
        responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
        body: Buffer.from(JSON.stringify({ error: 'Gateway Timeout' })).toString('base64')
      }).catch(() => {});
    }, 4000);
  } else {
    // Normal pass-through
    await client.send('Fetch.continueRequest', { requestId });
  }
});
```

---

### Puppeteer CDP Chaos Injection (`puppeteer-chaos.mjs`)

```javascript
import puppeteer from 'puppeteer-core';

// Connect Puppeteer directly to Mucker's CDP WebSocket on the Android device
const browser = await puppeteer.connect({
  browserWSEndpoint: 'ws://127.0.0.1:8080/devtools/page'
});

const pages = await browser.pages();
const client = await pages[0].target().createCDPSession();

// Enable network interception
await client.send('Fetch.enable', {
  patterns: [{ urlPattern: '*/checkout*' }]
});

// Intercept checkout request and mock failure
client.on('Fetch.requestPaused', async (event) => {
  console.log(`[Puppeteer Chaos] Simulating payment gateway outage for ${event.request.url}`);
  await client.send('Fetch.fulfillRequest', {
    requestId: event.requestId,
    responseCode: 500,
    responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
    body: Buffer.from(JSON.stringify({ error: 'Payment Gateway Down', retryable: false })).toString('base64')
  });
});
```

### Running Chaos Scripts

```bash
# Playwright Chaos Runner
npm run chaos:playwright

# Puppeteer Chaos Runner
npm run chaos:puppeteer

# Zero-dependency Built-in Chaos Injector (pure WebSocket)
npm run chaos -- --rate=0.35 --timeout=4000
```

---

## 5. AI Agent Skill Integration

Mucker includes an agent skill definition at `skills/mucker/SKILL.md`. When using AI coding assistants (such as Google Antigravity, Cursor, or Claude Code), the agent can:
1. Automatically run `mucker forward` to bridge ADB.
2. Inject edge-case mock responses (401 Unauthorized, 503 Maintenance, empty response lists) during interactive debugging.
3. Validate Android app resilience without requiring backend services to be deployed.

