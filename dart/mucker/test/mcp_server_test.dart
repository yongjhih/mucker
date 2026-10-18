import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'package:http/http.dart' as http;
import 'package:mucker/mucker.dart';
import 'package:test/test.dart';

void main() {
  group('MuckerMcpServer Tests', () {
    late MuckerMcpServer server;
    late int serverPort;

    setUp(() async {
      // Bind to an ephemeral port (0)
      server = await MuckerMcpServer.bind(
        address: InternetAddress.loopbackIPv4,
        port: 0,
      );
      serverPort = server.port;
    });

    tearDown(() async {
      await server.close(force: true);
    });

    test('POST /mcp handles Streamable HTTP JSON-RPC initialize', () async {
      final res = await http.post(
        Uri.parse('http://127.0.0.1:$serverPort/mcp'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'jsonrpc': '2.0',
          'id': 1,
          'method': 'initialize',
          'params': {'protocolVersion': '2024-11-05'},
        }),
      );

      expect(res.statusCode, equals(200));
      expect(res.headers['access-control-allow-origin'], equals('*'));

      final json = jsonDecode(res.body) as Map<String, dynamic>;
      expect(json['id'], equals(1));
      expect(json['result']['protocolVersion'], equals('2024-11-05'));
      expect(json['result']['serverInfo']['name'], equals('mucker-dart-mcp-server'));
    });

    test('POST /mcp returns all 11 MCP tools via tools/list', () async {
      final res = await http.post(
        Uri.parse('http://127.0.0.1:$serverPort/mcp'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'jsonrpc': '2.0',
          'id': 2,
          'method': 'tools/list',
        }),
      );

      expect(res.statusCode, equals(200));
      final json = jsonDecode(res.body) as Map<String, dynamic>;
      final tools = json['result']['tools'] as List;
      expect(tools.length, greaterThanOrEqualTo(11));

      final names = tools.map((t) => t['name']).toList();
      expect(names, contains('get_status'));
      expect(names, contains('list_rules'));
      expect(names, contains('add_rule'));
      expect(names, contains('remove_rule'));
      expect(names, contains('clear_rules'));
      expect(names, contains('toggle_rule'));
      expect(names, contains('set_breakpoint_mode'));
      expect(names, contains('list_history'));
      expect(names, contains('fulfill_request'));
      expect(names, contains('continue_request'));
      expect(names, contains('get_mock_rule_template'));
    });

    test('tools/call add_rule, list_rules, toggle_rule, and remove_rule work properly', () async {
      // 1. Add Rule
      final addRes = await http.post(
        Uri.parse('http://127.0.0.1:$serverPort/mcp'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'jsonrpc': '2.0',
          'id': 3,
          'method': 'tools/call',
          'params': {
            'name': 'add_rule',
            'arguments': {
              'urlPattern': '.*/api/v1/profile.*',
              'statusCode': 200,
              'responseBody': '{"name":"FlutterUser"}',
            }
          }
        }),
      );
      expect(addRes.statusCode, equals(200));
      expect(server.rules.length, equals(1));
      final createdRule = server.rules.first;
      expect(createdRule.urlPattern, equals('.*/api/v1/profile.*'));

      // 2. Toggle Rule
      final toggleRes = await http.post(
        Uri.parse('http://127.0.0.1:$serverPort/mcp'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'jsonrpc': '2.0',
          'id': 4,
          'method': 'tools/call',
          'params': {
            'name': 'toggle_rule',
            'arguments': {
              'id': createdRule.id,
              'isEnabled': false,
            }
          }
        }),
      );
      expect(toggleRes.statusCode, equals(200));
      expect(server.rules.first.isEnabled, isFalse);

      // 3. Remove Rule
      final removeRes = await http.post(
        Uri.parse('http://127.0.0.1:$serverPort/mcp'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'jsonrpc': '2.0',
          'id': 5,
          'method': 'tools/call',
          'params': {
            'name': 'remove_rule',
            'arguments': {'id': createdRule.id}
          }
        }),
      );
      expect(removeRes.statusCode, equals(200));
      expect(server.rules.isEmpty, isTrue);
    });

    test('GET /mcp returns discovery metadata', () async {
      final res = await http.get(Uri.parse('http://127.0.0.1:$serverPort/mcp'));
      expect(res.statusCode, equals(200));
      final json = jsonDecode(res.body) as Map<String, dynamic>;
      expect(json['service'], equals('mucker-dart-mcp-server'));
      expect(json['transports'], contains('streamable-http'));
      expect(json['transports'], contains('sse'));
    });

    test('GET /sse and POST /message full streaming lifecycle', () async {
      final client = HttpClient();
      final sseReq = await client.getUrl(Uri.parse('http://127.0.0.1:$serverPort/sse'));
      sseReq.headers.set('Accept', 'text/event-stream');
      final sseRes = await sseReq.close();

      expect(sseRes.statusCode, equals(200));
      expect(sseRes.headers.value('content-type'), contains('text/event-stream'));

      final completerEndpoint = Completer<String>();
      final completerMessage = Completer<String>();

      final broadcastStream = sseRes.transform(utf8.decoder).asBroadcastStream();

      String buffer = '';
      final sub = broadcastStream.listen((chunk) {
        buffer += chunk;
        if (buffer.contains('\r\n\r\n') || buffer.contains('\n\n')) {
          final lines = buffer.split(RegExp(r'\r?\n'));
          for (int i = 0; i < lines.length; i++) {
            final line = lines[i];
            if (line.startsWith('data: /message?sessionId=')) {
              if (!completerEndpoint.isCompleted) {
                completerEndpoint.complete(line.replaceFirst('data: ', '').trim());
              }
            } else if (line.startsWith('data: {') && line.contains('"jsonrpc"')) {
              if (!completerMessage.isCompleted) {
                completerMessage.complete(line.replaceFirst('data: ', '').trim());
              }
            }
          }
        }
      }, onError: (_) {});

      // 1. Await endpoint event
      final messageEndpoint = await completerEndpoint.future.timeout(const Duration(seconds: 3));
      expect(messageEndpoint, startsWith('/message?sessionId=sse_'));

      // 2. Post a JSON-RPC request to that message endpoint
      final postRes = await http.post(
        Uri.parse('http://127.0.0.1:$serverPort$messageEndpoint'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'jsonrpc': '2.0',
          'id': 999,
          'method': 'ping',
        }),
      );
      expect(postRes.statusCode, equals(202));

      // 3. Verify event is delivered through the open SSE stream
      final eventResultRaw = await completerMessage.future.timeout(const Duration(seconds: 3));
      final eventJson = jsonDecode(eventResultRaw) as Map<String, dynamic>;
      expect(eventJson['id'], equals(999));
      expect(eventJson['result'], isNotNull);

      await sub.cancel();
      client.close(force: true);
    });
  });
}
