# Mucker 🤹

<p align="center">
  <strong>Muck with your network, zero certs required.</strong><br>
  <em>CDP-compatible In-App Mock Engine and Inspector for OkHttp on Android.</em>
</p>

<p align="center">
  <a href="#quickstart"><img src="https://img.shields.io/badge/Android-SDK%2021%2B-brightgreen.svg" alt="Android SDK 21+"/></a>
  <a href="#quickstart"><img src="https://img.shields.io/badge/OkHttp-4.x%20%2F%205.x-blue.svg" alt="OkHttp 4.x/5.x"/></a>
  <a href="#cdp-protocol"><img src="https://img.shields.io/badge/CDP-Fetch%20Domain%201.3-orange.svg" alt="CDP Fetch 1.3"/></a>
  <a href="#zero-ca-certificates"><img src="https://img.shields.io/badge/Certificates-Zero%20CA%20Required-success.svg" alt="Zero CA Required"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-purple.svg" alt="License MIT"/></a>
</p>

---

## 🎯 What is Mucker?

**Mucker** is the mocking sidekick that [Chucker](https://github.com/ChuckerTeam/chucker) never had.

While Chucker is an indispensable tool for *inspecting* mobile network logs, it offers **zero mocking or request tampering capabilities**. Meanwhile, traditional proxy tools (like Proxyman and Charles) hit a brick wall starting with **Android 7.0+ (Nougat)**: apps no longer trust user CA certificates by default, demanding tedious root installations or modifying `network_security_config.xml`.

**Mucker solves this permanently.** By intercepting directly in the OkHttp application pipeline and hosting a lightweight embedded HTTP/WebSocket server, Mucker enables you to:
1. **Mock any API instantly** via desktop browser, in-app mobile WebView, CLI, or automated scripts.
2. **Never install a CA root certificate** or mess with network security configs again.
3. **Use standard Chrome DevTools Protocol (CDP)** Fetch domain commands (`Fetch.enable`, `Fetch.requestPaused`, `Fetch.fulfillRequest`) natively supported by Puppeteer and Playwright.
4. **Pause in-flight requests (Breakpoints)**, tweak response codes or JSON payloads on the fly, with automatic timeout protection.

---

## 🏗️ Architecture

```
┌────────────────────────────────────────────────────────────┐
│ Android App (Debug Build)                                  │
│                                                            │
│  [OkHttp Request Pipeline]                                 │
│         │                                                  │
│         ▼                                                  │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ MuckerInterceptor                                    │  │
│  │  1. Check static MockRuleRegistry (instant hit)       │  │
│  │  2. If Breakpoint Mode: Pause Thread via Future       │  │
│  │  3. Fallthrough: Pass to real network                │  │
│  └──────────────────────────┬───────────────────────────┘  │
│                             │ Internal Event Bus           │
│                             ▼                              │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ MuckerHttpServer (Embedded Micro-Server)             │  │
│  │ • HTTP 1.1: Serves Dashboard SPA & REST API          │  │
│  │ • WebSocket: CDP JSON-RPC 2.0 (Fetch Domain)         │  │
│  │ • Discovery: /json/version, /json/list               │  │
│  └──────────────────────────┬───────────────────────────┘  │
└─────────────────────────────┼──────────────────────────────┘
                              │ Standard WebSocket / HTTP
           ┌──────────────────┴──────────────────┐
           ▼                                     ▼
┌────────────────────────┐            ┌────────────────────────┐
│ Desktop Web Browser    │            │ In-App Android WebView │
│ (http://phone-ip:8080) │            │ (MuckerActivity)       │
└────────────────────────┘            └────────────────────────┘
           ▲                                     ▲
           └──────────────────┬──────────────────┘
                              ▼
            ┌──────────────────────────────────┐
            │ Mucker CLI & Automated CDP Tools │
            │ (Puppeteer, Playwright, Python)  │
            └──────────────────────────────────┘
```

---

## 🚀 One-Line Instant Demo

Experience Mucker immediately without manual setup:

```bash
npm run demo
# or
./scripts/quickstart.sh
# or
npx mucker demo
```
*Automatically boots emulator, compiles & installs the demo app, forwards ADB port 8080, launches the app, and opens the Mucker Web Dashboard in your default browser.*

> [!TIP]
> **Zero-Install Devcontainer**: Open this repo in **GitHub Codespaces** or **VS Code Dev Containers**. The devcontainer comes pre-configured with OpenJDK 17, Android SDK 34, and Node.js 22 LTS, compiling `:app:assembleDebug` automatically!

---

## ⚡ Quickstart & JitPack Integration

### 1. Add JitPack Repository

In your root `settings.gradle.kts` (or `build.gradle.kts`):

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

### 2. Add Dependencies (Modular & Flexible)

Pick the artifact configuration that fits your project needs:

```kotlin
dependencies {
    // Option A: Complete Bundle (Headless Interceptor + In-App WebView Dashboard)
    debugImplementation("com.github.yongjhih.mucker:mucker:1.0.0")

    // Option B: Headless Interceptor (Pure OkHttp Interceptor + Embedded CDP/REST Server, Zero UI)
    // debugImplementation("com.github.yongjhih.mucker:mucker-interceptor:1.0.0")

    // Option C: In-App Dashboard (MuckerActivity, Notification Drawer, and Web Assets)
    // debugImplementation("com.github.yongjhih.mucker:mucker-dashboard:1.0.0")

    // Release: Zero-overhead no-op stubs
    releaseImplementation("com.github.yongjhih.mucker:mucker-noop:1.0.0")
}
```

### 3. Initialize in Application

```kotlin
class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Starts embedded server & posts status notification
        Mucker.install(this, MuckerConfig(port = 8080))
    }
}
```

### 4. Attach OkHttp Interceptor

```kotlin
val okHttpClient = OkHttpClient.Builder()
    .addInterceptor(Mucker.interceptor) // Simply attach Mucker!
    .build()
```

---

## 📱 Accessing the Dashboard

### On Your Phone (In-App WebView):
Tap the persistent **"Mucker Active"** notification in your Android notification drawer to open `MuckerActivity`.

### On Your Computer Browser:
* **Over Wi-Fi**: Open `http://<phone-ip>:8080` (e.g. `http://192.168.1.50:8080`).
* **Over USB / Emulator**:
  ```bash
  npx mucker forward
  npx mucker open
  ```

---

## 💻 Mucker CLI

Mucker comes with a zero-dependency CLI:

```bash
# Forward ADB port automatically
npx mucker forward

# Check connection status
npx mucker status

# Add a mock rule
npx mucker rules add "/api/v1/user/profile" --status 200 --body '{"name":"Alex","role":"Admin"}'

# Simulate a 500 error with 1.5s delay
npx mucker rules add "/api/v1/checkout" --status 500 --delay 1500 --body '{"error":"BankOffline"}'

# Stream intercepted requests live to terminal
npx mucker listen
```

---

## 🤖 Chrome DevTools Protocol (CDP) & Automation

Mucker implements the standard **CDP Fetch Domain**:
* Discovery Version: `GET http://localhost:8080/json/version`
* Target List: `GET http://localhost:8080/json/list`
* WebSocket Target: `ws://localhost:8080/devtools/page`

### Automated Mocking via Puppeteer / Playwright:
```javascript
import WebSocket from 'ws';

const ws = new WebSocket('ws://localhost:8080/devtools/page');

ws.on('open', () => {
  ws.send(JSON.stringify({
    id: 1,
    method: 'Fetch.enable',
    params: { patterns: [{ urlPattern: '*' }] }
  }));
});

ws.on('message', (raw) => {
  const msg = JSON.parse(raw);
  if (msg.method === 'Fetch.requestPaused') {
    const { requestId, request } = msg.params;
    
    // Fulfill with mock response!
    ws.send(JSON.stringify({
      id: 2,
      method: 'Fetch.fulfillRequest',
      params: {
        requestId,
        responseCode: 200,
        responseHeaders: [{ name: 'Content-Type', value: 'application/json' }],
        body: Buffer.from(JSON.stringify({ mocked: true })).toString('base64')
      }
    }));
  }
});
```

---

## 🌐 Cross-Platform Client Interceptors

Mucker is not limited to OkHttp. Any platform or HTTP client can connect to Mucker's local mock engine:

### 1. Flutter / Dart (`dio` & `package:http`)
```dart
import 'package:dio/dio.dart';
import 'package:mucker/mucker.dart';

final dio = Dio();
dio.interceptors.add(MuckerDioInterceptor()); // Evaluates Mucker mock rules & streams traffic!
```

### 2. iOS Swift (`URLSession`)
```swift
URLProtocol.registerClass(MuckerURLProtocol.self) // Intercepts URLSession without certs
```

### 3. Web & Node.js (`axios`)
```javascript
attachMucker(axiosInstance); // Intercepts Axios requests via local Mucker REST API
```

Read the full [Cross-Platform Interceptors Guide](https://yongjhih.github.io/mucker/cross-platform) for setup across Flutter, iOS, and KMP.

---

## 📊 Comparison Matrix

| Feature | **Mucker** | Chucker | Stetho (Dead) | Flipper (Dead) | Charles / Proxyman |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Zero CA Certs Required** | ✅ **Yes** | ✅ Yes | ✅ Yes | ❌ Requires Certs | ❌ Requires CA Root Cert |
| **Dynamic API Mocking** | ✅ **Yes** | ❌ Read-Only | ❌ Read-Only | ⚠️ Via Plugin | ✅ Map Local |
| **Live Breakpoints (Pause)** | ✅ **Yes** | ❌ No | ❌ No | ❌ No | ✅ Breakpoints |
| **In-App Mobile UI** | ✅ **Yes** | ✅ Yes | ❌ No | ❌ No | ❌ No |
| **Computer Browser UI** | ✅ **Yes** | ❌ No | ⚠️ chrome://inspect | ❌ Requires Electron | ❌ Requires Desktop App |
| **CDP Fetch Protocol** | ✅ **Yes** | ❌ No | ❌ Network only | ❌ Proprietary | ❌ No |
| **Cross-Platform (Dio/iOS)** | ✅ **Yes** | ❌ Android Only| ❌ Android Only| ⚠️ Complex C++ | ⚠️ Requires Desktop |
| **Release Safety** | ✅ `mucker-noop` | ✅ `chucker-no-op` | ⚠️ Manual | ⚠️ Complex | ✅ Clean |

For a deep dive into the history of CDP mock responses (from 2017 `Network.continueInterceptedRequest` to 2019 `Fetch.fulfillRequest` and 2023 DevTools UI "Override Content") and why Stetho/Flipper were abandoned, read the [Evolution of CDP Mock Response](https://yongjhih.github.io/mucker/cdp-history).

---

## 📦 Monorepo Structure

* [`mucker-interceptor/`](mucker-interceptor): Headless Android library containing the OkHttp interceptor, embedded HTTP/CDP mock engine server (zero UI dependencies).
* [`mucker-dashboard/`](mucker-dashboard): In-app Android WebView activity (`MuckerActivity`), persistent status notification, and bundled web assets.
* [`mucker/`](mucker): Complete umbrella artifact combining `:mucker-interceptor` and `:mucker-dashboard` for 1-line integration.
* [`mucker-noop/`](mucker-noop): Empty stubs for release builds ensuring zero APK overhead.
* [`app/`](app): Demo Android application showing real-time OkHttp request mocking.
* [`examples/chaos-ci/`](examples/chaos-ci): Playwright & Puppeteer chaos fault-injection test scripts for CI/CD pipelines.
* [`dart/mucker/`](dart/mucker): Dart & Flutter client package supporting **Dio** and **package:http**.
* [`dashboard/`](dashboard): Impeccable single-page application (SPA) dashboard.
* [`cli/`](cli): Zero-dependency Node.js CLI tool (`mucker`).
* [`skills/mucker/`](skills/mucker): Agent skill definition for AI coding assistants.
* [`skills/mucker-dashboard/`](skills/mucker-dashboard): Agent skill for modular dashboard plugin development.
* [`docs/`](docs): GitHub Pages Jekyll documentation website adhering to [Web Craftsmanship Standards](https://yongjhih.github.io/mucker/web-craftsmanship).
* [`.devcontainer/`](.devcontainer): Containerized development environment with pre-installed Android SDK 34 and auto-compilation.

---

## 📄 License

MIT License. See [LICENSE](LICENSE) for details.
