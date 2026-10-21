package com.hossammo.cash.ui.screens.liquidity

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hossammo.cash.R
import com.hossammo.cash.data.analysis.ItemRow
import com.hossammo.cash.ui.components.ChipsRow
import com.hossammo.cash.ui.components.ConfirmDialog
import com.hossammo.cash.ui.components.EmptyState
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.ItemAvatar
import com.hossammo.cash.ui.components.LabeledValue
import com.hossammo.cash.ui.components.MonthSelector
import com.hossammo.cash.ui.components.SearchField
import com.hossammo.cash.ui.components.TrendBadge
import com.hossammo.cash.ui.components.fmt
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages

@Composable
fun viewModeLabel(m: ViewMode) = stringResource(
    when (m) {
        ViewMode.CARDS -> R.string.view_cards
        ViewMode.TABLE -> R.string.view_table
        ViewMode.BY_CATEGORY -> R.string.view_by_category
        ViewMode.BY_LOCATION -> R.string.view_by_location
        ViewMode.BY_CURRENCY -> R.string.view_by_currency
    }
)

@Composable
fun filterLabel(f: ItemFilter) = stringResource(
    when (f) {
        ItemFilter.ALL -> R.string.filter_all
        ItemFilter.ACTIVE -> R.string.filter_active
        ItemFilter.ARCHIVED -> R.string.filter_archived
        ItemFilter.INCLUDED -> R.string.filter_included
        ItemFilter.EXCLUDED -> R.string.filter_excluded
        ItemFilter.WITH_BALANCE -> R.string.filter_with_balance
        ItemFilter.WITHOUT_BALANCE -> R.string.filter_without_balance
    }
)

@Composable
fun sortLabel(s: ItemSort) = stringResource(
    when (s) {
        ItemSort.CUSTOM -> R.string.sort_custom
        ItemSort.NAME -> R.string.sort_name
        ItemSort.VALUE_DESC -> R.string.sort_value_desc
        ItemSort.VALUE_ASC -> R.string.sort_value_asc
        ItemSort.CHANGE_DESC -> R.string.sort_change_desc
        ItemSort.CHANGE_ASC -> R.string.sort_change_asc
        ItemSort.SHARE -> R.string.sort_share
    }
)

