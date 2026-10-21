package com.hossammo.cash.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hossammo.cash.R
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.LocalAppFormatter
import com.hossammo.cash.ui.navigation.MainScaffold
import com.hossammo.cash.ui.screens.security.LockScreen
import com.hossammo.cash.ui.screens.onboarding.OnboardingScreen
import com.hossammo.cash.ui.theme.HossamMoCashTheme
import com.hossammo.cash.util.AppFormatter

@Composable
fun AppRoot(viewModel: MainViewModel) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val locked by viewModel.locked.collectAsStateWithLifecycle()
    val ready by viewModel.ready.collectAsStateWithLifecycle()
    val startupError by viewModel.startupError.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val p = prefs
    HossamMoCashTheme(themeMode = p?.themeMode ?: com.hossammo.cash.data.settings.ThemeMode.SYSTEM) {
        val formatter = remember(p?.numberStyle, p?.hideValues) {
            AppFormatter(context, p?.numberStyle ?: com.hossammo.cash.data.settings.NumberStyle.LATIN, p?.hideValues ?: false)
        }
        // التطبيق عربي بالكامل: اتجاه RTL ثابت بغض النظر عن لغة الجهاز
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalAppFormatter provides formatter) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                when {
                    startupError -> EmptyState(stringResource(R.string.error_title), stringResource(R.string.startup_error))
                    p == null || !ready -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    p.pinEnabled && locked -> LockScreen(biometricEnabled = p.biometricEnabled, onUnlocked = { viewModel.lockManager.unlock() })
                    !p.onboardingDone -> OnboardingScreen(onFinish = { viewModel.completeOnboarding() })
                    else -> MainScaffold(hideValues = p.hideValues, onToggleHide = { viewModel.toggleHideValues() })
                }
            }
        }
    }
}
