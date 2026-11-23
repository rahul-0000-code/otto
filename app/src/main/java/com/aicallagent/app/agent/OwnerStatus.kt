package com.aicallagent.app.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * What the owner is doing right now ("Driving", "In a meeting"). While one is set, Otto answers
 * every call itself and tells the caller [message]; "{owner}" in it becomes the owner's first name.
 */
data class OwnerStatus(
    val id: String,
    val emoji: String,
    val label: String,
    val message: String,
    val custom: Boolean = false,
)

object OwnerStatuses {
    const val OWNER = "{owner}"

    val presets = listOf(
        OwnerStatus("driving", "🚗", "Driving", "$OWNER is driving right now and can't take calls. Tell me what it's about and I'll pass it on."),
        OwnerStatus("meeting", "💼", "In a meeting", "$OWNER is in a meeting right now. Tell me what it's about and I'll pass it on right after."),
        OwnerStatus("studying", "📚", "Studying", "$OWNER is studying right now and asked not to be disturbed. I can take a message."),
        OwnerStatus("swimming", "🏊", "Swimming", "$OWNER is swimming right now and away from the phone. I'll pass on your message."),
        OwnerStatus("sleeping", "😴", "Sleeping", "$OWNER is asleep right now. If it's urgent, tell me and I'll flag it."),
        OwnerStatus("busy", "⏳", "Busy", "$OWNER is busy with something right now. Tell me what it's about and I'll pass it on."),
    )

    fun preset(id: String): OwnerStatus? = presets.firstOrNull { it.id == id }

    fun encode(list: List<OwnerStatus>): String = JSONArray().apply {
        list.forEach {
            put(JSONObject().put("id", it.id).put("emoji", it.emoji).put("label", it.label)
                .put("message", it.message).put("custom", it.custom))
        }
    }.toString()

    /** Saved list, or the presets. Presets missing from an older saved list are added back. */
    fun decode(json: String?): List<OwnerStatus> {
        if (json.isNullOrBlank()) return presets
        val saved = runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id")
                val label = o.optString("label").trim()
                if (id.isEmpty() || label.isEmpty()) null
                else OwnerStatus(id, o.optString("emoji"), label, o.optString("message"), o.optBoolean("custom"))
            }
        }.getOrDefault(emptyList())
        if (saved.isEmpty()) return presets
        return saved + presets.filter { p -> saved.none { it.id == p.id } }
    }
}
