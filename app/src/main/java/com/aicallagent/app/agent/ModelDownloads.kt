package com.aicallagent.app.agent

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aicallagent.app.CallAccessibilityService
import com.aicallagent.app.R
import com.aicallagent.app.local.LocalModelRuntime
import com.aicallagent.app.ui.MainActivity
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/** On-device models the app can install with one tap. All are ungated, Apache-2.0, LiteRT-LM builds. */
data class CatalogModel(
    val id: String,
    val title: String,
    val repo: String,
    val file: String,
    val approxBytes: Long,
    val blurb: String,
    /** Total device RAM needed, in GB as Android reports it (an "8 GB" phone reports ~7.3). */
    val minRamGb: Double,
)

object ModelCatalog {
    val models = listOf(
        CatalogModel(
            "gemma-4-e2b", "Gemma 4 E2B", "litert-community/gemma-4-E2B-it-litert-lm", "gemma-4-E2B-it.litertlm",
            2_590_000_000L, "Google's phone-sized Gemma 4 · 140+ languages incl. हिंदी & मराठी", 7.0,
        ),
        CatalogModel(
            "qwen-3.5-0.8b", "Qwen 3.5 0.8B", "litert-community/Qwen3.5-0.8B", "Qwen3.5-0.8B_int8.litertlm",
            960_000_000L, "Lightest and fastest · basic Hindi", 0.0,
        ),
        CatalogModel(
            "gemma-4-e4b", "Gemma 4 E4B", "litert-community/gemma-4-E4B-it-litert-lm", "gemma-4-E4B-it.litertlm",
            3_660_000_000L, "Smarter, ~2× slower · for 12 GB+ phones", 11.0,
        ),
    )

    fun byId(id: String?) = models.firstOrNull { it.id == id }

    fun ramGb(context: Context): Double {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return info.totalMem / 1e9
    }

    /** Models this phone can run, best fit first. */
    fun forDevice(context: Context): List<CatalogModel> {
        val ram = ramGb(context)
        val fits = models.filter { ram >= it.minRamGb }
        // The most capable model that fits leads; lighter options follow, smallest first.
        val best = fits.maxByOrNull { it.minRamGb }
        return listOfNotNull(best) + fits.filter { it != best }.sortedBy { it.approxBytes }
    }
}

object ModelDownloads {
    enum class Phase { IDLE, CHECKING, DOWNLOADING, VERIFYING, DONE, FAILED, CANCELLED }

    data class State(
        val modelId: String = "",
        val phase: Phase = Phase.IDLE,
        val done: Long = 0,
        val total: Long = 0,
        val bytesPerSec: Long = 0,
        val error: String = "",
    ) {
        val active get() = phase == Phase.CHECKING || phase == Phase.DOWNLOADING || phase == Phase.VERIFYING
        val fraction get() = if (total > 0) (done.toDouble() / total).toFloat().coerceIn(0f, 1f) else 0f
    }

    private const val PREFS = "local_model_meta"
    private const val FREE_HEADROOM = 1_000_000_000L
    @Volatile var state = State()
        private set
    @Volatile private var cancelled = false

    fun installedId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("installed_id", null)

