package com.hossammo.cash.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.data.repo.CurrencyRepository
import com.hossammo.cash.data.repo.SeedRepository
import com.hossammo.cash.data.security.LockManager
import com.hossammo.cash.data.settings.UserPreferencesRepository
import com.hossammo.cash.data.settings.UserPrefs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val prefsRepo: UserPreferencesRepository,
    private val seed: SeedRepository,
    private val currency: CurrencyRepository,
    private val metals: com.hossammo.cash.data.metal.MetalRepository,
    val lockManager: LockManager,
) : ViewModel() {

    val prefs: StateFlow<UserPrefs?> = prefsRepo.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val locked: StateFlow<Boolean> = lockManager.locked

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _startupError = MutableStateFlow(false)
    val startupError: StateFlow<Boolean> = _startupError.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                lockManager.evaluateOnStart()
                seed.seedIfNeeded()
                _ready.value = true
                // تحديث تلقائي للأسعار عند الحاجة (يعمل بصمت؛ عند عدم وجود إنترنت تبقى الأسعار المحفوظة)
                if (currency.isAutoRefreshDue()) currency.refresh()
                // أسعار الذهب والفضة في مصر: تحديث صامت إذا مر أكثر من ساعة
                runCatching { metals.refreshIfDue() }
            } catch (e: Exception) {
                _startupError.value = true
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(15_000)
                lockManager.checkInactivity()
            }
        }
    }

    fun setDarkTheme(dark: Boolean) = viewModelScope.launch {
        prefsRepo.setTheme(if (dark) com.hossammo.cash.data.settings.ThemeMode.DARK else com.hossammo.cash.data.settings.ThemeMode.LIGHT)
    }
    fun completeOnboarding() = viewModelScope.launch { prefsRepo.setOnboardingDone(true) }
    fun toggleHideValues() = viewModelScope.launch { prefsRepo.setHideValues(!(prefs.value?.hideValues ?: false)) }
}
