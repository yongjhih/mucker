# Mucker CLI

Command line interface for **Mucker** — Zero-cert OkHttp network mocking and CDP inspection on Android.

## Installation

```bash
# Direct execution with npx
npx mucker --help

# Or install globally
npm install -g mucker
```

## Quick Start

```bash
# 1. Forward ADB port to connected device
mucker forward

# 2. Check connection status
mucker status

# 3. Add a mock rule
mucker rules add "/api/v1/user/profile" --status 200 --body '{"name":"Mucker Hero"}'

# 4. Stream real-time requests to terminal
mucker listen

# 5. Open Web Dashboard in browser
mucker open
```
