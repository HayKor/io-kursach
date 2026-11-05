package ru.haykor.sudv.reports

private const val CSV_SEPARATOR = ";"
private val FORMULA_TRIGGER_CHARS = charArrayOf('=', '+', '-', '@', '\t', '\r')
private val UTF8_BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

/**
 * UTF-8 with a BOM (so Excel detects the encoding), `;` separator, CRLF. The first row is the header.
 * Data cells starting with a formula trigger get a leading `'`: descriptions are free text.
 */
fun CsvWriter(rows: List<List<String>>): ByteArray {
    val text = buildString {
        rows.forEachIndexed { rowIndex, row ->
            val cells = if (rowIndex == 0) row else row.map { it.guardFormula() }
            append(cells.joinToString(CSV_SEPARATOR) { it.csvQuote() })
            append("\r\n")
        }
    }
    return UTF8_BOM + text.toByteArray(Charsets.UTF_8)
}

private fun String.guardFormula(): String = if (isNotEmpty() && first() in FORMULA_TRIGGER_CHARS) "'$this" else this

private fun String.csvQuote(): String =
    if (any { it == ';' || it == '"' || it == '\r' || it == '\n' }) "\"${replace("\"", "\"\"")}\"" else this
