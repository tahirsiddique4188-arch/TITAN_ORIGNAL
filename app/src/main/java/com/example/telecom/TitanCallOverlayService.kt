package com.example.telecom

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.titan.ide.os.TitanCallAssistantEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

/**
 * TitanCallOverlayService v3.8 — Equal-Style Floating Incoming Call HUD
 * Pops cleanly over the incoming call screen with AI Answer, Busy SMS, Take Over,
 * live transcript preview, and 1-tap Bengali Quick-Reply Chips.
 */
class TitanCallOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var rootView: View? = null
    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        private const val EXTRA_CALLER = "extra_caller"

        fun show(context: Context, caller: String) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                return
            }
            try {
                val i = Intent(context, TitanCallOverlayService::class.java).apply {
                    putExtra(EXTRA_CALLER, caller)
                }
                context.startService(i)
            } catch (_: Exception) {}
        }

        fun dismiss(context: Context) {
            try {
                context.stopService(Intent(context, TitanCallOverlayService::class.java))
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val caller = intent?.getStringExtra(EXTRA_CALLER).orEmpty().ifBlank { "Incoming Call" }
        showFloatingHud(caller)
        return START_NOT_STICKY
    }

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun showFloatingHud(caller: String) {
        removeOverlay()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#EE081020"))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.parseColor("#22D3EE"))
            }
        }

        // Header Row
        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleTv = TextView(this).apply {
            text = "TITAN AI CALL ASSISTANT  •  $caller"
            setTextColor(Color.parseColor("#22D3EE"))
            textSize = 12f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeTv = TextView(this).apply {
            text = "✕"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 15f
            setPadding(dp(8), dp(2), dp(4), dp(2))
            setOnClickListener { stopSelf() }
        }
        headerRow.addView(titleTv)
        headerRow.addView(closeTv)
        card.addView(headerRow)

        // Live Status / Transcript Preview
        val statusTv = TextView(this).apply {
            text = "Tap AI Answer to let Titan speak with the caller"
            setTextColor(Color.parseColor("#E2E8F0"))
            textSize = 12.5f
            maxLines = 3
            setPadding(0, dp(6), 0, dp(8))
        }
        card.addView(statusTv)

        // 3 Main Action Buttons: AI Answer | Busy SMS | Take Over
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        fun makeBtn(label: String, bgHex: String, txtHex: String, onClick: () -> Unit): TextView {
            return TextView(this).apply {
                text = label
                setTextColor(Color.parseColor(txtHex))
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor(bgHex))
                    cornerRadius = dp(10).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(3), 0, dp(3), 0)
                }
                setOnClickListener { onClick() }
            }
        }
        btnRow.addView(makeBtn("AI Answer", "#06B6D4", "#04101C") {
            TitanCallAssistantEngine.answerAndTalkWithAi(applicationContext, caller)
        })
        btnRow.addView(makeBtn("Busy SMS", "#F59E0B", "#1C1004") {
            TitanCallAssistantEngine.rejectAndSendBusySms(applicationContext, caller)
        })
        btnRow.addView(makeBtn("Take Over", "#1E293B", "#F8FAFC") {
            TitanCallAssistantEngine.takeOverCall(applicationContext)
        })
        card.addView(btnRow)

        // Equal-Style 1-Tap Quick-Reply Chips
        val chipScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(8), 0, 0)
        }
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        TitanCallAssistantEngine.QUICK_CHIPS.forEach { chip ->
            val tv = TextView(this).apply {
                text = chip
                setTextColor(Color.parseColor("#CBD5E1"))
                textSize = 11f
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#132038"))
                    cornerRadius = dp(14).toFloat()
                    setStroke(dp(1), Color.parseColor("#334155"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, dp(6), 0) }
                setOnClickListener {
                    TitanCallAssistantEngine.speakQuickChip(applicationContext, chip)
                }
            }
            chipRow.addView(tv)
        }
        chipScroll.addView(chipRow)
        card.addView(chipScroll)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(48)
            horizontalMargin = 0.03f
        }

        try {
            windowManager?.addView(card, params)
            rootView = card
        } catch (_: Exception) {}

        // Observe live status & transcript updates
        uiScope.launch {
            launch {
                TitanCallAssistantEngine.liveStatus.collectLatest { st ->
                    val latestLine = TitanCallAssistantEngine.liveTranscript.value.lastOrNull()
                    statusTv.text = if (!latestLine.isNullOrBlank()) "$st\n$latestLine" else st
                }
            }
            launch {
                TitanCallAssistantEngine.liveTranscript.collectLatest { lines ->
                    val last = lines.lastOrNull()
                    if (!last.isNullOrBlank()) {
                        statusTv.text = "${TitanCallAssistantEngine.liveStatus.value}\n$last"
                    }
                }
            }
        }
    }

    private fun removeOverlay() {
        try {
            rootView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        rootView = null
    }

    override fun onDestroy() {
        uiScope.cancel()
        removeOverlay()
        super.onDestroy()
    }
}
