package com.hossammo.cash.data.security

import android.os.SystemClock
import com.hossammo.cash.data.settings.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * يدير حالة قفل التطبيق: قفل عند البدء، قفل تلقائي بعد مدة في الخلفية، وانتهاء الجلسة بعد عدم النشاط.
 */
@Singleton
class LockManager @Inject constructor(private val prefs: UserPreferencesRepository) {

    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundAt: Long = 0
    private var lastInteraction: Long = SystemClock.elapsedRealtime()

    suspend fun evaluateOnStart() {
        val p = prefs.current()
        _locked.value = p.pinEnabled
    }

    fun unlock() {
        _locked.value = false
        lastInteraction = SystemClock.elapsedRealtime()
    }

    fun lockNow() { _locked.value = true }

    fun onUserInteraction() { lastInteraction = SystemClock.elapsedRealtime() }

    fun onBackground() { backgroundAt = SystemClock.elapsedRealtime() }

    suspend fun onForeground() {
        val p = prefs.current()
        if (!p.pinEnabled) { _locked.value = false; return }
        if (backgroundAt == 0L) return
        val elapsed = SystemClock.elapsedRealtime() - backgroundAt
        if (shouldLock(p.autoLockMinutes, elapsed)) _locked.value = true
        backgroundAt = 0
    }

    /** يُستدعى دوريًا أثناء عمل التطبيق للتحقق من انتهاء الجلسة بسبب عدم النشاط. */
    suspend fun checkInactivity() {
        val p = prefs.current()
        if (!p.pinEnabled || _locked.value || p.autoLockMinutes <= 0) return
        val idle = SystemClock.elapsedRealtime() - lastInteraction
        if (idle >= p.autoLockMinutes * 60_000L) _locked.value = true
    }

    companion object {
        fun shouldLock(autoLockMinutes: Int, elapsedMs: Long): Boolean = when {
            autoLockMinutes < 0 -> false
            autoLockMinutes == 0 -> true
            else -> elapsedMs >= autoLockMinutes * 60_000L
        }
    }
}
