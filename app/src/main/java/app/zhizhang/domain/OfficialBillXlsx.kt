package app.zhizhang.domain

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.ZoneId
import java.time.LocalDateTime
import java.util.zip.ZipInputStream

/**
 * 轻量 XLSX 读取器：只读取第一个 worksheet、sharedStrings 和单元格值。
 * 不依赖 Apache POI，避免把桌面端 XML/Java 依赖带进 Android。
 */
object OfficialBillXlsx {

    fun decode(
        bytes: ByteArray,
        fileName: String = "",
        rules: Map<String, String> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
        now: LocalDateTime = LocalDateTime.now(),
    ): BillImportResult {
        if (bytes.size > 15_000_000) throw BillImportException("XLSX 文件太大，请拆分后再导入")
        val entries = unzip(bytes)
        val sharedStrings = entries.entries.firstOrNull {
            it.key.endsWith("sharedStrings.xml", ignoreCase = true)
        }?.value?.let(::parseSharedStrings).orEmpty()
        val sheet = entries.entries
            .filter { it.key.contains("/worksheets/") && it.key.endsWith(".xml", ignoreCase = true) }
            .minByOrNull { it.key }
            ?: throw BillImportException("XLSX 里没有找到工作表")
        val rows = parseSheet(sheet.value, sharedStrings)
        if (rows.isEmpty()) throw BillImportException("XLSX 工作表是空的")
        val csv = rows.joinToString("\n") { row ->
            row.joinToString(",") { escape(it) }
        }
        // 复用 CSV 的表头识别、状态、转账、退款和去重逻辑。
        val result = OfficialBillCsv.decode(
            bytes = csv.toByteArray(Charsets.UTF_8),
            fileName = fileName,
            rules = rules,
            zone = zone,
            now = now,
        )
        return result.copy(source = result.source.ifBlank { "Excel 账单" })
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8_192)
                    var total = 0
                    while (true) {
                        val read = zip.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > 20_000_000) {
                            throw BillImportException("XLSX 内部文件过大")
                        }
                        output.write(buffer, 0, read)
                    }
                    entries[entry.name] = output.toByteArray()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return entries
    }

    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inString = false
        var inText = false
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "si" -> {
                        inString = true
                        current.clear()
                    }
                    "t" -> if (inString) inText = true
                }
                XmlPullParser.TEXT -> if (inString && inText) current.append(parser.text.orEmpty())
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "t" -> inText = false
                    "si" -> if (inString) {
                        result += current.toString()
                        inString = false
                    }
                }
            }
            parser.next()
        }
        return result
    }

    private fun parseSheet(xml: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val rows = mutableListOf<List<String>>()
        var rowCells = sortedMapOf<Int, String>()
        var currentColumn = -1
        var cellType = ""
        var cellValue = StringBuilder()
        var inValue = false
        var inInlineText = false

        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> when (parser.name.substringAfter(':')) {
                    "row" -> rowCells = sortedMapOf()
                    "c" -> {
                        currentColumn = columnIndex(parser.getAttributeValue(null, "r"))
                        cellType = parser.getAttributeValue(null, "t").orEmpty()
                        cellValue = StringBuilder()
                        inValue = false
                        inInlineText = false
                    }
                    "v" -> inValue = true
                    "t" -> if (cellType == "inlineStr") inInlineText = true
                }
                XmlPullParser.TEXT -> when {
                    inValue -> cellValue.append(parser.text.orEmpty())
                    inInlineText -> cellValue.append(parser.text.orEmpty())
                }
                XmlPullParser.END_TAG -> when (parser.name.substringAfter(':')) {
                    "v" -> inValue = false
                    "t" -> inInlineText = false
                    "c" -> {
                        val raw = cellValue.toString()
                        val value = when (cellType) {
                            "s" -> raw.trim().toIntOrNull()?.let { sharedStrings.getOrNull(it) }.orEmpty()
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> raw
                        }
                        if (currentColumn >= 0) rowCells[currentColumn] = value
                        currentColumn = -1
                    }
                    "row" -> {
                        val width = (rowCells.keys.maxOrNull() ?: -1) + 1
                        rows += List(width) { rowCells[it].orEmpty() }
                    }
                }
            }
            parser.next()
        }
        return rows
    }

    private fun columnIndex(reference: String?): Int {
        if (reference.isNullOrBlank()) return -1
        var value = 0
        for (char in reference) {
            if (char in 'A'..'Z') {
                value = value * 26 + (char - 'A' + 1)
            } else if (char in 'a'..'z') {
                value = value * 26 + (char - 'a' + 1)
            } else {
                break
            }
        }
        return value - 1
    }

    private fun escape(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) return value
        return "\"${value.replace("\"", "\"\"")}\""
    }
}
