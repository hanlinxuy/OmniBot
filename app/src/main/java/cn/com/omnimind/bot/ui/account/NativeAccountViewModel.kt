package cn.com.omnimind.bot.ui.account

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import cn.com.omnimind.nativeui.account.AccountActions
import cn.com.omnimind.nativeui.account.AccountError
import cn.com.omnimind.nativeui.account.AccountFieldError
import cn.com.omnimind.nativeui.account.AccountForm
import cn.com.omnimind.nativeui.account.AccountScreen
import cn.com.omnimind.nativeui.account.AccountView
import cn.com.omnimind.nativeui.account.AccountSheet
import cn.com.omnimind.nativeui.account.AccountState
import cn.com.omnimind.nativeui.account.AccountToast
import cn.com.omnimind.nativeui.account.AuthMode
import cn.com.omnimind.nativeui.account.SheetList
import cn.com.omnimind.nativeui.account.isValidAccountEmail
import cn.com.omnimind.nativeui.account.resendSecondsLeft
import cn.com.omnimind.nativeui.account.sortedDevices
import cn.com.omnimind.nativeui.account.validateDeletion
import cn.com.omnimind.nativeui.account.validateLogin
import cn.com.omnimind.nativeui.account.validateNewPasswordWithCode
import cn.com.omnimind.nativeui.account.validatePasswordChange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Native account page (batch 5h). One submit at a time; each form keeps its
 * own fields and its own error. The email typed so far survives process
 * death; passwords and codes never do.
 */
