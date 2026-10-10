package cn.com.omnimind.bot.activity

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cn.com.omnimind.baselib.i18n.AppLocaleManager
import cn.com.omnimind.bot.ui.nativehome.resolveNativeHomeLocale
import cn.com.omnimind.nativeui.ThemePreference
import cn.com.omnimind.nativeui.theme.OmniTheme

/**
 * A native screen outside native Home (onboarding, account): the stored
 * language and theme, edge-to-edge bars that follow the theme, and the
 * responsive orientation. Subclasses call [setScreen] instead of `setContent`.
 */
abstract class NativeScreenActivity : ComponentActivity() {
    protected val themePreference: ThemePreference by lazy {
        when (StartupThemeResolver.readStoredThemeMode(this)) {
            StartupThemeResolver.StartupThemeMode.DARK -> ThemePreference.Dark
            StartupThemeResolver.StartupThemeMode.LIGHT -> ThemePreference.Light
            StartupThemeResolver.StartupThemeMode.SYSTEM -> ThemePreference.System
        }
    }

    override fun attachBaseContext(newBase: Context) {
        val locale = resolveNativeHomeLocale(AppLocaleManager.readStoredLanguageMode(newBase).storageValue)
        super.attachBaseContext(newBase.createConfigurationContext(
            Configuration(newBase.resources.configuration).apply { setLocale(locale) },
        ))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(StartupThemeResolver.resolveSplashTheme(this))
        AppEntryBehaviors.applyResponsiveOrientation(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    protected fun setScreen(content: @Composable () -> Unit) = setContent {
        val dark = when (themePreference) {
            ThemePreference.System -> isSystemInDarkTheme()
            ThemePreference.Light -> false
            ThemePreference.Dark -> true
        }
        LaunchedEffect(dark) {
            val style = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            window.isNavigationBarContrastEnforced = false
        }
        OmniTheme(themePreference, content)
    }
}
