import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:ui/widgets/omnibot_markdown_body.dart';

void main() {
  for (final selectable in [false, true]) {
    testWidgets(
      'inline code exposes its text to semantics (selectable=$selectable)',
      (tester) async {
        final semantics = tester.ensureSemantics();
        await tester.pumpWidget(
          MaterialApp(
            home: Scaffold(
              body: OmnibotMarkdownBody(
                data: '事件 ID `3553`，提前 `[60]` 分钟。',
                baseStyle: const TextStyle(fontSize: 14),
                selectable: selectable,
              ),
            ),
          ),
        );
        await tester.pumpAndSettle();
        expect(find.bySemanticsLabel(RegExp('3553')), findsWidgets);
        expect(find.bySemanticsLabel(RegExp(r'\[60\]')), findsWidgets);
        semantics.dispose();
      },
    );
  }
}
