package ru.haykor.sudv.server.reports

import ru.haykor.sudv.server.reports.domain.CsvWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CsvWriterTest {
    @Test
    fun startsWithUtf8Bom() {
        val bytes = CsvWriter(listOf(listOf("A")))
        assertEquals(listOf(0xEF, 0xBB, 0xBF), bytes.take(3).map { it.toInt() and 0xFF })
    }

    @Test
    fun separatesWithSemicolonsAndEndsLinesWithCrLf() {
        val bytes = CsvWriter(listOf(listOf("a", "b"), listOf("c", "d")))
        val text = bytes.drop(3).toByteArray().decodeToString()
        assertEquals("a;b\r\nc;d\r\n", text)
    }

    @Test
    fun quotesFieldsContainingSeparatorQuoteOrNewlineAndDoublesQuotes() {
        val bytes = CsvWriter(listOf(listOf("plain", "a;b", "a\"b", "a\nb", "a\rb")))
        val text = bytes.drop(3).toByteArray().decodeToString()
        assertEquals("plain;\"a;b\";\"a\"\"b\";\"a\nb\";\"a\rb\"\r\n", text)
    }

    @Test
    fun guardsFormulaInjectionInDataRowsButNotInTheHeader() {
        val bytes = CsvWriter(listOf(listOf("=1+1"), listOf("=1+1"), listOf("+1"), listOf("-1"), listOf("@cmd"), listOf("plain")))
        val lines = bytes.drop(3).toByteArray().decodeToString().split("\r\n").filter { it.isNotEmpty() }

        assertEquals("=1+1", lines[0]) // header: left untouched
        assertEquals("'=1+1", lines[1])
        assertEquals("'+1", lines[2])
        assertEquals("'-1", lines[3])
        assertEquals("'@cmd", lines[4])
        assertEquals("plain", lines[5])
    }

    @Test
    fun emptyCellIsNotTreatedAsAFormulaTrigger() {
        val bytes = CsvWriter(listOf(listOf("header"), listOf("")))
        val text = bytes.drop(3).toByteArray().decodeToString()
        assertTrue(text.endsWith("header\r\n\r\n"))
    }
}
