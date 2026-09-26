---
layout: doc
title: CLI Reference
subtitle: Complete documentation for the mucker command-line interface.
---

The `mucker` CLI allows developers, QA engineers, and automated CI/CD runners to control Mucker without opening a browser.

## Installation

Run instantly without installation using `npx`:

```bash
npx mucker --help
```

Or install globally via npm:

```bash
npm install -g mucker
```

---

## Commands

### `mucker forward [port]`
Automatically discovers your ADB binary, queries connected devices or emulators, and establishes port forwarding (`adb forward tcp:<port> tcp:<port>`).

```bash
# Forward default port (8080)
mucker forward

# Forward custom port
mucker forward 8081
```

---

### `mucker status`
Checks connectivity to the Mucker server, reports host app package name, active mock rule count, and pending paused requests.

```bash
mucker status

# Check remote device over Wi-Fi
mucker status --host http://192.168.1.50:8080
```

---

### `mucker open`
Launches your default desktop browser and opens the Mucker Web Dashboard.

```bash
mucker open
```

---

### `mucker listen`
Streams intercepted network traffic in real time to your terminal with color-coded HTTP status and latency metrics:

```bash
mucker listen
```

Output preview:
```
[OUT]    GET https://dummyjson.com/api/v1/user/profile
[MOCKED] 200 https://dummyjson.com/api/v1/user/profile (2ms)
[PAUSED] POST https://dummyjson.com/api/v1/checkout (ID: req_9f201)
```

---

### `mucker rules`

#### List Active Rules:
```bash
mucker rules list
```

#### Add a Rule:
```bash
# Basic mock
mucker rules add "/api/v1/user" --status 200 --body '{"name":"Alice"}'

# With artificial latency (e.g. 1200ms)
mucker rules add "/api/v1/feed" --status 200 --delay 1200 --body '[]'

# Matching a specific HTTP method
mucker rules add "/api/v1/orders" --method POST --status 201 --body '{"orderId":"1029"}'

# Simulating a server failure
mucker rules add "/api/v1/payment" --status 500 --body '{"error":"BankOffline"}'
```

#### Remove a Rule:
```bash
mucker rules remove <ruleId>
```

#### Clear All Rules:
```bash
mucker rules clear
```

---

### `mucker fulfill <requestId>`
Fulfills an in-flight paused request (hit by a breakpoint) from the terminal:

```bash
mucker fulfill req_9f201 --status 200 --body '{"status":"approved"}'
```

---

### `mucker continue <requestId>`
Resumes a paused request and allows it to proceed to the real network:

```bash
mucker continue req_9f201
```

---

### `mucker cdp`
Displays the active Chrome DevTools Protocol WebSocket URL and copyable Puppeteer / Playwright starter snippets.

```bash
mucker cdp
```
