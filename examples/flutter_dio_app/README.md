# Mucker Flutter Dio Demo App

A sample Flutter application demonstrating zero-certificate HTTP traffic inspection, dynamic mocking, breakpoints, and chaos fault injection using [`mucker_dio`](../../dart/mucker_dio) with [Dio](https://pub.dev/packages/dio).

---

## Features

- 🚀 **Zero Certificates Required**: Plugs into Dio's `interceptors` pipeline without trusting root CAs.
- ⚡ **Dynamic Mock Rules**: Intercepts requests locally or syncs with the Mucker CDP engine / Dashboard.
- 💥 **Chaos Fault Injection**: Injects HTTP 500/503 errors, socket timeouts, and simulated latency for automated resilience testing.
- 🔄 **Real-Time Telemetry**: Reports network calls with timing, status codes, and payloads directly to the Mucker Dashboard.
- 🤖 **Emulator Auto-Host**: Automatically detects Android emulator environments (`10.0.2.2:8080`) vs desktop / simulator (`127.0.0.1:8080`).

---

## Quickstart

### 1. Run Flutter Test
```bash
cd examples/flutter_dio_app
flutter test
```

### 2. Run the App
```bash
flutter run
```

### 3. Connect to Mucker Dashboard
Start the Mucker server:
```bash
npx mucker-cli --port 8080
```
Open `http://localhost:8080` in your browser. Any requests fired from the Flutter app will stream live to the Mucker dashboard!
