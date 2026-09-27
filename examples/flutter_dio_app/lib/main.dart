import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import 'package:mucker_dio/mucker_dio.dart';

void main() {
  runApp(const MuckerFlutterDemoApp());
}

class MuckerFlutterDemoApp extends StatelessWidget {
  const MuckerFlutterDemoApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Mucker Dio Demo',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF6366F1),
          brightness: Brightness.dark,
        ),
        useMaterial3: true,
        scaffoldBackgroundColor: const Color(0xFF0F172A),
      ),
      home: const DemoHomePage(),
    );
  }
}

class DemoHomePage extends StatefulWidget {
  const DemoHomePage({super.key});

  @override
  State<DemoHomePage> createState() => _DemoHomePageState();
}

class _DemoHomePageState extends State<DemoHomePage> {
  late final Dio _dio;
  late final MuckerDioInterceptor _muckerInterceptor;

  bool _loading = false;
  String _statusText = 'Ready';
  int? _statusCode;
  int? _durationMs;
  String? _responseBody;
  String? _requestUrl;
  String? _requestMethod;

  @override
  void initState() {
    super.initState();
    _dio = Dio(BaseOptions(
      connectTimeout: const Duration(seconds: 5),
      receiveTimeout: const Duration(seconds: 5),
    ));

    _muckerInterceptor = MuckerDioInterceptor(
      serverUrl: MuckerDioInterceptor.defaultServerUrl(),
      reportTelemetry: true,
      autoSyncRules: false, // In demo, we can control rules directly or via sync
      initialRules: [
        MockRule(
          id: 'rule-profile',
          urlPattern: r'.*/api/v1/profile.*',
          statusCode: 200,
          responseBody: jsonEncode({
            'userId': 'usr_mucker_42',
            'username': 'mucker_tester',
            'role': 'QA Automation Lead',
            'status': 'active',
            'isMocked': true,
            'source': 'MuckerDioInterceptor',
          }),
          responseHeaders: {'content-type': 'application/json'},
          delayMs: 120,
        ),
        MockRule(
          id: 'rule-checkout-chaos',
          urlPattern: r'.*/api/v1/checkout.*',
          statusCode: 503,
          responseBody: jsonEncode({
            'error': 'ServiceUnavailable',
            'message': 'Chaos injection simulated database deadlock',
            'code': 503,
          }),
          responseHeaders: {'content-type': 'application/json'},
          delayMs: 80,
        ),
      ],
    );

    _dio.interceptors.add(_muckerInterceptor);
  }

  Future<void> _makeRequest({
    required String method,
    required String url,
    dynamic data,
  }) async {
    setState(() {
      _loading = true;
      _requestMethod = method;
      _requestUrl = url;
      _statusCode = null;
      _durationMs = null;
      _responseBody = null;
      _statusText = 'Sending $method $url...';
    });

    final stopwatch = Stopwatch()..start();
    try {
      Response response;
      if (method == 'POST') {
        response = await _dio.post(url, data: data);
      } else {
        response = await _dio.get(url);
      }
      stopwatch.stop();

      setState(() {
        _loading = false;
        _statusCode = response.statusCode;
        _durationMs = stopwatch.elapsedMilliseconds;
        _statusText = 'HTTP ${response.statusCode} OK';
        _responseBody = _formatJsonOrText(response.data);
      });
    } on DioException catch (e) {
      stopwatch.stop();
      setState(() {
        _loading = false;
        _statusCode = e.response?.statusCode ?? 0;
        _durationMs = stopwatch.elapsedMilliseconds;
        _statusText = 'DioException: ${e.type.name}';
        _responseBody = e.response?.data != null
            ? _formatJsonOrText(e.response!.data)
            : e.message ?? 'Unknown error';
      });
    } catch (e) {
      stopwatch.stop();
      setState(() {
        _loading = false;
        _statusCode = 0;
        _durationMs = stopwatch.elapsedMilliseconds;
        _statusText = 'Error: $e';
        _responseBody = e.toString();
      });
    }
  }

