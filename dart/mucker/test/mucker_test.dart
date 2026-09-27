import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mucker/mucker.dart';
import 'package:test/test.dart';

void main() {
  group('MockRule Tests', () {
    test('matches URL regex and method correctly', () {
      final rule = MockRule(
        id: 'rule_1',
        urlPattern: r'.*/api/v1/users/\d+',
        method: 'GET',
        statusCode: 200,
        responseBody: '{"id": 42, "name": "Alice"}',
      );

      expect(rule.matches('https://api.example.com/api/v1/users/42', 'GET'), isTrue);
      expect(rule.matches('https://api.example.com/api/v1/users/42', 'POST'), isFalse);
      expect(rule.matches('https://api.example.com/api/v1/products', 'GET'), isFalse);
    });

    test('ALL method matches any HTTP verb', () {
      final rule = MockRule(
        id: 'rule_all',
        urlPattern: '.*',
        method: 'ALL',
      );

      expect(rule.matches('https://api.example.com/login', 'POST'), isTrue);
      expect(rule.matches('https://api.example.com/data', 'GET'), isTrue);
      expect(rule.matches('https://api.example.com/item', 'DELETE'), isTrue);
    });

    test('disabled rule never matches', () {
      final rule = MockRule(
        id: 'rule_off',
        urlPattern: '.*',
        isEnabled: false,
      );

      expect(rule.matches('https://api.example.com', 'GET'), isFalse);
    });

    test('serializes and deserializes faultType correctly', () {
      final rule = MockRule(
        id: 'fault_1',
        urlPattern: '.*',
        faultType: 'timeout',
      );

      final json = rule.toJson();
      expect(json['faultType'], equals('timeout'));

      final parsed = MockRule.fromJson(json);
      expect(parsed.faultType, equals('timeout'));
    });
  });

  group('MuckerDioInterceptor Tests', () {
    test('intercepts matching request and returns mock response', () async {
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
            responseBody: '{"status": "ok", "username": "mucker_tester"}',
            responseHeaders: {'Content-Type': 'application/json'},
          ),
          MockRule(
            id: 'mock_error',
            urlPattern: '.*/payment/checkout.*',
            method: 'POST',
            statusCode: 402,
            responseBody: '{"error": "Payment Required"}',
          ),
        ],
      );

      dio.interceptors.add(interceptor);

      // Test matched GET request
      final res1 = await dio.get('https://example.com/api/v1/user/profile');
      expect(res1.statusCode, equals(200));
      expect(res1.data, isA<Map>());
      expect(res1.data['username'], equals('mucker_tester'));
      expect(res1.headers.value('X-Mocked-By'), equals('MuckerDioInterceptor'));

      // Test matched POST request with 402 error
      final res2 = await dio.post('https://example.com/api/v1/payment/checkout');
      expect(res2.statusCode, equals(402));
      expect(res2.data['error'], equals('Payment Required'));
    });

    test('simulates connection timeout fault injection', () async {
      final dio = Dio();
      final interceptor = MuckerDioInterceptor(
        autoSyncRules: false,
        reportTelemetry: false,
        initialRules: [
          MockRule(
            id: 'rule_timeout',
            urlPattern: '.*/slow/api.*',
            faultType: 'timeout',
            delayMs: 20,
          ),
        ],
      );
      dio.interceptors.add(interceptor);

      expect(
        () => dio.get('https://api.example.com/slow/api'),
        throwsA(isA<DioException>().having(
          (e) => e.type,
          'type',
          equals(DioExceptionType.connectionTimeout),
        )),
      );
    });

    test('simulates connection error fault injection', () async {
      final dio = Dio();
      final interceptor = MuckerDioInterceptor(
        autoSyncRules: false,
        reportTelemetry: false,
        initialRules: [
          MockRule(
            id: 'rule_conn_err',
            urlPattern: '.*/broken/api.*',
            faultType: 'connection_error',
          ),
        ],
      );
      dio.interceptors.add(interceptor);

      expect(
        () => dio.get('https://api.example.com/broken/api'),
        throwsA(isA<DioException>().having(
          (e) => e.type,
          'type',
          equals(DioExceptionType.connectionError),
        )),
      );
    });

    test('simulates delayMs latency correctly', () async {
      final dio = Dio();
      final interceptor = MuckerDioInterceptor(
        autoSyncRules: false,
        reportTelemetry: false,
        initialRules: [
          MockRule(
            id: 'rule_delay',
            urlPattern: '.*/delayed/endpoint.*',
            delayMs: 60,
            responseBody: '{"delayed": true}',
          ),
        ],
      );
      dio.interceptors.add(interceptor);

      final stopwatch = Stopwatch()..start();
      final res = await dio.get('https://api.example.com/delayed/endpoint');
      stopwatch.stop();

      expect(res.statusCode, equals(200));
      expect(stopwatch.elapsedMilliseconds, greaterThanOrEqualTo(45));
    });

    test('handles breakpoint pause and fulfill flow', () async {
      // Mock HTTP server responding to breakpoint pause and status
      final mockHttp = MockClient((request) async {
        if (request.url.path == '/api/paused' && request.method == 'POST') {
          return http.Response('{"ok": true}', 200);
        }
        if (request.url.path.startsWith('/api/paused/') && request.method == 'GET') {
          return http.Response(jsonEncode({
            'status': 'fulfilled',
            'statusCode': 201,
            'responseBody': '{"fulfilled_by": "mucker_ui"}',
            'responseHeaders': {'Content-Type': 'application/json'},
          }), 200);
        }
        return http.Response('Not Found', 404);
      });

      final dio = Dio();
      final interceptor = MuckerDioInterceptor(
        autoSyncRules: false,
        reportTelemetry: false,
        breakpointMode: true,
        breakpointTimeout: const Duration(seconds: 2),
        httpClient: mockHttp,
      );
      dio.interceptors.add(interceptor);

      final res = await dio.get('https://api.example.com/orders/pending');
      expect(res.statusCode, equals(201));
      expect(res.data, isA<Map>());
      expect(res.data['fulfilled_by'], equals('mucker_ui'));
      expect(res.headers.value('X-Mocked-By'), equals('Mucker-Breakpoint'));
    });

    test('captures and reports telemetry with headers and payload', () async {
      Map<String, dynamic>? reportedTelemetry;
      final mockHttp = MockClient((request) async {
        if (request.url.path == '/api/traffic') {
          reportedTelemetry = jsonDecode(request.body);
          return http.Response('{"ok": true}', 200);
        }
        return http.Response('Not Found', 404);
      });

      final dio = Dio();
      final interceptor = MuckerDioInterceptor(
        autoSyncRules: false,
        reportTelemetry: true,
        httpClient: mockHttp,
        initialRules: [
          MockRule(
            id: 'post_rule',
            urlPattern: '.*/items',
            method: 'POST',
            statusCode: 201,
            responseBody: '{"created": 100}',
          ),
        ],
      );
      dio.interceptors.add(interceptor);

      await dio.post(
        'https://api.example.com/items',
        data: {'name': 'Gadget', 'qty': 5},
        options: Options(headers: {'X-Custom-Client': 'FlutterApp'}),
      );

      expect(reportedTelemetry, isNotNull);
      expect(reportedTelemetry!['method'], equals('POST'));
      expect(reportedTelemetry!['statusCode'], equals(201));
      expect(reportedTelemetry!['isMocked'], isTrue);
      expect(reportedTelemetry!['postData'], contains('Gadget'));
      expect(reportedTelemetry!['headers']['X-Custom-Client'], equals('FlutterApp'));
    });

    test('dynamic addRule and removeRule work as expected', () async {
      final dio = Dio();
      final interceptor = MuckerDioInterceptor(
        autoSyncRules: false,
        reportTelemetry: false,
      );
      dio.interceptors.add(interceptor);

      interceptor.addRule(MockRule(
        id: 'dyn_1',
        urlPattern: '.*/dynamic/test.*',
        statusCode: 201,
        responseBody: '{"created": true}',
      ));

      final res = await dio.get('https://example.com/dynamic/test');
      expect(res.statusCode, equals(201));
      expect(res.data['created'], isTrue);

      interceptor.removeRule('dyn_1');
      expect(interceptor.localRules, isEmpty);

      interceptor.addRule(MockRule(id: 'r1', urlPattern: '.*'));
      interceptor.addRule(MockRule(id: 'r2', urlPattern: '.*'));
      expect(interceptor.localRules.length, equals(2));
      interceptor.clearRules();
      expect(interceptor.localRules, isEmpty);
    });
  });

  group('MuckerHttpClient Tests', () {
    test('intercepts http client requests when matched', () async {
      final mockInner = MockClient((request) async {
        return http.Response('real backend response', 200);
      });

      final client = MuckerHttpClient(
        innerClient: mockInner,
        initialRules: [
          MockRule(
            id: 'http_rule_1',
            urlPattern: '.*/mock/endpoint.*',
            statusCode: 200,
            responseBody: '{"mucker": "rocks"}',
            responseHeaders: {'Content-Type': 'application/json'},
          ),
        ],
      );

      // 1. Matched request
      final res1 = await client.get(Uri.parse('https://api.org/mock/endpoint'));
      expect(res1.statusCode, equals(200));
      expect(res1.body, equals('{"mucker": "rocks"}'));
      expect(res1.headers['x-mocked-by'], equals('MuckerHttpClient'));

      // 2. Unmatched request passes to inner client
      final res2 = await client.get(Uri.parse('https://api.org/unmatched'));
      expect(res2.statusCode, equals(200));
      expect(res2.body, equals('real backend response'));
      expect(res2.headers['x-mocked-by'], isNull);
    });
  });
}
