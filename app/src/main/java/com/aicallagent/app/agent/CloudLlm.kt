package com.aicallagent.app.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicReference

/** Minimal Gemini / OpenAI chat client. Error codes are stable strings; keys and content are never logged. */
object CloudLlm {
    data class Turn(val role: String, val text: String) // role: "user" | "assistant"

    class LlmException(val code: String, message: String = code) : Exception(message)

    /** Lets a caller abort an in-flight request from another thread. */
    class Handle {
        internal val connection = AtomicReference<HttpURLConnection?>()
        @Volatile var cancelled = false
            private set
        fun cancel() { cancelled = true; connection.get()?.disconnect() }
    }

    fun complete(
        provider: Provider,
        apiKey: String,
        model: String,
        system: String,
        turns: List<Turn>,
        maxTokens: Int = 400,
        json: Boolean = false,
        handle: Handle = Handle(),
    ): String {
        if (apiKey.isBlank()) throw LlmException("NO_KEY")
        val text = when (provider) {
            Provider.GEMINI -> gemini(apiKey, model, system, turns, maxTokens, json, handle)
            Provider.OPENAI -> openai(apiKey, model, system, turns, maxTokens, json, handle)
            Provider.LOCAL -> throw LlmException("UNSUPPORTED")
        }
        if (text.isBlank()) throw LlmException("EMPTY")
        return text.trim()
    }

    private fun gemini(key: String, model: String, system: String, turns: List<Turn>, maxTokens: Int,
                       json: Boolean, handle: Handle): String {
        val contents = JSONArray()
        // Gemini expects the conversation to open with a user turn.
        if (turns.firstOrNull()?.role != "user") {
            contents.put(part("user", "(The call has just been connected.)"))
        }
        for (t in turns) contents.put(part(if (t.role == "user") "user" else "model", t.text))
        fun body(withThinking: Boolean) = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", contents)
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", maxTokens + 600)
                if (json) put("responseMimeType", "application/json")
                val level = thinkingLevel(model)
                if (withThinking && level != null) put("thinkingConfig", JSONObject().put("thinkingLevel", level))
            })
        }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}:generateContent"
        val headers = mapOf("x-goog-api-key" to key)
        val response = try {
            post(url, headers, body(true), handle)
        } catch (e: LlmException) {
            // Older or newer models may reject the thinking field; retry once without it.
            if (e.code == "HTTP_400" && thinkingLevel(model) != null) post(url, headers, body(false), handle) else throw e
        }
        val candidate = response.optJSONArray("candidates")?.optJSONObject(0)
            ?: throw LlmException(if (response.has("promptFeedback")) "BLOCKED" else "EMPTY")
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts") ?: JSONArray()
        return buildString {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                append(p.optString("text", ""))
            }
        }
    }

    private fun thinkingLevel(model: String): String? {
        val m = model.lowercase()
        return when {
            !m.startsWith("gemini-3") -> null
            m.contains("flash-lite") || m.contains("3.6-flash") || m.contains("3.5-flash") -> "minimal"
            else -> "low"
        }
    }

    private fun part(role: String, text: String) = JSONObject()
        .put("role", role)
        .put("parts", JSONArray().put(JSONObject().put("text", text)))

    private fun openai(key: String, model: String, system: String, turns: List<Turn>, maxTokens: Int,
                       json: Boolean, handle: Handle): String {
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", system))
        for (t in turns) messages.put(JSONObject().put("role", if (t.role == "user") "user" else "assistant").put("content", t.text))
        fun body(withEffort: Boolean) = JSONObject().apply {
            put("model", model.trim())
            put("messages", messages)
            put("max_completion_tokens", maxTokens + 1200)
            if (json) put("response_format", JSONObject().put("type", "json_object"))
            if (withEffort) put("reasoning_effort", "none")
        }
        val url = "https://api.openai.com/v1/chat/completions"
        val headers = mapOf("Authorization" to "Bearer $key")
        val response = try {
            post(url, headers, body(true), handle)
        } catch (e: LlmException) {
            if (e.code == "HTTP_400") post(url, headers, body(false), handle) else throw e
        }
        return response.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "") ?: ""
    }

    private fun post(url: String, headers: Map<String, String>, body: JSONObject, handle: Handle): JSONObject {
        if (handle.cancelled) throw LlmException("CANCELLED")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        handle.connection.set(conn)
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (status !in 200..299) {
                throw LlmException(
                    when (status) {
                        400 -> "HTTP_400"
                        401, 403 -> "AUTH"
                        404 -> "MODEL_NOT_FOUND"
                        429 -> "QUOTA"
                        else -> "HTTP_$status"
                    },
                    errorMessage(text) ?: "HTTP $status",
                )
            }
            return JSONObject(text)
        } catch (e: IOException) {
            conn.disconnect()
            throw LlmException(if (handle.cancelled) "CANCELLED" else "NETWORK", e.javaClass.simpleName)
        } finally {
            // No disconnect on success: the fully read connection returns to the keep-alive pool,
            // so later turns in the same call skip DNS and TLS.
            handle.connection.set(null)
        }
    }

    /**
     * Opens DNS + TLS to the provider while the phone is still ringing. Fetches only model
     * metadata (no tokens billed); failures are ignored.
     */
    fun warmUp(provider: Provider, apiKey: String, model: String) {
        if (apiKey.isBlank() || provider == Provider.LOCAL) return
        runCatching {
            val (url, header) = when (provider) {
                Provider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/${model.trim()}" to ("x-goog-api-key" to apiKey)
                else -> "https://api.openai.com/v1/models/${model.trim()}" to ("Authorization" to "Bearer $apiKey")
            }
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000; readTimeout = 8_000
                setRequestProperty(header.first, header.second)
            }
            val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
            stream?.use { it.readBytes() }
        }
    }

    private fun errorMessage(body: String): String? = runCatching {
        JSONObject(body).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }?.take(200)
    }.getOrNull()

    /** Text-to-speech friendly: no markdown, control tokens or wrapping quotes. */
    fun speakable(raw: String): String {
        var s = raw
            .replace(Regex("\\[\\[[A-Z_]+]]"), " ")
            .replace(Regex("[*_`#>|~]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (s.length >= 2 && (s.first() == '"' && s.last() == '"')) s = s.substring(1, s.length - 1).trim()
        if (s.length > 700) {
            val cut = s.lastIndexOfAny(charArrayOf('.', '?', '!', '।'), 700)
            s = if (cut > 200) s.substring(0, cut + 1) else s.substring(0, 700)
        }
        return s
    }

    fun friendlyError(code: String): String = when (code) {
        "NO_KEY" -> "Add your API key first."
        "AUTH" -> "The API key was rejected. Check it and try again."
        "QUOTA" -> "Rate limit or quota reached on this key."
        "MODEL_NOT_FOUND" -> "That model name wasn't found for this key."
        "NETWORK" -> "Couldn't reach the AI service. Check your internet."
        "BLOCKED" -> "The AI service blocked this request."
        "EMPTY" -> "The AI returned an empty reply."
        "HTTP_400" -> "The AI service rejected the request (400)."
        else -> "AI request failed ($code)."
    }
}
