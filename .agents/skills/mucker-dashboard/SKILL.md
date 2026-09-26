---
name: mucker-dashboard
description: Build modular dashboard plugins, custom panels, or rebuild the entire Mucker Dashboard SPA for Android OkHttp network inspection and mocking using REST API and Chrome DevTools Protocol (CDP) WebSocket.
---

# Mucker Dashboard Development & Modularity Guide

This skill guides AI agents and developers in creating modular plugins, custom panels, or completely new custom frontends for **Mucker** (the zero-cert CDP-compatible OkHttp mock engine for Android).

---

## 1. Architectural Overview

Mucker adopts a headless, protocol-first design:
* **Backend**: An embedded NanoHTTPD micro-server running inside the Android debug process (default port: `8080`).
* **CORS**: Fully open (`Access-Control-Allow-Origin: *`), allowing any frontend (Vite, Next.js, local files) to connect directly.
* **REST API**: Provides JSON CRUD endpoints for mock rules, traffic history, and breakpoint state.
* **WebSocket (`/devtools/page`)**: Bi-directional event stream compatible with the Chrome DevTools Protocol (CDP) **Fetch Domain** (`Fetch.requestPaused`, `Fetch.fulfillRequest`, `Fetch.continueRequest`).
* **Static Asset Host**: The server serves static files located in Android assets `mucker-web/` (i.e., `mucker/src/main/assets/mucker-web/index.html`).

---

## 2. When to Use This Skill

Activate this skill when:
- Adding custom panels or tabs to the existing Mucker Dashboard (e.g. GraphQL inspector, Chaos latency injector, HAR exporter).
- Building an entirely new frontend using React, Vue, Svelte, or SolidJS to manage Mucker mock rules.
- Embedding a custom web dashboard into the Android library APK.
- Automating dashboard interactions with headless scripts.

---

## 3. Developing Modular Plugins for the Default Dashboard

The default dashboard (`dashboard/index.html`) supports dynamic plugin registration.

### Plugin Interface Structure

```javascript
window.MuckerDashboard.registerPlugin({
  id: 'plugin-id',              // Unique string
  name: 'Plugin Name',          // Rendered tab/menu title
  icon: '⚡',                    // Emoji or SVG markup
  
  // Lifecycle hooks:
  init(container, state) {
    // Render custom UI into `container`
  },
  onRequest(record) {
    // Fired when an OkHttp request is intercepted
  },
  onResponse(record) {
    // Fired when an OkHttp response completes
  }
});
```

### Example: Custom Latency & Chaos Plugin
```javascript
window.MuckerDashboard.registerPlugin({
  id: 'chaos',
  name: 'Chaos Engine',
  icon: '🎲',
  init(container, state) {
    container.innerHTML = `
      <div class="card p-4">
        <h3>Network Chaos Engine</h3>
        <p>Inject latency and error rates dynamically.</p>
        <button id="btnFlaky" class="btn btn-warning">Simulate 500 Server Error</button>
      </div>
    `;
    container.querySelector('#btnFlaky').addEventListener('click', async () => {
      await fetch('/api/rules', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          id: 'chaos_error',
          urlPattern: '.*',
          method: 'ALL',
          statusCode: 500,
          responseBody: '{"error": "Simulated Chaos Outage"}',
          isEnabled: true
        })
      });
      showToast('Simulated 500 rule applied!', 'warning');
    });
  }
});
```

---

## 4. Building a Custom Dashboard from Scratch

### Connection Checklist
1. **ADB Port Forwarding**:
   ```bash
   adb forward tcp:8080 tcp:8080
   ```
2. **API Base URL**: `http://localhost:8080` (or `http://<device-ip>:8080` if on same Wi-Fi).
3. **WebSocket URL**: `ws://localhost:8080/devtools/page`.

### REST Endpoints
* `GET /api/status`: Engine metrics and client counts.
* `GET /api/rules`: Array of all active mock rules.
* `POST /api/rules`: Add/update rule (`{ id, urlPattern, method, statusCode, delayMs, responseBody, responseHeaders, isEnabled }`).
* `DELETE /api/rules?id=<id>`: Remove rule.
* `GET /api/traffic`: Historical recorded requests and responses.
* `GET /api/breakpoints`: Currently paused requests.
* `POST /api/breakpoints/fulfill`: Fulfill paused request with custom body/status.
* `POST /api/breakpoints/continue`: Release paused request to real server.
* `POST /api/config/breakpoint`: Toggle breakpoint interception mode (`{ enabled: boolean }`).

### CDP WebSocket Handling (`/devtools/page`)
* **Listen for intercepted requests**:
  ```javascript
  ws.onmessage = (event) => {
    const data = JSON.parse(event.data);
    if (data.method === 'Fetch.requestPaused') {
      const { requestId, request } = data.params;
      console.log(`Paused request to ${request.url}`);
    }
  };
  ```
* **Fulfill request with Mock JSON**:
  ```javascript
  ws.send(JSON.stringify({
    id: 1,
    method: 'Fetch.fulfillRequest',
    params: {
      requestId: 'req_123',
      responseCode: 200,
      responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
      body: btoa(JSON.stringify({ status: 'ok', data: [1, 2, 3] }))
    }
  }));
  ```

---

## 5. Bundling Custom Dashboard into the Android Library

To have your custom build embedded directly in the Android app (for In-App WebView and standalone phone browsing):

1. Compile your web project to a single distribution directory (e.g. `dist/`).
2. Copy the bundled `index.html` (and bundled assets) to:
   ```
   mucker/src/main/assets/mucker-web/index.html
   ```
3. Re-run `./gradlew :app:assembleDebug` or `:mucker:bundleLibCompileToJarDebug`.
4. When `Mucker.install(context)` is called in Android, `MuckerHttpServer` automatically serves your custom web application!

---

## 6. Impeccable Web Craftsmanship Guidelines (`pbakaus/impeccableskilled`)

When creating custom Mucker frontends or plugins:
* **Never use blocking `alert()` or `confirm()`**: Always use non-blocking toast notifications (`showToast()`).
* **Keyboard navigation**: Support shortcuts (<kbd>Cmd/Ctrl+K</kbd> to focus filter, <kbd>Cmd/Ctrl+N</kbd> to add rule, <kbd>Esc</kbd> to dismiss).
* **Accessible focus rings**: Add `:focus-visible` styles with clear contrast.
* **Dual Theme Parity**: Provide seamless Dark and Light themes with WCAG AAA contrast ratios.
* **Responsive Layout**: Ensure UI looks great both on desktop browsers and mobile In-App WebViews.