@Composable
fun LiquidityScreen(
    onAddItem: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenMonths: () -> Unit,
    onOpenTransfers: () -> Unit,
    onOpenCategories: () -> Unit,
    onOpenCompare: () -> Unit,
    onOpenMonthEntry: (Int) -> Unit,
    onOpenMetals: () -> Unit = {},
    vm: LiquidityViewModel = hiltViewModel(),
) {
    val noLoc = stringResource(R.string.no_location)
    val noCat = stringResource(R.string.no_category)
    vm.setLabels(LiquidityViewModel.GroupLabels(noLoc, noCat))
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    val f = fmt()
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    val copySuffix = stringResource(R.string.copy_suffix)
    val cols = if (LocalConfiguration.current.screenWidthDp >= 600) 2 else 1

    AppScaffold(
        title = stringResource(R.string.nav_liquidity),
        snackbar = snackbar,
        actions = {
            IconButton(onClick = onOpenMetals) { Icon(Icons.Filled.WorkspacePremium, stringResource(R.string.metals_title)) }
            IconButton(onClick = onOpenTransfers) { Icon(Icons.Filled.SwapHoriz, stringResource(R.string.transfers_title)) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.months_title)) }, { menu = false; onOpenMonths() }, leadingIcon = { Icon(Icons.Filled.DateRange, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.compare_months)) }, { menu = false; onOpenCompare() }, leadingIcon = { Icon(Icons.Filled.CompareArrows, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.categories_title)) }, { menu = false; onOpenCategories() }, leadingIcon = { Icon(Icons.Filled.Category, null) })
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onAddItem, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_liquidity_item)) })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { MonthSelector(state.months, state.selected, vm::selectMonth) }
                if (state.selected != null) {
                    val fc = com.hossammo.cash.ui.theme.LocalFinanceColors.current
                    com.hossammo.cash.ui.components.HeroCard(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.month_total_label), style = MaterialTheme.typography.labelLarge, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f))
                                Text(f.money(state.total, state.base), style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Content), fontWeight = FontWeight.ExtraBold, color = fc.gold)
                            }
                            TextButton(onClick = { onOpenMonthEntry(state.selected!!.value) }) { Text(stringResource(R.string.edit_balances), color = fc.gold) }
                        }
                    }
                    // إجمالي كل فئة: البنوك، النقدية، الذهب والفضة...
                    if (state.categoryTotals.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            state.categoryTotals.forEach { (c, total) ->
                                FinanceCard(Modifier.width(170.dp), onClick = { vm.updateQuery { q -> q.copy(categoryId = if (q.categoryId == c.id) null else c.id) } }) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        ItemAvatar(c.icon, c.colorHex, size = 28)
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.category_total, c.name), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(f.money(total, state.base), style = MaterialTheme.typography.titleSmall.copy(textDirection = TextDirection.Content), fontWeight = FontWeight.ExtraBold, maxLines = 1,
                                        color = if (state.query.categoryId == c.id) MaterialTheme.colorScheme.secondary else androidx.compose.ui.graphics.Color.Unspecified)
                                }
                            }
                        }
                    }
                } else {
                    TextButton(onClick = onOpenMonths) { Text(stringResource(R.string.add_first_month)) }
                }
                SearchField(state.query.search, { s -> vm.updateQuery { it.copy(search = s) } })
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChipsRow(ViewMode.entries.toList(), state.query.mode, { viewModeLabel(it) }, { m -> vm.updateQuery { it.copy(mode = m) } }, Modifier.weight(1f))
                    IconButton(onClick = { showFilters = !showFilters }) { Icon(Icons.Filled.FilterList, stringResource(R.string.filter)) }
                    Box {
                        IconButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, stringResource(R.string.sort)) }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            ItemSort.entries.forEach { s ->
                                DropdownMenuItem({ Text(sortLabel(s) + if (s == state.query.sort) " ✓" else "") }, { vm.updateQuery { q -> q.copy(sort = s) }; sortMenu = false })
                            }
                        }
                    }
                }
                if (showFilters) {
                    ChipsRow(ItemFilter.entries.toList(), state.query.filter, { filterLabel(it) }, { fl -> vm.updateQuery { it.copy(filter = fl) } })
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(state.query.categoryId == null, { vm.updateQuery { it.copy(categoryId = null) } }, { Text(stringResource(R.string.all_categories)) })
                        state.categories.forEach { c ->
                            FilterChip(state.query.categoryId == c.id, { vm.updateQuery { it.copy(categoryId = c.id) } }, { Text(c.name) })
                        }
                    }
                }
            }
            if (!state.loading && state.rows.isEmpty()) {
                EmptyState(
                    stringResource(if (state.totalItems == 0) R.string.no_items_title else R.string.no_results_title),
                    stringResource(if (state.totalItems == 0) R.string.no_items_message else R.string.no_results_message),
                )
            } else when (state.query.mode) {
                ViewMode.TABLE -> ItemsTable(state.rows, state.base, onOpenItem)
                ViewMode.CARDS -> LazyVerticalGrid(GridCells.Fixed(cols), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.rows, key = { it.item.id }) { r ->
                        LiquidityItemCard(r, state.base, onOpenItem, onEdit = onOpenItem, onCopy = { vm.duplicate(r.item.id, copySuffix) }, onArchive = { vm.setArchived(r.item.id, !r.item.isArchived) }, onDelete = { deleteId = r.item.id })
                    }
                }
                else -> LazyVerticalGrid(GridCells.Fixed(cols), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.groups.forEach { (title, rows) ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "h_$title") {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text(f.money(vm.groupTotal(rows), state.base), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                        items(rows, key = { "${title}_${it.item.id}" }) { r ->
                            LiquidityItemCard(r, state.base, onOpenItem, onEdit = onOpenItem, onCopy = { vm.duplicate(r.item.id, copySuffix) }, onArchive = { vm.setArchived(r.item.id, !r.item.isArchived) }, onDelete = { deleteId = r.item.id })
                        }
                    }
                }
            }
        }
    }
    deleteId?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.delete_item_title),
            message = stringResource(R.string.delete_item_message),
            confirmText = stringResource(R.string.delete), destructive = true,
            onConfirm = { vm.delete(id); deleteId = null }, onDismiss = { deleteId = null },
        )
    }
}

