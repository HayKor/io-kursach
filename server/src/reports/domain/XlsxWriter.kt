package ru.haykor.sudv.server.reports.domain

import org.dhatim.fastexcel.Workbook
import java.io.ByteArrayOutputStream

private const val MIN_COLUMN_WIDTH = 10

private const val MAX_COLUMN_WIDTH = 40

private const val COLUMN_WIDTH_PADDING = 2.0

/** Every cell is written as a string (so formula injection is not possible); [rows] first entry (the header) is bold. */
fun XlsxWriter(sheetName: String, rows: List<List<String>>): ByteArray {
    val out = ByteArrayOutputStream()
    Workbook(out, "SUDV", "1.0").use { workbook ->
        val sheet = workbook.newWorksheet(sheetName)
        rows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { colIndex, cell -> sheet.value(rowIndex, colIndex, cell) }
        }

        val columnCount = rows.maxOfOrNull { it.size } ?: 0
        if (rows.isNotEmpty() && columnCount > 0) {
            sheet.range(0, 0, 0, columnCount - 1).style().bold().set()
        }
        for (col in 0 until columnCount) {
            val widestCell = rows.maxOf { it.getOrNull(col)?.length ?: 0 }
            sheet.width(col, widestCell.coerceIn(MIN_COLUMN_WIDTH, MAX_COLUMN_WIDTH) + COLUMN_WIDTH_PADDING)
        }
    }
    return out.toByteArray()
}
