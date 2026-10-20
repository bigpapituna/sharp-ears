package com.bigpapituna.sharpears.engine

/**
 * A small JSON writer and reader for the app's own files (run recordings, progress export), so engine code and
 * its JVM tests need no org.json. Objects read back as LinkedHashMap, arrays as List, numbers as Long when whole
 * (and written without a fraction), otherwise Double.
 * ponytail: no \u escapes beyond what it writes itself; enough for files this app writes
 */
object MiniJson {
    fun write(v: Any?): String = StringBuilder().also { write(v, it) }.toString()

    private fun write(v: Any?, out: StringBuilder) {
        when (v) {
            null -> out.append("null")
            is Boolean -> out.append(v)
            is Int, is Long, is Short, is Byte -> out.append(v)
            is Float -> if (v.isFinite()) out.append(v.toDouble()) else out.append("null")
            is Double -> if (v.isFinite()) out.append(v) else out.append("null")
            is String -> string(v, out)
            is Map<*, *> -> {
                out.append('{')
                v.entries.forEachIndexed { i, (k, x) -> if (i > 0) out.append(','); string(k.toString(), out); out.append(':'); write(x, out) }
                out.append('}')
            }
            is Iterable<*> -> {
                out.append('[')
                v.forEachIndexed { i, x -> if (i > 0) out.append(','); write(x, out) }
                out.append(']')
            }
            else -> string(v.toString(), out)
        }
    }

    private fun string(s: String, out: StringBuilder) {
        out.append('"')
        for (c in s) when (c) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> if (c < ' ') out.append("\\u%04x".format(c.code)) else out.append(c)
        }
        out.append('"')
    }

    // A byte-order mark (a backup re-saved by a text editor) is not part of the JSON.
    fun read(text: String): Any? = Reader(text.removePrefix("﻿")).run { val v = value(); ws(); require(i == s.length) { "trailing text at $i" }; v }

    private class Reader(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            ws()
            require(i < s.length) { "unexpected end" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("bad character '$c' at $i")
            }
        }
        fun lit(word: String, v: Any?): Any? { require(s.startsWith(word, i)) { "bad literal at $i" }; i += word.length; return v }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++; ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws(); val k = str(); ws()
                require(s[i] == ':') { "expected : at $i" }; i++
                m[k] = value(); ws()
                when (s[i++]) { ',' -> continue; '}' -> return m; else -> throw IllegalArgumentException("expected , or } at ${i - 1}") }
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++; ws()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value(); ws()
                when (s[i++]) { ',' -> continue; ']' -> return l; else -> throw IllegalArgumentException("expected , or ] at ${i - 1}") }
            }
        }
        fun str(): String {
            require(s[i] == '"') { "expected string at $i" }
            i++
            val b = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> b.append('\n'); 'r' -> b.append('\r'); 't' -> b.append('\t'); 'b' -> b.append('\b'); 'f' -> b.append('\u000c')
                        'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> b.append(e)
                    }
                    else -> b.append(c)
                }
            }
        }
        fun num(): Any {
            val start = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            val t = s.substring(start, i)
            return if (t.any { it in ".eE" }) t.toDouble() else t.toLong()
        }
    }
}
