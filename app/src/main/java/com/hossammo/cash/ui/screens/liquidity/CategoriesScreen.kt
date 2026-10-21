package com.hossammo.cash.ui.screens.liquidity

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hossammo.cash.R
import com.hossammo.cash.data.db.entity.SavingsCategoryEntity
import com.hossammo.cash.data.model.OpResult
import com.hossammo.cash.data.repo.LiquidityRepository
import com.hossammo.cash.ui.components.AppTextField
import com.hossammo.cash.ui.components.ColorPicker
import com.hossammo.cash.ui.components.ConfirmDialog
import com.hossammo.cash.ui.components.FinanceCard
import com.hossammo.cash.ui.components.IconPicker
import com.hossammo.cash.ui.components.ItemAvatar
import com.hossammo.cash.ui.screens.AppScaffold
import com.hossammo.cash.ui.screens.CollectMessages
import com.hossammo.cash.ui.screens.MessagesViewModel
import com.hossammo.cash.ui.theme.ItemPalette
import com.hossammo.cash.ui.theme.parseHexColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(private val repo: LiquidityRepository) : MessagesViewModel() {
    val categories: StateFlow<List<Pair<SavingsCategoryEntity, Int>>> = repo.observeData().map { d ->
        d.categories.map { c -> c to d.items.count { it.categoryId == c.id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(id: String?, name: String, color: String, icon: String, onDone: () -> Unit) = viewModelScope.launch {
        when (val r = repo.saveCategory(id, name, color, icon)) {
            OpResult.Success -> { message(R.string.msg_saved); onDone() }
            is OpResult.Failure -> message(errorText(r.error), true)
            else -> Unit
        }
    }

    fun delete(id: String) = viewModelScope.launch {
        when (val r = repo.deleteCategory(id)) {
            OpResult.Success -> message(R.string.msg_category_deleted)
            is OpResult.Failure -> message(errorText(r.error), true)
            else -> Unit
        }
    }
}

@Composable
fun CategoriesScreen(onBack: () -> Unit, vm: CategoriesViewModel = hiltViewModel()) {
    val list by vm.categories.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    CollectMessages(vm.messages, snackbar)
    var editing by remember { mutableStateOf<SavingsCategoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    AppScaffold(
        title = stringResource(R.string.categories_title), onBack = onBack, snackbar = snackbar, showHideToggle = false,
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_category)) }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(list, key = { it.first.id }) { (c, count) ->
                FinanceCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ItemAvatar(c.icon, c.colorHex)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(R.string.items_count, count), style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = { editing = c }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                        IconButton(onClick = { deleteId = c.id }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                    }
                }
            }
        }
    }
    if (adding || editing != null) {
        val c = editing
        CategoryDialog(c, onDismiss = { adding = false; editing = null }) { name, color, icon -> vm.save(c?.id, name, color, icon) { adding = false; editing = null } }
    }
    deleteId?.let { id ->
        ConfirmDialog(stringResource(R.string.delete_category_title), stringResource(R.string.delete_category_message), confirmText = stringResource(R.string.delete), destructive = true,
            onConfirm = { vm.delete(id); deleteId = null }, onDismiss = { deleteId = null })
    }
}

@Composable
private fun CategoryDialog(c: SavingsCategoryEntity?, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf(c?.name ?: "") }
    var color by remember { mutableStateOf(c?.colorHex ?: ItemPalette.first()) }
    var icon by remember { mutableStateOf(c?.icon ?: "savings") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (c == null) R.string.add_category else R.string.edit_category)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTextField(name, { name = it }, stringResource(R.string.category_name))
                ColorPicker(ItemPalette, color) { color = it }
                IconPicker(icon, parseHexColor(color)) { icon = it }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, color, icon) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
