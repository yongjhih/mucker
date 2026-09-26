import 'dart:convert';

/// Represents a dynamic network mock rule.
class MockRule {
  final String id;
  final String urlPattern;
  final String method;
  final int statusCode;
  final int delayMs;
  final String responseBody;
  final Map<String, String> responseHeaders;
  final bool isEnabled;

  MockRule({
    required this.id,
    required this.urlPattern,
    this.method = 'ALL',
    this.statusCode = 200,
    this.delayMs = 0,
    this.responseBody = '{}',
    this.responseHeaders = const {'Content-Type': 'application/json'},
    this.isEnabled = true,
  });

  /// Check if an incoming request matches this rule
  bool matches(String requestUrl, String requestMethod) {
    if (!isEnabled) return false;

    // Check HTTP Method
    if (method.toUpperCase() != 'ALL' &&
        method.toUpperCase() != requestMethod.toUpperCase()) {
      return false;
    }

    // Check URL pattern (supports regex and substring/glob)
    try {
      final regex = RegExp(urlPattern);
      if (regex.hasMatch(requestUrl)) return true;
    } catch (_) {
      // Fallback to substring matching if not a valid regex
      if (requestUrl.contains(urlPattern)) return true;
    }

    return false;
  }

  factory MockRule.fromJson(Map<String, dynamic> json) {
    Map<String, String> headers = {};
    if (json['responseHeaders'] is Map) {
      json['responseHeaders'].forEach((k, v) {
        headers[k.toString()] = v.toString();
      });
    }

    return MockRule(
      id: json['id']?.toString() ?? 'rule_${DateTime.now().millisecondsSinceEpoch}',
      urlPattern: json['urlPattern']?.toString() ?? '.*',
      method: json['method']?.toString() ?? 'ALL',
      statusCode: json['statusCode'] is int ? json['statusCode'] : 200,
      delayMs: json['delayMs'] is int ? json['delayMs'] : 0,
      responseBody: json['responseBody']?.toString() ?? '{}',
      responseHeaders: headers,
      isEnabled: json['isEnabled'] == null ? true : json['isEnabled'] == true,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'urlPattern': urlPattern,
      'method': method,
      'statusCode': statusCode,
      'delayMs': delayMs,
      'responseBody': responseBody,
      'responseHeaders': responseHeaders,
      'isEnabled': isEnabled,
    };
  }
}
