package cn.com.omnimind.nativeui.account

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.components.OmniConfirmDialog
import cn.com.omnimind.nativeui.components.OmniDialogActions
import cn.com.omnimind.nativeui.theme.LocalOmniPalette
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun Instant?.formatted(): String = this?.atZone(ZoneId.systemDefault())?.format(DATE) ?: "--"

/**
 * Every account sheet, keyed by [AccountState.sheet]. A sheet with a request
 * in flight cannot be dismissed (Dart allowed dragging the change-password
 * sheet away mid-submit).
 */
@Composable
internal fun AccountSheets(state: AccountState, actions: AccountActions) {
    val busy = state.submitting || state.sendingCode
    val dismiss = { if (!busy) actions.dismissSheet() }
    ResetPasswordSheet(state.sheet == AccountSheet.ResetPassword, state, actions, dismiss)
    ChangePasswordSheet(state.sheet == AccountSheet.ChangePassword, state, actions, dismiss)
    OmniConfirmDialog(
        show = state.sheet == AccountSheet.DeleteWarning,
        title = stringResource(R.string.omni_acc_delete_title),
        summary = stringResource(R.string.omni_acc_delete_body),
        confirmText = stringResource(R.string.omni_acc_delete_continue),
        onConfirm = { actions.openSheet(AccountSheet.DeleteConfirm) },
        onDismiss = dismiss,
    )
    DeleteConfirmSheet(state.sheet == AccountSheet.DeleteConfirm, state, actions, dismiss)
    OmniConfirmDialog(
        show = state.sheet == AccountSheet.SignOut,
        title = stringResource(R.string.omni_acc_signout_title),
        summary = stringResource(R.string.omni_acc_signout_body),
        confirmText = stringResource(R.string.omni_acc_signout_confirm),
        onConfirm = actions.signOut,
        onDismiss = dismiss,
        confirmEnabled = !busy,
    )
    UsageSheet(state.sheet == AccountSheet.Usage, state.usage, actions, dismiss)
    DevicesSheet(state.sheet == AccountSheet.Devices, state, actions, dismiss)
}

