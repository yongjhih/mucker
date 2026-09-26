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
    });
  });

  group('MuckerHttpClient Tests', () {
    test('intercepts http client requests when matched', () async {
      // Mock inner client to ensure real network is never hit
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
