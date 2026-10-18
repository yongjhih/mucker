import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'mock_rule.dart';

/// Active SSE client session for streaming MCP messages
class _SseSession {
  final String sessionId;
  final HttpResponse response;
  final StreamController<String> controller;

  _SseSession(this.sessionId, this.response, this.controller);

  void sendEvent(String event, String data) {
    try {
      response.write('event: $event\r\ndata: $data\r\n\r\n');
      response.flush();
    } catch (_) {}
  }
}

/// Lightweight, zero-dependency Dart & Flutter Model Context Protocol (MCP) server.
/// Exposes JSON-RPC 2.0 / MCP over Streamable HTTP (`POST /mcp`) and Server-Sent Events (`GET /sse`).
class MuckerMcpServer {
  final HttpServer _server;
  final List<MockRule> rules;
  final List<Map<String, dynamic>> history;
  bool breakpointMode;
  final Map<String, Completer<Map<String, dynamic>>> _pendingPaused;
  final Map<String, _SseSession> _activeSseSessions = {};

  MuckerMcpServer._(
    this._server, {
    List<MockRule>? initialRules,
    List<Map<String, dynamic>>? initialHistory,
    this.breakpointMode = false,
  })  : rules = initialRules ?? [],
        history = initialHistory ?? [],
        _pendingPaused = {} {
    _listen();
  }

  int get port => _server.port;
  String get address => _server.address.address;

  /// Binds and starts the MCP HTTP and SSE server
  static Future<MuckerMcpServer> bind({
    dynamic address,
    int port = 8080,
    List<MockRule>? initialRules,
    List<Map<String, dynamic>>? initialHistory,
    bool breakpointMode = false,
  }) async {
    final addr = address ?? InternetAddress.anyIPv4;
    final server = await HttpServer.bind(addr, port, shared: true);
    return MuckerMcpServer._(
      server,
      initialRules: initialRules,
      initialHistory: initialHistory,
      breakpointMode: breakpointMode,
    );
  }

  /// Stops the MCP server and closes all active SSE sessions
  Future<void> close({bool force = false}) async {
    final sessions = _activeSseSessions.values.toList();
    _activeSseSessions.clear();
    for (final session in sessions) {
      try {
        await session.controller.close();
        await session.response.close();
      } catch (_) {}
    }
    await _server.close(force: force);
  }

  void _listen() {
    _server.listen((HttpRequest request) async {
      try {
        _setCorsHeaders(request.response);

        // Handle CORS Preflight
        if (request.method == 'OPTIONS') {
          request.response.statusCode = HttpStatus.noContent;
          await request.response.close();
          return;
        }

        final cleanPath = request.uri.path.replaceAll(RegExp(r'/+$'), '');

        // 1. Streamable HTTP: POST /mcp or POST /api/mcp
        if (request.method == 'POST' && (cleanPath == '/mcp' || cleanPath == '/api/mcp')) {
          final body = await utf8.decoder.bind(request).join();
          final response = handleMessage(body);
          request.response.statusCode = HttpStatus.ok;
          request.response.headers.contentType = ContentType('application', 'json', charset: 'utf-8');
          request.response.write(response);
          await request.response.close();
          return;
        }

        // 2. Discovery: GET /mcp or GET /api/mcp
        if (request.method == 'GET' && (cleanPath == '/mcp' || cleanPath == '/api/mcp')) {
          final discovery = {
            'service': 'mucker-dart-mcp-server',
            'version': '1.0.0',
            'transports': ['streamable-http', 'sse'],
            'endpoints': {
              'http': '/mcp',
              'sse': '/sse',
              'message': '/message',
            }
          };
          request.response.statusCode = HttpStatus.ok;
          request.response.headers.contentType = ContentType('application', 'json', charset: 'utf-8');
          request.response.write(jsonEncode(discovery));
          await request.response.close();
          return;
        }

        // 3. SSE Stream: GET /sse or GET /mcp/sse
        if (request.method == 'GET' && (cleanPath == '/sse' || cleanPath == '/mcp/sse')) {
          await _handleSseConnect(request);
          return;
        }

        // 4. SSE Client Message: POST /message or POST /messages
        if (request.method == 'POST' && (cleanPath == '/message' || cleanPath == '/messages')) {
          final body = await utf8.decoder.bind(request).join();
          await _handleSsePostMessage(request, body);
          return;
        }

        // 5. Fallback REST API
        if (cleanPath == '/api/status' && request.method == 'GET') {
          final status = {
            'status': 'running',
            'port': port,
            'breakpointMode': breakpointMode,
            'activeRulesCount': rules.length,
            'historyCount': history.length,
            'mcp': {'http': '/mcp', 'sse': '/sse'}
          };
          request.response.statusCode = HttpStatus.ok;
          request.response.headers.contentType = ContentType('application', 'json', charset: 'utf-8');
          request.response.write(jsonEncode(status));
          await request.response.close();
          return;
        }

        request.response.statusCode = HttpStatus.notFound;
        request.response.write('Not Found');
        await request.response.close();
      } catch (_) {
        try {
          request.response.statusCode = HttpStatus.internalServerError;
          await request.response.close();
        } catch (_) {}
      }
    });
  }

