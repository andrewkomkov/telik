package dev.tvhomekit

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast

/** Баннер поверх любого приложения (спека 006, US6). Без права на наложение — системный тост. */
internal object MessageBanner {
    private var current: TextView? = null

    fun show(context: Context, text: String, durationMs: Long) {
        if (!Settings.canDrawOverlays(context)) {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            return
        }
        val wm = context.getSystemService(WindowManager::class.java)
        current?.let { runCatching { wm.removeView(it) } }
        val px = { dp: Float -> TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics).toInt() }
        val view = TextView(context).apply {
            this.text = text
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setPadding(px(24f), px(14f), px(24f), px(14f))
            background = GradientDrawable().apply {
                cornerRadius = px(18f).toFloat()
                setColor(0xE61C1F26.toInt())
            }
            alpha = 0f
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = px(30f)
        }
        runCatching { wm.addView(view, params) }.onFailure {
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
            return
        }
        current = view
        view.animate().alpha(1f).setDuration(250).start()
        view.postDelayed({
            view.animate().alpha(0f).setDuration(250).withEndAction {
                runCatching { wm.removeView(view) }
                if (current === view) current = null
            }.start()
        }, durationMs)
    }
}
