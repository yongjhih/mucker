import 'dart:async';
import 'dart:convert';
import 'dart:io' show Platform;
import 'package:dio/dio.dart';
import 'package:http/http.dart' as http;
import 'mock_rule.dart';

/// Dio Interceptor that hooks into Mucker to inspect, mock, and manipulate
/// HTTP network traffic without certificates in Flutter & Dart applications.
class MuckerDioInterceptor extends Interceptor {
  final String serverUrl;
  final bool autoSyncRules;
  final List<MockRule> localRules;
  final bool reportTelemetry;
  final bool breakpointMode;
  final Duration breakpointTimeout;

  DateTime? _lastSync;
  http.Client? _httpClient;

  http.Client get _client => _httpClient ??= http.Client();

  MuckerDioInterceptor({
    String? serverUrl,
    this.autoSyncRules = true,
    List<MockRule>? initialRules,
    this.reportTelemetry = true,
    this.breakpointMode = false,
    this.breakpointTimeout = const Duration(seconds: 30),
    http.Client? httpClient,
  })  : serverUrl = serverUrl ?? defaultServerUrl(),
        localRules = List<MockRule>.from(initialRules ?? []),
        _httpClient = httpClient;

  /// Determines default server host, auto-resolving 10.0.2.2 for Android emulators
  static String defaultServerUrl() {
    try {
      if (Platform.isAndroid) {
        return 'http://10.0.2.2:8080';
      }
    } catch (_) {
      // Platform may throw on web or environments where dart:io is unavailable
    }
    return 'http://127.0.0.1:8080';
  }

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
    final startTime = DateTime.now().millisecondsSinceEpoch;
    final reqId = 'req_${startTime}_${(options.uri.hashCode & 0xffff).toRadixString(16)}';
    options.extra['_mucker_id'] = reqId;
    options.extra['_mucker_start_time'] = startTime;

    // 1. Capture request headers and body for telemetry
    final requestHeaders = <String, String>{};
    options.headers.forEach((k, v) {
      if (v != null) requestHeaders[k] = v.toString();
    });
    options.extra['_mucker_headers'] = requestHeaders;

    String? postData;
    if (options.data != null) {
      try {
        if (options.data is String) {
          postData = options.data as String;
        } else if (options.data is Map || options.data is List) {
          postData = jsonEncode(options.data);
        } else if (options.data is FormData) {
          final fd = options.data as FormData;
          final fields = fd.fields.map((e) => '${e.key}=${e.value}').join('&');
          postData = fields.isNotEmpty ? fields : '[FormData]';
        } else {
          postData = options.data.toString();
        }
      } catch (_) {
        postData = options.data.toString();
      }
    }
    options.extra['_mucker_post_data'] = postData;

    // 2. Sync rules from Mucker Server if autoSync enabled (rate-limited to every 2 seconds)
    if (autoSyncRules) {
      await _syncRulesIfDue();
    }

    final url = options.uri.toString();
    final method = options.method.toUpperCase();

    // 3. Breakpoint evaluation
    if (breakpointMode) {
      final pausedResult = await _handleBreakpoint(reqId, url, method, requestHeaders, postData, options);
      if (pausedResult != null) {
        if (pausedResult['action'] == 'fulfill') {
          final status = (pausedResult['statusCode'] as int?) ?? 200;
          final bodyStr = (pausedResult['responseBody']?.toString()) ?? '{}';
          final headersMap = <String, dynamic>{'X-Mocked-By': 'Mucker-Breakpoint'};
          if (pausedResult['responseHeaders'] is Map) {
            (pausedResult['responseHeaders'] as Map).forEach((k, v) {
              headersMap[k.toString()] = v.toString();
            });
          }

          final mockResponse = Response(
            requestOptions: options,
            statusCode: status,
            data: _parseResponseBody(bodyStr),
            headers: Headers.fromMap(
              headersMap.map((k, v) => MapEntry(k, [v.toString()])),
            ),
          );

          if (reportTelemetry) {
            await _sendTelemetry(
              id: reqId,
              url: url,
              method: method,
              headers: requestHeaders,
              postData: postData,
              statusCode: status,
              responseHeaders: headersMap.map((k, v) => MapEntry(k, v.toString())),
              isMocked: true,
              responseBody: bodyStr,
              durationMs: DateTime.now().millisecondsSinceEpoch - startTime,
            );
          }

          return handler.resolve(mockResponse);
        }
        // If continue action, fall through to evaluate rules or real network
      }
    }

    // 4. Evaluate static/dynamic mock rules
    MockRule? matchedRule;
    for (final rule in localRules) {
      if (rule.matches(url, method)) {
        matchedRule = rule;
        break;
      }
    }

    // 5. If matched, handle mock response or fault injection
    if (matchedRule != null) {
      if (matchedRule.delayMs > 0) {
        await Future.delayed(Duration(milliseconds: matchedRule.delayMs));
      }

      // Check fault injection
      if (matchedRule.faultType == 'timeout') {
        final err = DioException.connectionTimeout(
          timeout: Duration(milliseconds: matchedRule.delayMs),
          requestOptions: options,
        );
        if (reportTelemetry) {
          await _sendTelemetry(
            id: reqId,
            url: url,
            method: method,
            headers: requestHeaders,
            postData: postData,
            statusCode: 0,
            responseHeaders: {},
            isMocked: true,
            responseBody: 'Connection Timeout (Simulated by Mucker)',
            durationMs: matchedRule.delayMs,
          );
        }
        return handler.reject(err);
      } else if (matchedRule.faultType == 'connection_error') {
        final err = DioException.connectionError(
          requestOptions: options,
          reason: 'Network Unreachable (Simulated by Mucker)',
        );
        if (reportTelemetry) {
          await _sendTelemetry(
            id: reqId,
            url: url,
            method: method,
            headers: requestHeaders,
            postData: postData,
            statusCode: 0,
            responseHeaders: {},
            isMocked: true,
            responseBody: 'Connection Error (Simulated by Mucker)',
            durationMs: matchedRule.delayMs,
          );
        }
        return handler.reject(err);
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

      // Report telemetry
      if (reportTelemetry) {
        await _sendTelemetry(
          id: reqId,
          url: url,
          method: method,
          headers: requestHeaders,
          postData: postData,
          statusCode: matchedRule.statusCode,
          responseHeaders: matchedRule.responseHeaders,
          isMocked: true,
          responseBody: matchedRule.responseBody,
          durationMs: matchedRule.delayMs,
        );
      }

      return handler.resolve(mockResponse);
    }

    // 6. Pass through to real network
    return handler.next(options);
  }

