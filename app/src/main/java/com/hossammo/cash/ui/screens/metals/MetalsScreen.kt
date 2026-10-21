package com.hossammo.cash.ui.screens.metals

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAs
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.db.entity.LiquidityItemEntity
import com.hossammo.cash.data.metal.MetalPriceService
import com.hossammo.cash.data.metal.MetalPrices
import com.hossammo.cash.data.metal.MetalRefreshResult
import com.hossammo.cash.data.metal.MetalRepository
import com.hossammo.cash.data.metal.MetalSpec
import com.hossammo.cash.data.metal.metalSpec
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.domain.calc.MetalCalculator
import com.hossammo.cash.domain.calc.MetalType
import com.hossammo.cash.domain.model.AssetType
import com.hossammo.cash.domain.model.Money
import com.hossammo.cash.domain.model.MonthKey
import com.hossammo.cash.ui.components.AmountField
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.HeroCard
import com.hossammo.cash.ui.components.ItemAvatar
import com.hossammo.cash.ui.components.LabeledValue
import com.hossammo.cash.ui.components.SectionHeader
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages
import com.hossammo.cash.ui.screens.MessagesViewModel
import com.hossammo.cash.ui.screens.liquidity.metalFormLabel
import com.hossammo.cash.ui.screens.liquidity.metalTypeLabel
import com.hossammo.cash.ui.screens.liquidity.purityLabel
import com.hossammo.cash.ui.theme.LocalFinanceColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import javax.inject.Inject

data class MetalRow(val item: LiquidityItemEntity, val spec: MetalSpec, val value: BigDecimal?)

data class MetalsUiState(
    val loading: Boolean = true,
    val prices: MetalPrices? = null,
    val rows: List<MetalRow> = emptyList(),
    /** عناصر ذهب بدون وزن/عيار (تحتاج استكمال البيانات). */
    val incomplete: List<LiquidityItemEntity> = emptyList(),
    val goldTotal: BigDecimal = BigDecimal.ZERO,
    val silverTotal: BigDecimal = BigDecimal.ZERO,
    val goldPureGrams: BigDecimal = BigDecimal.ZERO,
    val silverPureGrams: BigDecimal = BigDecimal.ZERO,
    val refreshing: Boolean = false,
    val autoSync: Boolean = true,
) {
    val total: BigDecimal get() = goldTotal + silverTotal
}

