package com.hossammo.cash.notifications

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.hossammo.cash.R
import com.hossammo.cash.data.db.AppDatabase
import com.hossammo.cash.data.repo.CurrencyRepository
import com.hossammo.cash.data.repo.RefreshResult
import com.hossammo.cash.data.settings.UserPreferencesRepository
import com.hossammo.cash.domain.calc.DebtCalculator
import com.hossammo.cash.domain.model.DebtStatus
import com.hossammo.cash.domain.model.MonthKey
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * فحص يومي محلي: تذكير بتسجيل الشهر، استحقاق/تأخر الديون، تحديث الأسعار، تذكير النسخ الاحتياطي.
 */
@HiltWorker
class DailyCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val db: AppDatabase,
    private val prefs: UserPreferencesRepository,
    private val currency: CurrencyRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val p = prefs.current()
        val n = p.notifications
        val today = LocalDate.now()

        // 1) تحديث أسعار العملات تلقائيًا إذا حان وقته
        if (currency.isAutoRefreshDue()) {
            val r = currency.refresh()
            if (n.ratesUpdate) {
                val text = if (r is RefreshResult.Success) ctx.getString(R.string.notif_rates_ok) else ctx.getString(R.string.notif_rates_failed)
                Notifier.show(ctx, Notifier.ID_RATES, Notifier.CHANNEL_SYSTEM, ctx.getString(R.string.notif_rates_title), text)
            }
        }

        // 2) تذكير تسجيل أرصدة الشهر الجديد
        val currentKey = MonthKey.of(today.year, today.monthValue)
        if (n.monthlyReminder && today.dayOfMonth >= n.monthlyReminderDay && p.lastMonthlyReminderKey != currentKey.value) {
            val recorded = db.monthDao().byKey(currentKey.value)?.deletedAt == null && db.monthDao().byKey(currentKey.value) != null
            if (!recorded) {
                Notifier.show(ctx, Notifier.ID_MONTHLY, Notifier.CHANNEL_REMINDERS, ctx.getString(R.string.notif_monthly_title), ctx.getString(R.string.notif_monthly_text))
                prefs.setLastMonthlyReminder(currentKey.value)
            }
        }

        // 3) الديون: اقتراب الاستحقاق والتأخر
        if (n.debtDue || n.debtOverdue) {
            val debts = db.debtDao().all().filter { it.deletedAt == null }
            var dueSoon = 0
            var overdue = 0
            for (d in debts) {
                val payments = db.paymentDao().activeForDebt(d.id).map { it.amount }
                val due = d.dueDate?.let { LocalDate.ofEpochDay(it) }
                val status = DebtCalculator.status(d.originalAmount, payments, due, today)
                if (status == DebtStatus.COMPLETED || due == null) continue
                if (status == DebtStatus.OVERDUE) overdue++
                else if (ChronoUnit.DAYS.between(today, due) in 0..n.debtDueDaysBefore.toLong()) dueSoon++
            }
            if (n.debtDue && dueSoon > 0) {
                Notifier.show(ctx, Notifier.ID_DEBT_DUE, Notifier.CHANNEL_DEBTS, ctx.getString(R.string.notif_due_title), ctx.resources.getQuantityString(R.plurals.notif_due_text, dueSoon, dueSoon))
            }
            if (n.debtOverdue && overdue > 0) {
                Notifier.show(ctx, Notifier.ID_DEBT_OVERDUE, Notifier.CHANNEL_DEBTS, ctx.getString(R.string.notif_overdue_title), ctx.resources.getQuantityString(R.plurals.notif_overdue_text, overdue, overdue))
            }
        }

        // 4) تذكير النسخ الاحتياطي
        if (n.backupReminder) {
            val last = db.backupDao().lastSuccessful()?.createdAt ?: 0L
            val days = (System.currentTimeMillis() - last) / 86_400_000L
            if (days >= n.backupIntervalDays) {
                Notifier.show(ctx, Notifier.ID_BACKUP, Notifier.CHANNEL_REMINDERS, ctx.getString(R.string.notif_backup_title), ctx.getString(R.string.notif_backup_text))
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_check"

        /** جدولة الفحص اليومي في الساعة التي يحددها المستخدم. */
        fun schedule(context: Context, hourOfDay: Int, replace: Boolean = false) {
            val now = LocalDateTime.now()
            var next = now.withHour(hourOfDay.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes()
            val request = PeriodicWorkRequestBuilder<DailyCheckWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