  Future<void> _handleSseConnect(HttpRequest request) async {
    final sessionId = 'sse_${DateTime.now().millisecondsSinceEpoch}_${request.uri.hashCode.toRadixString(16)}';
    final response = request.response;

    response.statusCode = HttpStatus.ok;
    response.bufferOutput = false;
    response.headers.set('Content-Type', 'text/event-stream; charset=utf-8');
    response.headers.set('Cache-Control', 'no-cache');
    response.headers.set('Connection', 'keep-alive');

    final controller = StreamController<String>();
    final session = _SseSession(sessionId, response, controller);
    _activeSseSessions[sessionId] = session;

    // Send initial endpoint event
    session.sendEvent('endpoint', '/message?sessionId=$sessionId');

    controller.stream.listen(
      (data) {
        session.sendEvent('message', data);
      },
      onDone: () {
        _activeSseSessions.remove(sessionId);
        response.close();
      },
      onError: (_) {
        _activeSseSessions.remove(sessionId);
        response.close();
      },
    );
  }

  Future<void> _handleSsePostMessage(HttpRequest request, String body) async {
    final sessionId = request.uri.queryParameters['sessionId'];
    final session = sessionId != null ? _activeSseSessions[sessionId] : null;

    final result = handleMessage(body);

    if (session != null) {
      if (result.isNotEmpty) {
        session.controller.add(result);
      }
      request.response.statusCode = HttpStatus.accepted;
      request.response.write('Accepted');
      await request.response.close();
    } else {
      if (result.isNotEmpty) {
        request.response.statusCode = HttpStatus.ok;
        request.response.headers.contentType = ContentType('application', 'json', charset: 'utf-8');
        request.response.write(result);
      } else {
        request.response.statusCode = HttpStatus.noContent;
      }
      await request.response.close();
    }
  }