@HiltViewModel
class MetalsViewModel @Inject constructor(
    private val metals: MetalRepository,
    liquidity: LiquidityRepository,
) : MessagesViewModel() {
    private val refreshing = MutableStateFlow(false)

    val state: StateFlow<MetalsUiState> = combine(liquidity.observeData(), metals.prices, refreshing, metals.autoSyncFlow) { data, prices, busy, sync ->
        val active = data.items.filter { !it.isArchived && it.assetType == AssetType.GOLD }
        val rows = active.mapNotNull { i -> i.metalSpec()?.let { MetalRow(i, it, metals.valueOf(i, prices)) } }
        fun sum(t: MetalType) = rows.filter { it.spec.type == t }.mapNotNull { it.value }.fold(BigDecimal.ZERO, BigDecimal::add)
        fun grams(t: MetalType) = rows.filter { it.spec.type == t }
            .map { MetalCalculator.pureWeight(t, it.spec.weightGrams, it.spec.purity, it.spec.count) }.fold(BigDecimal.ZERO, BigDecimal::add)
        MetalsUiState(
            loading = false, prices = prices, rows = rows, incomplete = active.filter { it.metalSpec() == null },
            goldTotal = sum(MetalType.GOLD), silverTotal = sum(MetalType.SILVER),
            goldPureGrams = grams(MetalType.GOLD), silverPureGrams = grams(MetalType.SILVER), refreshing = busy, autoSync = sync,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MetalsUiState())

    init {
        // تحديث تلقائي عند فتح الشاشة إذا مر أكثر من 15 دقيقة (ولا يُستبدل السعر اليدوي تلقائيًا)
        viewModelScope.launch {
            refreshing.value = true
            runCatching { metals.refreshIfDue(15 * 60 * 1000L) }
            refreshing.value = false
        }
    }

    fun setAutoSync(v: Boolean) = viewModelScope.launch { metals.setAutoSync(v) }

    fun refresh(silent: Boolean = false) = viewModelScope.launch {
        if (refreshing.value) return@launch
        refreshing.value = true
        val r = metals.refresh()
        refreshing.value = false
        when (r) {
            MetalRefreshResult.Ok -> if (!silent) message(R.string.msg_metal_prices_updated)
            MetalRefreshResult.Offline -> if (!silent) message(R.string.msg_metal_offline, true)
            MetalRefreshResult.Failed -> if (!silent) message(R.string.msg_metal_failed, true)
        }
    }

    fun setManual(gold21: String, silver: String) = viewModelScope.launch {
        val g = Money.parseOrNull(gold21)
        val s = Money.parseOrNull(silver)
        if ((gold21.isNotBlank() && (g == null || g.signum() <= 0)) || (silver.isNotBlank() && (s == null || s.signum() <= 0)) || (g == null && s == null)) {
            message(R.string.msg_invalid_amount, true); return@launch
        }
        metals.setManual(g, s)
        message(R.string.msg_metal_prices_updated)
    }

    fun applyToCurrentMonth() = viewModelScope.launch {
        val now = LocalDate.now()
        val n = metals.applyToMonth(MonthKey.of(now.year, now.monthValue))
        if (n == 0) message(R.string.msg_metal_nothing_recorded, true) else message(R.string.msg_metal_recorded, false, null, n)
    }
}

@Composable
fun MetalsScreen(onBack: () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit, vm: MetalsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val f = fmt()
    val fc = LocalFinanceColors.current
    var manual by remember { mutableStateOf(false) }
    var confirmApply by remember { mutableStateOf(false) }
    val base = state.prices?.base ?: "EGP"

    AppScaffold(
        title = stringResource(R.string.metals_title), onBack = onBack, snackbar = snackbar,
        actions = {
            if (state.refreshing) CircularProgressIndicator(Modifier.padding(8.dp).width(20.dp).height(20.dp), strokeWidth = 2.dp)
            else androidx.compose.material3.IconButton(onClick = { vm.refresh() }) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh_prices)) }
        },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onAdd, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_metal)) }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                HeroCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.metals_total_value), style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.8f))
                    Text(f.money(state.total, base), style = MaterialTheme.typography.headlineLarge.copy(textDirection = TextDirection.Content), fontWeight = FontWeight.ExtraBold, color = fc.gold)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.metal_gold), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                            Text(f.money(state.goldTotal, base), style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content), color = Color.White)
                            Text(stringResource(R.string.pure_grams, f.decimal(state.goldPureGrams)), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        }
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.metal_silver), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.7f))
                            Text(f.money(state.silverTotal, base), style = MaterialTheme.typography.titleMedium.copy(textDirection = TextDirection.Content), color = Color.White)
                            Text(stringResource(R.string.pure_grams, f.decimal(state.silverPureGrams)), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                }
            }
            item {
                SectionHeader(stringResource(R.string.market_prices))
                FinanceCard(Modifier.fillMaxWidth()) {
                    val p = state.prices
                    if (p?.goldPurePerGram == null && p?.silverPurePerGram == null) {
                        Text(stringResource(R.string.metal_no_price_long), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        listOf(24, 21, 18).forEach { k ->
                            LabeledValue(stringResource(R.string.gram_price_at, purityLabel(MetalType.GOLD, k)), f.money(p.gramAt(MetalType.GOLD, k), base), valueColor = if (k == 21) fc.gold else Color.Unspecified)
                        }
                        LabeledValue(stringResource(R.string.gram_price_at, purityLabel(MetalType.SILVER, 999)), f.money(p.gramAt(MetalType.SILVER, 999), base))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                        Text(
                            stringResource(R.string.prices_updated_at, f.dateTime(p.updatedAt), stringResource(when (p.source) {
                                MetalPriceService.SOURCE_MANUAL -> R.string.price_src_manual
                                MetalPriceService.SOURCE_DALEELAK -> R.string.price_src_egypt
                                else -> R.string.price_src_global
                            })),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        stringResource(if (p?.source == MetalPriceService.SOURCE_DALEELAK) R.string.metal_price_note_egypt else R.string.metal_price_note),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                    )
                    com.hossammo.cash.ui.components.SwitchRow(
                        stringResource(R.string.metal_auto_sync), state.autoSync, vm::setAutoSync, subtitle = stringResource(R.string.metal_auto_sync_hint),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.refresh() }, enabled = !state.refreshing, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.refresh_prices)) }
                        OutlinedButton(onClick = { manual = true }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.EditNote, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.manual_price)) }
                    }
                }
            }
            item {
                SectionHeader(stringResource(R.string.my_metals)) {
                    if (state.rows.isNotEmpty()) TextButton(onClick = { confirmApply = true }) { Icon(Icons.Filled.SaveAs, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.record_in_month)) }
                }
            }
            if (!state.loading && state.rows.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_metals_title), stringResource(R.string.no_metals_message), icon = Icons.Filled.Add) }
            }
            items(state.rows, key = { it.item.id }) { r ->
                FinanceCard(Modifier.fillMaxWidth(), onClick = { onEdit(r.item.id) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemAvatar(r.item.icon, r.item.colorHex, size = 44)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.item.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                listOf(
                                    metalTypeLabel(r.spec.type), metalFormLabel(r.spec.form),
                                    stringResource(R.string.grams_value, f.decimal(r.spec.weightGrams)), purityLabel(r.spec.type, r.spec.purity),
                                ).joinToString(" • ") + if (r.spec.count > 1) " × " + f.integer(r.spec.count) else "",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            if (r.value == null) stringResource(R.string.metal_no_price) else f.money(r.value, base),
                            style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content), fontWeight = FontWeight.ExtraBold, color = fc.gold,
                        )
                    }
                }
            }
            if (state.incomplete.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.metals_incomplete)) }
                items(state.incomplete, key = { "inc_" + it.id }) { i ->
                    FinanceCard(Modifier.fillMaxWidth(), onClick = { onEdit(i.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ItemAvatar(i.icon, i.colorHex, size = 36)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(i.name, style = MaterialTheme.typography.titleSmall)
                                Text(stringResource(R.string.metals_incomplete_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (manual) {
        var g by remember { mutableStateOf("") }
        var s by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { manual = false },
            title = { Text(stringResource(R.string.manual_price)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.manual_price_hint), style = MaterialTheme.typography.bodySmall)
                    AmountField(g, { g = it }, stringResource(R.string.gram_price_at, purityLabel(MetalType.GOLD, 21)), suffix = base)
                    AmountField(s, { s = it }, stringResource(R.string.gram_price_at, purityLabel(MetalType.SILVER, 999)), suffix = base)
                }
            },
            confirmButton = { TextButton(onClick = { vm.setManual(g, s); manual = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { manual = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
    if (confirmApply) {
        val now = LocalDate.now()
        com.hossammo.cash.ui.components.ConfirmDialog(
            stringResource(R.string.record_in_month),
            stringResource(R.string.record_in_month_message, f.month(MonthKey.of(now.year, now.monthValue))),
            confirmText = stringResource(R.string.confirm),
            onConfirm = { confirmApply = false; vm.applyToCurrentMonth() },
            onDismiss = { confirmApply = false },
        )
    }
}
