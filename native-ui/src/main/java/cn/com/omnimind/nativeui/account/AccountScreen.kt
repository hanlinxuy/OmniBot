package cn.com.omnimind.nativeui.account

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.components.OmniIcon
import cn.com.omnimind.nativeui.components.OmniIconButton
import cn.com.omnimind.nativeui.components.OmniPage
import cn.com.omnimind.nativeui.components.OmniTabRow
import cn.com.omnimind.nativeui.settings.PreferenceDivider
import cn.com.omnimind.nativeui.settings.PreferenceRow
import cn.com.omnimind.nativeui.settings.PreferenceSectionHeader
import cn.com.omnimind.nativeui.theme.LocalOmniPalette
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import java.time.Instant
import java.time.ZoneId

internal val ACCOUNT_DANGER = Color(0xFFE5484D)

/**
 * Native account page (batch 5h), replacing the Flutter `AccountPage` and
 * `AccountAuthPage`. One state at a time cross-fades; sign-in and register
 * slide between each other; every sheet renders in this page's popup host.
 */
@Composable
fun AccountScreen(state: AccountState, actions: AccountActions, onBack: () -> Unit) {
    val title = stringResource(if (state.authOnly) R.string.omni_acc_auth_title else R.string.omni_account_title)
    OmniPage(title, onBack, notice = state.toast?.let { toastText(it) }, onNoticeShown = actions.consumeToast) { insets ->
        Box(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding(), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(state.view, contentKey = { it::class }, transitionSpec = {
                fadeIn(tween(220, 60)) togetherWith fadeOut(tween(120))
            }, label = "account-view", modifier = Modifier.widthIn(max = 560.dp).fillMaxSize()) { view ->
                when (view) {
                    AccountView.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    is AccountView.Failed -> Notice(R.drawable.omni_triangle_alert, stringResource(R.string.omni_acc_load_failed),
                        errorText(view.error), stringResource(R.string.omni_acc_retry), actions.retry)
                    AccountView.NotConfigured -> Notice(R.drawable.omni_globe, stringResource(R.string.omni_acc_not_configured_title),
                        stringResource(R.string.omni_acc_not_configured_body))
                    AccountView.StorageUnavailable -> Notice(R.drawable.omni_shield, stringResource(R.string.omni_acc_storage_title),
                        stringResource(R.string.omni_acc_storage_body), stringResource(R.string.omni_acc_retry), actions.retry)
                    is AccountView.UpdateRequired -> UpdateGate(view, state, actions)
                    AccountView.SignedOut -> AuthForm(state, actions)
                    is AccountView.SignedIn -> if (state.authOnly) {
                        Notice(R.drawable.omni_circle_check, stringResource(R.string.omni_acc_signed_in_already), view.email,
                            stringResource(R.string.omni_acc_done), onBack)
                    } else {
                        SignedInPage(view, state, actions)
                    }
                }
            }
        }
        AccountSheets(state, actions)
    }
}

