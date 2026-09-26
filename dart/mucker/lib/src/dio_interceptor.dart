import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:http/http.dart' as http;
import 'mock_rule.dart';

/// Dio Interceptor that hooks into Mucker to inspect, mock, and manipulate
/// HTTP network traffic without certificates.
class MuckerDioInterceptor extends Interceptor {
  final String serverUrl;
  final bool autoSyncRules;
  final List<MockRule> localRules;
  final bool reportTelemetry;

  DateTime? _lastSync;

  MuckerDioInterceptor({
    this.serverUrl = 'http://127.0.0.1:8080',
    this.autoSyncRules = true,
    List<MockRule>? initialRules,
    this.reportTelemetry = true,
  }) : localRules = List<MockRule>.from(initialRules ?? []);

  /// Add a mock rule dynamically
  void addRule(MockRule rule) {
    localRules.removeWhere((r) => r.id == rule.id);
    localRules.add(rule);
  }

  /// Remove a rule by ID
  void removeRule(String id) {
    localRules.removeWhere((r) => r.id == id);
  }

  /// Clear all rules
  void clearRules() {
    localRules.clear();
  }

  @override
  void onRequest(RequestOptions options, RequestInterceptorHandler handler) async {
    // 1. Sync rules from Mucker Server if autoSync enabled (rate-limited to every 2 seconds)
    if (autoSyncRules) {
      await _syncRulesIfDue();
    }

    final url = options.uri.toString();
    final method = options.method.toUpperCase();

    // 2. Evaluate rules against incoming request
    MockRule? matchedRule;
    for (final rule in localRules) {
      if (rule.matches(url, method)) {
        matchedRule = rule;
        break;
      }
    }

    // 3. If matched, return mock Response immediately
    if (matchedRule != null) {
      if (matchedRule.delayMs > 0) {
        await Future.delayed(Duration(milliseconds: matchedRule.delayMs));
      }

      final headersMap = Map<String, dynamic>.from(matchedRule.responseHeaders);
      headersMap['X-Mocked-By'] = 'MuckerDioInterceptor';

      final mockResponse = Response(
        requestOptions: options,
        statusCode: matchedRule.statusCode,
        data: _parseResponseBody(matchedRule.responseBody),
        headers: Headers.fromMap(
          headersMap.map((k, v) => MapEntry(k, [v.toString()])),
        ),
      );

      // Report mocked request telemetry
      if (reportTelemetry) {
        _sendTelemetry(
          url: url,
          method: method,
          statusCode: matchedRule.statusCode,
          isMocked: true,
          responseBody: matchedRule.responseBody,
          durationMs: matchedRule.delayMs,
        );
      }

      return handler.resolve(mockResponse);
    }

    // 4. Pass through to real network
    options.extra['_mucker_start_time'] = DateTime.now().millisecondsSinceEpoch;
    return handler.next(options);
  }

  @override
  void onResponse(Response response, ResponseInterceptorHandler handler) {
    if (reportTelemetry) {
      final start = response.requestOptions.extra['_mucker_start_time'] as int?;
      final duration = start != null ? DateTime.now().millisecondsSinceEpoch - start : 0;
      final bodyStr = response.data is String ? response.data : jsonEncode(response.data);

      _sendTelemetry(
        url: response.requestOptions.uri.toString(),
        method: response.requestOptions.method.toUpperCase(),
        statusCode: response.statusCode ?? 200,
        isMocked: response.headers['X-Mocked-By'] != null,
        responseBody: bodyStr,
        durationMs: duration,
      );
    }
    return handler.next(response);
  }

  @override
  void onError(DioException err, ErrorInterceptorHandler handler) {
    if (reportTelemetry) {
      _sendTelemetry(
        url: err.requestOptions.uri.toString(),
        method: err.requestOptions.method.toUpperCase(),
        statusCode: err.response?.statusCode ?? 0,
        isMocked: false,
        responseBody: err.message ?? 'Network Error',
        durationMs: 0,
      );
    }
    return handler.next(err);
  }

  Future<void> _syncRulesIfDue() async {
    final now = DateTime.now();
    if (_lastSync != null && now.difference(_lastSync!).inSeconds < 2) {
      return;
    }
    _lastSync = now;

    try {
      final uri = Uri.parse('$serverUrl/api/rules');
      final res = await http.get(uri).timeout(const Duration(milliseconds: 350));
      if (res.statusCode == 200) {
        final List list = jsonDecode(res.body);
        localRules.clear();
        for (final item in list) {
          if (item is Map<String, dynamic>) {
            localRules.add(MockRule.fromJson(item));
          }
        }
      }
    } catch (_) {
      // Server unreachable; keep using local rules
    }
  }

  dynamic _parseResponseBody(String rawBody) {
    try {
      return jsonDecode(rawBody);
    } catch (_) {
      return rawBody;
    }
  }

  void _sendTelemetry({
    required String url,
    required String method,
    required int statusCode,
    required bool isMocked,
    required String responseBody,
    required int durationMs,
  }) {
    // Fire-and-forget report to Mucker Server
    http.post(
      Uri.parse('$serverUrl/api/traffic'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'url': url,
        'method': method,
        'statusCode': statusCode,
        'isMocked': isMocked,
        'responseBody': responseBody,
        'responseTime': durationMs,
        'timestamp': DateTime.now().millisecondsSinceEpoch,
      }),
    ).catchError((_) => http.Response('', 500));
  }
}
