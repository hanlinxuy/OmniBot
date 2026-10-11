package cn.com.omnimind.nativeui.chat.browser

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.components.OmniIcon
import cn.com.omnimind.nativeui.components.OmniIconButton
import cn.com.omnimind.nativeui.theme.LocalOmniPalette
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import kotlin.math.roundToInt

private val WARNING = Color(0xFFB54708)
private val SECURE = Color(0xFF067647)
private val INSECURE = Color(0xFFB42318)

/**
 * The agent browser window over the native chat page (batch 5m), replacing
 * the Flutter `ChatBrowserOverlay`. [webView] hosts the agent's own WebView
 * and must stay attached for the window's life: Dart re-keyed its platform
 * view on every snapshot, which detached and reattached the WebView several
 * times per page load, dropped the address being typed and orphaned sheets.
 *
 * The window drags by its grip, resizes from a visible corner handle that no
 * longer covers the toolbar, follows the theme, and shows page prompts in the
 * window so they are answered where the page is.
 */
@Composable
fun BrowserOverlay(
    session: BrowserSession,
    frame: BrowserWindowFrame,
    onFrameChange: (BrowserWindowFrame) -> Unit,
    actions: BrowserActions,
    onClose: () -> Unit,
    onOpenSheet: (BrowserSheet) -> Unit,
    webView: @Composable (Modifier) -> Unit,
) {
    val palette = LocalOmniPalette.current
    val density = LocalDensity.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.offset { with(density) { IntOffset(frame.x.dp.roundToPx(), frame.y.dp.roundToPx()) } }
            .size(frame.width.dp, frame.height.dp)
            .shadow(18.dp, shape, ambientColor = palette.accent.copy(alpha = .2f), spotColor = palette.accent.copy(alpha = .25f))
            .clip(shape).background(palette.surface).border(1.dp, palette.border, shape),
    ) {
        Chrome(session, actions, onClose, onDrag = { dx, dy ->
            onFrameChange(frame.copy(x = frame.x + dx, y = frame.y + dy))
        })
        // The page's own progress, under the address bar (Dart only swapped the reload icon).
        AnimatedVisibility(session.loading, enter = fadeIn(), exit = fadeOut(tween(400))) {
            LinearProgressIndicator(progress = null, height = 2.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = palette.accent,
                    backgroundColor = Color.Transparent))
        }
        Banners(session)
        Prompts(session, actions)
        Box(Modifier.weight(1f).fillMaxWidth().background(palette.secondarySurface)) {
            webView(Modifier.fillMaxSize())
        }
        Toolbar(session, actions, onOpenSheet, onResize = { dw, dh ->
            onFrameChange(frame.copy(width = frame.width + dw, height = frame.height + dh))
        })
    }
}

/** The sheets the window opens; the page shows them in its popup host. */
enum class BrowserSheet { Tabs, Menu, History, Bookmarks, Downloads }

@Composable
private fun Chrome(session: BrowserSession, actions: BrowserActions, onClose: () -> Unit, onDrag: (Float, Float) -> Unit) {
    val palette = LocalOmniPalette.current
    val density = LocalDensity.current
    Row(Modifier.fillMaxWidth().background(palette.secondarySurface).padding(start = 2.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        val moveLabel = stringResource(R.string.omni_br_move)
        Box(Modifier.size(28.dp, 40.dp).semantics { contentDescription = moveLabel }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    with(density) { onDrag(drag.x.toDp().value, drag.y.toDp().value) }
                }
            }, contentAlignment = Alignment.Center) {
            OmniIcon(R.drawable.omni_grip, size = 16.dp, tint = palette.tertiaryText)
        }
        AddressBar(session, actions.navigate, Modifier.weight(1f))
        SmallButton(R.drawable.omni_star, stringResource(R.string.omni_br_bookmark), actions.toggleBookmark,
            tint = if (session.bookmarked) Color(0xFFF5A623) else palette.secondaryText)
        AnimatedContent(session.loading, transitionSpec = { (scaleIn() + fadeIn()) togetherWith (scaleOut() + fadeOut()) },
            label = "reload-stop") { loading ->
            if (loading) SmallButton(R.drawable.omni_x, stringResource(R.string.omni_br_stop), actions.stop)
            else SmallButton(R.drawable.omni_refresh_cw, stringResource(R.string.omni_br_reload), actions.reload)
        }
        SmallButton(R.drawable.omni_x, stringResource(R.string.omni_br_close), onClose)
    }
}