internal class NativeAccountViewModel(
    authOnly: Boolean,
    private val savedState: SavedStateHandle,
    private val repository: AccountPageRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        AccountState(
            authOnly = authOnly,
            mode = savedState.get<String>(KEY_MODE)?.let(AuthMode::valueOf) ?: AuthMode.SignIn,
            signIn = AccountForm(email = savedState.get<String>(KEY_SIGN_IN_EMAIL).orEmpty()),
            register = AccountForm(email = savedState.get<String>(KEY_REGISTER_EMAIL).orEmpty()),
        ),
    )
    val state = mutableState.asStateFlow()
    private var loadJob: Job? = null

    val actions = AccountActions(
        retry = ::load,
        refresh = ::refresh,
        checkUpdate = ::checkUpdate,
        setMode = ::setMode,
        editForm = ::editForm,
        editSheetForm = { change -> mutableState.update { it.copy(sheetForm = it.sheetForm.change(), fieldError = null, error = null) } },
        sendRegisterCode = { sendCode(reset = false) },
        sendResetCode = { sendCode(reset = true) },
        signIn = ::signIn,
        register = ::register,
        openSheet = ::openSheet,
        dismissSheet = { mutableState.update { it.copy(sheet = null, sheetForm = AccountForm(), fieldError = null, error = null) } },
        resetPassword = ::resetPassword,
        changePassword = ::changePassword,
        deleteAccount = ::deleteAccount,
        signOut = ::signOut,
        refreshUsage = ::loadUsage,
        refreshDevices = ::loadDevices,
        revokeDevice = ::revokeDevice,
        revokeOtherDevices = ::revokeOtherDevices,
        consumeToast = { mutableState.update { it.copy(toast = null) } },
    )

    init {
        load()
    }

    // ---- Loading ----------------------------------------------------------

    private fun load() {
        loadJob?.cancel()
        mutableState.update { it.copy(view = AccountView.Loading, error = null) }
        loadJob = viewModelScope.launch {
            val view = try {
                repository.view()
            } catch (failure: AccountFailure) {
                // An expired session is a signed-out page, not a failure (Dart only knew `invalid_refresh_token`).
                if (failure.error == AccountError.SessionExpired) AccountView.SignedOut else AccountView.Failed(failure.error)
            }
            mutableState.update { it.copy(view = view) }
        }
    }

    private fun refresh() {
        if (state.value.refreshing || state.value.view !is AccountView.SignedIn) return
        mutableState.update { it.copy(refreshing = true, error = null) }
        viewModelScope.launch {
            try {
                val view = repository.signedInView(refreshCatalog = true)
                mutableState.update { it.copy(view = view) }
            } catch (failure: AccountFailure) {
                handleSignedInFailure(failure)
            } finally {
                mutableState.update { it.copy(refreshing = false) }
            }
        }
    }

    private fun checkUpdate() = submit {
        if (repository.recheckPolicy()) load() else mutableState.update { it.copy(error = AccountError.UpdateRequired) }
    }

    /** A signed-in call that finds the session gone drops to the sign-in form instead of a stale page. */
    private fun handleSignedInFailure(failure: AccountFailure) {
        if (failure.error == AccountError.SessionExpired) {
            mutableState.update { it.copy(view = AccountView.SignedOut, sheet = null, error = AccountError.SessionExpired) }
        } else {
            mutableState.update { it.copy(error = failure.error) }
        }
    }

    // ---- Forms ------------------------------------------------------------

    private fun setMode(mode: AuthMode) {
        if (state.value.submitting) return
        savedState[KEY_MODE] = mode.name
        mutableState.update { it.copy(mode = mode, fieldError = null, error = null) }
    }

    /** Edits the visible auth tab's form. */
    private fun editForm(change: AccountForm.() -> AccountForm) {
        mutableState.update {
            if (it.mode == AuthMode.SignIn) it.copy(signIn = it.signIn.change(), fieldError = null, error = null)
            else it.copy(register = it.register.change(), fieldError = null, error = null)
        }
        savedState[KEY_SIGN_IN_EMAIL] = state.value.signIn.email
        savedState[KEY_REGISTER_EMAIL] = state.value.register.email
    }

    private fun openSheet(sheet: AccountSheet) {
        val current = state.value
        // Reset starts from the sign-in email; deletion starts empty so the email must be typed.
        val form = when (sheet) {
            AccountSheet.ResetPassword -> AccountForm(email = current.signIn.email)
            else -> AccountForm()
        }
        mutableState.update { it.copy(sheet = sheet, sheetForm = form, fieldError = null, error = null) }
        when (sheet) {
            AccountSheet.Usage -> loadUsage()
            AccountSheet.Devices -> loadDevices()
            else -> Unit
        }
    }

    /** Shows [error] on its field; true when the form may be submitted. */
    private fun accepts(error: AccountFieldError?): Boolean {
        if (error != null) mutableState.update { it.copy(fieldError = error) }
        return error == null
    }

    /**
     * Runs one request with the submit button in progress. Dart covered the
     * page with a scrim for every call, including sending a code.
     */
    private fun submit(block: suspend () -> Unit) {
        if (state.value.submitting) return
        mutableState.update { it.copy(submitting = true, error = null, fieldError = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (failure: AccountFailure) {
                if (state.value.view is AccountView.SignedIn) handleSignedInFailure(failure)
                else mutableState.update { it.copy(error = failure.error) }
            } finally {
                mutableState.update { it.copy(submitting = false) }
            }
        }
    }

    private fun sendCode(reset: Boolean) {
        val current = state.value
        val email = (if (reset) current.sheetForm.email else current.register.email).trim()
        val previous = if (reset) current.resetCode else current.registerCode
        if (current.sendingCode || resendSecondsLeft(previous, email, System.currentTimeMillis()) > 0) return
        if (!accepts(AccountFieldError.InvalidEmail.takeUnless { isValidAccountEmail(email) })) return
        mutableState.update { it.copy(sendingCode = true, error = null, fieldError = null) }
        viewModelScope.launch {
            try {
                val request = if (reset) repository.requestResetCode(email) else repository.requestRegistrationCode(email)
                mutableState.update {
                    if (reset) it.copy(resetCode = request, toast = AccountToast.CodeSent)
                    else it.copy(registerCode = request, toast = AccountToast.CodeSent)
                }
            } catch (failure: AccountFailure) {
                mutableState.update { it.copy(error = failure.error) }
            } finally {
                mutableState.update { it.copy(sendingCode = false) }
            }
        }
    }

    private fun signIn() {
        val form = state.value.signIn
        if (!accepts(validateLogin(form.email, form.password))) return
        submit {
            repository.signIn(form.email.trim(), form.password)
            finishAuthentication(AccountToast.SignedIn)
        }
    }

    /**
     * Registers then signs in. When the sign in after a successful register
     * fails, the sign-in tab opens with the email filled in (Dart switched
     * tabs but the error was then shown on the empty form).
     */
    private fun register() {
        val form = state.value.register
        val code = state.value.registerCode
        if (!accepts(validateNewPasswordWithCode(form.email, form.password, form.confirmation, form.code, code,
                System.currentTimeMillis()))) return
        submit {
            val email = form.email.trim()
            repository.register(email, form.password, code!!, form.code.trim())
            try {
                repository.signIn(email, form.password)
            } catch (failure: AccountFailure) {
                setMode(AuthMode.SignIn)
                mutableState.update { it.copy(signIn = AccountForm(email = email), register = AccountForm(), registerCode = null) }
                throw failure
            }
            mutableState.update { it.copy(register = AccountForm(), registerCode = null) }
            finishAuthentication(AccountToast.Registered)
        }
    }

    /**
     * Shows the signed-in page, or for onboarding asks the host to close.
     * Dart toasted success even when loading the signed-in page then failed.
     */
    private suspend fun finishAuthentication(toast: AccountToast) {
        mutableState.update { it.copy(signIn = it.signIn.copy(password = "")) }
        if (state.value.authOnly) {
            mutableState.update { it.copy(toast = toast, authenticated = true) }
            return
        }
        val view = repository.signedInView(refreshCatalog = false)
        mutableState.update { it.copy(view = view, toast = toast) }
    }

    private fun resetPassword() {
        val current = state.value
        val form = current.sheetForm
        if (!accepts(validateNewPasswordWithCode(form.email, form.password, form.confirmation, form.code, current.resetCode,
                System.currentTimeMillis()))) return
        submit {
            val email = form.email.trim()
            repository.resetPassword(email, form.password, current.resetCode!!, form.code.trim())
            mutableState.update {
                it.copy(sheet = null, sheetForm = AccountForm(), resetCode = null, mode = AuthMode.SignIn,
                    signIn = AccountForm(email = email), toast = AccountToast.PasswordReset)
            }
        }
    }

    private fun changePassword() {
        val form = state.value.sheetForm
        if (!accepts(validatePasswordChange(form.currentPassword, form.password, form.confirmation))) return
        submit {
            repository.changePassword(form.currentPassword, form.password)
            mutableState.update { it.copy(sheet = null, sheetForm = AccountForm(), toast = AccountToast.PasswordChanged) }
        }
    }

    private fun deleteAccount() {
        val email = (state.value.view as? AccountView.SignedIn)?.email ?: return
        val form = state.value.sheetForm
        if (!accepts(validateDeletion(email, form.typedEmail, form.currentPassword))) return
        submit {
            repository.deleteAccount(form.currentPassword)
            mutableState.update { signedOut(it).copy(toast = AccountToast.AccountDeleted) }
        }
    }

    /**
     * Signs out this device. Local credentials are cleared even when the
     * server call fails, so the page shows signed out either way (as in
     * Dart), but now without an error banner over the sign-in form.
     */
    private fun signOut() = submit {
        try {
            repository.signOut()
        } finally {
            mutableState.update(::signedOut)
        }
    }

    private fun signedOut(current: AccountState) = current.copy(
        view = AccountView.SignedOut, sheet = null, sheetForm = AccountForm(), mode = AuthMode.SignIn,
        usage = SheetList(), devices = SheetList(),
        signIn = AccountForm(email = (current.view as? AccountView.SignedIn)?.email ?: current.signIn.email),
    )

    // ---- Lists ------------------------------------------------------------

    private fun loadUsage() {
        mutableState.update { it.copy(usage = it.usage.copy(loading = true, failed = false)) }
        viewModelScope.launch {
            val result = runCatchingAccount { repository.usage() }
            mutableState.update {
                it.copy(usage = SheetList(items = result ?: it.usage.items, failed = result == null))
            }
        }
    }

    private fun loadDevices() {
        mutableState.update { it.copy(devices = it.devices.copy(loading = true, failed = false)) }
        viewModelScope.launch {
            val result = runCatchingAccount { sortedDevices(repository.devices()) }
            mutableState.update {
                it.copy(devices = SheetList(items = result ?: it.devices.items, failed = result == null))
            }
        }
    }

    private fun revokeDevice(id: String) = submit {
        repository.revokeDevice(id)
        // Removed at once; the next refresh confirms it.
        mutableState.update {
            it.copy(devices = it.devices.copy(items = it.devices.items.filterNot { device -> device.id == id }),
                toast = AccountToast.DeviceSignedOut)
        }
    }

    private fun revokeOtherDevices() = submit {
        repository.revokeOtherDevices()
        mutableState.update {
            it.copy(devices = it.devices.copy(items = it.devices.items.filter { device -> device.current }),
                toast = AccountToast.OthersSignedOut)
        }
    }

    /** Null on failure; an expired session also drops to the sign-in form. */
    private suspend fun <T> runCatchingAccount(block: suspend () -> T): T? = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: AccountFailure) {
        if (failure.error == AccountError.SessionExpired) handleSignedInFailure(failure)
        null
    }

    class Factory(context: Context, private val authOnly: Boolean) : ViewModelProvider.Factory {
        private val appContext = context.applicationContext

        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            NativeAccountViewModel(authOnly, extras.createSavedStateHandle(), AccountPageRepository(appContext)) as T
    }

    private companion object {
        const val KEY_MODE = "mode"
        const val KEY_SIGN_IN_EMAIL = "signInEmail"
        const val KEY_REGISTER_EMAIL = "registerEmail"
    }
}

@Composable
internal fun NativeAccountRoute(viewModel: NativeAccountViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Onboarding's sign-in closes once authenticated.
    LaunchedEffect(state.authenticated) { if (state.authenticated) onBack() }
    AccountScreen(state, viewModel.actions, onBack)
}
