package cn.com.omnimind.nativeui.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class AccountModelsTest {
    @Test fun emailNeedsAnAtAndADotAfterIt() {
        assertTrue(isValidAccountEmail(" a@b.co "))
        assertFalse(isValidAccountEmail("@b.co"))
        assertFalse(isValidAccountEmail("a@.co"))
        assertFalse(isValidAccountEmail("a@bco"))
    }

    @Test fun passwordsAreEightToSixteenCharactersCountingEmojiOnce() {
        assertFalse(isValidAccountPassword("1234567"))
        assertTrue(isValidAccountPassword("12345678"))
        assertTrue(isValidAccountPassword("1234567890123456"))
        assertFalse(isValidAccountPassword("12345678901234567"))
        // Eight emoji are sixteen UTF-16 units but eight characters.
        assertTrue(isValidAccountPassword("😀".repeat(8)))
    }

    private val request = VerificationRequest("me@x.io", "r1", sentAt = 0, expiresAt = 600_000)

    @Test fun registerChecksRunInFieldOrder() {
        fun check(email: String = "me@x.io", password: String = "password1", confirm: String = password,
            code: String = "123456", req: VerificationRequest? = request, now: Long = 1_000) =
            validateNewPasswordWithCode(email, password, confirm, code, req, now)
        assertEquals(AccountFieldError.InvalidEmail, check(email = "me"))
        assertEquals(AccountFieldError.PasswordLength, check(password = "short"))
        assertEquals(AccountFieldError.PasswordMismatch, check(confirm = "password2"))
        assertEquals(AccountFieldError.InvalidCode, check(code = "12345"))
        assertNull(check())
    }

    /** Fix (5h): Dart reused a code sent to another email and never noticed it had expired. */
    @Test fun aCodeBelongsToItsEmailAndExpires() {
        fun check(email: String, now: Long) = validateNewPasswordWithCode(email, "password1", "password1", "123456", request, now)
        assertNull(check(" ME@x.io ", 1_000))
        assertEquals(AccountFieldError.CodeNotRequested, check("other@x.io", 1_000))
        assertEquals(AccountFieldError.CodeNotRequested, check("me@x.io", 600_000))
    }

    /** Fix (5h): Dart had no resend cooldown, so a double tap mailed two codes. */
    @Test fun resendWaitsAMinutePerEmail() {
        assertEquals(60, resendSecondsLeft(request, "me@x.io", now = 0))
        assertEquals(1, resendSecondsLeft(request, "me@x.io", now = 59_500))
        assertEquals(0, resendSecondsLeft(request, "me@x.io", now = 60_000))
        assertEquals(0, resendSecondsLeft(request, "other@x.io", now = 0))
        assertEquals(0, resendSecondsLeft(null, "me@x.io", now = 0))
    }

    /** Fix (5h): Dart sent an unchanged password and showed the server's `password_reuse`. */
    @Test fun aChangedPasswordMustDiffer() {
        assertEquals(AccountFieldError.MissingCurrentPassword, validatePasswordChange("", "password1", "password1"))
        assertEquals(AccountFieldError.SamePassword, validatePasswordChange("password1", "password1", "password1"))
        assertNull(validatePasswordChange("password0", "password1", "password1"))
    }

    @Test fun deletionNeedsTheAccountEmailTyped() {
        assertEquals(AccountFieldError.EmailMismatch, validateDeletion("me@x.io", "me@x", "pw"))
        assertEquals(AccountFieldError.MissingCurrentPassword, validateDeletion("me@x.io", " ME@X.IO ", ""))
        assertNull(validateDeletion("me@x.io", "me@x.io", "pw"))
    }

    /** Fix (5h): Dart sent these codes to the generic message or had cases that could never match. */
    @Test fun serverCodesMapToSpecificErrors() {
        assertEquals(AccountError.CodeExpired, accountErrorFor("invalid_verification_request", 400))
        assertEquals(AccountError.RateLimited, accountErrorFor("rate_limited", 429))
        assertEquals(AccountError.UserDisabled, accountErrorFor("user_disabled", 403))
        assertEquals(AccountError.SessionExpired, accountErrorFor("invalid_access_token", 401))
        assertEquals(AccountError.FeatureUnavailable, accountErrorFor(null, 404))
        assertEquals(AccountError.RateLimited, accountErrorFor(null, 429))
        assertEquals(AccountError.ServiceUnavailable, accountErrorFor("something_new", 503))
        assertEquals(AccountError.Unknown, accountErrorFor("something_new", 400))
    }

    /** Fix (5h): Dart always assumed a local Monday reset and ignored the server's period. */
    @Test fun quotaResetsAWeekAfterTheServerPeriod() {
        val start = Instant.parse("2026-10-08T04:00:00Z")
        assertEquals(Instant.parse("2026-10-15T04:00:00Z"), quotaResetAt(start, Instant.parse("2026-10-11T00:00:00Z"), ZoneOffset.UTC))
        // A stale period rolls forward to the next reset.
        assertEquals(Instant.parse("2026-10-22T04:00:00Z"), quotaResetAt(start, Instant.parse("2026-10-16T00:00:00Z"), ZoneOffset.UTC))
        // Without one, the next local Monday 00:00 (2026-10-11 is a Sunday).
        val zone = ZoneId.of("Asia/Shanghai")
        assertEquals(Instant.parse("2026-10-11T16:00:00Z"), quotaResetAt(null, Instant.parse("2026-10-11T02:00:00Z"), zone))
    }

    @Test fun resetCountdownRoundsHoursUp() {
        val now = Instant.parse("2026-10-11T00:00:00Z")
        assertEquals(1L to 1L, quotaResetIn(now.plusSeconds(24 * 3600 + 60), now))
        assertEquals(0L to 0L, quotaResetIn(now.minusSeconds(10), now))
    }

    @Test fun thisDeviceComesFirstThenTheMostRecent() {
        val old = AccountDevice("a", false, Instant.parse("2026-01-01T00:00:00Z"), null)
        val recent = AccountDevice("b", false, null, Instant.parse("2026-10-01T00:00:00Z"))
        val current = AccountDevice("c", true, Instant.parse("2025-01-01T00:00:00Z"), null)
        assertEquals(listOf("c", "b", "a"), sortedDevices(listOf(old, recent, current)).map { it.id })
    }

    @Test fun remainingShareNeedsALimit() {
        val quota = AccountQuota(true, null, true, balance = 25, weeklyLimit = 100, weeklyUsed = 75, weeklyPeriodStart = null)
        assertEquals(.25f, quota.remainingFraction!!, .0001f)
        assertNull(quota.copy(weeklyLimit = 0).remainingFraction)
    }
}