  @override
  void onResponse(Response response, ResponseInterceptorHandler handler) {
    if (reportTelemetry) {
      final reqId = response.requestOptions.extra['_mucker_id'] as String? ?? 'req_${DateTime.now().millisecondsSinceEpoch}';
      final start = response.requestOptions.extra['_mucker_start_time'] as int?;
      final duration = start != null ? DateTime.now().millisecondsSinceEpoch - start : 0;
      final headers = (response.requestOptions.extra['_mucker_headers'] as Map<String, String>?) ?? {};
      final postData = response.requestOptions.extra['_mucker_post_data'] as String?;

      final bodyStr = response.data is String ? response.data as String : jsonEncode(response.data);
      final resHeaders = <String, String>{};
      response.headers.map.forEach((k, v) => resHeaders[k] = v.join(', '));

      _sendTelemetry(
        id: reqId,
        url: response.requestOptions.uri.toString(),
        method: response.requestOptions.method.toUpperCase(),
        headers: headers,
        postData: postData,
        statusCode: response.statusCode ?? 200,
        responseHeaders: resHeaders,
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
      final reqId = err.requestOptions.extra['_mucker_id'] as String? ?? 'req_${DateTime.now().millisecondsSinceEpoch}';
      final start = err.requestOptions.extra['_mucker_start_time'] as int?;
      final duration = start != null ? DateTime.now().millisecondsSinceEpoch - start : 0;
      final headers = (err.requestOptions.extra['_mucker_headers'] as Map<String, String>?) ?? {};
      final postData = err.requestOptions.extra['_mucker_post_data'] as String?;

      final resHeaders = <String, String>{};
      err.response?.headers.map.forEach((k, v) => resHeaders[k] = v.join(', '));

      _sendTelemetry(
        id: reqId,
        url: err.requestOptions.uri.toString(),
        method: err.requestOptions.method.toUpperCase(),
        headers: headers,
        postData: postData,
        statusCode: err.response?.statusCode ?? 0,
        responseHeaders: resHeaders,
        isMocked: false,
        responseBody: err.response?.data?.toString() ?? err.message ?? 'Network Error',
        durationMs: duration,
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
      final res = await _client.get(uri).timeout(const Duration(milliseconds: 350));
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

  Future<Map<String, dynamic>?> _handleBreakpoint(
    String reqId,
    String url,
    String method,
    Map<String, String> headers,
    String? postData,
    RequestOptions options,
  ) async {
    try {
      // 1. Notify server about paused request
      final pauseUri = Uri.parse('$serverUrl/api/paused');
      await _client.post(
        pauseUri,
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'id': reqId,
          'url': url,
          'method': method,
          'headers': headers,
          'postData': postData,
          'timestamp': DateTime.now().millisecondsSinceEpoch,
          'isPaused': true,
        }),
      ).timeout(const Duration(milliseconds: 500));

      // 2. Poll for developer action in Mucker Dashboard
      final deadline = DateTime.now().add(breakpointTimeout);
      final pollUri = Uri.parse('$serverUrl/api/paused/$reqId');

      while (DateTime.now().isBefore(deadline)) {
        await Future.delayed(const Duration(milliseconds: 250));
        final res = await _client.get(pollUri).timeout(const Duration(milliseconds: 400));
        if (res.statusCode == 200) {
          final data = jsonDecode(res.body);
          if (data is Map<String, dynamic>) {
            if (data['status'] == 'fulfilled') {
              return {
                'action': 'fulfill',
                'statusCode': data['statusCode'] ?? 200,
                'responseBody': data['responseBody'] ?? '{}',
                'responseHeaders': data['responseHeaders'] ?? {},
              };
            } else if (data['status'] == 'continue') {
              return {'action': 'continue'};
            }
          }
        } else if (res.statusCode == 404) {
          break;
        }
      }
    } catch (_) {
      // Fallback on network or timeout error
    }
    return {'action': 'continue'};
  }

  dynamic _parseResponseBody(String rawBody) {
    try {
      return jsonDecode(rawBody);
    } catch (_) {
      return rawBody;
    }
  }

  Future<void> _sendTelemetry({
    required String id,
    required String url,
    required String method,
    required Map<String, String> headers,
    String? postData,
    required int statusCode,
    required Map<String, String> responseHeaders,
    required bool isMocked,
    required String responseBody,
    required int durationMs,
  }) async {
    try {
      await _client.post(
        Uri.parse('$serverUrl/api/traffic'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'id': id,
          'url': url,
          'method': method,
          'headers': headers,
          'postData': postData,
          'statusCode': statusCode,
          'responseHeaders': responseHeaders,
          'isMocked': isMocked,
          'responseBody': responseBody,
          'responseTime': durationMs,
          'timestamp': DateTime.now().millisecondsSinceEpoch,
        }),
      ).timeout(const Duration(milliseconds: 400));
    } catch (_) {}
  }
}
