package cn.com.omnimind.nativeui.account

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/*
 * Account rules (batch 5h), ported from `my/pages/account/account_page.dart`
 * and `account_lifecycle_sheets.dart`. Pure: the app owns `OmniAccount` and
 * maps its exceptions to [AccountError].
 */

/** Dart `_isValidEmail`: something before the `@` and a dot after it. */
fun isValidAccountEmail(value: String): Boolean {
    val email = value.trim()
    val at = email.indexOf('@')
    return at > 0 && email.indexOf('.', at) > at + 1
}

const val ACCOUNT_PASSWORD_MIN = 8
const val ACCOUNT_PASSWORD_MAX = 16
const val ACCOUNT_CODE_LENGTH = 6

/** Dart counts grapheme clusters; code points match it for every realistic password. */
fun isValidAccountPassword(value: String): Boolean =
    value.codePointCount(0, value.length) in ACCOUNT_PASSWORD_MIN..ACCOUNT_PASSWORD_MAX

/** Why a form was refused before any request. */
enum class AccountFieldError {
    InvalidEmail, MissingPassword, PasswordLength, PasswordMismatch, InvalidCode, CodeNotRequested,
    MissingCurrentPassword, SamePassword, EmailMismatch,
}

/**
 * A sent code belongs to one email. Dart only remembered the request id, so
 * changing the email after sending reused the old request. [expiresAt] lets
 * the form say the code is stale instead of waiting for the server.
 */
@Immutable
data class VerificationRequest(val email: String, val requestId: String, val sentAt: Long, val expiresAt: Long) {
    fun matches(email: String): Boolean = this.email.equals(email.trim(), ignoreCase = true)
    fun expired(now: Long): Boolean = now >= expiresAt

    companion object {
        /** A code the server just sent for [email], valid for [expiresInSeconds]. */
        fun sent(email: String, requestId: String, expiresInSeconds: Long, now: Long = System.currentTimeMillis()) =
            VerificationRequest(email.trim(), requestId, now, now + expiresInSeconds.coerceAtLeast(0) * 1000)
    }
}

/**
 * Cooldown before another code can be sent. Dart had none, so a double tap
 * mailed two codes and the first was then invalid. The server's limit still
 * applies; this only stops accidental repeats.
 */
const val ACCOUNT_RESEND_COOLDOWN_MS = 60_000L

fun resendSecondsLeft(request: VerificationRequest?, email: String, now: Long): Int {
    if (request == null || !request.matches(email)) return 0
    val left = ACCOUNT_RESEND_COOLDOWN_MS - (now - request.sentAt)
    return if (left <= 0) 0 else ((left + 999) / 1000).toInt()
}

fun validateLogin(email: String, password: String): AccountFieldError? = when {
    !isValidAccountEmail(email) -> AccountFieldError.InvalidEmail
    password.isEmpty() -> AccountFieldError.MissingPassword
    else -> null
}

/** Register and reset share the new-password rules and the code checks. */
fun validateNewPasswordWithCode(
    email: String,
    password: String,
    confirmation: String,
    code: String,
    request: VerificationRequest?,
    now: Long,
): AccountFieldError? = when {
    !isValidAccountEmail(email) -> AccountFieldError.InvalidEmail
    !isValidAccountPassword(password) -> AccountFieldError.PasswordLength
    password != confirmation -> AccountFieldError.PasswordMismatch
    code.trim().length != ACCOUNT_CODE_LENGTH -> AccountFieldError.InvalidCode
    request == null || !request.matches(email) || request.expired(now) -> AccountFieldError.CodeNotRequested
    else -> null
}

/** Dart did not check that the new password differs; the server answered `password_reuse`. */
fun validatePasswordChange(current: String, password: String, confirmation: String): AccountFieldError? = when {
    current.isEmpty() -> AccountFieldError.MissingCurrentPassword
    !isValidAccountPassword(password) -> AccountFieldError.PasswordLength
    password != confirmation -> AccountFieldError.PasswordMismatch
    password == current -> AccountFieldError.SamePassword
    else -> null
}

fun validateDeletion(accountEmail: String, typedEmail: String, password: String): AccountFieldError? = when {
    !typedEmail.trim().equals(accountEmail.trim(), ignoreCase = true) -> AccountFieldError.EmailMismatch
    password.isEmpty() -> AccountFieldError.MissingCurrentPassword
    else -> null
}

