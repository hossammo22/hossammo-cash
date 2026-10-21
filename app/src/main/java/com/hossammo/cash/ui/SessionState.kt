package com.hossammo.cash.ui

import com.hossammo.cash.domain.model.MonthKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** حالة مشتركة بين الشاشات: الشهر المختار (يتزامن بين الرئيسية والسيولة والمدخرات المتغيرة). */
@Singleton
class SessionState @Inject constructor() {
    private val _selectedMonth = MutableStateFlow<MonthKey?>(null)
    val selectedMonth: StateFlow<MonthKey?> = _selectedMonth.asStateFlow()

    fun selectMonth(key: MonthKey?) { _selectedMonth.value = key }

    /** يعيد الشهر المختار إن كان موجودًا ضمن الأشهر، وإلا آخر شهر مسجل. */
    fun resolve(months: List<MonthKey>): MonthKey? {
        val s = _selectedMonth.value
        return if (s != null && s in months) s else months.lastOrNull()
    }
}
