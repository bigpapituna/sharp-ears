package com.bigpapituna.sharpears.engine

/**
 * Progress export and import: every settings file (name to its key-value map) as one JSON document. Each value
 * carries its type so it comes back exactly as SharedPreferences stored it (an Int stays an Int, a Float a Float).
 *   {"app": "sharp-ears", "version": 1, "files": {"progress": {"best:twinkle": {"int": 92}, ...}, ...}}
 */
object ProgressBackup {
    const val APP = "sharp-ears"
    const val VERSION = 1L

    class BadBackup(message: String) : Exception(message)

    fun encode(files: Map<String, Map<String, Any?>>): String = MiniJson.write(linkedMapOf(
        "app" to APP,
        "version" to VERSION,
        "files" to files.toSortedMap().mapValues { (_, values) ->
            values.toSortedMap().mapNotNull { (k, v) -> typed(v)?.let { k to it } }.toMap(LinkedHashMap())
        },
    ))

    private fun typed(v: Any?): Map<String, Any?>? = when (v) {
        is Boolean -> mapOf("bool" to v)
        is Int -> mapOf("int" to v)
        is Long -> mapOf("long" to v)
        is Float -> mapOf("float" to v.toDouble())
        is String -> mapOf("string" to v)
        is Set<*> -> mapOf("set" to v.map { it.toString() }.sorted())
        else -> null                                                      // nothing else lives in SharedPreferences
    }

    /** Throws [BadBackup] with a readable message when [text] is not a Sharp Ears progress file. */
    fun decode(text: String): Map<String, Map<String, Any>> {
        val root = runCatching { MiniJson.read(text) }.getOrNull() as? Map<*, *> ?: throw BadBackup("This is not a JSON file.")
        if (root["app"] != APP) throw BadBackup("This file was not exported by Sharp Ears.")
        if ((root["version"] as? Number)?.toLong() != VERSION) throw BadBackup("This progress file is from a newer version of the app.")
        val files = root["files"] as? Map<*, *> ?: throw BadBackup("The file has no progress in it.")
        return files.entries.associate { (name, values) ->
            name.toString() to ((values as? Map<*, *>) ?: emptyMap<Any, Any>()).entries.mapNotNull { (k, t) ->
                val (type, v) = (t as? Map<*, *>)?.entries?.singleOrNull() ?: return@mapNotNull null
                val value: Any = when (type) {
                    "bool" -> v as? Boolean
                    "int" -> (v as? Number)?.toInt()
                    "long" -> (v as? Number)?.toLong()
                    "float" -> (v as? Number)?.toFloat()
                    "string" -> v as? String
                    "set" -> (v as? List<*>)?.map { it.toString() }?.toSet()
                    else -> null
                } ?: return@mapNotNull null
                k.toString() to value
            }.toMap()
        }
    }
}
