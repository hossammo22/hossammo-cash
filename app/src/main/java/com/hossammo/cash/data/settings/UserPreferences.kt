package com.hossammo.cash.data.settings

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hossammo.cash.data.security.PinHasher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_prefs")

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class NumberStyle { ARABIC, LATIN }

/** مصادر أسعار العملات المجانية المتاحة. */
enum class RateProviderId(val id: String) {
    OPEN_ER_API("open_er_api"),
    FAWAZ_CURRENCY_API("fawaz_currency_api");

    companion object {
        fun from(id: String?): RateProviderId = entries.firstOrNull { it.id == id } ?: OPEN_ER_API
    }
}

data class NotificationPrefs(
    val monthlyReminder: Boolean = true,
    val monthlyReminderDay: Int = 1,
    val debtDue: Boolean = true,
    val debtDueDaysBefore: Int = 3,
    val debtOverdue: Boolean = true,
    val ratesUpdate: Boolean = false,
    val backupReminder: Boolean = true,
    val backupIntervalDays: Int = 30,
    val hourOfDay: Int = 10,
)

/** إعدادات غير مالية تُحفظ في DataStore. */
data class UserPrefs(
    val onboardingDone: Boolean = false,
    val displayName: String = "",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val numberStyle: NumberStyle = NumberStyle.LATIN,
    val hideValues: Boolean = false,
    val biometricEnabled: Boolean = false,
    val pinEnabled: Boolean = false,
    /** بالدقائق؛ 0 = فوري عند الخروج، -1 = بدون قفل تلقائي. */
    val autoLockMinutes: Int = 1,
    val secureScreen: Boolean = true,
    val rateProvider: RateProviderId = RateProviderId.OPEN_ER_API,
    /** بالساعات؛ 0 = إيقاف التحديث التلقائي. */
    val autoRefreshHours: Int = 24,
    val lastRateRefreshAt: Long = 0,
    val lastRateRefreshOk: Boolean = false,
    val lastRateError: String? = null,
    val notifications: NotificationPrefs = NotificationPrefs(),
    val lastMonthlyReminderKey: Int = 0,
)