/**
 * The address pill: host in bold and the rest dimmed when idle; the full URL
 * selected when tapped. The text survives snapshots while it is being typed.
 */
@Composable
private fun AddressBar(session: BrowserSession, onNavigate: (String) -> Unit, modifier: Modifier) {
    val palette = LocalOmniPalette.current
    val focus = LocalFocusManager.current
    var editing by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }
    LaunchedEffect(session.url, editing) {
        if (!editing) field = TextFieldValue(if (isBlankPage(session.url)) "" else session.url)
    }
    val icon = when {
        session.sslError -> R.drawable.omni_triangle_alert
        session.url.startsWith("https://") -> R.drawable.omni_lock
        else -> R.drawable.omni_globe
    }
    val iconTint = when {
        session.sslError -> INSECURE
        session.url.startsWith("https://") -> SECURE
        else -> palette.tertiaryText
    }
    Row(modifier.height(36.dp).clip(CircleShape).background(palette.surface).border(1.dp, palette.border, CircleShape)
        .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        OmniIcon(icon, size = 14.dp, tint = iconTint)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            BasicTextField(
                field, { field = it },
                Modifier.fillMaxWidth().onFocusChanged { state ->
                    if (state.isFocused && !editing) field = field.copy(selection = TextRange(0, field.text.length))
                    editing = state.isFocused
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 13.sp, color = if (editing) palette.text else Color.Transparent),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = {
                    addressTarget(field.text)?.let(onNavigate)
                    focus.clearFocus()
                }),
            )
            if (!editing) {
                val parts = addressParts(session.url)
                val shown = if (isBlankPage(session.url)) null else parts
                Text(
                    if (shown == null) buildAnnotatedString {
                        withStyle(SpanStyle(color = palette.tertiaryText)) { append(stringResource(R.string.omni_br_address_hint)) }
                    } else buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = palette.text)) { append(shown.host) }
                        withStyle(SpanStyle(color = palette.tertiaryText)) { append(shown.rest) }
                    },
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Banners(session: BrowserSession) {
    val text = when {
        session.risk == BrowserRisk.RateLimited -> stringResource(R.string.omni_br_risk_rate)
        session.risk == BrowserRisk.AccessDenied -> stringResource(R.string.omni_br_risk_denied)
        session.risk == BrowserRisk.Challenge -> stringResource(R.string.omni_br_risk_challenge)
        session.sslError -> stringResource(R.string.omni_br_ssl)
        else -> null
    }
    AnimatedVisibility(text != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Row(Modifier.fillMaxWidth().background(WARNING.copy(alpha = .1f)).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            OmniIcon(R.drawable.omni_shield, size = 14.dp, tint = WARNING)
            Spacer(Modifier.width(8.dp))
            Text(text.orEmpty(), fontSize = 12.sp, lineHeight = 17.sp, color = WARNING)
        }
    }
}

/**
 * Page prompts, one at a time. The prompt text is keyed by its request, so
 * a snapshot cannot reset what is being typed (Dart did), and an alert has
 * only OK (Dart offered a Cancel that meant nothing).
 */
