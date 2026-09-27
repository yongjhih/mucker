# mucker_dio (Flutter & Dart Dio Interceptor)

Zero-certificate network mocking, live traffic inspection, chaos injection, and breakpoint fulfillment for **Flutter** and **Dart** using the popular [`dio`](https://pub.dev/packages/dio) package.

Connects to the **Mucker Mock Engine** running locally (or standalone within integration tests).

---

## Features

* **Zero Certificates**: No CA root certificate installation, no Android `network_security_config.xml` tampering.
* **Dynamic Mock Rules**: Match by URL regex, HTTP method, status code, delay (`delayMs`), and response headers.
* **Live Breakpoint Mode**: Pause outgoing Dio requests and fulfill or continue directly from the Mucker Web Dashboard.
* **Chaos / Fault Injection**: Injects timeouts (`DioExceptionType.connectionTimeout`) and connection errors for resilience and QA automation.
* **Full Inspection Telemetry**: Captures request headers, query params, request body (`data`), response headers, status code, and latency in real time.
* **Platform-Aware**: Automatically resolves `http://10.0.2.2:8080` on Android emulators and `http://127.0.0.1:8080` on iOS simulators / desktop.
* **Offline & Standalone**: Operates 100% in-memory with local rules in unit tests without requiring a running server.

---

## Installation

Add to your Flutter / Dart `pubspec.yaml`:

```yaml
dependencies:
  dio: ^5.4.0
  mucker_dio:
    path: ../../dart/mucker_dio  # or git / pub.dev
```

---

## Usage

### 1. Basic Integration

```dart
import 'package:dio/dio.dart';
import 'package:mucker_dio/mucker_dio.dart';

void main() async {
  final dio = Dio();

  // Attach Mucker Dio Interceptor
  dio.interceptors.add(
    MuckerDioInterceptor(
      serverUrl: 'http://127.0.0.1:8080', // Auto-detected on Android emulator (10.0.2.2)
      autoSyncRules: true,                // Sync rules dynamically from Mucker Dashboard
    ),
  );

  // Requests matching mock rules will instantly return mock data
  final response = await dio.get('https://api.example.com/v1/user/profile');
  print(response.data);
  print(response.headers.value('X-Mocked-By')); // "MuckerDioInterceptor"
}
```

### 2. Standalone In-Memory Mocking (Unit & Widget Tests)

```dart
final dio = Dio();
final interceptor = MuckerDioInterceptor(
  autoSyncRules: false,
  reportTelemetry: false,
  initialRules: [
    MockRule(
      id: 'mock_profile',
      urlPattern: '.*/user/profile.*',
      method: 'GET',
      statusCode: 200,
      responseBody: '{"username": "flutter_hero", "role": "admin"}',
      responseHeaders: {'Content-Type': 'application/json'},
    ),
  ],
);
dio.interceptors.add(interceptor);

final res = await dio.get('https://api.example.com/user/profile');
expect(res.data['username'], equals('flutter_hero'));
```

### 3. Chaos & Fault Injection

```dart
interceptor.addRule(
  MockRule(
    id: 'chaos_timeout',
    urlPattern: '.*/payment/checkout.*',
    faultType: 'timeout', // Injects connection timeout
    delayMs: 2000,
  ),
);

// Throws DioException of type connectionTimeout
await dio.post('https://api.example.com/payment/checkout');
```

---

## Running Tests

```bash
dart test
```
