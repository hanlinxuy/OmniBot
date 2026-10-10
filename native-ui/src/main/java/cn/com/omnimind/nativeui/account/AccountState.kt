package cn.com.omnimind.nativeui.account

import androidx.compose.runtime.Immutable

/** What the account page shows, in the order Dart checked it (`account_page.dart` build). */
sealed interface AccountView {
    data object Loading : AccountView
    /** The session could not be read at all. */
    data class Failed(val error: AccountError) : AccountView
    /** The build has no account server (`OMNIBOT_BASE_URL`). */
    data object NotConfigured : AccountView
    /** Secure storage is recovering; this is not a sign-out. */
    data object StorageUnavailable : AccountView
    /** The update policy blocks account and official AI traffic. */
    data class UpdateRequired(
        val policyKnown: Boolean,
        val reason: String,
        val currentVersion: String,
        val minimumVersion: String,
        val signedIn: Boolean,
    ) : AccountView
    data object SignedOut : AccountView
    data class SignedIn(val email: String, val quota: AccountQuota) : AccountView
}

enum class AuthMode { SignIn, Register }

/** The forms that can be open over the signed-in or signed-out page. */
enum class AccountSheet { ResetPassword, ChangePassword, DeleteWarning, DeleteConfirm, Usage, Devices, SignOut }

/** One-shot confirmations, shown as a snackbar. */
enum class AccountToast { SignedIn, Registered, CodeSent, PasswordReset, PasswordChanged, AccountDeleted, DeviceSignedOut, OthersSignedOut }

@Immutable
data class AccountForm(
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val code: String = "",
    val currentPassword: String = "",
    val typedEmail: String = "",
)

@Immutable
data class AccountState(
    val view: AccountView = AccountView.Loading,
    /** Opened from onboarding: a successful sign-in closes the page. */
    val authOnly: Boolean = false,
    val mode: AuthMode = AuthMode.SignIn,
    /** Sign-in and register keep their own fields, so switching tabs loses nothing. */
    val signIn: AccountForm = AccountForm(),
    val register: AccountForm = AccountForm(),
    /** The sheet's own fields; reset when a sheet opens. */
    val sheetForm: AccountForm = AccountForm(),
    val sheet: AccountSheet? = null,
    /** The last code request per purpose. */
    val registerCode: VerificationRequest? = null,
    val resetCode: VerificationRequest? = null,
    /** The submit in flight; one at a time, and only its button shows progress. */
    val submitting: Boolean = false,
    val sendingCode: Boolean = false,
    val refreshing: Boolean = false,
    val fieldError: AccountFieldError? = null,
    val error: AccountError? = null,
    val toast: AccountToast? = null,
    val usage: SheetList<AccountUsage> = SheetList(),
    val devices: SheetList<AccountDevice> = SheetList(),
    /** Set when the user finished signing in from [authOnly]; the host closes the page. */
    val authenticated: Boolean = false,
)

/** A list loaded inside a sheet. */
@Immutable
data class SheetList<T>(val loading: Boolean = false, val items: List<T> = emptyList(), val failed: Boolean = false)

data class AccountActions(
    val retry: () -> Unit,
    val refresh: () -> Unit,
    val checkUpdate: () -> Unit,
    val setMode: (AuthMode) -> Unit,
    val editForm: (AccountForm.() -> AccountForm) -> Unit,
    val editSheetForm: (AccountForm.() -> AccountForm) -> Unit,
    val sendRegisterCode: () -> Unit,
    val sendResetCode: () -> Unit,
    val signIn: () -> Unit,
    val register: () -> Unit,
    val openSheet: (AccountSheet) -> Unit,
    val dismissSheet: () -> Unit,
    val resetPassword: () -> Unit,
    val changePassword: () -> Unit,
    val deleteAccount: () -> Unit,
    val signOut: () -> Unit,
    val refreshUsage: () -> Unit,
    val refreshDevices: () -> Unit,
    val revokeDevice: (String) -> Unit,
    val revokeOtherDevices: () -> Unit,
    val consumeToast: () -> Unit,
)
