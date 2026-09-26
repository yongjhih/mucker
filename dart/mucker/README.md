# mucker (Dart / Flutter Client)

Zero-certificate network mocking and inspection library for Dart & Flutter HTTP clients (**Dio** and **package:http**).

Connects to the **Mucker Mock Engine** running locally (or standalone within integration tests).

---

## Features

* **Dio Interceptor (`MuckerDioInterceptor`)**: Intercepts requests, evaluates dynamic mock rules, and mocks responses directly in the client pipeline.
* **HTTP Client (`MuckerHttpClient`)**: Drop-in wrapper around `package:http/http.dart`.
* **Zero Certificates**: No CA root certificate installation, no Android `network_security_config.xml` tampering.
* **Live Dashboard Sync**: Synchronizes mock rules and streams traffic telemetry to the Mucker SPA Dashboard and CLI in real time.

---

## Installation

Add to your `pubspec.yaml`:

```yaml
dependencies:
  dio: ^5.4.0
  mucker:
    path: ../../dart/mucker  # or git / pub.dev
```

---

## Usage

### With Dio

```dart
import 'package:dio/dio.dart';
import 'package:mucker/mucker.dart';

void main() async {
  final dio = Dio();

  // Attach Mucker Interceptor
  dio.interceptors.add(
    MuckerDioInterceptor(
      serverUrl: 'http://127.0.0.1:8080', // Default Mucker engine port
      autoSyncRules: true,                // Sync rules dynamically from Mucker
    ),
  );

  // Requests matching mock rules will instantly return mock data
  final response = await dio.get('https://api.example.com/v1/user/profile');
  print(response.data);
  print(response.headers.value('X-Mocked-By')); // "MuckerDioInterceptor"
}
```

### With `package:http`

```dart
import 'package:http/http.dart' as http;
import 'package:mucker/mucker.dart';

void main() async {
  final client = MuckerHttpClient(
    innerClient: http.Client(),
    serverUrl: 'http://127.0.0.1:8080',
  );

  final response = await client.get(Uri.parse('https://api.example.com/items'));
  print(response.body);
}
```

---

## Running Tests

```bash
dart test
```