/** A form sheet: fields, an inline error and one primary button. */
@Composable
private fun FormSheet(
    show: Boolean,
    title: String,
    summary: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    OverlayBottomSheet(show = show, title = title, onDismissRequest = onDismiss, allowDismiss = !busy) {
        Column(Modifier.fillMaxWidth().imePadding().padding(bottom = 16.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(summary, fontSize = 13.sp, lineHeight = 19.sp, color = LocalOmniPalette.current.secondaryText)
            content()
        }
    }
}

@Composable
private fun ResetPasswordSheet(show: Boolean, state: AccountState, actions: AccountActions, onDismiss: () -> Unit) {
    val form = state.sheetForm
    FormSheet(show, stringResource(R.string.omni_acc_reset_title), stringResource(R.string.omni_acc_reset_body),
        state.submitting || state.sendingCode, onDismiss) {
        AccountField(stringResource(R.string.omni_acc_email), form.email, { v -> actions.editSheetForm { copy(email = v) } },
            keyboard = KeyboardType.Email, error = state.fieldError.takeIf { it == AccountFieldError.InvalidEmail })
        CodeField(form.code, { v -> actions.editSheetForm { copy(code = v) } }, form.email, state.resetCode, state.sendingCode,
            actions.sendResetCode,
            error = state.fieldError.takeIf { it == AccountFieldError.InvalidCode || it == AccountFieldError.CodeNotRequested })
        PasswordField(stringResource(R.string.omni_acc_new_password), form.password,
            { v -> actions.editSheetForm { copy(password = v) } }, helper = stringResource(R.string.omni_acc_password_hint),
            error = state.fieldError.takeIf { it == AccountFieldError.PasswordLength })
        PasswordField(stringResource(R.string.omni_acc_confirm_new_password), form.confirmation,
            { v -> actions.editSheetForm { copy(confirmation = v) } },
            error = state.fieldError.takeIf { it == AccountFieldError.PasswordMismatch })
        state.error?.let { ErrorBanner(errorText(it)) }
        PrimarySubmit(stringResource(R.string.omni_acc_reset_confirm), state.submitting, actions.resetPassword)
    }
}

@Composable
private fun ChangePasswordSheet(show: Boolean, state: AccountState, actions: AccountActions, onDismiss: () -> Unit) {
    val form = state.sheetForm
    FormSheet(show, stringResource(R.string.omni_acc_change_password), stringResource(R.string.omni_acc_change_body),
        state.submitting, onDismiss) {
        PasswordField(stringResource(R.string.omni_acc_current_password), form.currentPassword,
            { v -> actions.editSheetForm { copy(currentPassword = v) } },
            error = state.fieldError.takeIf { it == AccountFieldError.MissingCurrentPassword })
        PasswordField(stringResource(R.string.omni_acc_new_password), form.password,
            { v -> actions.editSheetForm { copy(password = v) } }, helper = stringResource(R.string.omni_acc_password_hint),
            error = state.fieldError.takeIf { it == AccountFieldError.PasswordLength || it == AccountFieldError.SamePassword })
        PasswordField(stringResource(R.string.omni_acc_confirm_new_password), form.confirmation,
            { v -> actions.editSheetForm { copy(confirmation = v) } },
            error = state.fieldError.takeIf { it == AccountFieldError.PasswordMismatch })
        state.error?.let { ErrorBanner(errorText(it)) }
        PrimarySubmit(stringResource(R.string.omni_acc_change_confirm), state.submitting, actions.changePassword)
    }
}

/** Dart put the account email in the field's hint, so the check could be passed by copying it. */
@Composable
private fun DeleteConfirmSheet(show: Boolean, state: AccountState, actions: AccountActions, onDismiss: () -> Unit) {
    val form = state.sheetForm
    FormSheet(show, stringResource(R.string.omni_acc_delete_final_title), stringResource(R.string.omni_acc_delete_final_body),
        state.submitting, onDismiss) {
        AccountField(stringResource(R.string.omni_acc_delete_email), form.typedEmail,
            { v -> actions.editSheetForm { copy(typedEmail = v) } }, keyboard = KeyboardType.Email,
            error = state.fieldError.takeIf { it == AccountFieldError.EmailMismatch })
        PasswordField(stringResource(R.string.omni_acc_current_password), form.currentPassword,
            { v -> actions.editSheetForm { copy(currentPassword = v) } },
            error = state.fieldError.takeIf { it == AccountFieldError.MissingCurrentPassword })
        state.error?.let { ErrorBanner(errorText(it)) }
        PrimarySubmit(stringResource(R.string.omni_acc_delete_confirm), state.submitting, actions.deleteAccount, danger = true)
    }
}

/** A list sheet: header action, then loading, failure, empty or the rows. */
@Composable
private fun <T> ListSheet(
    show: Boolean,
    title: String,
    summary: String,
    list: SheetList<T>,
    emptyText: String,
    failedText: String,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
    footer: @Composable () -> Unit = {},
    row: @Composable (index: Int, item: T) -> Unit,
) {
    val palette = LocalOmniPalette.current
    OverlayBottomSheet(show = show, title = title, onDismissRequest = onDismiss, endAction = {
        TextButton(stringResource(R.string.omni_acc_refresh), onRefresh, enabled = !list.loading, minHeight = 36.dp)
    }) {
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(summary, fontSize = 12.sp, lineHeight = 18.sp, color = palette.tertiaryText)
            Spacer(Modifier.height(12.dp))
            AnimatedContent(when {
                list.loading && list.items.isEmpty() -> 0
                list.failed -> 1
                list.items.isEmpty() -> 2
                else -> 3
            }, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "sheet-list") { phase ->
                when (phase) {
                    0 -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    1 -> Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
                        Text(failedText, fontSize = 13.sp, color = palette.secondaryText)
                        TextButton(stringResource(R.string.omni_acc_retry), onRefresh)
                    }
                    2 -> Text(emptyText, fontSize = 13.sp, color = palette.secondaryText,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp))
                    else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                        itemsIndexed(list.items) { index, item -> row(index, item) }
                    }
                }
            }
            footer()
        }
    }
}

