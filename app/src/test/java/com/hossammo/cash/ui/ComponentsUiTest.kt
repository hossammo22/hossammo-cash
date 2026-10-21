package com.hossammo.cash.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.hossammo.cash.data.settings.NumberStyle
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.domain.model.TrendStatus
import com.hossammo.cash.ui.components.LocalAppFormatter
import com.hossammo.cash.ui.components.MonthSelector
import com.hossammo.cash.ui.components.TrendBadge
import com.hossammo.cash.ui.screens.security.PinPad
import com.hossammo.cash.ui.theme.HossamMoCashTheme
import com.hossammo.cash.util.AppFormatter
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class ComponentsUiTest {
    @get:Rule val compose = createComposeRule()

    private fun setContent(style: NumberStyle = NumberStyle.LATIN, hide: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        val f = AppFormatter(ApplicationProvider.getApplicationContext(), style, hide)
        compose.setContent {
            HossamMoCashTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalAppFormatter provides f) { content() }
            }
        }
    }

    @Test
    fun newItemShowsNotAvailableInsteadOfZeroPercent() {
        setContent { TrendBadge(TrendStatus.NEW, null, null) }
        compose.onNodeWithText("غير متاح").assertIsDisplayed()
    }

    @Test
    fun increaseShowsSignedChangeAndPercent() {
        setContent { TrendBadge(TrendStatus.INCREASED, BigDecimal("19000"), BigDecimal("2.0408")) }
        compose.onNodeWithText("+19,000 • +2.04%", substring = true).assertIsDisplayed()
    }

    @Test
    fun monthSelectorNavigatesToPreviousMonth() {
        val months = listOf(MonthKey.of(2026, 9), MonthKey.of(2026, 10))
        var selected = months[1]
        setContent { MonthSelector(months, selected, { selected = it }) }
        compose.onNodeWithText("أكتوبر 2026").assertIsDisplayed()
        compose.onNode(androidx.compose.ui.test.hasContentDescription("الشهر السابق")).performClick()
        assertEquals(months[0], selected)
    }

    @Test
    fun pinPadSendsDigitsAndSubmit() {
        val typed = StringBuilder()
        var submitted = false
        setContent { PinPad(onDigit = { typed.append(it) }, onBackspace = {}, onSubmit = { submitted = true }) }
        compose.onNodeWithText("1").performClick()
        compose.onNodeWithText("9").performClick()
        compose.onNodeWithText("دخول").performClick()
        assertEquals("19", typed.toString())
        assertEquals(true, submitted)
    }

    @Test
    fun arabicDigitsStyle() {
        setContent(style = NumberStyle.ARABIC) { PinPad(onDigit = {}, onBackspace = {}, onSubmit = {}) }
        compose.onNodeWithText("٥").assertIsDisplayed()
    }
}