  String _formatJsonOrText(dynamic data) {
    if (data == null) return '(empty response)';
    if (data is Map || data is List) {
      return const JsonEncoder.withIndent('  ').convert(data);
    }
    if (data is String) {
      try {
        final decoded = jsonDecode(data);
        return const JsonEncoder.withIndent('  ').convert(decoded);
      } catch (_) {
        return data;
      }
    }
    return data.toString();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Mucker Dio Demo', style: TextStyle(fontWeight: FontWeight.w700)),
        backgroundColor: const Color(0xFF1E293B),
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: 'Clear Output',
            onPressed: () {
              setState(() {
                _statusText = 'Cleared';
                _statusCode = null;
                _durationMs = null;
                _responseBody = null;
                _requestUrl = null;
                _requestMethod = null;
              });
            },
          ),
        ],
      ),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Server & Interceptor Status Card
            Card(
              color: const Color(0xFF1E293B),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              child: Padding(
                padding: const EdgeInsets.all(14.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Icon(Icons.hub, color: Color(0xFF818CF8), size: 20),
                        const SizedBox(width: 8),
                        const Text(
                          'Mucker CDP Interceptor',
                          style: TextStyle(fontSize: 15, fontWeight: FontWeight.bold, color: Colors.white),
                        ),
                        const Spacer(),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: const Color(0xFF10B981).withOpacity(0.2),
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: const Color(0xFF10B981).withOpacity(0.5)),
                          ),
                          child: const Text('ACTIVE', style: TextStyle(fontSize: 11, color: Color(0xFF34D399), fontWeight: FontWeight.w600)),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Host: ${_muckerInterceptor.serverUrl}',
                      style: const TextStyle(fontSize: 12, fontFamily: 'monospace', color: Color(0xFF94A3B8)),
                    ),
                    Text(
                      'Active Mock Rules: ${_muckerInterceptor.localRules.length}',
                      style: const TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Action Buttons Section
            const Text(
              'Test Actions',
              style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: Color(0xFFCBD5E1)),
            ),
            const SizedBox(height: 8),

            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                ElevatedButton.icon(
                  key: const Key('btn_get_profile'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF6366F1),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                  onPressed: _loading
                      ? null
                      : () => _makeRequest(
                            method: 'GET',
                            url: 'https://api.example.com/api/v1/profile',
                          ),
                  icon: const Icon(Icons.bolt, size: 18),
                  label: const Text('1. GET Profile (Mocked 200)'),
                ),
                ElevatedButton.icon(
                  key: const Key('btn_post_checkout'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFFEF4444),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                  onPressed: _loading
                      ? null
                      : () => _makeRequest(
                            method: 'POST',
                            url: 'https://api.example.com/api/v1/checkout',
                            data: {'items': ['item_1', 'item_2'], 'total': 99.5},
                          ),
                  icon: const Icon(Icons.warning_amber_rounded, size: 18),
                  label: const Text('2. POST Checkout (Chaos 503)'),
                ),
                ElevatedButton.icon(
                  key: const Key('btn_get_live'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: const Color(0xFF0EA5E9),
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
                  ),
                  onPressed: _loading
                      ? null
                      : () => _makeRequest(
                            method: 'GET',
                            url: 'https://httpbin.org/get?source=mucker_dio_flutter',
                          ),
                  icon: const Icon(Icons.public, size: 18),
                  label: const Text('3. GET Live (Real Traffic)'),
                ),
              ],
            ),

            const SizedBox(height: 16),

            // Response Result Card
            Card(
              color: const Color(0xFF1E293B),
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
              child: Padding(
                padding: const EdgeInsets.all(14.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        const Text(
                          'Response Inspection',
                          style: TextStyle(fontSize: 14, fontWeight: FontWeight.bold, color: Colors.white),
                        ),
                        const Spacer(),
                        if (_loading)
                          const SizedBox(
                            width: 16,
                            height: 16,
                            child: CircularProgressIndicator(strokeWidth: 2, color: Color(0xFF818CF8)),
                          )
                        else if (_statusCode != null)
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                            decoration: BoxDecoration(
                              color: (_statusCode! >= 200 && _statusCode! < 300)
                                  ? const Color(0xFF10B981).withOpacity(0.2)
                                  : const Color(0xFFEF4444).withOpacity(0.2),
                              borderRadius: BorderRadius.circular(6),
                            ),
                            child: Text(
                              'HTTP $_statusCode · ${_durationMs ?? 0}ms',
                              style: TextStyle(
                                fontSize: 11,
                                fontWeight: FontWeight.bold,
                                color: (_statusCode! >= 200 && _statusCode! < 300)
                                    ? const Color(0xFF34D399)
                                    : const Color(0xFFF87171),
                              ),
                            ),
                          ),
                      ],
                    ),
                    const Divider(color: Color(0xFF334155), height: 20),
                    if (_requestUrl != null) ...[
                      Text(
                        '$_requestMethod $_requestUrl',
                        style: const TextStyle(fontSize: 12, fontFamily: 'monospace', color: Color(0xFF38BDF8)),
                      ),
                      const SizedBox(height: 8),
                    ],
                    Text(
                      'Status: $_statusText',
                      style: const TextStyle(fontSize: 12, color: Color(0xFF94A3B8)),
                    ),
                    const SizedBox(height: 8),
                    Container(
                      width: double.infinity,
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: const Color(0xFF0F172A),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: const Color(0xFF334155)),
                      ),
                      child: Text(
                        _responseBody ?? '// Tap an action button above to trigger network traffic',
                        style: const TextStyle(
                          fontFamily: 'monospace',
                          fontSize: 12,
                          color: Color(0xFFE2E8F0),
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