@Composable
private fun UsageSheet(show: Boolean, usage: SheetList<AccountUsage>, actions: AccountActions, onDismiss: () -> Unit) {
    val palette = LocalOmniPalette.current
    ListSheet(show, stringResource(R.string.omni_acc_usage), stringResource(R.string.omni_acc_usage_subtitle), usage,
        stringResource(R.string.omni_acc_usage_empty), stringResource(R.string.omni_acc_usage_failed),
        actions.refreshUsage, onDismiss) { _, entry ->
        Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.model.ifBlank { stringResource(R.string.omni_acc_usage_model_fallback) }, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium, color = palette.text, modifier = Modifier.weight(1f))
                Text(entry.createdAt.formatted(), fontSize = 12.sp, color = palette.tertiaryText)
            }
            Spacer(Modifier.height(3.dp))
            Text(stringResource(R.string.omni_acc_usage_line, entry.quotaUsed, entry.promptTokens, entry.completionTokens,
                entry.totalTokens), fontSize = 12.sp, color = palette.secondaryText)
        }
    }
}

@Composable
private fun DevicesSheet(show: Boolean, state: AccountState, actions: AccountActions, onDismiss: () -> Unit) {
    val palette = LocalOmniPalette.current
    var revoking by rememberSaveable { mutableStateOf<String?>(null) }
    var revokingOthers by rememberSaveable { mutableStateOf(false) }
    val devices = state.devices.items
    ListSheet(show, stringResource(R.string.omni_acc_devices), stringResource(R.string.omni_acc_devices_subtitle), state.devices,
        stringResource(R.string.omni_acc_devices_empty), stringResource(R.string.omni_acc_devices_failed),
        actions.refreshDevices, onDismiss,
        footer = {
            if (devices.any { !it.current }) {
                Spacer(Modifier.height(12.dp))
                TextButton(stringResource(R.string.omni_acc_device_signout_others), { revokingOthers = true },
                    Modifier.fillMaxWidth(), enabled = !state.submitting,
                    colors = ButtonDefaults.textButtonColors(textColor = ACCOUNT_DANGER))
            }
        },
    ) { _, device ->
        val otherIndex = devices.filter { !it.current }.indexOf(device) + 1
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (device.current) stringResource(R.string.omni_acc_device_current)
                    else stringResource(R.string.omni_acc_device_other, otherIndex),
                    fontSize = 14.sp, fontWeight = FontWeight.Medium, color = if (device.current) palette.accent else palette.text)
                Text(stringResource(R.string.omni_acc_device_last_active, device.lastActive.formatted()),
                    fontSize = 12.sp, color = palette.tertiaryText)
            }
            if (!device.current) {
                TextButton(stringResource(R.string.omni_acc_device_signout), { revoking = device.id }, enabled = !state.submitting,
                    minHeight = 36.dp)
            }
        }
    }
    OverlayDialog(show = revoking != null, title = stringResource(R.string.omni_acc_device_signout),
        onDismissRequest = { revoking = null }) {
        OmniDialogActions({ revoking = null }, stringResource(R.string.omni_acc_device_signout), {
            revoking?.let(actions.revokeDevice)
            revoking = null
        })
    }
    OverlayDialog(show = revokingOthers, title = stringResource(R.string.omni_acc_device_signout_others),
        onDismissRequest = { revokingOthers = false }) {
        OmniDialogActions({ revokingOthers = false }, stringResource(R.string.omni_acc_device_signout_others), {
            actions.revokeOtherDevices()
            revokingOthers = false
        })
    }
}
