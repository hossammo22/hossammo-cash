package com.hossammo.cash.ui.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Handshake
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.hossammo.cash.R
import com.hossammo.cash.ui.screens.charts.ChartsScreen
import com.hossammo.cash.ui.screens.dashboard.DashboardScreen
import com.hossammo.cash.ui.screens.debts.DebtDetailScreen
import com.hossammo.cash.ui.screens.debts.DebtEditorScreen
import com.hossammo.cash.ui.screens.debts.DebtsScreen
import com.hossammo.cash.ui.screens.liquidity.CategoriesScreen
import com.hossammo.cash.ui.screens.liquidity.CompareMonthsScreen
import com.hossammo.cash.ui.screens.liquidity.ItemDetailScreen
import com.hossammo.cash.ui.screens.liquidity.ItemEditorScreen
import com.hossammo.cash.ui.screens.liquidity.LiquidityScreen
import com.hossammo.cash.ui.screens.liquidity.MonthEntryScreen
import com.hossammo.cash.ui.screens.liquidity.MonthsScreen
import com.hossammo.cash.ui.screens.liquidity.TransfersScreen
import com.hossammo.cash.ui.screens.more.AboutScreen
import com.hossammo.cash.ui.screens.more.AuditLogScreen
import com.hossammo.cash.ui.screens.more.BackupScreen
import com.hossammo.cash.ui.screens.more.CurrencySettingsScreen
import com.hossammo.cash.ui.screens.more.DeleteAllScreen
import com.hossammo.cash.ui.screens.more.GeneralSettingsScreen
import com.hossammo.cash.ui.screens.more.MoreScreen
import com.hossammo.cash.ui.screens.more.NotificationSettingsScreen
import com.hossammo.cash.ui.screens.more.ReportScreen
import com.hossammo.cash.ui.screens.more.SecuritySettingsScreen
import com.hossammo.cash.ui.screens.more.TrashScreen
import com.hossammo.cash.ui.screens.variable.VariableSavingsScreen

object Routes {
    const val DASHBOARD = "dashboard"
    const val LIQUIDITY = "liquidity"
    const val VARIABLE = "variable"
    const val DEBTS = "debts"
    const val MORE = "more"
    const val CHARTS = "charts"
    const val ITEM_EDIT = "item_edit?id={id}"
    const val ITEM_DETAIL = "item/{id}"
    const val MONTHS = "months"
    const val MONTH_ENTRY = "month_entry/{key}"
    const val COMPARE = "compare"
    const val TRANSFERS = "transfers"
    const val CATEGORIES = "categories"
    const val DEBT_EDIT = "debt_edit?id={id}&dir={dir}"
    const val DEBT_DETAIL = "debt/{id}"
    const val REPORT = "report/{type}"
    const val SETTINGS_GENERAL = "settings_general"
    const val SETTINGS_SECURITY = "settings_security"
    const val SETTINGS_CURRENCY = "settings_currency"
    const val SETTINGS_NOTIFICATIONS = "settings_notifications"
    const val BACKUP = "backup"
    const val TRASH = "trash"
    const val AUDIT = "audit"
    const val ABOUT = "about"
    const val DELETE_ALL = "delete_all"

    fun itemEdit(id: String? = null) = if (id == null) "item_edit" else "item_edit?id=$id"
    fun itemDetail(id: String) = "item/$id"
    fun monthEntry(key: Int) = "month_entry/$key"
    fun debtEdit(id: String? = null, dir: String? = null) = buildString {
        append("debt_edit")
        val params = listOfNotNull(id?.let { "id=$it" }, dir?.let { "dir=$it" })
        if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
    }
    fun debtDetail(id: String) = "debt/$id"
    fun report(type: String) = "report/$type"
}

/** يتيح للشاشات الوصول إلى زر إخفاء القيم. */
data class HideValuesControl(val hidden: Boolean, val toggle: () -> Unit)
val LocalHideValues = staticCompositionLocalOf { HideValuesControl(false) {} }

private data class TopDest(val route: String, val label: Int, val icon: ImageVector, val selectedIcon: ImageVector)

