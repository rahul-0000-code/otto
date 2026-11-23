package com.aicallagent.app.agent

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CallRecord(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long,
    val number: String,
    val name: String,
    val transcript: List<CloudLlm.Turn>,
    val title: String = "",
    val summary: String = "",
    val urgent: Boolean = false,
    val spam: Boolean = false,
) {
    val displayName: String get() = name.ifBlank { number.ifBlank { "Unknown caller" } }
    val durationSec: Long get() = ((endedAt - startedAt) / 1000).coerceAtLeast(0)
}

/** Private JSON file of calls the assistant handled. Stays on the device (no backup). */
object CallRecords {
    private const val FILE = "calls.json"
    private const val MAX = 200

    @Volatile var version = 0L
        private set

    @Synchronized
    fun all(context: Context): List<CallRecord> {
        val file = File(context.filesDir, FILE)
        if (!file.isFile) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { array.optJSONObject(it)?.let(::decode) }
        }.getOrDefault(emptyList()).sortedByDescending { it.startedAt }
    }

    fun get(context: Context, id: Long): CallRecord? = all(context).firstOrNull { it.id == id }

    @Synchronized
    fun upsert(context: Context, record: CallRecord) {
        val list = all(context).filterNot { it.id == record.id }.toMutableList()
        list.add(0, record)
        write(context, list.sortedByDescending { it.startedAt }.take(MAX))
    }

    @Synchronized
    fun delete(context: Context, id: Long) = write(context, all(context).filterNot { it.id == id })

    @Synchronized
    fun clear(context: Context) = write(context, emptyList())

    private fun write(context: Context, list: List<CallRecord>) {
        val array = JSONArray()
        list.forEach { array.put(encode(it)) }
        val target = File(context.filesDir, FILE)
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(array.toString())
        tmp.renameTo(target)
        version++
    }

    private fun encode(r: CallRecord) = JSONObject().apply {
        put("id", r.id); put("startedAt", r.startedAt); put("endedAt", r.endedAt)
        put("number", r.number); put("name", r.name)
        put("title", r.title); put("summary", r.summary); put("urgent", r.urgent); put("spam", r.spam)
        put("transcript", JSONArray().apply {
            r.transcript.forEach { put(JSONObject().put("role", it.role).put("text", it.text)) }
        })
    }

    private fun decode(o: JSONObject): CallRecord {
        val t = o.optJSONArray("transcript") ?: JSONArray()
        return CallRecord(
            id = o.optLong("id"),
            startedAt = o.optLong("startedAt"),
            endedAt = o.optLong("endedAt"),
            number = o.optString("number"),
            name = o.optString("name"),
            transcript = (0 until t.length()).mapNotNull { i ->
                t.optJSONObject(i)?.let { CloudLlm.Turn(it.optString("role"), it.optString("text")) }
            },
            title = o.optString("title"),
            summary = o.optString("summary"),
            urgent = o.optBoolean("urgent"),
            spam = o.optBoolean("spam"),
        )
    }
}
