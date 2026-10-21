package com.hossammo.cash.report

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.dhatim.fastexcel.Workbook
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * تصدير التقارير إلى PDF (بخط عربي مضمّن) و Excel و CSV، ومشاركتها عبر FileProvider.
 */
@javax.inject.Singleton
class ReportExporter @javax.inject.Inject constructor(@dagger.hilt.android.qualifiers.ApplicationContext private val context: Context) {

    suspend fun export(doc: ReportDocument, format: ExportFormat, baseName: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "reports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safe = baseName.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = File(dir, "HossamMoCash_${safe}_$stamp.${format.ext}")
        file.outputStream().use { os ->
            when (format) {
                ExportFormat.PDF -> writePdf(doc, os)
                ExportFormat.EXCEL -> writeXlsx(doc, os)
                ExportFormat.CSV -> writeCsv(doc, os)
            }
        }
        file
    }

    fun shareUri(file: File) = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    // ---------------- CSV ----------------

    fun writeCsv(doc: ReportDocument, os: OutputStream) {
        val sb = StringBuilder()
        sb.append('\uFEFF') // BOM لعرض العربية بشكل صحيح في Excel
        fun line(cells: List<String>) {
            sb.append(cells.joinToString(",") { csvEscape(it) }).append("\r\n")
        }
        line(listOf(doc.title))
        line(listOf(doc.subtitle))
        line(listOf(doc.generatedAt))
        doc.summary.forEach { line(listOf(it.first, it.second)) }
        for (t in doc.tables) {
            sb.append("\r\n")
            line(listOf(t.title))
            line(t.headers)
            t.rows.forEach { row -> line(row.map { c -> c.number?.toPlainString() ?: c.text }) }
        }
        os.write(sb.toString().toByteArray(Charsets.UTF_8))
    }