/** A centered state with an optional action. */
@Composable
private fun Notice(@DrawableRes icon: Int, title: String, body: String, action: String? = null, onAction: () -> Unit = {}) {
    val palette = LocalOmniPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(palette.accent.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
            OmniIcon(icon, size = 28.dp, tint = palette.accent)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, fontSize = 13.sp, lineHeight = 20.sp, color = palette.secondaryText, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            Button(onAction, minWidth = 160.dp, colors = ButtonDefaults.buttonColorsPrimary()) {
                Text(action, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun UpdateGate(gate: AccountView.UpdateRequired, state: AccountState, actions: AccountActions) {
    val palette = LocalOmniPalette.current
    val reason = gate.reason.ifBlank {
        stringResource(if (gate.policyKnown) R.string.omni_acc_update_reason_old else R.string.omni_acc_update_reason_unknown)
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(palette.accent.copy(alpha = .1f)), contentAlignment = Alignment.Center) {
            OmniIcon(R.drawable.omni_hard_drive_download, size = 28.dp, tint = palette.accent)
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(if (gate.policyKnown) R.string.omni_acc_update_title else R.string.omni_acc_update_check_title),
            fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
        Spacer(Modifier.height(8.dp))
        Text(reason, fontSize = 13.sp, lineHeight = 20.sp, color = palette.secondaryText, textAlign = TextAlign.Center)
        if (gate.minimumVersion.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.omni_acc_update_versions, gate.currentVersion, gate.minimumVersion),
                fontSize = 12.sp, color = palette.tertiaryText)
        }
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.omni_acc_update_body), fontSize = 12.sp, lineHeight = 18.sp, color = palette.tertiaryText,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimarySubmit(stringResource(if (gate.policyKnown) R.string.omni_acc_update_now else R.string.omni_acc_check_update),
            state.submitting, actions.checkUpdate)
        state.error?.let { ErrorBanner(errorText(it)) }
        if (gate.signedIn) TextButton(stringResource(R.string.omni_acc_signout_confirm), { actions.openSheet(AccountSheet.SignOut) })
    }
}

/**
 * Sign in and register as two tabs that slide between each other. Each tab
 * keeps its own fields (Dart shared one controller set and copied the email
 * across), and the register code has a visible resend countdown.
 */
@Composable
private fun AuthForm(state: AccountState, actions: AccountActions) {
    val palette = LocalOmniPalette.current
    val register = state.mode == AuthMode.Register
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        AnimatedContent(register, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(100)) }, label = "auth-heading") {
            Text(stringResource(if (it) R.string.omni_acc_register_heading else R.string.omni_acc_signin_heading),
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = palette.text)
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.omni_acc_auth_subtitle), fontSize = 13.sp, lineHeight = 20.sp, color = palette.secondaryText)
        Spacer(Modifier.height(20.dp))
        OmniTabRow(listOf(stringResource(R.string.omni_acc_tab_signin), stringResource(R.string.omni_acc_tab_register)),
            if (register) 1 else 0, { actions.setMode(if (it == 1) AuthMode.Register else AuthMode.SignIn) })
        Spacer(Modifier.height(20.dp))
        AnimatedContent(register, transitionSpec = {
            val direction = if (targetState) 1 else -1
            (slideInHorizontally(tween(280)) { it * direction / 5 } + fadeIn(tween(200, 60))) togetherWith
                (slideOutHorizontally(tween(220)) { -it * direction / 6 } + fadeOut(tween(120)))
        }, label = "auth-form") { showRegister ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (showRegister) RegisterFields(state, actions) else SignInFields(state, actions)
            }
        }
    }
}

@Composable
private fun SignInFields(state: AccountState, actions: AccountActions) {
    val form = state.signIn
    val focus = LocalFocusManager.current
    AccountField(stringResource(R.string.omni_acc_email), form.email, { v -> actions.editForm { copy(email = v) } },
        keyboard = KeyboardType.Email, error = state.fieldError.takeIf { it == AccountFieldError.InvalidEmail })
    PasswordField(stringResource(R.string.omni_acc_password), form.password, { v -> actions.editForm { copy(password = v) } },
        imeAction = ImeAction.Done, onDone = { focus.clearFocus(); actions.signIn() },
        error = state.fieldError.takeIf { it == AccountFieldError.MissingPassword })
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextLink(stringResource(R.string.omni_acc_forgot)) { actions.openSheet(AccountSheet.ResetPassword) }
    }
    state.error?.let { ErrorBanner(errorText(it)) }
    PrimarySubmit(stringResource(R.string.omni_acc_signin), state.submitting, actions.signIn)
}

@Composable
private fun RegisterFields(state: AccountState, actions: AccountActions) {
    val form = state.register
    AccountField(stringResource(R.string.omni_acc_email), form.email, { v -> actions.editForm { copy(email = v) } },
        keyboard = KeyboardType.Email, error = state.fieldError.takeIf { it == AccountFieldError.InvalidEmail })
    PasswordField(stringResource(R.string.omni_acc_password), form.password, { v -> actions.editForm { copy(password = v) } },
        helper = stringResource(R.string.omni_acc_password_hint),
        error = state.fieldError.takeIf { it == AccountFieldError.PasswordLength })
    PasswordField(stringResource(R.string.omni_acc_confirm_password), form.confirmation,
        { v -> actions.editForm { copy(confirmation = v) } },
        error = state.fieldError.takeIf { it == AccountFieldError.PasswordMismatch })
    CodeField(form.code, { v -> actions.editForm { copy(code = v) } }, form.email, state.registerCode, state.sendingCode,
        actions.sendRegisterCode,
        error = state.fieldError.takeIf { it == AccountFieldError.InvalidCode || it == AccountFieldError.CodeNotRequested })
    state.error?.let { ErrorBanner(errorText(it)) }
    PrimarySubmit(stringResource(R.string.omni_acc_register), state.submitting, actions.register)
}

