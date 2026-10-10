package cn.com.omnimind.bot.activity

import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import cn.com.omnimind.bot.ui.account.NativeAccountRoute
import cn.com.omnimind.bot.ui.account.NativeAccountViewModel

/**
 * The account page on its own, for callers outside native Home (batch 5h).
 * With [EXTRA_AUTH_ONLY] (onboarding) it only signs in, and a successful
 * sign-in closes it.
 */
class NativeAccountActivity : NativeScreenActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val authOnly = intent.getBooleanExtra(EXTRA_AUTH_ONLY, false)
        val viewModel = ViewModelProvider(this, NativeAccountViewModel.Factory(this, authOnly))[NativeAccountViewModel::class.java]
        setScreen { NativeAccountRoute(viewModel, onBack = ::finish) }
    }

    companion object {
        const val EXTRA_AUTH_ONLY = "account_auth_only"
    }
}
