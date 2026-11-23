package com.aicallagent.app.assistant

import org.json.JSONObject

/** "Samy, call Pooja" and friends: what the owner asked for, before any contact is looked up. */
data class VoiceCommand(
    val action: Action,
    val contact: String = "",
    val message: String = "",
    val destination: String = "",
    val mode: TravelMode = TravelMode.CAR,
) {
    enum class Action { CALL, SMS, WHATSAPP, NAVIGATE, UNKNOWN }

    val needsContact: Boolean get() = action == Action.CALL || action == Action.SMS || action == Action.WHATSAPP
}

/** Google Maps navigation modes. In India "bike" means a motorbike: Maps' two-wheeler mode. */
enum class TravelMode(val mapsCode: String, val label: String) {
    TWO_WHEELER("l", "by bike"),
    CAR("d", "by car"),
    WALK("w", "on foot"),
    BICYCLE("b", "by cycle"),
}

object CommandParser {

    /** Instructions for Gemini, which turns what the owner said into [VoiceCommand] JSON. */
    fun systemPrompt(assistant: String): String = """
        You turn a phone voice command, said to an assistant named $assistant, into JSON. It comes from speech-to-text,
        so expect mistakes, and it may be English, Hindi or Marathi written in Roman letters (e.g. "Soham ko call karo",
        "Aai la message kar ki mi nighalo").
        Reply with only this JSON:
        {"action": "call" | "sms" | "whatsapp" | "navigate" | "unknown",
         "contact": "<who to call or message, as they said the name; drop words like ko, la, bhai, ji, to, my>",
         "message": "<for sms/whatsapp: only the message to send, as the other person should read it>",
         "destination": "<for navigate: the place to go>",
         "mode": "two_wheeler" | "car" | "walk" | "bicycle"}
        Rules: "text", "SMS" or plain "message" means sms; WhatsApp only when they say WhatsApp. "Bike", "scooty" or
        "scooter" is two_wheeler; "cycle" is bicycle; with no mode given use car. Unsupported requests are "unknown".
        The message goes out as written, so polish it: fix grammar, spelling, capital letters and punctuation (end a
        question with "?"). Keep their meaning, tone and language: Hinglish or Marathi stays in Roman letters, never
        translate, never add anything they didn't say. "where have you reached" becomes "Where have you reached?";
        "kahan tak pahuncha" becomes "Kahan tak pahuncha?".
    """.trimIndent()

    fun fromJson(raw: String): VoiceCommand {
        val json = JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
        val action = when (json.optString("action").lowercase()) {
            "call" -> VoiceCommand.Action.CALL
            "sms", "text" -> VoiceCommand.Action.SMS
            "whatsapp" -> VoiceCommand.Action.WHATSAPP
            "navigate" -> VoiceCommand.Action.NAVIGATE
            else -> VoiceCommand.Action.UNKNOWN
        }
        return VoiceCommand(
            action,
            contact = json.optString("contact").trim(),
            // Gemini polishes the message; this only adds what it may leave out, like the final "?".
            message = MessageTidy.tidy(json.optString("message")),
            destination = json.optString("destination").trim(),
            mode = mode(json.optString("mode")),
        ).validated()
    }

    private val I = RegexOption.IGNORE_CASE
    private val CALL = Regex("^(?:call|phone|ring|dial)\\s+(.+)$", I)
    private val CALL_HI = Regex("^(.+?)\\s+(?:ko|la)\\s+(?:call|phone)(?:\\s+(?:karo|kar|lagao|laga|kara))?$", I)
    private val WHATSAPP = Regex("^(?:whatsapp|send (?:a )?whatsapp(?: message)? to|whatsapp message to)\\s+(.+?)(?:\\s+(?:saying|that|:)\\s+|,\\s*)(.+)$", I)
    private val SMS = Regex("^(?:text|sms|message|send (?:a )?(?:text|message|sms) to)\\s+(.+?)(?:\\s+(?:saying|that|:)\\s+|,\\s*)(.+)$", I)
    private val NAVIGATE = Regex("^(?:navigate|take me|directions|start navigation|go)\\s+(?:to\\s+)?(.+?)(?:\\s+(?:by|on)\\s+(bike|scooty|scooter|car|foot|walk|cycle|bicycle))?$", I)

    /** Works without a cloud brain: the plain English (and simple Hinglish) phrasings. */
    fun rules(said: String, assistant: String): VoiceCommand {
        val text = clean(said, assistant)
        NAVIGATE.find(text)?.let { m ->
            return VoiceCommand(VoiceCommand.Action.NAVIGATE, destination = m.groupValues[1].trim(), mode = mode(m.groupValues[2]))
        }
        WHATSAPP.find(text)?.let { m ->
            return VoiceCommand(VoiceCommand.Action.WHATSAPP, contact = m.groupValues[1].trim(), message = MessageTidy.tidy(m.groupValues[2])).validated()
        }
        SMS.find(text)?.let { m ->
            return VoiceCommand(VoiceCommand.Action.SMS, contact = m.groupValues[1].trim(), message = MessageTidy.tidy(m.groupValues[2])).validated()
        }
        (CALL.find(text) ?: CALL_HI.find(text))?.let { m ->
            return VoiceCommand(VoiceCommand.Action.CALL, contact = m.groupValues[1].trim()).validated()
        }
        return VoiceCommand(VoiceCommand.Action.UNKNOWN)
    }

