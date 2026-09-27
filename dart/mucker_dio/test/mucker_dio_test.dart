import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:mucker_dio/mucker_dio.dart';
import 'package:test/test.dart';

void main() {
  group('mucker_dio Tests', () {
    test('intercepts Dio request and returns mock response', () async {
      final dio = Dio();
      final interceptor = MuckerDioInterceptor(
        autoSyncRules: false,
        reportTelemetry: false,
        initialRules: [
          MockRule(
            id: 'rule_user',
            urlPattern: '.*/users/me',
            method: 'GET',
            statusCode: 200,
            responseBody: '{"name": "FlutterDev"}',
          ),
        ],
      );
      dio.interceptors.add(interceptor);

      final res = await dio.get('https://api.example.com/users/me');
      expect(res.statusCode, equals(200));
      expect(res.data['name'], equals('FlutterDev'));
      expect(res.headers.value('X-Mocked-By'), equals('MuckerDioInterceptor'));
    });

    test('injects simulated connection timeout', () async {
      final dio = Dio();
      final interceptor = MuckerDioInterceptor(
        autoSyncRules: false,
        reportTelemetry: false,
        initialRules: [
          MockRule(
            id: 'rule_timeout',
            urlPattern: '.*/checkout',
            faultType: 'timeout',
          ),
        ],
      );
      dio.interceptors.add(interceptor);

      expect(
        () => dio.post('https://api.example.com/checkout'),
        throwsA(isA<DioException>().having(
          (e) => e.type,
          'type',
          equals(DioExceptionType.connectionTimeout),
        )),
      );
    });

    test('handles breakpoint pause and fulfill flow', () async {
      final mockHttp = MockClient((request) async {
        if (request.url.path == '/api/paused' && request.method == 'POST') {
          return http.Response('{"ok": true}', 200);
        }
        if (request.url.path.startsWith('/api/paused/') && request.method == 'GET') {
          return http.Response(jsonEncode({
            'status': 'fulfilled',
            'statusCode': 200,
            'responseBody': '{"breakpoint": "fulfilled"}',
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

      final res = await dio.get('https://api.example.com/breakpoint/test');
      expect(res.statusCode, equals(200));
      expect(res.data['breakpoint'], equals('fulfilled'));
      expect(res.headers.value('X-Mocked-By'), equals('Mucker-Breakpoint'));
    });
  });
}
