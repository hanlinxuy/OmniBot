package cn.com.omnimind.bot.ui.account

import cn.com.omnimind.baselib.account.AccountApiException
import cn.com.omnimind.baselib.account.AccountCredentialStorageException
import cn.com.omnimind.baselib.account.AccountNotAuthenticatedException
import cn.com.omnimind.baselib.account.CloudServicePolicyUnavailableException
import cn.com.omnimind.baselib.account.CloudServiceUpgradeRequiredException
import cn.com.omnimind.nativeui.account.AccountError
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.SocketTimeoutException

class AccountPageRepositoryTest {
    private fun map(error: Exception) = AccountPageRepository.errorFor(error)

    @Test fun nativeFailuresMapToWhatThePageShows() {
        assertEquals(AccountError.InvalidCredentials, map(AccountApiException(401, "invalid_credentials", "x")))
        assertEquals(AccountError.SessionExpired, map(AccountNotAuthenticatedException()))
        assertEquals(AccountError.StorageUnavailable, map(AccountCredentialStorageException()))
        assertEquals(AccountError.UpdateRequired, map(CloudServiceUpgradeRequiredException("1.0", "2.0", "x")))
        assertEquals(AccountError.PolicyUnavailable, map(CloudServicePolicyUnavailableException("x")))
        // Dart showed "cloud service went wrong" for an offline phone.
        assertEquals(AccountError.Network, map(SocketTimeoutException()))
        assertEquals(AccountError.Unknown, map(IllegalStateException()))
    }
}
