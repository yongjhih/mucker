import 'dart:convert';
import 'package:http/http.dart' as http;
import 'mock_rule.dart';

/// An HTTP Client wrapper implementing `http.BaseClient` that intercepts
/// requests and provides zero-cert mocking with Mucker.
class MuckerHttpClient extends http.BaseClient {
  final http.Client _inner;
  final String serverUrl;
  final List<MockRule> rules;

  MuckerHttpClient({
    http.Client? innerClient,
    this.serverUrl = 'http://127.0.0.1:8080',
    List<MockRule>? initialRules,
  })  : _inner = innerClient ?? http.Client(),
        rules = List<MockRule>.from(initialRules ?? []);

  void addRule(MockRule rule) {
    rules.removeWhere((r) => r.id == rule.id);
    rules.add(rule);
  }

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final url = request.url.toString();
    final method = request.method.toUpperCase();

    // Check if matched
    MockRule? matched;
    for (final r in rules) {
      if (r.matches(url, method)) {
        matched = r;
        break;
      }
    }

    if (matched != null) {
      if (matched.delayMs > 0) {
        await Future.delayed(Duration(milliseconds: matched.delayMs));
      }

      final bodyBytes = utf8.encode(matched.responseBody);
      final headers = Map<String, String>.from(matched.responseHeaders);
      headers['x-mocked-by'] = 'MuckerHttpClient';
      headers['X-Mocked-By'] = 'MuckerHttpClient';

      return http.StreamedResponse(
        Stream.value(bodyBytes),
        matched.statusCode,
        contentLength: bodyBytes.length,
        headers: headers,
        request: request,
      );
    }

    return _inner.send(request);
  }

  @override
  void close() {
    _inner.close();
  }
}