/** A labelled single-line field with its own inline error. */
@Composable
internal fun AccountField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    helper: String? = null,
    error: AccountFieldError? = null,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: (@Composable () -> Unit)? = null,
) {
    val palette = LocalOmniPalette.current
    Column {
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = palette.secondaryText,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        TextField(value, onChange, Modifier.fillMaxWidth(), singleLine = true, visualTransformation = visual,
            keyboardOptions = KeyboardOptions(keyboardType = keyboard, imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone() }), trailingIcon = trailing)
        val note = error?.let { fieldErrorText(it) } ?: helper
        AnimatedVisibility(note != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Text(note.orEmpty(), fontSize = 12.sp, color = if (error != null) ACCOUNT_DANGER else palette.tertiaryText,
                modifier = Modifier.padding(start = 4.dp, top = 5.dp))
        }
    }
}

@Composable
internal fun PasswordField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    helper: String? = null,
    error: AccountFieldError? = null,
) {
    var shown by rememberSaveable { mutableStateOf(false) }
    val palette = LocalOmniPalette.current
    AccountField(label, value, onChange, KeyboardType.Password, imeAction, onDone, helper, error,
        visual = if (shown) VisualTransformation.None else PasswordVisualTransformation()) {
        OmniIconButton(if (shown) R.drawable.omni_eye else R.drawable.omni_eye_off,
            stringResource(if (shown) R.string.omni_acc_hide_password else R.string.omni_acc_show_password),
            { shown = !shown }, size = 18.dp, tint = palette.tertiaryText)
    }
}

/** The code field with Send, a spinner while sending, then a live resend countdown. */
@Composable
internal fun CodeField(
    value: String,
    onChange: (String) -> Unit,
    email: String,
    request: VerificationRequest?,
    sending: Boolean,
    onSend: () -> Unit,
    error: AccountFieldError?,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val secondsLeft = resendSecondsLeft(request, email, now)
    LaunchedEffect(request, secondsLeft > 0) {
        while (resendSecondsLeft(request, email, System.currentTimeMillis()) > 0) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
        now = System.currentTimeMillis()
    }
    AccountField(stringResource(R.string.omni_acc_code), value, { onChange(it.filter(Char::isDigit).take(ACCOUNT_CODE_LENGTH)) },
        keyboard = KeyboardType.NumberPassword, error = error) {
        val label = when {
            secondsLeft > 0 -> stringResource(R.string.omni_acc_resend_in, secondsLeft)
            request != null && request.matches(email) -> stringResource(R.string.omni_acc_resend_code)
            else -> stringResource(R.string.omni_acc_send_code)
        }
        Box(Modifier.padding(end = 6.dp).widthIn(min = 72.dp), contentAlignment = Alignment.Center) {
            if (sending) CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
            else TextButton(label, onSend, enabled = secondsLeft == 0 && isValidAccountEmail(email), minHeight = 36.dp,
                colors = ButtonDefaults.textButtonColorsPrimary())
        }
    }
}

/** The page's main button; progress shows inside it instead of a full-screen scrim (Dart). */
@Composable
internal fun PrimarySubmit(text: String, busy: Boolean, onClick: () -> Unit, danger: Boolean = false) {
    val palette = LocalOmniPalette.current
    Button(onClick, Modifier.fillMaxWidth(), enabled = !busy, minHeight = 50.dp,
        colors = ButtonDefaults.buttonColorsPrimary(color = if (danger) ACCOUNT_DANGER else palette.accent)) {
        if (busy) {
            CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp,
                colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = Color.White,
                    backgroundColor = Color.White.copy(alpha = .3f)))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** A quiet accent link; a filled button is too loud for a secondary path like "forgot password". */
@Composable
private fun TextLink(text: String, onClick: () -> Unit) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = LocalOmniPalette.current.accent,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 40.dp).padding(horizontal = 8.dp, vertical = 10.dp))
}