  void _setCorsHeaders(HttpResponse response) {
    response.headers.set('Access-Control-Allow-Origin', '*');
    response.headers.set('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
    response.headers.set('Access-Control-Allow-Headers', '*');
  }

  /// Processes an MCP JSON-RPC 2.0 message string and returns the response string
  String handleMessage(String rawMessage) {
    try {
      final trimmed = rawMessage.trim();
      if (trimmed.isEmpty) return _createError(null, -32700, 'Empty payload');

      if (trimmed.startsWith('[')) {
        final list = jsonDecode(trimmed) as List;
        final results = [];
        for (final item in list) {
          final res = _processSingleRequest(item as Map<String, dynamic>);
          if (res != null) results.add(jsonDecode(res));
        }
        return jsonEncode(results);
      } else {
        final map = jsonDecode(trimmed) as Map<String, dynamic>;
        return _processSingleRequest(map) ?? '';
      }
    } catch (e) {
      return _createError(null, -32700, 'Parse error: $e');
    }
  }

  String? _processSingleRequest(Map<String, dynamic> json) {
    final id = json['id'];
    final method = json['method']?.toString() ?? '';
    final params = (json['params'] is Map) ? (json['params'] as Map<String, dynamic>) : <String, dynamic>{};

    switch (method) {
      case 'initialize':
        return _handleInitialize(id, params);
      case 'notifications/initialized':
        return id != null ? _createResult(id, {}) : null;
      case 'ping':
        return _createResult(id, {});
      case 'tools/list':
        return _handleToolsList(id);
      case 'tools/call':
        return _handleToolsCall(id, params);
      default:
        return _createError(id, -32601, 'Method not found: $method');
    }
  }

  String _handleInitialize(dynamic id, Map<String, dynamic> params) {
    final result = {
      'protocolVersion': '2024-11-05',
      'capabilities': {
        'tools': {'listChanged': false}
      },
      'serverInfo': {
        'name': 'mucker-dart-mcp-server',
        'version': '1.0.0',
      }
    };
    return _createResult(id, result);
  }

  String _handleToolsList(dynamic id) {
    final tools = [
      _createToolDefinition(
        name: 'get_status',
        description: 'Get live Mucker server status, active mock rules count, and breakpoint state.',
        inputProperties: {},
      ),
      _createToolDefinition(
        name: 'list_rules',
        description: 'List all registered mock rules currently active in Mucker memory.',
        inputProperties: {},
      ),
      _createToolDefinition(
        name: 'add_rule',
        description: 'Add or update a dynamic mock rule with custom URL pattern, method, and response body.',
        inputProperties: {
          'urlPattern': {'type': 'string', 'description': 'Regex or substring pattern to match'},
          'method': {'type': 'string', 'description': 'HTTP Method (GET, POST, ALL). Default: ALL'},
          'statusCode': {'type': 'integer', 'description': 'Status code. Default: 200'},
          'delayMs': {'type': 'integer', 'description': 'Latency in ms. Default: 0'},
          'responseBody': {'type': 'string', 'description': 'Response body. Default: {"mocked":true}'},
        },
        required: ['urlPattern'],
      ),
      _createToolDefinition(
        name: 'remove_rule',
        description: 'Delete a mock rule by ID.',
        inputProperties: {
          'id': {'type': 'string', 'description': 'The rule ID to delete'}
        },
        required: ['id'],
      ),
      _createToolDefinition(
        name: 'clear_rules',
        description: 'Clear all mock rules from Mucker engine memory.',
        inputProperties: {},
      ),
      _createToolDefinition(
        name: 'toggle_rule',
        description: 'Enable or disable a mock rule without deleting it.',
        inputProperties: {
          'id': {'type': 'string', 'description': 'Rule ID'},
          'isEnabled': {'type': 'boolean', 'description': 'true to enable, false to disable'}
        },
        required: ['id', 'isEnabled'],
      ),
      _createToolDefinition(
        name: 'set_breakpoint_mode',
        description: 'Toggle global breakpoint interception on or off.',
        inputProperties: {
          'breakpointMode': {'type': 'boolean', 'description': 'true to enable, false to disable'}
        },
        required: ['breakpointMode'],
      ),
      _createToolDefinition(
        name: 'list_history',
        description: 'Query recorded network requests history.',
        inputProperties: {
          'query': {'type': 'string', 'description': 'Search keyword'},
          'limit': {'type': 'integer', 'description': 'Maximum records. Default: 20'}
        },
      ),
      _createToolDefinition(
        name: 'fulfill_request',
        description: 'Fulfill an in-flight paused OkHttp/Dio request with a mocked payload.',
        inputProperties: {
          'requestId': {'type': 'string', 'description': 'Paused request ID'},
          'statusCode': {'type': 'integer', 'description': 'Status code. Default: 200'},
          'responseBody': {'type': 'string', 'description': 'Mocked response body'}
        },
        required: ['requestId'],
      ),
      _createToolDefinition(
        name: 'continue_request',
        description: 'Resume a paused request without modification.',
        inputProperties: {
          'requestId': {'type': 'string', 'description': 'Paused request ID'}
        },
        required: ['requestId'],
      ),
      _createToolDefinition(
        name: 'get_mock_rule_template',
        description: 'Generate a ready-to-run curl or REST API payload template for Mucker.',
        inputProperties: {
          'urlPattern': {'type': 'string', 'description': 'Target URL pattern'},
          'statusCode': {'type': 'integer', 'description': 'Status code. Default: 200'},
          'responseBody': {'type': 'string', 'description': 'Sample response body'}
        },
        required: ['urlPattern'],
      ),
    ];

    return _createResult(id, {'tools': tools});
  }

  String _handleToolsCall(dynamic id, Map<String, dynamic> params) {
    final name = params['name']?.toString() ?? '';
    final args = (params['arguments'] is Map) ? (params['arguments'] as Map<String, dynamic>) : <String, dynamic>{};

    try {
      String contentText;
      switch (name) {
        case 'get_status':
          contentText = const JsonEncoder.withIndent('  ').convert({
            'status': 'running',
            'port': port,
            'breakpointMode': breakpointMode,
            'activeRulesCount': rules.length,
            'historyCount': history.length,
          });
          break;

        case 'list_rules':
          contentText = const JsonEncoder.withIndent('  ').convert(rules.map((r) => r.toJson()).toList());
          break;

        case 'add_rule':
          final rule = MockRule(
            id: 'rule_${DateTime.now().millisecondsSinceEpoch}',
            urlPattern: args['urlPattern']?.toString() ?? '.*',
            method: args['method']?.toString() ?? 'ALL',
            statusCode: args['statusCode'] is int ? args['statusCode'] : 200,
            delayMs: args['delayMs'] is int ? args['delayMs'] : 0,
            responseBody: args['responseBody']?.toString() ?? '{"mocked":true}',
          );
          rules.removeWhere((r) => r.id == rule.id);
          rules.add(rule);
          contentText = 'Successfully created mock rule:\n${const JsonEncoder.withIndent('  ').convert(rule.toJson())}';
          break;

        case 'remove_rule':
          final ruleId = args['id']?.toString() ?? '';
          final countBefore = rules.length;
          rules.removeWhere((r) => r.id == ruleId);
          contentText = rules.length < countBefore
              ? 'Successfully removed mock rule: $ruleId'
              : 'Rule not found: $ruleId';
          break;

        case 'clear_rules':
          final count = rules.length;
          rules.clear();
          contentText = 'Cleared all $count mock rules from Mucker memory.';
          break;

        case 'toggle_rule':
          final ruleId = args['id']?.toString() ?? '';
          final isEnabled = args['isEnabled'] == true;
          final idx = rules.indexWhere((r) => r.id == ruleId);
          if (idx != -1) {
            final old = rules[idx];
            rules[idx] = MockRule(
              id: old.id,
              urlPattern: old.urlPattern,
              method: old.method,
              statusCode: old.statusCode,
              delayMs: old.delayMs,
              responseBody: old.responseBody,
              responseHeaders: old.responseHeaders,
              isEnabled: isEnabled,
              faultType: old.faultType,
            );
            contentText = 'Rule $ruleId is now ${isEnabled ? "ENABLED" : "DISABLED"}';
          } else {
            contentText = 'Rule not found: $ruleId';
          }
          break;

        case 'set_breakpoint_mode':
          final mode = args['breakpointMode'] == true;
          breakpointMode = mode;
          contentText = 'Breakpoint mode is now ${mode ? "ENABLED" : "DISABLED"}';
          break;

        case 'list_history':
          final query = (args['query']?.toString() ?? '').toLowerCase();
          final limit = args['limit'] is int ? args['limit'] as int : 20;
          final filtered = history.where((item) {
            if (query.isEmpty) return true;
            final url = item['url']?.toString().toLowerCase() ?? '';
            final body = item['responseBody']?.toString().toLowerCase() ?? '';
            return url.contains(query) || body.contains(query);
          }).take(limit).toList();
          contentText = const JsonEncoder.withIndent('  ').convert(filtered);
          break;

        case 'fulfill_request':
          final reqId = args['requestId']?.toString() ?? '';
          final statusCode = args['statusCode'] is int ? args['statusCode'] as int : 200;
          final resBody = args['responseBody']?.toString() ?? '{"mocked":true}';
          final future = _pendingPaused[reqId];
          if (future != null && !future.isCompleted) {
            future.complete({'statusCode': statusCode, 'responseBody': resBody});
            contentText = 'Successfully fulfilled request $reqId with status $statusCode';
          } else {
            contentText = 'Request $reqId not found or already completed';
          }
          break;

        case 'continue_request':
          final reqId = args['requestId']?.toString() ?? '';
          final future = _pendingPaused[reqId];
          if (future != null && !future.isCompleted) {
            future.complete({'continue': true});
            contentText = 'Successfully resumed request $reqId';
          } else {
            contentText = 'Request $reqId not found or already completed';
          }
          break;

        case 'get_mock_rule_template':
          final urlPattern = args['urlPattern']?.toString() ?? '.*';
          final statusCode = args['statusCode'] is int ? args['statusCode'] : 200;
          final body = args['responseBody']?.toString() ?? '{"mocked":true}';
          final curl = 'curl -X POST http://localhost:$port/api/rules -H "Content-Type: application/json" -d \'{"urlPattern":"$urlPattern","statusCode":$statusCode,"responseBody":${jsonEncode(body)}}\'';
          contentText = 'Mock Rule Template for $urlPattern (HTTP $statusCode):\n\n$curl';
          break;

        default:
          throw ArgumentError('Unknown tool: $name');
      }

      return _createToolCallResult(id, contentText, isError: false);
    } catch (e) {
      return _createToolCallResult(id, 'Error executing $name: $e', isError: true);
    }
  }

  Map<String, dynamic> _createToolDefinition({
    required String name,
    required String description,
    required Map<String, Map<String, String>> inputProperties,
    List<String> required = const [],
  }) {
    return {
      'name': name,
      'description': description,
      'inputSchema': {
        'type': 'object',
        'properties': inputProperties,
        if (required.isNotEmpty) 'required': required,
      }
    };
  }

  String _createToolCallResult(dynamic id, String text, {required bool isError}) {
    final result = {
      'content': [
        {'type': 'text', 'text': text}
      ],
      'isError': isError,
    };
    return _createResult(id, result);
  }

  String _createResult(dynamic id, Map<String, dynamic> result) {
    return jsonEncode({
      'jsonrpc': '2.0',
      'id': id,
      'result': result,
    });
  }

  String _createError(dynamic id, int code, String message) {
    return jsonEncode({
      'jsonrpc': '2.0',
      'id': id,
      'error': {
        'code': code,
        'message': message,
      }
    });
  }
}
