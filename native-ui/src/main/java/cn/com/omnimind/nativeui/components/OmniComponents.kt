package cn.com.omnimind.nativeui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.theme.LocalOmniPalette
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun OmniIcon(
    @DrawableRes icon: Int,
    description: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    tint: Color = LocalOmniPalette.current.text,
) = Icon(painterResource(icon), description, modifier.size(size), tint = tint)

/** Miuix [IconButton] (press feedback, squircle) with a 48dp touch target and Lucide icon. */
@Composable
internal fun OmniIconButton(
    @DrawableRes icon: Int,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    enabled: Boolean = true,
    tint: Color = LocalOmniPalette.current.text,
) {
    IconButton(onClick, modifier, enabled = enabled, minWidth = 48.dp, minHeight = 48.dp) {
        OmniIcon(icon, description, size = size, tint = if (enabled) tint else tint.copy(alpha = .4f))
    }
}

/** Miuix [SmallTopAppBar] on the page color with the shared back button. */
@Composable
internal fun OmniTopBar(title: String, onBack: () -> Unit) = OmniTopBar(title, onBack) {}

/** Same toolbar with trailing actions; kept as an overload so a trailing lambda stays `onBack`. */
@Composable
internal fun OmniTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    val palette = LocalOmniPalette.current
    SmallTopAppBar(
        title = title,
        color = palette.page,
        titleColor = palette.text,
        navigationIcon = {
            OmniIconButton(R.drawable.omni_chevron_left, stringResource(R.string.omni_back), onBack)
        },
        actions = actions,
    )
}

@Composable
internal fun SectionTitle(title: String, modifier: Modifier = Modifier) {
    Text(title, modifier = modifier, fontSize = 11.sp, letterSpacing = .6.sp,
        fontWeight = FontWeight.SemiBold, color = LocalOmniPalette.current.tertiaryText)
}

/**
 * A page or tab with nothing to show: an icon, a title, an optional hint and
 * an optional action (retry, enable). Centered in the space it is given.
 */
@Composable
internal fun OmniEmptyState(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
) {
    val palette = LocalOmniPalette.current
    Column(modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        OmniIcon(icon, size = 44.dp, tint = palette.tertiaryText)
        Spacer(Modifier.height(14.dp))
        Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = palette.text, textAlign = TextAlign.Center)
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(message, fontSize = 13.sp, lineHeight = 19.sp, color = palette.secondaryText, textAlign = TextAlign.Center)
        }
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction, enabled = actionEnabled, colors = ButtonDefaults.buttonColorsPrimary()) {
                Text(actionLabel, color = Color.White)
            }
        }
    }
}