@Singleton
class UserPreferencesRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object K {
        val onboarding = booleanPreferencesKey("onboarding_done")
        val displayName = stringPreferencesKey("display_name")
        val theme = stringPreferencesKey("theme_mode")
        val numbers = stringPreferencesKey("number_style")
        val hide = booleanPreferencesKey("hide_values")
        val bio = booleanPreferencesKey("biometric_enabled")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val autoLock = intPreferencesKey("auto_lock_minutes")
        val secure = booleanPreferencesKey("secure_screen")
        val provider = stringPreferencesKey("rate_provider")
        val refreshHours = intPreferencesKey("auto_refresh_hours")
        val lastRefresh = longPreferencesKey("last_rate_refresh")
        val lastRefreshOk = booleanPreferencesKey("last_rate_refresh_ok")
        val lastRateError = stringPreferencesKey("last_rate_error")
        val nMonthly = booleanPreferencesKey("n_monthly")
        val nMonthlyDay = intPreferencesKey("n_monthly_day")
        val nDue = booleanPreferencesKey("n_due")
        val nDueDays = intPreferencesKey("n_due_days")
        val nOverdue = booleanPreferencesKey("n_overdue")
        val nRates = booleanPreferencesKey("n_rates")
        val nBackup = booleanPreferencesKey("n_backup")
        val nBackupDays = intPreferencesKey("n_backup_days")
        val nHour = intPreferencesKey("n_hour")
        val lastMonthlyReminder = intPreferencesKey("last_monthly_reminder")
        val pinFailures = intPreferencesKey("pin_failures")
        val pinLockedUntil = longPreferencesKey("pin_locked_until")
    }

    val prefs: Flow<UserPrefs> = context.dataStore.data.map { p ->
        UserPrefs(
            onboardingDone = p[K.onboarding] ?: false,
            displayName = p[K.displayName] ?: "",
            themeMode = runCatching { ThemeMode.valueOf(p[K.theme] ?: "") }.getOrDefault(ThemeMode.SYSTEM),
            numberStyle = runCatching { NumberStyle.valueOf(p[K.numbers] ?: "") }.getOrDefault(NumberStyle.LATIN),
            hideValues = p[K.hide] ?: false,
            biometricEnabled = p[K.bio] ?: false,
            pinEnabled = p[K.pinHash] != null,
            autoLockMinutes = p[K.autoLock] ?: 1,
            secureScreen = p[K.secure] ?: true,
            rateProvider = RateProviderId.from(p[K.provider]),
            autoRefreshHours = p[K.refreshHours] ?: 24,
            lastRateRefreshAt = p[K.lastRefresh] ?: 0,
            lastRateRefreshOk = p[K.lastRefreshOk] ?: false,
            lastRateError = p[K.lastRateError],
            notifications = NotificationPrefs(
                monthlyReminder = p[K.nMonthly] ?: true,
                monthlyReminderDay = p[K.nMonthlyDay] ?: 1,
                debtDue = p[K.nDue] ?: true,
                debtDueDaysBefore = p[K.nDueDays] ?: 3,
                debtOverdue = p[K.nOverdue] ?: true,
                ratesUpdate = p[K.nRates] ?: false,
                backupReminder = p[K.nBackup] ?: true,
                backupIntervalDays = p[K.nBackupDays] ?: 30,
                hourOfDay = p[K.nHour] ?: 10,
            ),
            lastMonthlyReminderKey = p[K.lastMonthlyReminder] ?: 0,
        )
    }

    suspend fun current(): UserPrefs = prefs.first()

    suspend fun setOnboardingDone(done: Boolean) = context.dataStore.edit { it[K.onboarding] = done }
    suspend fun setDisplayName(name: String) = context.dataStore.edit { it[K.displayName] = name.trim() }
    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[K.theme] = mode.name }
    suspend fun setNumberStyle(style: NumberStyle) = context.dataStore.edit { it[K.numbers] = style.name }
    suspend fun setHideValues(hide: Boolean) = context.dataStore.edit { it[K.hide] = hide }
    suspend fun setBiometric(enabled: Boolean) = context.dataStore.edit { it[K.bio] = enabled }
    suspend fun setAutoLock(minutes: Int) = context.dataStore.edit { it[K.autoLock] = minutes }
    suspend fun setSecureScreen(enabled: Boolean) = context.dataStore.edit { it[K.secure] = enabled }
    suspend fun setRateProvider(p: RateProviderId) = context.dataStore.edit { it[K.provider] = p.id }
    suspend fun setAutoRefreshHours(h: Int) = context.dataStore.edit { it[K.refreshHours] = h }
    suspend fun setLastMonthlyReminder(key: Int) = context.dataStore.edit { it[K.lastMonthlyReminder] = key }

    suspend fun recordRateRefresh(ok: Boolean, at: Long, error: String?) = context.dataStore.edit {
        it[K.lastRefreshOk] = ok
        if (ok) it[K.lastRefresh] = at
        if (error == null) it.remove(K.lastRateError) else it[K.lastRateError] = error
    }

    suspend fun setNotifications(n: NotificationPrefs) = context.dataStore.edit {
        it[K.nMonthly] = n.monthlyReminder
        it[K.nMonthlyDay] = n.monthlyReminderDay.coerceIn(1, 28)
        it[K.nDue] = n.debtDue
        it[K.nDueDays] = n.debtDueDaysBefore.coerceIn(0, 60)
        it[K.nOverdue] = n.debtOverdue
        it[K.nRates] = n.ratesUpdate
        it[K.nBackup] = n.backupReminder
        it[K.nBackupDays] = n.backupIntervalDays.coerceIn(1, 365)
        it[K.nHour] = n.hourOfDay.coerceIn(0, 23)
    }

    // ---------- PIN ----------

    suspend fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin))
        val salt = PinHasher.newSalt()
        val hash = PinHasher.hash(pin, salt)
        context.dataStore.edit {
            it[K.pinSalt] = Base64.encodeToString(salt, Base64.NO_WRAP)
            it[K.pinHash] = Base64.encodeToString(hash, Base64.NO_WRAP)
            it[K.pinFailures] = 0
        }
    }

    suspend fun clearPin() = context.dataStore.edit {
        it.remove(K.pinHash); it.remove(K.pinSalt); it[K.bio] = false
    }

    sealed interface PinResult {
        data object Ok : PinResult
        data class Wrong(val remainingAttempts: Int) : PinResult
        data class LockedOut(val untilMillis: Long) : PinResult
    }

    /** يتحقق من الـ PIN مع تأخير متزايد بعد 5 محاولات خاطئة. */
    suspend fun verifyPin(pin: String, now: Long = System.currentTimeMillis()): PinResult {
        val data = context.dataStore.data.first()
        val lockedUntil = data[K.pinLockedUntil] ?: 0
        if (now < lockedUntil) return PinResult.LockedOut(lockedUntil)
        val salt = data[K.pinSalt]?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return PinResult.Ok
        val hash = data[K.pinHash]?.let { Base64.decode(it, Base64.NO_WRAP) } ?: return PinResult.Ok
        return if (PinHasher.verify(pin, salt, hash)) {
            context.dataStore.edit { it[K.pinFailures] = 0; it.remove(K.pinLockedUntil) }
            PinResult.Ok
        } else {
            val failures = (data[K.pinFailures] ?: 0) + 1
            context.dataStore.edit {
                it[K.pinFailures] = failures
                if (failures % MAX_ATTEMPTS == 0) {
                    it[K.pinLockedUntil] = now + 30_000L * (failures / MAX_ATTEMPTS)
                }
            }
            if (failures % MAX_ATTEMPTS == 0) PinResult.LockedOut(now + 30_000L * (failures / MAX_ATTEMPTS))
            else PinResult.Wrong(MAX_ATTEMPTS - failures % MAX_ATTEMPTS)
        }
    }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }

    companion object {
        const val MAX_ATTEMPTS = 5
    }
}
