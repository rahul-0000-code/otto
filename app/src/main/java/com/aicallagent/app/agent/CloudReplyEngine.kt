package com.aicallagent.app.agent

import android.content.Context
import android.util.Log
import com.aicallagent.app.local.ReplyEngine
import com.aicallagent.app.local.ReplyRequest
import java.util.concurrent.Executors

/** Cloud implementation of the call reply engine. One request at a time, owned by the accessibility service. */
class CloudReplyEngine(context: Context) : ReplyEngine {
    private val app = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor()
    // Speculative replies get their own thread so a cancelled one never delays a real request.
    private val speculator = Executors.newSingleThreadExecutor()
    @Volatile private var owner: Any? = null
    @Volatile private var active: CloudLlm.Handle? = null
    private var speculation: Speculation? = null // guarded by this

    private class Speculation(val key: String, val handle: CloudLlm.Handle) {
        var finished = false
        var answer = ""
        var error = ""
        var waiter: ReplyEngine.Callback? = null
    }

    fun claim(owner: Any) { this.owner = owner }

    override fun isReady(owner: Any?): Boolean =
        owner != null && owner === this.owner && AgentSettings.hasCloudBrain(app)

    override fun generate(owner: Any?, request: ReplyRequest, callback: ReplyEngine.Callback): Boolean {
        if (!isReady(owner) || active != null) return false
        val spec = takeSpeculation()
        if (spec != null) {
            if (spec.key == keyOf(request) && adopt(spec, callback)) return true
            spec.handle.cancel()
        }
        val handle = CloudLlm.Handle()
        active = handle
        val config = AgentSettings.load(app)
        worker.execute {
            val (answer, error) = call(config, request, handle, speculative = false)
            if (active === handle) active = null
            callback.complete(answer, error)
        }
        return true
    }

    /**
     * Starts the reply to a turn the caller may not have finished, so the answer is ready when the
     * turn-end silence ends. Nothing is spoken early: [generate] uses the result only for the exact
     * same conversation, and a changed turn cancels it and starts over.
     */
    fun speculate(owner: Any?, request: ReplyRequest) {
        if (!isReady(owner) || active != null) return
        val key = keyOf(request)
        val spec = Speculation(key, CloudLlm.Handle())
        val previous = synchronized(this) {
            if (speculation?.key == key) return
            speculation.also { speculation = spec }
        }
        previous?.handle?.cancel()
        val config = AgentSettings.load(app)
        speculator.execute {
            val (answer, error) =
                if (spec.handle.cancelled) "" to "CANCELLED" else call(config, request, spec.handle, speculative = true)
            val waiter = synchronized(spec) {
                spec.answer = answer
                spec.error = error
                spec.finished = true
                if (spec.waiter != null && active === spec.handle) active = null
                spec.waiter
            }
            waiter?.complete(answer, error)
        }
    }

    /** Cancels any speculative reply, e.g. when the call ends. */
    fun dropSpeculation() {
        takeSpeculation()?.handle?.cancel()
    }

    private fun takeSpeculation(): Speculation? = synchronized(this) { speculation.also { speculation = null } }

    /** Hands a speculative reply to the real request. A failed or cancelled one is asked again instead. */
    private fun adopt(spec: Speculation, callback: ReplyEngine.Callback): Boolean {
        val ready = synchronized(spec) {
            if (spec.finished && spec.error.isNotEmpty()) return false
            if (!spec.finished) {
                spec.waiter = callback
                active = spec.handle
            }
            spec.finished
        }
        Log.i(TAG, "CloudReply speculative=ADOPTED ready=$ready")
        if (ready) callback.complete(spec.answer, "")
        return true
    }

    private fun call(config: AgentConfig, request: ReplyRequest, handle: CloudLlm.Handle, speculative: Boolean): Pair<String, String> {
        // Keystore decryption stays off the main thread, which also draws the call overlay.
        val key = AgentSettings.apiKey(app, config.provider)
        val turns = request.history.map { CloudLlm.Turn(it.role, it.content) }
        val started = System.nanoTime()
        var answer = ""
        var error = ""
        try {
            answer = CloudLlm.speakable(
                CloudLlm.complete(config.provider, key, config.model(), request.system, turns, handle = handle)
            )
            if (answer.isEmpty()) error = "INVALID_RESPONSE"
        } catch (e: CloudLlm.LlmException) {
            error = if (e.code == "CANCELLED" || handle.cancelled) "CANCELLED" else "CLOUD_" + e.code
        } catch (e: Exception) {
            error = if (handle.cancelled) "CANCELLED" else "CLOUD_FAILED"
        }
        Log.i(TAG, "CloudReply provider=${config.provider} ms=${(System.nanoTime() - started) / 1_000_000} " +
            "error=${error.ifEmpty { "NONE" }}" + if (speculative) " speculative=true" else "")
        return (if (error.isEmpty()) answer else "") to error
    }

    /** Called when a call starts ringing so the first reply doesn't pay for connection setup. */
    fun warmUp() {
        val config = AgentSettings.load(app)
        if (config.provider == Provider.LOCAL) return
        worker.execute { CloudLlm.warmUp(config.provider, AgentSettings.apiKey(app, config.provider), config.model()) }
    }

    override fun cancel(owner: Any?) {
        if (owner === this.owner) active?.cancel()
    }

    override fun release(owner: Any?) {
        if (owner !== this.owner) return
        active?.cancel()
        dropSpeculation()
        this.owner = null
    }

    private fun keyOf(request: ReplyRequest): String =
        request.system + "\u0000" + request.history.joinToString("\u0001") { it.role + "\u0002" + it.content }

    companion object { private const val TAG = "CloudReply" }
}