@Composable
fun LiquidityItemCard(
    r: ItemRow,
    base: String,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
    onCopy: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val f = fmt()
    val c = r.comparison?.change
    var menu by remember { mutableStateOf(false) }
    FinanceCard(Modifier.fillMaxWidth(), onClick = { onOpen(r.item.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ItemAvatar(r.item.icon, r.item.colorHex)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (r.item.isArchived) { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.Archive, stringResource(R.string.archived), Modifier.height(14.dp), tint = MaterialTheme.colorScheme.outline) }
                    if (r.balance?.rateLocked == true) { Spacer(Modifier.width(4.dp)); Icon(Icons.Filled.Lock, stringResource(R.string.rate_locked), Modifier.height(14.dp), tint = MaterialTheme.colorScheme.secondary) }
                }
                Text(
                    listOfNotNull(r.location?.name, r.item.currencyCode, r.category?.name).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(stringResource(R.string.edit)) }, { menu = false; onEdit(r.item.id) }, leadingIcon = { Icon(Icons.Filled.Edit, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.duplicate)) }, { menu = false; onCopy() }, leadingIcon = { Icon(Icons.Filled.ContentCopy, null) })
                    DropdownMenuItem({ Text(stringResource(if (r.item.isArchived) R.string.unarchive else R.string.archive)) }, { menu = false; onArchive() }, leadingIcon = { Icon(if (r.item.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive, null) })
                    DropdownMenuItem({ Text(stringResource(R.string.delete)) }, { menu = false; onDelete() }, leadingIcon = { Icon(Icons.Filled.Delete, null) })
                }
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (r.balance == null) {
            Text(stringResource(R.string.no_balance_this_month), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        } else {
            if (r.item.currencyCode != base) LabeledValue(stringResource(R.string.original_value), f.money(r.balance.amountOriginal, r.item.currencyCode))
            LabeledValue(stringResource(R.string.value_in_base), if (r.missingRate) stringResource(R.string.missing_rate) else f.money(r.valueBase, base))
            LabeledValue(stringResource(R.string.previous_month_value), f.money(c?.previous, base))
            LabeledValue(stringResource(R.string.share_label), if (r.item.includeInTotal) f.percent(r.comparison?.shareOfTotal, signed = false) else stringResource(R.string.excluded_from_total))
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TrendBadge(c?.status, c?.change, c?.percent)
                Spacer(Modifier.weight(1f))
                Text(f.trend(c?.status), style = MaterialTheme.typography.labelSmall, color = com.hossammo.cash.ui.components.trendColor(c?.status))
            }
            Text(stringResource(R.string.last_modified, f.dateTime(r.balance.updatedAt)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ItemsTable(rows: List<ItemRow>, base: String, onOpen: (String) -> Unit) {
    val f = fmt()
    val widths = listOf(140, 70, 110, 110, 110, 100, 80, 70)
    val headers = listOf(R.string.col_item, R.string.col_currency, R.string.col_original, R.string.col_value_base, R.string.col_previous, R.string.col_change, R.string.col_change_pct, R.string.col_share)
    Box(Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
        androidx.compose.foundation.lazy.LazyColumn(contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)) {
            item {
                Row(Modifier.padding(vertical = 6.dp)) {
                    headers.forEachIndexed { i, h -> Text(stringResource(h), Modifier.width(widths[i].dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) }
                }
                HorizontalDivider()
            }
            items(rows.size) { idx ->
                val r = rows[idx]
                val c = r.comparison?.change
                Row(Modifier.padding(vertical = 8.dp).then(Modifier), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onOpen(r.item.id) }, modifier = Modifier.width(widths[0].dp), contentPadding = PaddingValues(0.dp)) {
                        Text(r.item.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(r.item.currencyCode, Modifier.width(widths[1].dp))
                    Text(f.money(r.balance?.amountOriginal), Modifier.width(widths[2].dp))
                    Text(f.money(r.valueBase), Modifier.width(widths[3].dp))
                    Text(f.money(c?.previous), Modifier.width(widths[4].dp))
                    Text(f.signedMoney(c?.change), Modifier.width(widths[5].dp), color = com.hossammo.cash.ui.components.trendColor(c?.status))
                    Text(f.percent(c?.percent), Modifier.width(widths[6].dp), color = com.hossammo.cash.ui.components.trendColor(c?.status))
                    Text(f.percent(r.comparison?.shareOfTotal, signed = false), Modifier.width(widths[7].dp))
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            }
        }
    }
}
