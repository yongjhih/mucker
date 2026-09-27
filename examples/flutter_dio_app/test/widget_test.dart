import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_dio_app/main.dart';

void main() {
  testWidgets('MuckerFlutterDemoApp renders and performs mocked network calls', (WidgetTester tester) async {
    await tester.pumpWidget(const MuckerFlutterDemoApp());

    // Verify initial UI elements
    expect(find.text('Mucker Dio Demo'), findsOneWidget);
    expect(find.text('Mucker CDP Interceptor'), findsOneWidget);
    expect(find.text('1. GET Profile (Mocked 200)'), findsOneWidget);
    expect(find.text('2. POST Checkout (Chaos 503)'), findsOneWidget);

    // Tap 1. GET Profile (Mocked 200)
    final profileBtn = find.byKey(const Key('btn_get_profile'));
    expect(profileBtn, findsOneWidget);
    await tester.tap(profileBtn);
    await tester.pumpAndSettle();

    // Verify profile response rendered
    expect(find.textContaining('usr_mucker_42'), findsOneWidget);
    expect(find.textContaining('QA Automation Lead'), findsOneWidget);

    // Tap 2. POST Checkout (Chaos 503)
    final checkoutBtn = find.byKey(const Key('btn_post_checkout'));
    expect(checkoutBtn, findsOneWidget);
    await tester.tap(checkoutBtn);
    await tester.pumpAndSettle();

    // Verify chaos response rendered
    expect(find.textContaining('ServiceUnavailable'), findsOneWidget);
    expect(find.textContaining('Chaos injection simulated database deadlock'), findsOneWidget);
  });
}
