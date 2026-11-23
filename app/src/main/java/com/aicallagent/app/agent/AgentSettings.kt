package com.aicallagent.app.agent

import android.content.Context

enum class AnswerMode { OFF, ASK, AUTO }

enum class Provider(val label: String, val tagline: String) {
    GEMINI("Gemini", "Fast, great at Hindi & Marathi"),
    OPENAI("ChatGPT", "OpenAI models"),
    LOCAL("On-device", "Private & offline · Gemma"),
}

enum class Style(val label: String, val prompt: String) {
    FRIENDLY("Friendly", "Warm, friendly and casual, like a helpful friend."),
    PROFESSIONAL("Professional", "Polite, crisp and professional, like an executive assistant."),
    RESPECTFUL("Respectful", "Very respectful and gentle (use 'ji' and 'aap' in Hindi, 'tumhi' in Marathi). Good for elders."),
    CONCISE("Concise", "Extremely brief. Say only what is needed, in as few words as possible."),
    WITTY("Witty", "Light-hearted with a gentle sense of humour, while still being genuinely helpful."),
}

enum class ReplyLanguage(val label: String, val native: String) {
    MATCH("Match caller", "Auto"),
    ENGLISH("English", "English"),
    HINDI("Hindi", "हिंदी"),
    MARATHI("Marathi", "मराठी"),
    HINGLISH("Hinglish", "Roman"),
    MARATHI_ROMAN("Marathi (Roman)", "Roman"),
}

enum class VoiceGender(val label: String) { FEMALE("Female"), MALE("Male") }

data class AgentConfig(
    val mode: AnswerMode = AnswerMode.OFF,
    val autoDelaySec: Int = 8,
    val assistantName: String = "Otto",
    val ownerName: String = "",
    val gender: VoiceGender = VoiceGender.FEMALE,
    val style: Style = Style.FRIENDLY,
    val language: ReplyLanguage = ReplyLanguage.MATCH,
    val instructions: String = "",
    val provider: Provider = Provider.GEMINI,
    val geminiModel: String = Models.GEMINI_DEFAULT,
    val openAiModel: String = Models.OPENAI_DEFAULT,
    val onboarded: Boolean = false,
    // Banking apps flag any app with accessibility switched on. When set, Otto switches its
    // accessibility on only while a call rings or is in progress (needs WRITE_SECURE_SETTINGS).
    val accessibilityOnlyDuringCalls: Boolean = false,
    val statuses: List<OwnerStatus> = OwnerStatuses.presets,
    val statusId: String = "",
    // When the status ends by itself, in epoch ms; 0 keeps it until the owner changes it.
    val statusUntil: Long = 0,
    // The voice assistant's name ("Samy, call Pooja"). Separate from [assistantName], the persona
    // callers hear, so the two assistants never share a name.
    val voiceName: String = "Samy",
) {
    val owner: String get() = ownerName.trim().ifEmpty { "the owner" }
    val assistant: String get() = assistantName.trim().ifEmpty { "Otto" }
    val voice: String get() = voiceName.trim().ifEmpty { "Samy" }

    @JvmOverloads
    fun activeStatus(now: Long = System.currentTimeMillis()): OwnerStatus? =
        if (statusId.isEmpty() || (statusUntil > 0 && now >= statusUntil)) null
        else statuses.firstOrNull { it.id == statusId }

    /** While a status is on, Otto answers every call: nobody can tap a button while driving or swimming. */
    @JvmOverloads
    fun effectiveMode(now: Long = System.currentTimeMillis()): AnswerMode =
        if (activeStatus(now) != null) AnswerMode.AUTO else mode

    /** The status message as the caller should hear it, with the owner's first name filled in. */
    fun statusMessage(status: OwnerStatus): String =
        status.message.replace(OwnerStatuses.OWNER, PromptBuilder.spokenName(owner)).trim()
    fun model(provider: Provider = this.provider): String = when (provider) {
        Provider.GEMINI -> geminiModel.trim().ifEmpty { Models.GEMINI_DEFAULT }
        Provider.OPENAI -> openAiModel.trim().ifEmpty { Models.OPENAI_DEFAULT }
        Provider.LOCAL -> "gemma"
    }
}

