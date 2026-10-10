package cn.com.omnimind.bot.ui.account

import android.content.Context
import cn.com.omnimind.baselib.account.AccountApiException
import cn.com.omnimind.baselib.account.AccountCredentialStorageException
import cn.com.omnimind.baselib.account.AccountDeviceSession
import cn.com.omnimind.baselib.account.AccountNotAuthenticatedException
import cn.com.omnimind.baselib.account.AccountNotConfiguredException
import cn.com.omnimind.baselib.account.AiSettings
import cn.com.omnimind.baselib.account.CloudServicePolicyUnavailableException
import cn.com.omnimind.baselib.account.CloudServiceUpgradeRequiredException
import cn.com.omnimind.baselib.account.OmniAccount
import cn.com.omnimind.baselib.account.PlatformUsageEntry
import cn.com.omnimind.baselib.llm.PlatformAiProvisioner
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.update.AppUpdateManager
import cn.com.omnimind.nativeui.account.AccountDevice
import cn.com.omnimind.nativeui.account.AccountError
import cn.com.omnimind.nativeui.account.AccountQuota
import cn.com.omnimind.nativeui.account.AccountView
import cn.com.omnimind.nativeui.account.AccountUsage
import cn.com.omnimind.nativeui.account.VerificationRequest
import cn.com.omnimind.nativeui.account.accountErrorFor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant

/** A failed account call, already mapped to what the page shows. */
internal class AccountFailure(val error: AccountError, cause: Throwable) : Exception(cause)

/**
 * The account page's calls on [OmniAccount], with the side effects the
 * Flutter channel ran (`AccountChannel`): refreshing official AI after sign
 * in, deactivating it on sign out and deletion. Every call runs on IO and
 * throws [AccountFailure].
 */
internal class AccountPageRepository(context: Context) {
    private val appContext = context.applicationContext

    private val repository get() = OmniAccount.repository()

    /**
     * Which state the page opens in. An unknown update policy is checked
     * once, as Dart did, so a fresh install does not land on the gate.
     */
    suspend fun view(): AccountView = call {
        if (!OmniAccount.isConfigured()) return@call AccountView.NotConfigured
        val signedIn = try {
            repository.isSignedIn()
        } catch (_: AccountCredentialStorageException) {
            return@call AccountView.StorageUnavailable
        }
        var access = OmniAccount.currentCloudServiceAccess()
        if (!access.policyKnown) {
            runCatching { AppUpdateManager.checkNow(appContext, force = true) }
            access = OmniAccount.currentCloudServiceAccess()
        }
        when {
            !access.allowed -> AccountView.UpdateRequired(access.policyKnown, access.message, access.currentVersion,
                access.minimumVersion, signedIn)
            !signedIn -> AccountView.SignedOut
            else -> signedInView(refreshCatalog = false)
        }
    }

    /**
     * Dart synchronized the official AI catalog with a forced refresh on
     * every page open; it now refreshes only on pull-to-refresh and after
     * sign in, and otherwise reuses the provisioned catalog.
     */
    suspend fun signedInView(refreshCatalog: Boolean): AccountView.SignedIn = call {
        val user = repository.currentUser()
        val settings = repository.getAiSettings()
        PlatformAiProvisioner.synchronize(settings, forceRefresh = refreshCatalog)
        AccountView.SignedIn(user.email, settings.toQuota())
    }

    /** Checks for an update once more; true when the gate is now open. */
    suspend fun recheckPolicy(): Boolean = call {
        AppUpdateManager.checkNow(appContext, force = true)
        OmniAccount.currentCloudServiceAccess().allowed
    }

    suspend fun requestRegistrationCode(email: String): VerificationRequest = call {
        repository.requestRegistrationCode(email).let { VerificationRequest.sent(email, it.requestId, it.expiresInSeconds) }
    }

    suspend fun requestResetCode(email: String): VerificationRequest = call {
        repository.requestPasswordResetCode(email).let { VerificationRequest.sent(email, it.requestId, it.expiresInSeconds) }
    }

    suspend fun signIn(email: String, password: String) = call {
        repository.login(email, password)
        PlatformAiProvisioner.synchronize(forceRefresh = true)
    }

    suspend fun register(email: String, password: String, code: VerificationRequest, codeText: String) = call {
        repository.register(email, password, code.requestId, codeText)
    }

    suspend fun resetPassword(email: String, password: String, code: VerificationRequest, codeText: String) = call {
        repository.resetPassword(email, password, code.requestId, codeText)
    }

    suspend fun changePassword(current: String, password: String) = call { repository.changePassword(current, password) }

    /** Signs out this device; local credentials are cleared even when the server call fails. */
    suspend fun signOut() = call {
        deactivatePlatformAi()
        repository.logout()
    }

    suspend fun deleteAccount(password: String) = call {
        repository.deleteAccount(password)
        // The server already deleted the account; a cleanup failure must not read as a failed deletion.
        deactivatePlatformAi()
    }

    suspend fun usage(): List<AccountUsage> = call { repository.listPlatformUsage(USAGE_LIMIT).map { it.toUsage() } }

    suspend fun devices(): List<AccountDevice> = call { repository.listSessions().map { it.toDevice() } }

    suspend fun revokeDevice(id: String) = call { repository.revokeSession(id) }

    suspend fun revokeOtherDevices(): Int = call { repository.revokeOtherSessions() }

    private suspend fun deactivatePlatformAi() {
        try {
            PlatformAiProvisioner.deactivate()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            OmniLog.w(TAG, "Platform AI cleanup failed: ${error.javaClass.simpleName}")
        }
    }

    private suspend fun <T> call(block: suspend () -> T): T = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: AccountFailure) {
            throw failure
        } catch (error: Exception) {
            // Class names only: messages can carry the email or server text.
            OmniLog.w(TAG, "Account call failed: ${error.javaClass.simpleName}")
            throw AccountFailure(errorFor(error), error)
        }
    }

    companion object {
        private const val TAG = "AccountPage"
        private const val USAGE_LIMIT = 20

        fun errorFor(error: Exception): AccountError = when (error) {
            is AccountApiException -> accountErrorFor(error.errorCode, error.statusCode)
            is AccountNotConfiguredException -> AccountError.NotConfigured
            is AccountNotAuthenticatedException -> AccountError.SessionExpired
            is AccountCredentialStorageException -> AccountError.StorageUnavailable
            is CloudServiceUpgradeRequiredException -> AccountError.UpdateRequired
            is CloudServicePolicyUnavailableException -> AccountError.PolicyUnavailable
            is IOException -> AccountError.Network
            else -> AccountError.Unknown
        }

        private fun instant(value: String?): Instant? = value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

        private fun AiSettings.toQuota() = AccountQuota(
            available = platformAvailable,
            unavailableReason = platformUnavailableReason,
            enabled = platform.enabled,
            balance = platform.balance,
            weeklyLimit = platform.weeklyLimit,
            weeklyUsed = platform.weeklyUsed,
            weeklyPeriodStart = instant(platform.weeklyPeriodStart),
        )

        private fun PlatformUsageEntry.toUsage() =
            AccountUsage(model, promptTokens, completionTokens, totalTokens, quotaUsed, instant(createdAt))

        private fun AccountDeviceSession.toDevice() = AccountDevice(id, current, instant(createdAt), instant(lastUsedAt))
    }
}
