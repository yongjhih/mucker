/// Direct entry point for Flutter & Dart Dio users.
///
/// Usage:
/// ```dart
/// import 'package:dio/dio.dart';
/// import 'package:mucker/dio.dart';
///
/// final dio = Dio()..interceptors.add(MuckerDioInterceptor());
/// ```
library mucker.dio;

export 'src/mock_rule.dart';
export 'src/dio_interceptor.dart';
