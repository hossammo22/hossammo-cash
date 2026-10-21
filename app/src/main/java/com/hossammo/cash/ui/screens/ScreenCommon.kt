package com.hossammo.cash.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.ViewModel
import com.hossammo.cash.R
import com.hossammo.cash.ui.navigation.LocalHideValues
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** رسالة للمستخدم (نجاح/خطأ) مع إجراء تراجع اختياري. */
data class UiMessage(@StringRes val text: Int, val isError: Boolean = false, val undo: (() -> Unit)? = null, val args: List<Any> = emptyList())

open class MessagesViewModel : ViewModel() {
    private val _messages = Channel<UiMessage>(Channel.BUFFERED)
    val messages: Flow<UiMessage> = _messages.receiveAsFlow()
    protected fun message(@StringRes text: Int, isError: Boolean = false, undo: (() -> Unit)? = null, vararg args: Any) {
        _messages.trySend(UiMessage(text, isError, undo, args.toList()))
    }
}

@Composable
fun CollectMessages(messages: Flow<UiMessage>, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    LaunchedEffect(messages) {
        messages.collect { m ->
            val text = if (m.args.isEmpty()) context.getString(m.text) else context.getString(m.text, *m.args.toTypedArray())
            val result = snackbar.showSnackbar(
                message = text,
                actionLabel = if (m.undo != null) context.getString(R.string.undo) else null,
                duration = if (m.undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
                withDismissAction = m.undo == null,
            )
            if (result == SnackbarResult.ActionPerformed) m.undo?.invoke()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    snackbar: SnackbarHostState = remember { SnackbarHostState() },
    showHideToggle: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val hide = LocalHideValues.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    actions()
                    if (showHideToggle) {
                        IconButton(onClick = hide.toggle) {
                            Icon(
                                if (hide.hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                stringResource(if (hide.hidden) R.string.show_values else R.string.hide_values),
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = floatingActionButton,
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}
