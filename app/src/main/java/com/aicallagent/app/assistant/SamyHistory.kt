package com.aicallagent.app.assistant

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** What Samy did, newest first, for the Samy screen's "recent" list. Kept only on this phone. */
object SamyHistory {
    data class Entry(val at: Long, val action: VoiceCommand.Action, val said: String, val title: String, val detail: String)

    private const val FILE = "samy_history.json"
    private const val MAX = 30

    @Volatile var version = 0L
        private set

    @Synchronized
    fun add(context: Context, entry: Entry) {
        val list = (listOf(entry) + all(context)).take(MAX)
        val array = JSONArray()
        list.forEach {
            array.put(JSONObject().put("at", it.at).put("action", it.action.name).put("said", it.said)
                .put("title", it.title).put("detail", it.detail))
        }
        File(context.filesDir, FILE).writeText(array.toString())
        version++
    }

    @Synchronized
    fun clear(context: Context) {
        File(context.filesDir, FILE).delete()
        version++
    }

    @Synchronized
    fun all(context: Context): List<Entry> {
        val file = File(context.filesDir, FILE)
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText())
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val action = runCatching { VoiceCommand.Action.valueOf(o.optString("action")) }.getOrNull() ?: return@mapNotNull null
                Entry(o.optLong("at"), action, o.optString("said"), o.optString("title"), o.optString("detail"))
            }
        }.getOrDefault(emptyList())
    }
}
