package com.aicallagent.app.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.aicallagent.app.R

/**
 * Neo Noir cards drawn by the accessibility service over Samsung's call screen.
 * TYPE_ACCESSIBILITY_OVERLAY needs no extra permission and shows over the in-call UI.
 */
class CallOverlay(private val service: AccessibilityService) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val grotesk: Typeface? = runCatching { ResourcesCompat.getFont(service, R.font.space_grotesk) }.getOrNull()
    private val mono: Typeface? = runCatching { ResourcesCompat.getFont(service, R.font.jetbrains_mono) }.getOrNull()
    private var view: View? = null
    private var kind = Kind.NONE
    private var timer: TextView? = null
    private var bar: View? = null
    private var title: TextView? = null
    private var autoTotalSec = 0

    private enum class Kind { NONE, INCOMING, ACTIVE }

    val isIncomingShown: Boolean get() = kind == Kind.INCOMING

    /** Hand-over card. [autoInSec] > 0 adds the auto-answer countdown. */
    fun showIncoming(name: String, autoInSec: Int, onAnswer: Runnable, onDismiss: Runnable) {
        if (kind == Kind.INCOMING) return
        hide()
        autoTotalSec = autoInSec
        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(10))
            background = rounded(0xF2101010.toInt(), dp(22), stroke = 0x33FFFFFF)
            elevation = dp(16).toFloat()
        }
        card.addView(mono("${name.uppercase()} CAN TAKE THIS", 0xFFF2F2F2.toInt()))

        // v3: the auto-answer countdown lives inside the button. The bright fill drains from right
        // to left; in "Ask me" mode the button is simply solid lime.
        val button = FrameLayout(service).apply {
            background = rounded(if (autoInSec > 0) 0x8CC8FF2E.toInt() else LIME, dp(16))
            clipToOutline = true
            elevation = dp(6).toFloat()
            contentDescription = "Let $name answer"
            setOnClickListener { onAnswer.run() }
        }
        bar = View(service).apply { setBackgroundColor(LIME) }
        button.addView(bar, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        val label = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), 0, dp(18), 0)
        }
        title = TextView(service).apply {
            text = "✦  Let $name answer"
            setTextColor(INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            typeface = Typeface.create(grotesk, Typeface.BOLD)
        }
        label.addView(title, LinearLayout.LayoutParams(0, -2, 1f))
        timer = mono(if (autoInSec > 0) "AUTO · $autoInSec" else "", INK)
        label.addView(timer)
        button.addView(label, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        card.addView(button, LinearLayout.LayoutParams(-1, dp(60)).apply { topMargin = dp(14) })

        val skip = TextView(service).apply {
            text = "Not this time"
            gravity = Gravity.CENTER
            setTextColor(0xFFA8A8A8.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
            typeface = grotesk
            setOnClickListener { onDismiss.run() }
        }
        card.addView(skip, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(4) })

        val width = service.resources.displayMetrics.widthPixels - dp(40)
        attach(card, width, yFraction = 0.46f)
        kind = Kind.INCOMING
        updateCountdown(autoInSec)
    }

    fun updateCountdown(seconds: Int) {
        if (kind != Kind.INCOMING || autoTotalSec <= 0) return
        val left = seconds.coerceAtLeast(0)
        timer?.text = "AUTO · $left"
        val track = bar?.parent as? View ?: return
        track.post {
            val remaining = left.toFloat() / autoTotalSec
            bar?.layoutParams = (bar?.layoutParams as FrameLayout.LayoutParams).apply { width = (track.width * remaining).toInt() }
            bar?.requestLayout()
        }
    }

    fun showConnecting(name: String) {
        if (kind != Kind.INCOMING) return
        timer?.text = ""
        title?.text = "$name is picking up…"
        bar?.layoutParams = (bar?.layoutParams as? FrameLayout.LayoutParams)?.apply { width = FrameLayout.LayoutParams.MATCH_PARENT }
        bar?.requestLayout()
    }

    /** Compact "on the call" pill with Stop AI so the owner can take over. */
    fun showActive(name: String, onStop: Runnable) {
        if (kind == Kind.ACTIVE) return
        hide()
        val pill = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(8), dp(8))
            background = rounded(0xF2101010.toInt(), dp(99), stroke = 0x33FFFFFF)
            elevation = dp(12).toFloat()
        }
        val dot = View(service).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(LIME) } }
        pill.addView(dot, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(10) })
        pill.addView(mono("${name.uppercase()} ON CALL", 0xFFF2F2F2.toInt()))
        val stop = TextView(service).apply {
            text = "Stop AI"
            setTextColor(RED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            typeface = Typeface.create(grotesk, Typeface.BOLD)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = rounded(0x00000000, dp(99), stroke = RED)
            setOnClickListener { onStop.run() }
        }
        pill.addView(stop, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(14) })
        attach(pill, WindowManager.LayoutParams.WRAP_CONTENT, yFraction = 0.105f)
        kind = Kind.ACTIVE
    }

    fun hide() {
        view?.let { runCatching { wm.removeViewImmediate(it) } }
        view = null
        timer = null; bar = null; title = null
        kind = Kind.NONE
    }

    private fun attach(v: View, width: Int, yFraction: Float) {
        val params = WindowManager.LayoutParams(
            width,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = (service.resources.displayMetrics.heightPixels * yFraction).toInt()
        }
        runCatching { wm.addView(v, params); view = v }
    }

    private fun mono(text: String, color: Int) = TextView(service).apply {
        this.text = text
        setTextColor(color)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
        letterSpacing = 0.08f
        typeface = mono
    }

    private fun rounded(color: Int, radius: Int, stroke: Int = 0) = GradientDrawable().apply {
        cornerRadius = radius.toFloat()
        setColor(color)
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun dp(v: Int) = (v * service.resources.displayMetrics.density).toInt()

    private companion object {
        val LIME = Color.parseColor("#C8FF2E")
        val INK = Color.parseColor("#0A0A0A")
        val RED = Color.parseColor("#FF4D5E")
    }
}