    /** "Hey Samy, Call Pooja." becomes "Call Pooja". */
    private fun clean(said: String, assistant: String): String {
        var text = said.trim().trimEnd('.', '!', '?')
        val name = Regex.escape(assistant)
        text = text.replace(Regex("^(?:(?:hey|hi|ok|okay)\\s*)?$name[,\\s]*", I), "")
        return text.replace(Regex("\\s+"), " ").trim()
    }

    private fun mode(word: String): TravelMode = when (word.lowercase().trim()) {
        "two_wheeler", "bike", "scooty", "scooter", "motorbike" -> TravelMode.TWO_WHEELER
        "walk", "foot", "walking" -> TravelMode.WALK
        "bicycle", "cycle" -> TravelMode.BICYCLE
        else -> TravelMode.CAR
    }

    private fun VoiceCommand.validated(): VoiceCommand = when {
        needsContact && contact.isBlank() -> copy(action = VoiceCommand.Action.UNKNOWN)
        (action == VoiceCommand.Action.SMS || action == VoiceCommand.Action.WHATSAPP) && message.isBlank() -> copy(action = VoiceCommand.Action.UNKNOWN)
        action == VoiceCommand.Action.NAVIGATE && destination.isBlank() -> copy(action = VoiceCommand.Action.UNKNOWN)
        else -> this
    }
}

/**
 * Speech-to-text gives "where have you reached" with no capitals or punctuation. Before a message
 * goes out, give it the basics people expect: a capital first letter, "I" not "i", tidy spacing,
 * and a closing "?" or ".". Conservative on purpose: words are never changed or translated.
 */
object MessageTidy {
    // Only openers that almost always ask something: texts like "Will call you later", "Am on the
    // way" or "Have fun" must not get a "?".
    private val QUESTION_STARTS = setOf(
        "where", "what", "when", "why", "how", "who", "whom", "whose", "which", "are", "is", "can", "could",
        "would", "should", "does", "did", "kya", "kahan", "kaha", "kidhar", "kab", "kaise", "kaisa", "kaisi",
        "kyun", "kyu", "kaun", "kitna", "kitne", "kuthe", "kadhi", "kasa", "kashi",
    )
    // Hindi "… kya" and Marathi "… ka" turn a sentence into a question.
    private val QUESTION_ENDS = setOf("kya", "ka")

    fun tidy(raw: String): String {
        var text = raw.trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty()) return text
        text = text.replace(Regex("\\s+([,.!?;:])"), "$1")
        text = text.replace(Regex("\\bi\\b(?!\\.)"), "I").replace(Regex("\\bi'(m|ll|ve|d)\\b"), "I'$1")
        text = text.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val last = text.last()
        if (last.isLetterOrDigit()) {
            val words = text.lowercase().split(Regex("[^\\p{L}\\p{N}']+")).filter { it.isNotEmpty() }
            val question = words.firstOrNull() in QUESTION_STARTS || words.lastOrNull() in QUESTION_ENDS
            text += if (question) "?" else "."
        }
        return text
    }
}

/** A phone contact; [number] as stored. */
data class ContactEntry(val name: String, val number: String)

object ContactMatcher {
    /**
     * Contacts that fit the spoken name, best first. Matching stays on the phone: the contact list
     * is never sent to the cloud brain.
     */
    fun rank(spoken: String, contacts: List<ContactEntry>): List<ContactEntry> {
        val query = normalize(spoken)
        if (query.isEmpty()) return emptyList()
        val queryTokens = query.split(" ")
        return contacts.asSequence()
            .map { it to score(query, queryTokens, normalize(it.name)) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .distinctBy { it.name.lowercase() to it.number.filter(Char::isDigit).takeLast(10) }
            .toList()
    }

    /** The one contact meant, or null when nothing fits or several fit equally well. */
    fun best(spoken: String, contacts: List<ContactEntry>): ContactEntry? {
        val query = normalize(spoken)
        val ranked = contacts.map { it to score(query, query.split(" "), normalize(it.name)) }
            .filter { it.second > 0 }.sortedByDescending { it.second }
        if (ranked.isEmpty()) return null
        val top = ranked[0]
        val rivals = ranked.drop(1).filter {
            it.second == top.second && it.first.number.filter(Char::isDigit).takeLast(10) != top.first.number.filter(Char::isDigit).takeLast(10)
        }
        return if (rivals.isEmpty()) top.first else null
    }

    private fun score(query: String, queryTokens: List<String>, name: String): Int {
        if (name.isEmpty()) return 0
        val nameTokens = name.split(" ")
        return when {
            name == query -> 100
            name.startsWith("$query ") -> 90
            queryTokens.all { q -> nameTokens.any { it == q } } -> 80
            nameTokens.all { n -> queryTokens.any { it == n } } -> 75
            queryTokens.any { q -> q.length >= 3 && nameTokens.any { it == q } } -> 60
            queryTokens.any { q -> q.length >= 4 && nameTokens.any { n -> n.length >= 4 && editDistance(q, n) <= 1 } } -> 40
            else -> 0
        }
    }

    private fun normalize(s: String): String =
        s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun editDistance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val cur = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = cur
            }
        }
        return d[b.length]
    }
}

object PhoneNumbers {
    /** WhatsApp wants the full international number without "+": "98765 43210" becomes "919876543210". */
    fun forWhatsApp(number: String, defaultCountry: String = "91"): String {
        val plus = number.trim().startsWith("+")
        var digits = number.filter(Char::isDigit)
        if (plus) return digits
        if (digits.startsWith("00")) return digits.drop(2)
        digits = digits.trimStart('0')
        return if (digits.length == 10) defaultCountry + digits else digits
    }
}