@Composable
private fun Prompts(session: BrowserSession, actions: BrowserActions) {
    val palette = LocalOmniPalette.current
    val dialog = session.dialog
    val permission = session.permission
    val external = session.externalOpen
    AnimatedVisibility(dialog != null || permission != null || external != null,
        enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Column(Modifier.fillMaxWidth().background(palette.secondarySurface).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                dialog != null -> {
                    Text(stringResource(when {
                        dialog.isAlert -> R.string.omni_br_dialog_alert
                        dialog.isPrompt -> R.string.omni_br_dialog_prompt
                        else -> R.string.omni_br_dialog_confirm
                    }), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                    if (dialog.message.isNotBlank()) Text(dialog.message, fontSize = 13.sp, lineHeight = 19.sp, color = palette.secondaryText)
                    var value by rememberSaveable(dialog.requestId) { mutableStateOf(dialog.defaultValue) }
                    if (dialog.isPrompt) TextField(value, { value = it }, Modifier.fillMaxWidth(), singleLine = true)
                    PromptButtons(
                        cancel = if (dialog.isAlert) null else ({ actions.resolveDialog(dialog.requestId, false, null) }),
                        confirmText = stringResource(R.string.omni_br_ok),
                        onConfirm = { actions.resolveDialog(dialog.requestId, true, value.takeIf { dialog.isPrompt }) },
                    )
                }
                permission != null -> {
                    val resources = permission.resources.map { permissionLabel(it) }.distinct().joinToString(", ")
                    Text(stringResource(R.string.omni_br_permission, permission.origin.ifBlank { session.title }, resources),
                        fontSize = 13.sp, lineHeight = 19.sp, color = palette.text)
                    PromptButtons(cancel = { actions.answerPermission(permission.requestId, false) },
                        cancelText = stringResource(R.string.omni_br_deny),
                        confirmText = stringResource(R.string.omni_br_allow),
                        onConfirm = { actions.answerPermission(permission.requestId, true) })
                }
                external != null -> {
                    Text(stringResource(R.string.omni_br_external), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
                    Text(external.target, fontSize = 12.sp, color = palette.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    PromptButtons(cancel = { actions.answerExternalOpen(external.requestId, false) },
                        confirmText = stringResource(R.string.omni_br_open_link),
                        onConfirm = { actions.answerExternalOpen(external.requestId, true) })
                }
            }
        }
    }
}

/** Readable names for WebView and Android permission strings; Dart showed them raw. */
@Composable
private fun permissionLabel(resource: String): String = when {
    "VIDEO_CAPTURE" in resource -> stringResource(R.string.omni_br_res_camera)
    "AUDIO_CAPTURE" in resource -> stringResource(R.string.omni_br_res_mic)
    "LOCATION" in resource -> stringResource(R.string.omni_br_res_location)
    "PROTECTED_MEDIA" in resource -> stringResource(R.string.omni_br_res_media)
    else -> resource.substringAfterLast('.')
}

@Composable
private fun PromptButtons(cancel: (() -> Unit)?, confirmText: String, onConfirm: () -> Unit,
    cancelText: String = stringResource(R.string.omni_br_cancel)) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        if (cancel != null) TextButton(cancelText, cancel, minHeight = 36.dp)
        TextButton(confirmText, onConfirm, minHeight = 36.dp, colors = ButtonDefaults.textButtonColorsPrimary())
    }
}

@Composable
private fun Toolbar(session: BrowserSession, actions: BrowserActions, onOpenSheet: (BrowserSheet) -> Unit,
    onResize: (Float, Float) -> Unit) {
    val palette = LocalOmniPalette.current
    val density = LocalDensity.current
    Row(Modifier.fillMaxWidth().height(44.dp).background(palette.secondarySurface).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        SmallButton(R.drawable.omni_arrow_left, stringResource(R.string.omni_br_back), actions.back, enabled = session.canGoBack)
        SmallButton(R.drawable.omni_arrow_right, stringResource(R.string.omni_br_forward), actions.forward, enabled = session.canGoForward)
        Spacer(Modifier.weight(1f))
        SmallButton(R.drawable.omni_plus, stringResource(R.string.omni_br_new_tab), actions.newTab)
        val tabsLabel = stringResource(R.string.omni_br_tabs)
        Box(Modifier.size(36.dp).clip(CircleShape).semantics { contentDescription = tabsLabel },
            contentAlignment = Alignment.Center) {
            TextButton(session.tabs.size.toString(), { onOpenSheet(BrowserSheet.Tabs) }, minWidth = 32.dp, minHeight = 32.dp)
        }
        SmallButton(R.drawable.omni_ellipsis, stringResource(R.string.omni_br_menu), { onOpenSheet(BrowserSheet.Menu) })
        // The resize handle sits after the buttons, so it never covers one (Dart's handles overlapped Back and Menu).
        val resizeLabel = stringResource(R.string.omni_br_resize)
        Box(Modifier.size(28.dp, 44.dp).semantics { contentDescription = resizeLabel }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    with(density) { onResize(drag.x.toDp().value, drag.y.toDp().value) }
                }
            }, contentAlignment = Alignment.BottomEnd) {
            Box(Modifier.padding(6.dp).size(10.dp).border(2.dp, palette.tertiaryText.copy(alpha = .6f),
                RoundedCornerShape(bottomEnd = 6.dp)))
        }
    }
}

@Composable
private fun SmallButton(@DrawableRes icon: Int, label: String, onClick: () -> Unit, enabled: Boolean = true,
    tint: Color = LocalOmniPalette.current.secondaryText) {
    OmniIconButton(icon, label, onClick, Modifier.size(36.dp), size = 17.dp, enabled = enabled, tint = tint)
}
