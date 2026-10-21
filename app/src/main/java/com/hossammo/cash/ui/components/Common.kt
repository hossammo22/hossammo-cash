package com.hossammo.cash.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FiberNew
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hossammo.cash.R
import com.hossammo.cash.domain.model.TrendStatus
import com.hossammo.cash.ui.theme.LocalFinanceColors
import com.hossammo.cash.util.AppFormatter
import java.math.BigDecimal

val LocalAppFormatter = staticCompositionLocalOf<AppFormatter> { error("AppFormatter not provided") }

@Composable
fun fmt(): AppFormatter = LocalAppFormatter.current

@Composable
fun trendColor(status: TrendStatus?): Color {
    val c = LocalFinanceColors.current
    return when (status) {
        TrendStatus.INCREASED -> c.increase
        TrendStatus.DECREASED -> c.decrease
        TrendStatus.STABLE -> c.stable
        TrendStatus.NEW, null -> MaterialTheme.colorScheme.outline
    }
}

fun trendIcon(status: TrendStatus?): ImageVector = when (status) {
    TrendStatus.INCREASED -> Icons.AutoMirrored.Filled.TrendingUp
    TrendStatus.DECREASED -> Icons.AutoMirrored.Filled.TrendingDown
    TrendStatus.STABLE -> Icons.Filled.DragHandle
    TrendStatus.NEW, null -> Icons.Filled.FiberNew
}

/** شارة الاتجاه: سهم + المبلغ + النسبة بلون الحالة. */
@Composable
fun TrendBadge(status: TrendStatus?, change: BigDecimal?, percent: BigDecimal?, modifier: Modifier = Modifier, compact: Boolean = false) {
    val f = fmt()
    val color = trendColor(status)
    val label = f.trend(status)
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .semantics { contentDescription = label },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(trendIcon(status), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(4.dp))
        val text = when {
            status == TrendStatus.NEW || change == null -> stringResource(R.string.comparison_unavailable_short)
            compact -> f.percent(percent)
            else -> "${f.signedMoney(change)} • ${f.percent(percent)}"
        }
        androidx.compose.material3.Text(
            text, color = color, style = MaterialTheme.typography.labelMedium.copy(textDirection = TextDirection.Content),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MoneyText(
    value: BigDecimal?,
    modifier: Modifier = Modifier,
    currency: String? = null,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
) {
    androidx.compose.material3.Text(
        fmt().money(value, currency),
        modifier = modifier,
        style = style.copy(textDirection = TextDirection.Content),
        color = color,
        fontWeight = fontWeight,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
fun FinanceCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val fc = LocalFinanceColors.current
    val colors = CardDefaults.cardColors(containerColor = fc.card)
    val elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    val border = androidx.compose.foundation.BorderStroke(1.dp, fc.cardBorder)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, colors = colors, elevation = elevation, shape = MaterialTheme.shapes.large, border = border) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    } else {
        Card(modifier = modifier, colors = colors, elevation = elevation, shape = MaterialTheme.shapes.large, border = border) {
            Column(Modifier.padding(16.dp)) { content() }
        }
    }
}

/** بطاقة رئيسية فاخرة بتدرج كحلي ولمسة ذهبية؛ النصوص بداخلها بيضاء. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val fc = LocalFinanceColors.current
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(fc.heroStart, fc.heroEnd))
    val shape = MaterialTheme.shapes.extraLarge
    Box(
        modifier
            .clip(shape)
            .background(brush)
            .border(1.dp, androidx.compose.ui.graphics.Brush.linearGradient(listOf(fc.gold.copy(alpha = 0.9f), fc.gold.copy(alpha = 0.15f))), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        // زخرفة دائرية ذهبية خفيفة
        Box(
            Modifier.align(Alignment.BottomEnd).size(120.dp)
                .graphicsLayer { translationX = -40.dp.toPx(); translationY = 50.dp.toPx() }
                .clip(CircleShape).background(fc.gold.copy(alpha = 0.08f))
        )
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides Color.White) {
            Column(Modifier.padding(20.dp)) { content() }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.secondary,
    trend: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    FinanceCard(modifier, onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Box(
                    Modifier.size(34.dp).clip(RoundedCornerShape(10.dp))
                        .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.6f)))),
                    contentAlignment = Alignment.Center,
                ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp)) }
                Spacer(Modifier.width(8.dp))
            }
            androidx.compose.material3.Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.material3.Text(
            value, style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content),
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            androidx.compose.material3.Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        if (trend != null) {
            Spacer(Modifier.height(6.dp)); trend()
        }
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 4.dp, height = 18.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.secondary))
        Spacer(Modifier.width(8.dp))
        androidx.compose.material3.Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(title: String, message: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Filled.Inbox, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(42.dp)) }
        Spacer(Modifier.height(16.dp))
        androidx.compose.material3.Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        androidx.compose.material3.Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}

/** هيكل تحميل (Skeleton) متحرك. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(0.25f, 0.6f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "alpha")
    Box(modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha * 0.25f)))
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = stringResource(R.string.confirm),
    dismissText: String = stringResource(R.string.cancel),
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text(title) },
        text = { androidx.compose.material3.Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                androidx.compose.material3.Text(confirmText, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { androidx.compose.material3.Text(dismissText) } },
    )
}

/** حقل إدخال مبلغ مالي بلوحة أرقام. */
@Composable
fun AmountField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    isError: Boolean = false,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onValueChange(v.filter { it.isDigit() || it == '.' || it == ',' || it == '٫' || it in '٠'..'٩' }) },
        label = { androidx.compose.material3.Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        isError = isError,
        suffix = suffix?.let { { androidx.compose.material3.Text(it) } },
        supportingText = supporting?.let { { androidx.compose.material3.Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr, color = LocalFinanceColors.current.editable),
    )
}

