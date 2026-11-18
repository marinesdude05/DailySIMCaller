package com.example.dailysimcaller

/** Expects phone_number as a header. Other columns are ignored. */
object PhoneCsv {
    fun cleanPhoneNumber(raw: String): String? {
        if (raw.any { it.isLetter() }) return null
        val compact = raw.filter { it.isDigit() || it == '+' }
        return compact.takeIf { Regex("\\+?[0-9]{7,15}").matches(it) }
    }

    /** Handles quoted commas and newlines, escaped double-quotes, CRLF, and BOM. */
    private fun records(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var cells = mutableListOf<String>()
        val value = StringBuilder()
        var quoted = false
        var i = 0
        val source = text.removePrefix("\uFEFF")
        while (i < source.length) {
            when (val c = source[i]) {
                '"' -> if (quoted && i + 1 < source.length && source[i + 1] == '"') {
                    value.append('"'); i++
                } else quoted = !quoted
                ',' -> if (quoted) value.append(c) else {
                    cells.add(value.toString().trim()); value.setLength(0)
                }
                '\r', '\n' -> if (quoted) value.append(c) else {
                    cells.add(value.toString().trim()); value.setLength(0)
                    if (cells.any { it.isNotEmpty() }) rows.add(cells)
                    cells = mutableListOf()
                    if (c == '\r' && i + 1 < source.length && source[i + 1] == '\n') i++
                }
                else -> value.append(c)
            }
            i++
        }
        require(!quoted) { "CSV has an unmatched quote" }
        cells.add(value.toString().trim())
        if (cells.any { it.isNotEmpty() }) rows.add(cells)
        return rows
    }

    fun parse(text: String): List<String> {
        val rows = records(text)
        if (rows.isEmpty()) return emptyList()
        val col = rows.first().indexOfFirst {
            it.lowercase().filter(Char::isLetterOrDigit) in setOf("phonenumber", "phone", "mobile", "number")
        }
        require(col >= 0) { "Missing phone_number column header" }
        return rows.drop(1).mapNotNull { row ->
            cleanPhoneNumber(row.getOrNull(col).orEmpty())
        }.distinct()
    }
}
