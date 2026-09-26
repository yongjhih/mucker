---
name: mucker
description: |
  Inspect, monitor, and mock Android OkHttp network traffic dynamically using Mucker via CLI, REST API, or Chrome DevTools Protocol (CDP) WebSocket without installing certificates.
  Use when:
  - Testing Android apps under flaky network conditions or specific API error codes (401, 404, 500, 503).
  - Injecting mock responses for new backend endpoints before backend is deployed.
  - Inspecting HTTP/HTTPS requests sent by an Android application in real time.
  - Automating E2E UI testing on Android with dynamic mock data via CDP or REST API.
---

# Mucker Agent Skill

Mucker is a zero-certificate in-app network mock engine and inspector for Android OkHttp. It runs a lightweight embedded HTTP/WebSocket server inside the Android debug build, exposing REST APIs, a web dashboard, and standard Chrome DevTools Protocol (CDP) Fetch domain endpoints.

## Quick Connection Workflow

### 1. Ensure Port Forwarding (USB or Emulator)
If the device is connected via USB or running an Android emulator:
```bash
# Using Mucker CLI
mucker forward 8080

# Or using adb directly
adb forward tcp:8080 tcp:8080
```
If using Wi-Fi on the same local network, connect directly to `http://<device-ip>:8080`.

### 2. Verify Mucker Status
```bash
# Via CLI
mucker status

# Or via cURL
curl -s http://localhost:8080/api/status
```
Expected response:
```json
{
  "status": "running",
  "version": "1.0.0",
  "app": "io.github.mucker.demo",
  "port": 8080,
  "activeRulesCount": 0,
  "pausedRequestsCount": 0
}
```

---

## Mocking APIs

### Adding a Mock Rule

To intercept and mock an API matching a URL path or regex pattern:

```bash
# Via CLI
mucker rules add "/api/v1/user/profile" --status 200 --body '{"id":"usr_99","name":"Agent Tester","role":"admin"}'

# With artificial latency (e.g. 1500ms delay)
mucker rules add "/api/v1/products" --status 200 --delay 1500 --body '[{"id":1,"name":"Turbo Widget","price":29.99}]'

# Simulating an API error (e.g. 500 Server Error)
mucker rules add "/api/v1/checkout" --status 500 --body '{"error":"PaymentGatewayTimeout","code":50001}'
```

Or via direct HTTP POST:
```bash
curl -X POST http://localhost:8080/api/rules \
  -H "Content-Type: application/json" \
  -d '{
    "urlPattern": ".*/api/v1/user/profile.*",
    "method": "GET",
    "statusCode": 200,
    "delayMs": 200,
    "responseHeaders": {
      "Content-Type": "application/json",
      "X-Mocked-By": "Mucker-Agent"
    },
    "responseBody": "{\"id\":\"u101\",\"name\":\"John Doe\"}",
    "isEnabled": true
  }'
```

### Listing Active Rules
```bash
mucker rules list
# or
curl -s http://localhost:8080/api/rules
```

### Removing or Clearing Rules
```bash
# Remove specific rule by ID
mucker rules remove <rule-id>
# or
curl -X DELETE http://localhost:8080/api/rules/<rule-id>

# Clear all rules
mucker rules clear
# or
curl -X DELETE http://localhost:8080/api/rules
```

---

## Live Request Inspection (Streaming)

Stream intercepted requests live to stdout:
```bash
mucker listen
```

Or fetch recent request history:
```bash
curl -s http://localhost:8080/api/history | jq .
```

---

## Chrome DevTools Protocol (CDP) Integration

Mucker exposes standard CDP discovery endpoints and WebSocket handlers compatible with Puppeteer, Playwright, or custom scripts:

- Discovery endpoint: `http://localhost:8080/json/version`
- Target list: `http://localhost:8080/json/list`
- WebSocket target: `ws://localhost:8080/devtools/page`

### CDP Fetch Domain Methods Supported:
1. `Fetch.enable`: Activates request pausing with optional URL patterns.
   ```json
   { "id": 1, "method": "Fetch.enable", "params": { "patterns": [{ "urlPattern": "*api*" }] } }
   ```
2. `Fetch.requestPaused`: Emitted to client when an OkHttp request hits a breakpoint.
   ```json
   {
     "method": "Fetch.requestPaused",
     "params": {
       "requestId": "req_1042",
       "request": {
         "url": "https://example.com/api/v1/data",
         "method": "GET",
         "headers": { "Authorization": "Bearer token" }
       }
     }
   }
   ```
3. `Fetch.fulfillRequest`: Resolves the paused request with a mock response.
   ```json
   {
     "id": 2,
     "method": "Fetch.fulfillRequest",
     "params": {
       "requestId": "req_1042",
       "responseCode": 200,
       "responseHeaders": [{ "name": "Content-Type", "value": "application/json" }],
       "body": "eyJzdGF0dXMiOiAibW9ja2VkIn0="
     }
   }
   ```
4. `Fetch.continueRequest`: Resumes the paused request to hit the real network.
   ```json
   { "id": 3, "method": "Fetch.continueRequest", "params": { "requestId": "req_1042" } }
   ```

---

## Automation in Scripts

### Node.js Example with WebSocket:
```javascript
import WebSocket from 'ws';

const ws = new WebSocket('ws://localhost:8080/devtools/page');

ws.on('open', () => {
  // Enable Fetch interception
  ws.send(JSON.stringify({
    id: 1,
    method: 'Fetch.enable',
    params: { patterns: [{ urlPattern: '*' }] }
  }));
});

ws.on('message', (data) => {
  const msg = JSON.parse(data.toString());
  if (msg.method === 'Fetch.requestPaused') {
    const { requestId, request } = msg.params;
    console.log(`Intercepted: ${request.method} ${request.url}`);

    if (request.url.includes('/user/')) {
      // Mock with custom response
      ws.send(JSON.stringify({
        id: 2,
        method: 'Fetch.fulfillRequest',
        params: {
          requestId,
          responseCode: 200,
          responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
          body: Buffer.from(JSON.stringify({ user: 'Mocked Hero' })).toString('base64')
        }
      }));
    } else {
      // Pass through
      ws.send(JSON.stringify({
        id: 3,
        method: 'Fetch.continueRequest',
        params: { requestId }
      }));
    }
  }
});
```