private val topDestinations = listOf(
    TopDest(Routes.DASHBOARD, R.string.nav_home, Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    TopDest(Routes.LIQUIDITY, R.string.nav_liquidity, Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    TopDest(Routes.VARIABLE, R.string.nav_variable, Icons.Outlined.Savings, Icons.Filled.Savings),
    TopDest(Routes.DEBTS, R.string.nav_debts, Icons.Outlined.Handshake, Icons.Filled.Handshake),
    TopDest(Routes.MORE, R.string.nav_more, Icons.Outlined.Assessment, Icons.Filled.Assessment),
)

@Composable
fun MainScaffold(hideValues: Boolean, onToggleHide: () -> Unit) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val isTop = topDestinations.any { it.route == current }
    val wide = LocalConfiguration.current.screenWidthDp >= 600

    fun go(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalHideValues provides HideValuesControl(hideValues, onToggleHide)) {
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    topDestinations.forEach { d ->
                        NavigationRailItem(
                            selected = current == d.route,
                            onClick = { go(d.route) },
                            icon = { Icon(if (current == d.route) d.selectedIcon else d.icon, null) },
                            label = { Text(stringResource(d.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
                AppNavHost(nav, Modifier.weight(1f))
            }
        } else {
            Scaffold(
                bottomBar = {
                    if (isTop) NavigationBar {
                        topDestinations.forEach { d ->
                            NavigationBarItem(
                                selected = current == d.route,
                                onClick = { go(d.route) },
                                icon = { Icon(if (current == d.route) d.selectedIcon else d.icon, null) },
                                label = { Text(stringResource(d.label), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            )
                        }
                    }
                },
            ) { padding ->
                AppNavHost(nav, Modifier.padding(bottom = padding.calculateBottomPadding()))
            }
        }
    }
}

@Composable
fun AppNavHost(nav: NavHostController, modifier: Modifier = Modifier) {
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(nav, startDestination = Routes.DASHBOARD, modifier = modifier) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenCharts = { nav.navigate(Routes.CHARTS) },
                onOpenItem = { nav.navigate(Routes.itemDetail(it)) },
                onOpenMonths = { nav.navigate(Routes.MONTHS) },
                onOpenMonthEntry = { nav.navigate(Routes.monthEntry(it)) },
            )
        }
        composable(Routes.CHARTS) { ChartsScreen(onBack = back, onOpenItem = { nav.navigate(Routes.itemDetail(it)) }) }
        composable(Routes.LIQUIDITY) {
            LiquidityScreen(
                onAddItem = { nav.navigate(Routes.itemEdit()) },
                onOpenItem = { nav.navigate(Routes.itemDetail(it)) },
                onOpenMonths = { nav.navigate(Routes.MONTHS) },
                onOpenTransfers = { nav.navigate(Routes.TRANSFERS) },
                onOpenCategories = { nav.navigate(Routes.CATEGORIES) },
                onOpenCompare = { nav.navigate(Routes.COMPARE) },
                onOpenMonthEntry = { nav.navigate(Routes.monthEntry(it)) },
            )
        }
        composable(Routes.ITEM_EDIT, arguments = listOf(navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null })) {
            ItemEditorScreen(onBack = back, onSaved = back)
        }
        composable(Routes.ITEM_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            ItemDetailScreen(onBack = back, onEdit = { nav.navigate(Routes.itemEdit(it)) }, onOpenItem = { nav.navigate(Routes.itemDetail(it)) })
        }
        composable(Routes.MONTHS) {
            MonthsScreen(onBack = back, onOpenMonth = { nav.navigate(Routes.monthEntry(it)) }, onCompare = { nav.navigate(Routes.COMPARE) })
        }
        composable(Routes.MONTH_ENTRY, arguments = listOf(navArgument("key") { type = NavType.IntType })) { MonthEntryScreen(onBack = back) }
        composable(Routes.COMPARE) { CompareMonthsScreen(onBack = back) }
        composable(Routes.TRANSFERS) { TransfersScreen(onBack = back) }
        composable(Routes.CATEGORIES) { CategoriesScreen(onBack = back) }
        composable(Routes.VARIABLE) { VariableSavingsScreen(onOpenItem = { nav.navigate(Routes.itemDetail(it)) }, onOpenCurrencySettings = { nav.navigate(Routes.SETTINGS_CURRENCY) }) }
        composable(Routes.DEBTS) {
            DebtsScreen(onAdd = { dir -> nav.navigate(Routes.debtEdit(dir = dir)) }, onOpen = { nav.navigate(Routes.debtDetail(it)) })
        }
        composable(
            Routes.DEBT_EDIT,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("dir") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { DebtEditorScreen(onBack = back, onSaved = back) }
        composable(Routes.DEBT_DETAIL, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            DebtDetailScreen(onBack = back, onEdit = { nav.navigate(Routes.debtEdit(id = it)) })
        }
        composable(Routes.MORE) { MoreScreen(onNavigate = { nav.navigate(it) }) }
        composable(Routes.REPORT, arguments = listOf(navArgument("type") { type = NavType.StringType })) { ReportScreen(onBack = back) }
        composable(Routes.SETTINGS_GENERAL) { GeneralSettingsScreen(onBack = back) }
        composable(Routes.SETTINGS_SECURITY) { SecuritySettingsScreen(onBack = back) }
        composable(Routes.SETTINGS_CURRENCY) { CurrencySettingsScreen(onBack = back) }
        composable(Routes.SETTINGS_NOTIFICATIONS) { NotificationSettingsScreen(onBack = back) }
        composable(Routes.BACKUP) { BackupScreen(onBack = back) }
        composable(Routes.TRASH) { TrashScreen(onBack = back) }
        composable(Routes.AUDIT) { AuditLogScreen(onBack = back) }
        composable(Routes.ABOUT) { AboutScreen(onBack = back) }
        composable(Routes.DELETE_ALL) { DeleteAllScreen(onBack = back) }
    }
}
