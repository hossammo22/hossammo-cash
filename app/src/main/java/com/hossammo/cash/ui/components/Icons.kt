package com.hossammo.cash.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Factory
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.ui.graphics.vector.ImageVector

/** أيقونات العناصر والفئات (تُحفظ كمفتاح نصي في القاعدة). */
object IconCatalog {
    val all: List<Pair<String, ImageVector>> = listOf(
        "bank" to Icons.Filled.AccountBalance,
        "cash" to Icons.Filled.Payments,
        "gold" to Icons.Filled.WorkspacePremium,
        "stocks" to Icons.Filled.ShowChart,
        "farm" to Icons.Filled.Agriculture,
        "factory" to Icons.Filled.Factory,
        "currency" to Icons.Filled.CurrencyExchange,
        "dollar" to Icons.Filled.AttachMoney,
        "savings" to Icons.Filled.Savings,
        "wallet" to Icons.Filled.Wallet,
        "business" to Icons.Filled.Business,
        "store" to Icons.Filled.Store,
        "home" to Icons.Filled.Home,
        "car" to Icons.Filled.DirectionsCar,
        "diamond" to Icons.Filled.Diamond,
        "asset" to Icons.Filled.Inventory2,
    )
    private val map = all.toMap()
    fun icon(key: String?): ImageVector = map[key] ?: Icons.Filled.Savings
}