@Composable
fun AppTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = true, isError: Boolean = false, supporting: String? = null, minLines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, label = { androidx.compose.material3.Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine, isError = isError, minLines = minLines,
        supportingText = supporting?.let { { androidx.compose.material3.Text(it) } },
    )
}

@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query, onValueChange = onQueryChange, modifier = modifier.fillMaxWidth(), singleLine = true,
        placeholder = { androidx.compose.material3.Text(stringResource(R.string.search)) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Clear, stringResource(R.string.clear)) } }) else null,
        shape = RoundedCornerShape(50),
    )
}

/** قائمة منسدلة عامة. */
@Composable
fun <T> DropdownField(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected?.let { optionLabel(it) } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { androidx.compose.material3.Text(label) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { androidx.compose.material3.Text(optionLabel(o)) }, onClick = { onSelect(o); expanded = false })
            }
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null, enabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) androidx.compose.material3.Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun <T> ChipsRow(options: List<T>, selected: T, label: @Composable (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { o ->
            FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { androidx.compose.material3.Text(label(o)) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorPicker(colors: List<String>, selected: String, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        colors.forEach { hex ->
            val c = com.hossammo.cash.ui.theme.parseHexColor(hex)
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(c)
                    .border(if (hex == selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    .clickable { onSelect(hex) },
                contentAlignment = Alignment.Center,
            ) { if (hex == selected) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconPicker(selected: String, tint: Color, onSelect: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconCatalog.all.forEach { (key, icon) ->
            Surface(
                onClick = { onSelect(key) },
                shape = CircleShape,
                color = if (key == selected) tint.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(42.dp),
            ) { Box(contentAlignment = Alignment.Center) { Icon(icon, key, tint = if (key == selected) tint else MaterialTheme.colorScheme.onSurfaceVariant) } }
        }
    }
}

@Composable
fun ItemAvatar(icon: String?, colorHex: String?, modifier: Modifier = Modifier, size: Int = 40) {
    val c = com.hossammo.cash.ui.theme.parseHexColor(colorHex)
    val brush = androidx.compose.ui.graphics.Brush.linearGradient(listOf(c, c.copy(alpha = 0.65f)))
    Box(modifier.size(size.dp).clip(RoundedCornerShape((size * 0.32f).dp)).background(brush), contentAlignment = Alignment.Center) {
        Icon(IconCatalog.icon(icon), null, tint = Color.White, modifier = Modifier.size((size * 0.55f).dp))
    }
}

/** محدد الشهر بأسهم السابق/التالي وقائمة منسدلة. */
@Composable
fun MonthSelector(
    months: List<com.hossammo.cash.domain.model.MonthKey>,
    selected: com.hossammo.cash.domain.model.MonthKey?,
    onSelect: (com.hossammo.cash.domain.model.MonthKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val f = fmt()
    var expanded by remember { mutableStateOf(false) }
    val idx = selected?.let { months.indexOf(it) } ?: -1
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // في RTL: السهم الأيمن = الشهر السابق
            IconButton(onClick = { if (idx > 0) onSelect(months[idx - 1]) }, enabled = idx > 0) {
                Icon(Icons.Filled.ChevronRight, stringResource(R.string.previous_month))
            }
            Box {
                TextButton(onClick = { expanded = true }, enabled = months.isNotEmpty()) {
                    androidx.compose.material3.Text(selected?.let { f.month(it) } ?: stringResource(R.string.no_months), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Filled.ArrowDropDown, null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    months.asReversed().forEach { m ->
                        DropdownMenuItem(text = { androidx.compose.material3.Text(f.month(m)) }, onClick = { onSelect(m); expanded = false })
                    }
                }
            }
            IconButton(onClick = { if (idx in 0 until months.lastIndex) onSelect(months[idx + 1]) }, enabled = idx in 0 until months.lastIndex) {
                Icon(Icons.Filled.ChevronLeft, stringResource(R.string.next_month))
            }
        }
    }
}

@Composable
fun LabeledValue(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = Color.Unspecified) {
    Row(modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        androidx.compose.material3.Text(value, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content), fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
fun rememberContext() = LocalContext.current