@Composable
internal fun ErrorBanner(text: String) {
    val palette = LocalOmniPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
        .background(ACCOUNT_DANGER.copy(alpha = .08f)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        OmniIcon(R.drawable.omni_triangle_alert, size = 16.dp, tint = ACCOUNT_DANGER)
        Spacer(Modifier.width(8.dp))
        Text(text, fontSize = 13.sp, lineHeight = 19.sp, color = palette.text)
    }
}

@Composable
private fun SignedInPage(account: AccountView.SignedIn, state: AccountState, actions: AccountActions) {
    PullToRefresh(isRefreshing = state.refreshing, onRefresh = actions.refresh, pullToRefreshState = rememberPullToRefreshState(),
        modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Column {
                PreferenceSectionHeader(stringResource(R.string.omni_account_section))
                PreferenceRow(account.email, stringResource(R.string.omni_acc_verified), icon = R.drawable.omni_user) {}
                PreferenceDivider()
                QuotaRow(account.quota)
            }
            Column {
                PreferenceSectionHeader(stringResource(R.string.omni_acc_section_manage))
                ManageRow(R.drawable.omni_receipt_text, R.string.omni_acc_usage) { actions.openSheet(AccountSheet.Usage) }
                PreferenceDivider()
                ManageRow(R.drawable.omni_monitor_smartphone, R.string.omni_acc_devices) { actions.openSheet(AccountSheet.Devices) }
                PreferenceDivider()
                ManageRow(R.drawable.omni_shield, R.string.omni_acc_change_password) { actions.openSheet(AccountSheet.ChangePassword) }
                PreferenceDivider()
                ManageRow(R.drawable.omni_trash_2, R.string.omni_acc_delete, danger = true) { actions.openSheet(AccountSheet.DeleteWarning) }
            }
            state.error?.let { ErrorBanner(errorText(it)) }
            TextButton(stringResource(R.string.omni_acc_signout), { actions.openSheet(AccountSheet.SignOut) }, Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(textColor = ACCOUNT_DANGER))
        }
    }
}

@Composable
private fun ManageRow(@DrawableRes icon: Int, title: Int, danger: Boolean = false, onClick: () -> Unit) {
    val palette = LocalOmniPalette.current
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 52.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        OmniIcon(icon, size = 18.dp, tint = if (danger) ACCOUNT_DANGER else palette.text)
        Spacer(Modifier.width(14.dp))
        Text(stringResource(title), fontSize = 14.sp, fontWeight = FontWeight.Medium,
            color = if (danger) ACCOUNT_DANGER else palette.text, modifier = Modifier.weight(1f))
        OmniIcon(R.drawable.omni_chevron_right, size = 16.dp, tint = palette.tertiaryText)
    }
}

/**
 * The quota as a bar that fills to the remaining share, with the reset time.
 * Dart showed only `balance/limit` and a percentage; the bar makes "how much
 * is left" readable at a glance.
 */
@Composable
private fun QuotaRow(quota: AccountQuota) {
    val palette = LocalOmniPalette.current
    val summary = when {
        !quota.available -> quota.unavailableReason?.takeIf { it.isNotBlank() } ?: stringResource(R.string.omni_acc_quota_unavailable)
        !quota.enabled -> stringResource(R.string.omni_acc_quota_disabled)
        quota.weeklyLimit > 0 -> {
            val now = Instant.now()
            val (days, hours) = quotaResetIn(quotaResetAt(quota.weeklyPeriodStart, now, ZoneId.systemDefault()), now)
            stringResource(R.string.omni_acc_quota_reset, days, hours)
        }
        else -> null
    }
    Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 2.dp, top = 14.dp, bottom = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OmniIcon(R.drawable.omni_zap, size = 18.dp, tint = palette.text)
            Spacer(Modifier.width(14.dp))
            Text(stringResource(R.string.omni_acc_quota), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text,
                modifier = Modifier.weight(1f))
            if (quota.available && quota.enabled) {
                Text(if (quota.weeklyLimit > 0) "${quota.balance} / ${quota.weeklyLimit}" else quota.balance.toString(),
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            }
        }
        quota.remainingFraction?.takeIf { quota.available && quota.enabled }?.let { fraction ->
            val shown by animateFloatAsState(fraction, tween(600), label = "quota")
            val color by animateColorAsState(if (fraction < .15f) ACCOUNT_DANGER else palette.accent, label = "quota-color")
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(progress = shown, height = 6.dp, modifier = Modifier.padding(start = 32.dp),
                colors = ProgressIndicatorDefaults.progressIndicatorColors(foregroundColor = color, backgroundColor = palette.border))
        }
        if (summary != null) {
            Spacer(Modifier.height(6.dp))
            Text(summary, fontSize = 11.sp, lineHeight = 17.sp, color = palette.secondaryText, modifier = Modifier.padding(start = 32.dp))
        }
    }
}