    fun isMetered(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return true
        return !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    fun start(context: Context, model: CatalogModel) {
        if (state.active) return
        cancelled = false
        state = State(model.id, Phase.CHECKING)
        context.startForegroundService(Intent(context, ModelDownloadService::class.java).putExtra(ModelDownloadService.EXTRA_MODEL, model.id))
    }

    fun cancel() { cancelled = true }

    fun friendlyError(code: String): String = when (code) {
        "STORAGE" -> "Not enough free storage. Free up space and retry."
        "NETWORK" -> "Download kept failing. Check your connection and retry — it resumes where it stopped."
        "HASH_MISMATCH" -> "The file didn't match its checksum, so it was discarded. Retry."
        "GATED" -> "This model now requires a Hugging Face login."
        "MODEL_SIZE" -> "Model is larger than the 4 GB limit."
        "BUSY" -> "The on-device brain is busy. Retry in a moment."
        "LOAD_FAILED" -> "Model downloaded but couldn't start on this phone."
        else -> "Download failed ($code)."
    }

    /** Runs on the service's worker thread. */
    internal fun run(context: Context, model: CatalogModel, progress: (State) -> Unit) {
        fun set(s: State) { state = s; progress(s) }
        try {
            set(State(model.id, Phase.CHECKING))
            val meta = JSONObject(httpGetText("https://huggingface.co/api/models/${model.repo}?blobs=true"))
            if (meta.opt("gated").let { it != null && it != false && it.toString() != "false" }) throw IOException("GATED")
            val siblings = meta.getJSONArray("siblings")
            var size = -1L
            var sha = ""
            for (i in 0 until siblings.length()) {
                val f = siblings.getJSONObject(i)
                if (f.optString("rfilename") == model.file) {
                    size = f.optLong("size", -1)
                    sha = f.optJSONObject("lfs")?.optString("sha256").orEmpty()
                }
            }
            if (size <= 0 || !sha.matches(Regex("[0-9a-f]{64}"))) throw IOException("METADATA")
            if (size > com.aicallagent.app.local.LocalModelStore.MAX_BYTES) throw IOException("MODEL_SIZE")

            val runtime = LocalModelRuntime.get(context)
            val dir = runtime.downloadDirectory()
            val part = File(dir, "download-$sha.part")
            // Keep only one model: drop leftovers of other downloads.
            dir.listFiles()?.filter { it.name.startsWith("download-") && it.name != part.name }?.forEach { it.delete() }
            if (dir.usableSpace + part.length() < size + FREE_HEADROOM) throw IOException("STORAGE")

            var attempt = 0
            while (true) {
                try {
                    download(model, part, size) { done, speed -> set(State(model.id, Phase.DOWNLOADING, done, size, speed)) }
                    break
                } catch (e: IOException) {
                    if (cancelled) throw IOException("CANCELLED")
                    if (++attempt > 6) throw IOException("NETWORK")
                    Log.w(TAG, "download retry=$attempt cause=${e.message}")
                    Thread.sleep(minOf(30_000L, 2_000L shl attempt))
                }
            }

            set(State(model.id, Phase.VERIFYING, size, size))
            // The engine can't swap models while armed with the on-device brain.
            onMain { if (CallAccessibilityService.isAutoReplyEnabled()) CallAccessibilityService.setAutoReplyEnabled(false) }
            val done = CountDownLatch(1)
            var error = ""
            var started = false
            for (i in 0 until 100) {
                started = runtime.adoptDownloaded(part, sha) { _, e -> error = e; done.countDown() }
                if (started) break
                Thread.sleep(200)
            }
            if (!started) throw IOException("BUSY")
            done.await(10, TimeUnit.MINUTES)
            if (error.isNotEmpty()) throw IOException(error)

            runtime.preferGpu(File("/vendor/lib64/libOpenCL.so").exists() || File("/system/vendor/lib64/libOpenCL.so").exists())
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("installed_id", model.id).putString("installed_sha", sha).apply()
            set(State(model.id, Phase.DONE, size, size))
            onMain { CallAccessibilityService.applySettings() }
        } catch (e: Exception) {
            val code = if (cancelled) "CANCELLED" else (e as? IOException)?.message ?: e.javaClass.simpleName
            Log.w(TAG, "download failed code=$code")
            set(State(model.id, if (code == "CANCELLED") Phase.CANCELLED else Phase.FAILED, state.done, state.total, 0, code))
            onMain { CallAccessibilityService.applySettings() }
        }
    }

    /** Resumable: continues a partial file with an HTTP Range request. */
    private fun download(model: CatalogModel, part: File, size: Long, progress: (Long, Long) -> Unit) {
        var have = part.length()
        if (have > size) { part.delete(); have = 0 }
        if (have == size) return
        val conn = (URL("https://huggingface.co/${model.repo}/resolve/main/${model.file}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = conn.responseCode
            if (code == 416 && have == size) return
            if (code != 200 && code != 206) throw IOException("HTTP_$code")
            val append = code == 206
            if (!append) have = 0
            FileOutputStream(part, append).use { out ->
                conn.inputStream.use { input ->
                    val buffer = ByteArray(256 * 1024)
                    var windowStart = System.nanoTime()
                    var windowBytes = 0L
                    var speed = 0L
                    var lastReport = 0L
                    while (true) {
                        if (cancelled) throw IOException("CANCELLED")
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        have += n
                        windowBytes += n
                        val now = System.nanoTime()
                        if (now - windowStart > 1_000_000_000L) {
                            speed = windowBytes * 1_000_000_000L / (now - windowStart)
                            windowStart = now; windowBytes = 0
                        }
                        if (now - lastReport > 300_000_000L) { lastReport = now; progress(have, speed) }
                    }
                    out.fd.sync()
                }
            }
            progress(have, 0)
            if (have != size) throw IOException("SHORT")
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGetText(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 20_000 }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP_${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun onMain(block: () -> Unit) {
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { try { block() } finally { latch.countDown() } }
        latch.await(3, TimeUnit.SECONDS)
    }

    private const val TAG = "ModelDownload"
}

/** Foreground service so a multi-GB download survives leaving the app. */
class ModelDownloadService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { ModelDownloads.cancel(); return START_NOT_STICKY }
        val model = ModelCatalog.byId(intent?.getStringExtra(EXTRA_MODEL)) ?: run { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        startForeground(NOTIFICATION, build(model, ModelDownloads.state), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        if (running) return START_NOT_STICKY
        running = true
        thread(name = "model-download") {
            var last = 0L
            ModelDownloads.run(applicationContext, model) { s ->
                val now = System.currentTimeMillis()
                if (now - last > 1000 || !s.active) { last = now; manager.notify(NOTIFICATION, build(model, s)) }
            }
            running = false
            stopForeground(STOP_FOREGROUND_DETACH)
            val end = ModelDownloads.state
            if (end.phase == ModelDownloads.Phase.CANCELLED) manager.cancel(NOTIFICATION) else manager.notify(NOTIFICATION, build(model, end))
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun build(model: CatalogModel, s: ModelDownloads.State): Notification {
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_spark)
            .setContentIntent(open)
            .setOnlyAlertOnce(true)
        when (s.phase) {
            ModelDownloads.Phase.DONE -> b.setContentTitle("${model.title} installed").setContentText("Otto can now answer calls offline.").setAutoCancel(true)
            ModelDownloads.Phase.FAILED -> b.setContentTitle("${model.title} download failed").setContentText(ModelDownloads.friendlyError(s.error)).setAutoCancel(true)
            ModelDownloads.Phase.VERIFYING -> b.setContentTitle("Verifying ${model.title}").setProgress(0, 0, true).setOngoing(true)
            else -> {
                val pct = (s.fraction * 100).toInt()
                val cancel = PendingIntent.getService(this, 2, Intent(this, ModelDownloadService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_IMMUTABLE)
                b.setContentTitle("Downloading ${model.title}")
                    .setContentText(if (s.total > 0) "$pct% · ${gb(s.done)} of ${gb(s.total)} GB" else "Starting…")
                    .setProgress(100, pct, s.total <= 0)
                    .setOngoing(true)
                    .addAction(0, "Cancel", cancel)
            }
        }
        return b.build()
    }

    companion object {
        const val EXTRA_MODEL = "model"
        const val ACTION_CANCEL = "cancel"
        private const val CHANNEL = "model_downloads"
        private const val NOTIFICATION = 7001
        @Volatile private var running = false
        fun gb(bytes: Long) = "%.2f".format(bytes / 1e9)
    }
}