    private fun csvEscape(s: String): String {
        val needs = s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needs) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }

    // ---------------- Excel ----------------

    fun writeXlsx(doc: ReportDocument, os: OutputStream) {
        val wb = Workbook(os, "HossamMo Cash", "1.0")
        val summary = wb.newWorksheet(sheetName(doc.title, 0, emptySet()))
        summary.value(0, 0, doc.title)
        summary.style(0, 0).bold().fontSize(14).set()
        summary.value(1, 0, doc.subtitle)
        summary.value(2, 0, doc.generatedAt)
        var r = 4
        doc.summary.forEach { (k, v) ->
            summary.value(r, 0, k); summary.style(r, 0).bold().set()
            summary.value(r, 1, v); r++
        }
        summary.width(0, 40.0); summary.width(1, 40.0)
        val used = mutableSetOf(sheetName(doc.title, 0, emptySet()))
        doc.tables.forEachIndexed { idx, t ->
            val name = sheetName(t.title, idx + 1, used)
            used += name
            val ws = wb.newWorksheet(name)
            t.headers.forEachIndexed { c, h ->
                ws.value(0, c, h)
                ws.style(0, c).bold().fillColor("172A3A").fontColor("FFFFFF").set()
                ws.width(c, 20.0)
            }
            t.rows.forEachIndexed { ri, row ->
                row.forEachIndexed { c, cell ->
                    if (cell.number != null) ws.value(ri + 1, c, cell.number) else ws.value(ri + 1, c, cell.text)
                }
            }
        }
        wb.finish()
    }

    private fun sheetName(raw: String, index: Int, used: Set<String>): String {
        var n = raw.replace(Regex("[\\[\\]:*?/\\\\]"), " ").trim().take(28).ifEmpty { "Sheet" }
        if (n in used) n = "${n.take(25)} ${index}"
        return n
    }

    // ---------------- PDF ----------------

    private val pageWidth = 595 // A4 بالنقاط
    private val pageHeight = 842
    private val margin = 36f

    fun writePdf(doc: ReportDocument, os: OutputStream) {
        val typeface = runCatching { Typeface.createFromAsset(context.assets, "fonts/NotoNaskhArabic.ttf") }.getOrDefault(Typeface.DEFAULT)
        val bold = Typeface.create(typeface, Typeface.BOLD)
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = bold; textSize = 18f; color = Color.parseColor("#172A3A") }
        val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 11f; color = Color.DKGRAY }
        val headPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = bold; textSize = 9f; color = Color.WHITE }
        val cellPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = typeface; textSize = 8.5f; color = Color.BLACK }
        val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { this.typeface = bold; textSize = 12f; color = Color.parseColor("#C69B35") }
        val fill = Paint().apply { style = Paint.Style.FILL }
        val stroke = Paint().apply { style = Paint.Style.STROKE; strokeWidth = 0.5f; color = Color.parseColor("#BBBBBB") }

        val pdf = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var y = 0f
        val contentWidth = pageWidth - 2 * margin

        fun finishPage() {
            page?.let { p ->
                val footer = layout("HossamMo Cash • ${pageNo}", subPaint, contentWidth.toInt(), Layout.Alignment.ALIGN_CENTER)
                p.canvas.save(); p.canvas.translate(margin, pageHeight - margin + 8); footer.draw(p.canvas); p.canvas.restore()
                pdf.finishPage(p)
            }
        }

        fun newPage() {
            finishPage()
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNo).create())
            y = margin
        }

        fun ensure(h: Float) {
            if (page == null || y + h > pageHeight - margin - 16) newPage()
        }

        fun drawText(text: String, paint: TextPaint, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL) {
            val l = layout(text, paint, contentWidth.toInt(), align)
            ensure(l.height.toFloat() + 4)
            val c = page!!.canvas
            c.save(); c.translate(margin, y); l.draw(c); c.restore()
            y += l.height + 4
        }

        newPage()
        drawText(doc.title, titlePaint)
        drawText(doc.subtitle, subPaint)
        drawText(doc.generatedAt, subPaint)
        y += 6
        for ((k, v) in doc.summary) drawText("$k: $v", cellPaint.apply { textSize = 10f })
        cellPaint.textSize = 8.5f
        y += 6

        for (t in doc.tables) {
            ensure(40f)
            drawText(t.title, sectionPaint)
            if (t.rows.isEmpty()) { drawText("—", subPaint); continue }
            val cols = t.headers.size.coerceAtLeast(1)
            val colW = contentWidth / cols

            fun drawRow(cells: List<String>, paint: TextPaint, bg: Int?) {
                val layouts = cells.map { layout(it, paint, (colW - 6).toInt(), Layout.Alignment.ALIGN_NORMAL) }
                val h = (layouts.maxOfOrNull { it.height } ?: 10) + 6f
                ensure(h)
                val c = page!!.canvas
                // RTL: العمود الأول على اليمين
                layouts.forEachIndexed { i, l ->
                    val right = margin + contentWidth - i * colW
                    val left = right - colW
                    if (bg != null) { fill.color = bg; c.drawRect(left, y, right, y + h, fill) }
                    c.drawRect(left, y, right, y + h, stroke)
                    c.save(); c.translate(left + 3, y + 3); l.draw(c); c.restore()
                }
                y += h
            }

            drawRow(t.headers, headPaint, Color.parseColor("#172A3A"))
            t.rows.forEachIndexed { i, row ->
                val cells = row.map { it.text }
                // إعادة رسم رأس الجدول عند بداية صفحة جديدة
                val before = pageNo
                ensure(14f)
                if (pageNo != before) drawRow(t.headers, headPaint, Color.parseColor("#172A3A"))
                drawRow(cells, cellPaint, if (i % 2 == 0) Color.parseColor("#F8F3E7") else null)
            }
            y += 10
        }
        finishPage()
        pdf.writeTo(os)
        pdf.close()
    }

    private fun layout(text: String, paint: TextPaint, width: Int, align: Layout.Alignment): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(10))
            .setAlignment(align)
            .setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL)
            .setIncludePad(false)
            .build()
}
