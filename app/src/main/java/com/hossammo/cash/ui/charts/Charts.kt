package com.hossammo.cash.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

data class LineSeries(val label: String, val color: Color, val values: List<Float?>)
data class PieSlice(val label: String, val value: Float, val color: Color)

/** تنسيق مختصر للمحاور (ألف/مليون). */
fun compactNumber(v: Float): String {
    val a = abs(v)
    val s = when {
        a >= 1_000_000f -> String.format(java.util.Locale.US, "%.1fM", v / 1_000_000f)
        a >= 1_000f -> String.format(java.util.Locale.US, "%.0fK", v / 1_000f)
        else -> String.format(java.util.Locale.US, "%.0f", v)
    }
    return s
}

private fun niceRange(minV: Float, maxV: Float): Pair<Float, Float> {
    if (minV == maxV) return (minV - max(1f, abs(minV) * 0.1f)) to (maxV + max(1f, abs(maxV) * 0.1f))
    val pad = (maxV - minV) * 0.08f
    return (minV - pad) to (maxV + pad)
}

/**
 * رسم خطي تفاعلي متعدد السلاسل. المحور الأفقي زمني من اليسار لليمين.
 */
@Composable
fun LineChart(
    series: List<LineSeries>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelect: (Int) -> Unit = {},
    height: Int = 220,
    description: String = "",
    startFromZero: Boolean = false,
) {
    val measurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selColor = MaterialTheme.colorScheme.secondary
    val n = xLabels.size
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier
                .fillMaxWidth()
                .height(height.dp)
                .semantics { contentDescription = description }
                .pointerInput(n) {
                    detectTapGestures { pos ->
                        if (n == 0) return@detectTapGestures
                        val left = 44.dp.toPx(); val right = size.width - 8.dp.toPx()
                        val step = if (n > 1) (right - left) / (n - 1) else 0f
                        val idx = if (n == 1) 0 else ((pos.x - left) / step).let { kotlin.math.round(it).toInt() }.coerceIn(0, n - 1)
                        onSelect(idx)
                    }
                },
        ) {
            if (n == 0) return@Canvas
            val all = series.flatMap { it.values.filterNotNull() }
            if (all.isEmpty()) return@Canvas
            val (lo, hi) = niceRange(if (startFromZero) min(0f, all.min()) else all.min(), all.max())
            val left = 44.dp.toPx(); val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx(); val bottom = size.height - 24.dp.toPx()
            fun x(i: Int) = if (n == 1) (left + right) / 2 else left + (right - left) * i / (n - 1)
            fun y(v: Float) = bottom - (bottom - top) * (v - lo) / (hi - lo)
            drawGrid(lo, hi, left, right, top, bottom, gridColor) { v, yy ->
                val t = measurer.measure(compactNumber(v), TextStyle(fontSize = 9.sp, color = labelColor))
                drawText(t, topLeft = Offset(left - t.size.width - 4.dp.toPx(), yy - t.size.height / 2))
            }
            // تسميات المحور الأفقي (بحد أقصى ~6)
            val every = max(1, n / 6)
            xLabels.forEachIndexed { i, l ->
                if (i % every == 0 || i == n - 1) {
                    val t = measurer.measure(l, TextStyle(fontSize = 9.sp, color = labelColor), maxLines = 1)
                    drawText(t, topLeft = Offset((x(i) - t.size.width / 2).coerceIn(0f, size.width - t.size.width), bottom + 4.dp.toPx()))
                }
            }
            selectedIndex?.takeIf { it in 0 until n }?.let { si ->
                drawLine(selColor, Offset(x(si), top), Offset(x(si), bottom), strokeWidth = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)))
            }
            series.forEach { s ->
                // منحنى ناعم (Bezier) مع تظليل متدرج أسفل الخط
                val segments = mutableListOf<List<Pair<Float, Float>>>()
                var cur = mutableListOf<Pair<Float, Float>>()
                s.values.forEachIndexed { i, v ->
                    if (v == null) { if (cur.isNotEmpty()) segments += cur; cur = mutableListOf() } else cur += x(i) to y(v)
                }
                if (cur.isNotEmpty()) segments += cur
                segments.forEach { pts ->
                    val path = Path()
                    pts.forEachIndexed { k, (px, py) ->
                        if (k == 0) path.moveTo(px, py) else {
                            val (qx, qy) = pts[k - 1]
                            val mx = (qx + px) / 2
                            path.cubicTo(mx, qy, mx, py, px, py)
                        }
                    }
                    if (pts.size > 1 && series.size <= 2) {
                        val area = Path().apply { addPath(path); lineTo(pts.last().first, bottom); lineTo(pts.first().first, bottom); close() }
                        drawPath(area, androidx.compose.ui.graphics.Brush.verticalGradient(listOf(s.color.copy(alpha = 0.32f), s.color.copy(alpha = 0.02f)), startY = top, endY = bottom))
                    }
                    drawPath(path, s.color, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                }
                s.values.forEachIndexed { i, v ->
                    if (v != null) {
                        val r = if (i == selectedIndex) 6.dp.toPx() else 4.dp.toPx()
                        drawCircle(s.color, r, Offset(x(i), y(v)))
                        drawCircle(Color.White, r * 0.45f, Offset(x(i), y(v)))
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawGrid(lo: Float, hi: Float, left: Float, right: Float, top: Float, bottom: Float, color: Color, label: DrawScope.(Float, Float) -> Unit) {
    val lines = 4
    for (k in 0..lines) {
        val v = lo + (hi - lo) * k / lines
        val yy = bottom - (bottom - top) * k / lines
        drawLine(color, Offset(left, yy), Offset(right, yy), strokeWidth = 1f)
        label(v, yy)
    }
}

/** رسم أعمدة يدعم القيم السالبة (للتغير الشهري). */
@Composable
fun BarChart(
    values: List<Float>,
    xLabels: List<String>,
    modifier: Modifier = Modifier,
    positiveColor: Color,
    negativeColor: Color,
    barColors: List<Color>? = null,
    selectedIndex: Int? = null,
    onSelect: (Int) -> Unit = {},
    height: Int = 200,
    description: String = "",
) {
    val measurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val selColor = MaterialTheme.colorScheme.onSurface
    val n = values.size
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Canvas(
            modifier.fillMaxWidth().height(height.dp).semantics { contentDescription = description }
                .pointerInput(n) {
                    detectTapGestures { pos ->
                        if (n == 0) return@detectTapGestures
                        val left = 44.dp.toPx(); val right = size.width - 8.dp.toPx()
                        val slot = (right - left) / n
                        onSelect(((pos.x - left) / slot).toInt().coerceIn(0, n - 1))
                    }
                },
        ) {
            if (n == 0) return@Canvas
            val (lo, hi) = niceRange(min(0f, values.min()), max(0f, values.max()))
            val left = 44.dp.toPx(); val right = size.width - 8.dp.toPx()
            val top = 8.dp.toPx(); val bottom = size.height - 24.dp.toPx()
            fun y(v: Float) = bottom - (bottom - top) * (v - lo) / (hi - lo)
            drawGrid(lo, hi, left, right, top, bottom, gridColor) { v, yy ->
                val t = measurer.measure(compactNumber(v), TextStyle(fontSize = 9.sp, color = labelColor))
                drawText(t, topLeft = Offset(left - t.size.width - 4.dp.toPx(), yy - t.size.height / 2))
            }
            val slot = (right - left) / n
            val barW = slot * 0.6f
            val zero = y(0f)
            values.forEachIndexed { i, v ->
                val cx = left + slot * i + slot / 2
                val color = barColors?.getOrNull(i) ?: if (v >= 0) positiveColor else negativeColor
                val yv = y(v)
                drawRoundRectBar(color, cx - barW / 2, min(yv, zero), barW, abs(zero - yv).coerceAtLeast(1f))
                if (i == selectedIndex) {
                    drawRect(selColor.copy(alpha = 0.08f), Offset(left + slot * i, top), Size(slot, bottom - top))
                }
            }
            val every = max(1, n / 6)
            xLabels.forEachIndexed { i, l ->
                if (i % every == 0 || i == n - 1) {
                    val t = measurer.measure(l, TextStyle(fontSize = 9.sp, color = labelColor), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val cx = left + slot * i + slot / 2
                    drawText(t, topLeft = Offset((cx - t.size.width / 2).coerceIn(0f, size.width - t.size.width), bottom + 4.dp.toPx()))
                }
            }
        }
    }
}

private fun DrawScope.drawRoundRectBar(color: Color, x: Float, y: Float, w: Float, h: Float) {
    val brush = androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color, color.copy(alpha = 0.55f)), startY = y, endY = y + h)
    val r = min(8.dp.toPx(), w / 2)
    drawRoundRect(brush, Offset(x, y), Size(w, h), androidx.compose.ui.geometry.CornerRadius(r, r))
}

/** رسم دائري (Donut) تفاعلي. */
@Composable
fun PieChart(
    slices: List<PieSlice>,
    modifier: Modifier = Modifier,
    selectedIndex: Int? = null,
    onSelect: (Int) -> Unit = {},
    size: Int = 200,
    description: String = "",
    center: (@Composable () -> Unit)? = null,
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat()
    Box(modifier.size(size.dp), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier.size(size.dp).semantics { contentDescription = description }
                .pointerInput(slices) {
                    detectTapGestures { pos ->
                        if (total <= 0f) return@detectTapGestures
                        val cx = this.size.width / 2f; val cy = this.size.height / 2f
                        val dx = pos.x - cx; val dy = pos.y - cy
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist > min(cx, cy)) return@detectTapGestures
                        var angle = (atan2(dy, dx) * 180f / PI.toFloat()) + 90f
                        if (angle < 0) angle += 360f
                        var acc = 0f
                        slices.forEachIndexed { i, s ->
                            val sweep = s.value / total * 360f
                            if (angle >= acc && angle < acc + sweep) { onSelect(i); return@detectTapGestures }
                            acc += sweep
                        }
                    }
                },
        ) {
            if (total <= 0f) return@Canvas
            val stroke = this.size.minDimension * 0.18f
            val inset = stroke / 2 + 6.dp.toPx()
            var start = -90f
            slices.forEachIndexed { i, s ->
                val sweep = s.value / total * 360f
                val extra = if (i == selectedIndex) 6.dp.toPx() else 0f
                drawArc(
                    color = s.color, startAngle = start, sweepAngle = max(0.5f, sweep - if (slices.size > 1) 2f else 0f), useCenter = false,
                    topLeft = Offset(inset - extra / 2, inset - extra / 2),
                    size = Size(this.size.width - 2 * inset + extra, this.size.height - 2 * inset + extra),
                    style = Stroke(width = stroke + extra),
                )
                start += sweep
            }
        }
        center?.invoke()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Legend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.width(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** أعمدة أفقية للنِّسَب (تعمل جيدًا مع RTL). */
@Composable
fun HorizontalBars(rows: List<Triple<String, Float, Color>>, valueText: (Int) -> String, modifier: Modifier = Modifier, onClick: ((Int) -> Unit)? = null) {
    val maxV = rows.maxOfOrNull { abs(it.second) }?.takeIf { it > 0f } ?: 1f
    Column(modifier) {
        rows.forEachIndexed { i, (label, v, color) ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    .then(if (onClick != null) Modifier.pointerInput(i) { detectTapGestures { onClick(i) } } else Modifier)
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(valueText(i), style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.height(2.dp))
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(abs(v) / maxV).height(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
                }
            }
        }
    }
}
