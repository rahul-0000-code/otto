package com.aicallagent.app.agent

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CallLog
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aicallagent.app.R
import com.aicallagent.app.model.CallConversation
import com.aicallagent.app.ui.MainActivity
import org.json.JSONObject
import java.util.concurrent.Executors

/** Saves a finished AI-handled call, looks up the caller, summarises it and notifies the owner. */
object CallFinisher {
    private const val CHANNEL = "ai_calls"
    private const val TAG = "CallFinisher"
    private val worker = Executors.newSingleThreadExecutor()
    // Right after a call the free Gemini tier is often out of requests for the minute (HTTP 429),
    // so a failed summary is retried after the limit resets.
    private val SUMMARY_RETRY_DELAYS_MS = longArrayOf(0, 20_000, 60_000)
    @Volatile private var backfilled = false

    @JvmStatic
    fun onAiCallFinished(context: Context, messages: List<CallConversation.Message>, startedAt: Long) {
        val app = context.applicationContext
        val transcript = messages.filter { it.content.isNotBlank() }.map { CloudLlm.Turn(it.role, it.content.trim()) }
        if (transcript.none { it.role == "user" }) return
        val endedAt = System.currentTimeMillis()
        worker.execute {
            try {
                Thread.sleep(1500) // The dialer writes the call log entry shortly after hang-up.
                val (number, name) = lookupCaller(app, startedAt)
                var record = CallRecord(endedAt, startedAt, endedAt, number, name, transcript)
                CallRecords.upsert(app, record)
                record = summarise(app, record)
                CallRecords.upsert(app, record)
                notify(app, record)
            } catch (e: Exception) {
                Log.w(TAG, "finish failed: ${e.javaClass.simpleName}")
            }
        }
    }

    private fun lookupCaller(context: Context, startedAt: Long): Pair<String, String> {
        if (context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return "" to ""
        return runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.DATE),
                "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} > ?",
                arrayOf(CallLog.Calls.INCOMING_TYPE.toString(), (startedAt - 5 * 60_000).toString()),
                "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                if (c.moveToFirst()) (c.getString(0) ?: "") to (c.getString(1) ?: "") else "" to ""
            } ?: ("" to "")
        }.getOrDefault("" to "")
    }

    /**
     * Summarises saved calls that ended up without one (the request failed, or the app was closed).
     * Runs once per app start, newest first, a few calls at most.
     */
    @JvmStatic
    fun backfillSummaries(context: Context) {
        if (backfilled) return
        backfilled = true
        val app = context.applicationContext
        worker.execute {
            val missing = CallRecords.all(app)
                .filter { it.summary.isBlank() && it.transcript.any { t -> t.role == "user" } }
                .sortedByDescending { it.startedAt }
                .take(5)
            Log.i(TAG, "SummaryBackfill missing=${missing.size}")
            for (record in missing) {
                val done = summarise(app, record, retry = false)
                if (done.summary.isBlank()) continue
                // The owner may have deleted the call meanwhile; don't bring it back.
                if (CallRecords.get(app, record.id) != null) CallRecords.upsert(app, done)
            }
        }
    }

    private fun summarise(context: Context, record: CallRecord, retry: Boolean = true): CallRecord {
        val config = AgentSettings.load(context)
        if (!AgentSettings.hasCloudBrain(context, config)) {
            Log.i(TAG, "SummarySkipped reason=NO_CLOUD_BRAIN provider=${config.provider}")
            return record
        }
        val transcript = record.transcript.joinToString("\n") {
            (if (it.role == "user") "Caller: " else "${config.assistant}: ") + it.text
        }
        val delays = if (retry) SUMMARY_RETRY_DELAYS_MS else longArrayOf(0)
        for ((attempt, delay) in delays.withIndex()) {
            if (delay > 0) Thread.sleep(delay)
            try {
                val raw = CloudLlm.complete(
                    config.provider, AgentSettings.apiKey(context, config.provider), config.model(),
                    PromptBuilder.summaryPrompt(config), listOf(CloudLlm.Turn("user", transcript)), maxTokens = 300, json = true,
                )
                val json = JSONObject(raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1))
                Log.i(TAG, "Summary ok attempt=${attempt + 1}")
                return record.copy(
                    title = json.optString("title").take(80),
                    summary = json.optString("summary").take(400),
                    urgent = json.optBoolean("urgent"),
                    spam = json.optBoolean("spam"),
                )
            } catch (e: CloudLlm.LlmException) {
                Log.w(TAG, "SummaryError code=${e.code} attempt=${attempt + 1}")
                if (e.code == "NO_KEY" || e.code == "BLOCKED") break
            } catch (e: Exception) {
                Log.w(TAG, "SummaryError code=${e.javaClass.simpleName} attempt=${attempt + 1}")
            }
        }
        return record
    }

    private fun notify(context: Context, record: CallRecord) {
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Calls handled by AI", NotificationManager.IMPORTANCE_HIGH)
        )
        val config = AgentSettings.load(context)
        val open = PendingIntent.getActivity(
            context, record.id.toInt(),
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_CALL_ID, record.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val body = record.summary.ifBlank { record.transcript.firstOrNull { it.role == "user" }?.text ?: "" }
        val title = (if (record.urgent) "Urgent · " else "") + "${config.assistant} took a call from ${record.displayName}"
        manager.notify(
            record.id.toInt(),
            NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_spark)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }
}
