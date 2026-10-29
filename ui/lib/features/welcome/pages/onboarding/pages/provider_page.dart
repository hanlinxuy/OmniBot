import 'package:flutter/material.dart';
import 'package:lucide_icons_flutter/lucide_icons.dart';
import 'package:ui/theme/theme_context.dart';

import '../onboarding_l10n.dart';
import '../widgets/onboarding_page_scaffold.dart';

/// Personal builds use model providers directly, without an OmniBot account.
class OnboardingProviderPage extends StatelessWidget {
  const OnboardingProviderPage({super.key});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(20),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          OnboardingPageHeading(
            icon: LucideIcons.bot,
            title: onbTr(context, '配置自己的模型', 'Use your own model'),
          ),
          const SizedBox(height: 16),
          Text(
            onbTr(
              context,
              '直接连接自己的模型服务，无需注册或登录小万账号。你可以现在配置 API Key，也可以稍后在设置中的“模型提供商”配置。',
              'Connect your model provider directly without an OmniBot account. Configure an API key now, or add a model provider in Settings later.',
            ),
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: context.omniPalette.textSecondary,
              height: 1.65,
            ),
          ),
        ],
      ),
    );
  }
}
