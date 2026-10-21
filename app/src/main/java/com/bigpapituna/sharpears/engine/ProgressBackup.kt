package com.bigpapituna.sharpears.engine

/**
 * Progress export and import: every settings file (name to its key-value map) as one JSON document. Each value
 * carries its type so it comes back exactly as SharedPreferences stored it (an Int stays an Int, a Float a Float).
 *   {"app": "sharp-ears", "version": 1, "files": {"progress": {"best:twinkle": {"int": 92}, ...}, ...}}
 * Since the parent brief, the event logs ride along (data/EventLog.kt), each profile's lines as they are written:
 *   ..., "events": {"owner": ["{\"t\":...,\"e\":\"start\"}", ...], "p1": [...]}
 * The version stays 1: an older app reads "files" and never looks at "events", and a backup without "events" (made
 * before the brief) reads back with [Backup.events] null.
 */
object ProgressBackup {
    const val APP = "sharp-ears"
    const val VERSION = 1L

    class BadBackup(message: String) : Exception(message)

    /** What a backup holds: the settings files, and the event logs by profile id (null in a backup from before the brief). */
    data class Backup(val files: Map<String, Map<String, Any>>, val events: Map<String, List<String>>? = null)

    fun encode(files: Map<String, Map<String, Any?>>, events: Map<String, List<String>>? = null): String = MiniJson.write(linkedMapOf<String, Any?>(
        "app" to APP,
        "version" to VERSION,
        "files" to files.toSortedMap().mapValues { (_, values) ->
            values.toSortedMap().mapNotNull { (k, v) -> typed(v)?.let { k to it } }.toMap(LinkedHashMap())
        },
    ).apply { if (events != null) put("events", events.toSortedMap()) })

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
    fun decode(text: String): Map<String, Map<String, Any>> = decodeBackup(text).files

    /** The settings files and the event logs; throws [BadBackup] as [decode] does. Lines that are not text are skipped. */
    fun decodeBackup(text: String): Backup {
        val root = runCatching { MiniJson.read(text) }.getOrNull() as? Map<*, *> ?: throw BadBackup("This is not a JSON file.")
        val events = (root["events"] as? Map<*, *>)?.entries?.associate { (id, lines) ->
            id.toString() to (lines as? List<*>).orEmpty().filterIsInstance<String>()
        }
        return Backup(files(root), events)
    }

    private fun files(root: Map<*, *>): Map<String, Map<String, Any>> {
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