/**
 * Every failure the account page can show, from the native exceptions and
 * server codes. Dart matched channel codes in `_messageFor`, which missed
 * several server codes (`user_disabled`, `password_reset_failed`…) and had
 * one case that could never run.
 */
enum class AccountError {
    InvalidCredentials, EmailTaken, InvalidEmail, InvalidPassword, InvalidCode, CodeExpired, RateLimited,
    VerificationUnavailable, ServiceUnavailable, FeatureUnavailable, CurrentPasswordInvalid, PasswordReuse,
    SessionNotFound, CannotRevokeCurrent, UserDisabled, SessionExpired, NotConfigured, UpdateRequired,
    PolicyUnavailable, StorageUnavailable, Network, Unknown,
}

/** Server error codes to [AccountError]; anything else is [AccountError.Unknown]. */
fun accountErrorFor(serverCode: String?, statusCode: Int): AccountError = when (serverCode) {
    "invalid_credentials" -> AccountError.InvalidCredentials
    "email_already_registered" -> AccountError.EmailTaken
    "invalid_email" -> AccountError.InvalidEmail
    "invalid_password" -> AccountError.InvalidPassword
    "invalid_verification_code" -> AccountError.InvalidCode
    "invalid_verification_request" -> AccountError.CodeExpired
    "rate_limited", "too_many_requests" -> AccountError.RateLimited
    "verification_unavailable" -> AccountError.VerificationUnavailable
    "account_service_unavailable", "internal_error" -> AccountError.ServiceUnavailable
    "current_password_invalid" -> AccountError.CurrentPasswordInvalid
    "password_reuse" -> AccountError.PasswordReuse
    "session_not_found", "invalid_session_id" -> AccountError.SessionNotFound
    "cannot_revoke_current_session" -> AccountError.CannotRevokeCurrent
    "user_disabled" -> AccountError.UserDisabled
    "invalid_access_token", "invalid_refresh_token", "missing_access_token" -> AccountError.SessionExpired
    else -> when {
        statusCode == 404 && serverCode.isNullOrBlank() -> AccountError.FeatureUnavailable
        statusCode == 429 -> AccountError.RateLimited
        statusCode >= 500 -> AccountError.ServiceUnavailable
        else -> AccountError.Unknown
    }
}

@Immutable
data class AccountQuota(
    val available: Boolean,
    val unavailableReason: String?,
    val enabled: Boolean,
    val balance: Long,
    val weeklyLimit: Long,
    val weeklyUsed: Long,
    val weeklyPeriodStart: Instant?,
) {
    /** Remaining share of the weekly limit, 0..1; null without a limit. */
    val remainingFraction: Float?
        get() = if (weeklyLimit <= 0) null else (balance.toFloat() / weeklyLimit).coerceIn(0f, 1f)
}

/**
 * When the weekly quota resets. Dart always assumed the next local Monday;
 * the server's period start is authoritative when it sends one (a week
 * after it), and the Monday rule is the fallback.
 */
fun quotaResetAt(periodStart: Instant?, now: Instant, zone: ZoneId): Instant {
    if (periodStart != null) {
        var reset = periodStart.plus(Duration.ofDays(7))
        while (!reset.isAfter(now)) reset = reset.plus(Duration.ofDays(7))
        return reset
    }
    val today = ZonedDateTime.ofInstant(now, zone).toLocalDate()
    return today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant()
}

/** Days and hours until [resetAt], hours rounded up as in Dart. */
fun quotaResetIn(resetAt: Instant, now: Instant): Pair<Long, Long> {
    val minutes = Duration.between(now, resetAt).toMinutes().coerceAtLeast(0)
    val hours = (minutes + 59) / 60
    return hours / 24 to hours % 24
}

@Immutable
data class AccountDevice(val id: String, val current: Boolean, val createdAt: Instant?, val lastUsedAt: Instant?) {
    val lastActive: Instant? get() = lastUsedAt ?: createdAt
}

/** Dart `SessionsSheet` order: this device first, then the most recently active. */
fun sortedDevices(devices: List<AccountDevice>): List<AccountDevice> =
    devices.sortedWith(compareByDescending<AccountDevice> { it.current }.thenByDescending { it.lastActive })

@Immutable
data class AccountUsage(
    val model: String,
    val promptTokens: Long,
    val completionTokens: Long,
    val totalTokens: Long,
    val quotaUsed: Long,
    val createdAt: Instant?,
)