@Composable
private fun toastText(toast: AccountToast): String = stringResource(when (toast) {
    AccountToast.SignedIn -> R.string.omni_acc_toast_signed_in
    AccountToast.Registered -> R.string.omni_acc_toast_registered
    AccountToast.CodeSent -> R.string.omni_acc_toast_code_sent
    AccountToast.PasswordReset -> R.string.omni_acc_toast_reset
    AccountToast.PasswordChanged -> R.string.omni_acc_toast_changed
    AccountToast.AccountDeleted -> R.string.omni_acc_toast_deleted
    AccountToast.DeviceSignedOut -> R.string.omni_acc_toast_device_out
    AccountToast.OthersSignedOut -> R.string.omni_acc_toast_others_out
})

@Composable
internal fun fieldErrorText(error: AccountFieldError): String = stringResource(when (error) {
    AccountFieldError.InvalidEmail -> R.string.omni_acc_field_email
    AccountFieldError.MissingPassword -> R.string.omni_acc_field_password
    AccountFieldError.PasswordLength -> R.string.omni_acc_field_length
    AccountFieldError.PasswordMismatch -> R.string.omni_acc_field_mismatch
    AccountFieldError.InvalidCode -> R.string.omni_acc_field_code
    AccountFieldError.CodeNotRequested -> R.string.omni_acc_field_code_missing
    AccountFieldError.MissingCurrentPassword -> R.string.omni_acc_field_current
    AccountFieldError.SamePassword -> R.string.omni_acc_field_same
    AccountFieldError.EmailMismatch -> R.string.omni_acc_field_email_mismatch
})

@Composable
internal fun errorText(error: AccountError): String = stringResource(when (error) {
    AccountError.InvalidCredentials -> R.string.omni_acc_err_credentials
    AccountError.EmailTaken -> R.string.omni_acc_err_email_taken
    AccountError.InvalidEmail -> R.string.omni_acc_err_invalid_email
    AccountError.InvalidPassword -> R.string.omni_acc_err_invalid_password
    AccountError.InvalidCode -> R.string.omni_acc_err_code
    AccountError.CodeExpired -> R.string.omni_acc_err_code_expired
    AccountError.RateLimited -> R.string.omni_acc_err_rate
    AccountError.VerificationUnavailable -> R.string.omni_acc_err_verification
    AccountError.ServiceUnavailable -> R.string.omni_acc_err_service
    AccountError.FeatureUnavailable -> R.string.omni_acc_err_feature
    AccountError.CurrentPasswordInvalid -> R.string.omni_acc_err_current
    AccountError.PasswordReuse -> R.string.omni_acc_err_reuse
    AccountError.SessionNotFound -> R.string.omni_acc_err_session
    AccountError.CannotRevokeCurrent -> R.string.omni_acc_err_cannot_revoke
    AccountError.UserDisabled -> R.string.omni_acc_err_disabled
    AccountError.SessionExpired -> R.string.omni_acc_err_expired
    AccountError.NotConfigured -> R.string.omni_acc_not_configured_title
    AccountError.UpdateRequired -> R.string.omni_acc_update_reason_old
    AccountError.PolicyUnavailable -> R.string.omni_acc_update_reason_unknown
    AccountError.StorageUnavailable -> R.string.omni_acc_storage_title
    AccountError.Network -> R.string.omni_acc_err_network
    AccountError.Unknown -> R.string.omni_acc_err_unknown
})