object Models {
    const val GEMINI_DEFAULT = "gemini-3.5-flash-lite"
    const val OPENAI_DEFAULT = "gpt-5.4-nano"

    data class Option(val id: String, val label: String, val note: String)

    // Measured on a live key (Hindi call turns): 3.1 FL ~1.1 s, 3.5 FL ~1.2 s, 3.6 F ~1.9 s; 3.8 F timed out.
    val gemini = listOf(
        Option("gemini-3.5-flash-lite", "3.5 Flash-Lite", "~1.2 s · best balance · recommended"),
        Option("gemini-3.1-flash-lite", "3.1 Flash-Lite", "~1.1 s · fastest, most consistent"),
        Option("gemini-3.6-flash", "3.6 Flash", "~1.9 s · smarter, slower pauses"),
    )
    val openai = listOf(
        Option("gpt-5.4-nano", "GPT-5.4 nano", "Fastest replies"),
        Option("gpt-5.4-mini", "GPT-5.4 mini", "Smarter · a little slower"),
    )
}

object AgentSettings {
    private const val PREFS = "agent_settings"

    @JvmStatic
    fun load(context: Context): AgentConfig {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val d = AgentConfig()
        return AgentConfig(
            mode = enumOr(p.getString("mode", null), d.mode),
            autoDelaySec = p.getInt("auto_delay", d.autoDelaySec),
            assistantName = p.getString("assistant_name", d.assistantName)!!,
            ownerName = p.getString("owner_name", d.ownerName)!!,
            gender = enumOr(p.getString("gender", null), d.gender),
            style = enumOr(p.getString("style", null), d.style),
            language = enumOr(p.getString("language", null), d.language),
            instructions = p.getString("instructions", d.instructions)!!,
            provider = enumOr(p.getString("provider", null), d.provider),
            geminiModel = p.getString("gemini_model", d.geminiModel)!!,
            openAiModel = p.getString("openai_model", d.openAiModel)!!,
            onboarded = p.getBoolean("onboarded", d.onboarded),
            accessibilityOnlyDuringCalls = p.getBoolean("a11y_only_during_calls", d.accessibilityOnlyDuringCalls),
            statuses = OwnerStatuses.decode(p.getString("statuses", null)),
            statusId = p.getString("status_id", d.statusId)!!,
            statusUntil = p.getLong("status_until", d.statusUntil),
            voiceName = p.getString("voice_name", d.voiceName)!!,
        )
    }

    @JvmStatic
    fun save(context: Context, c: AgentConfig) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("mode", c.mode.name)
            .putInt("auto_delay", c.autoDelaySec)
            .putString("assistant_name", c.assistantName)
            .putString("owner_name", c.ownerName)
            .putString("gender", c.gender.name)
            .putString("style", c.style.name)
            .putString("language", c.language.name)
            .putString("instructions", c.instructions)
            .putString("provider", c.provider.name)
            .putString("gemini_model", c.geminiModel)
            .putString("openai_model", c.openAiModel)
            .putBoolean("onboarded", c.onboarded)
            .putBoolean("a11y_only_during_calls", c.accessibilityOnlyDuringCalls)
            .putString("statuses", OwnerStatuses.encode(c.statuses))
            .putString("status_id", c.statusId)
            .putLong("status_until", c.statusUntil)
            .putString("voice_name", c.voiceName)
            .apply()
    }

    @JvmStatic
    fun apiKey(context: Context, provider: Provider): String =
        if (provider == Provider.LOCAL) "" else SecureStore.get(context, "key_" + provider.name)

    @JvmStatic
    fun setApiKey(context: Context, provider: Provider, key: String) {
        if (provider != Provider.LOCAL) SecureStore.put(context, "key_" + provider.name, key.trim())
    }

    @JvmStatic
    fun hasCloudBrain(context: Context, c: AgentConfig = load(context)): Boolean =
        c.provider != Provider.LOCAL && SecureStore.has(context, "key_" + c.provider.name)

    private inline fun <reified T : Enum<T>> enumOr(value: String?, fallback: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
