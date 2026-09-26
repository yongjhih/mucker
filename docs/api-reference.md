---
layout: doc
title: Android SDK & REST API Reference
subtitle: Complete API reference for Mucker Kotlin library and embedded HTTP endpoints.
---

## 1. Kotlin API Reference

### `io.github.mucker.Mucker`

The primary singleton providing access to the mock engine, server lifecycle, and OkHttp interceptor.

#### Methods & Properties:
* `Mucker.install(context: Context, config: MuckerConfig = MuckerConfig())`
  Initializes the engine, starts the background micro-server, and registers the system notification.
* `Mucker.interceptor: MuckerInterceptor`
  Returns the OkHttp interceptor instance to add to `OkHttpClient.Builder()`.
* `Mucker.serverUrl: String`
  Returns the local URL of the server (e.g. `http://192.168.1.50:8080`).
* `Mucker.port: Int`
  Returns the active listening port (default: `8080`).
* `Mucker.rules: MockRuleRegistry`
  Direct programmatic access to add, remove, and toggle rules in memory.
* `Mucker.history: RequestHistory`
  Access to inspected request and response records.
* `Mucker.start()` / `Mucker.stop()`
  Controls embedded HTTP & WebSocket server state.

---

### `io.github.mucker.MuckerConfig`

Configuration class passed to `Mucker.install()`.

| Property | Type | Default | Description |
| :--- | :--- | :--- | :--- |
| `port` | `Int` | `8080` | Port for the embedded HTTP and WebSocket server. |
| `showNotification` | `Boolean` | `true` | Displays persistent system notification with link to in-app dashboard. |
| `autoStart` | `Boolean` | `true` | Starts embedded server immediately during `Mucker.install()`. |
| `maxHistorySize` | `Int` | `200` | Ring buffer size for captured network requests. |
| `defaultTimeoutSeconds`| `Long` | `25L` | Safe timeout before an unhandled breakpoint automatically proceeds. |
| `breakpointMode` | `Boolean` | `false` | Enables global request pausing on all requests by default. |

---

## 2. HTTP REST API Reference

When Mucker is running in your debug APK, its embedded server responds to standard HTTP requests on `http://localhost:8080` (or the phone IP):

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `GET` | `/json/version` | CDP protocol version and browser identifier. |
| `GET` | `/json/list` | CDP target list with `webSocketDebuggerUrl`. |
| `GET` | `/api/status` | Current server state, port, package name, rule count. |
| `POST` | `/api/status` | Updates engine settings (e.g. `{ "breakpointMode": true }`). |
| `GET` | `/api/rules` | Returns JSON array of all registered mock rules. |
| `POST` | `/api/rules` | Creates or updates a mock rule. |
| `DELETE` | `/api/rules` | Clears all registered mock rules. |
| `DELETE` | `/api/rules/{id}` | Deletes a specific mock rule by ID. |
| `POST` | `/api/rules/toggle` | Toggles rule state: `{ "id": "...", "isEnabled": true }`. |
| `GET` | `/api/history` | Returns ring buffer of recent network requests and responses. |
| `DELETE` | `/api/history` | Clears recorded request history. |
| `POST` | `/api/paused/{id}/fulfill` | Fulfills a paused request: `{ "statusCode": 200, "responseBody": "..." }`. |
| `POST` | `/api/paused/{id}/continue` | Resumes a paused request without mocking. |
| `GET` | `/` or `/index.html` | Serves the in-app Web Dashboard SPA. |
