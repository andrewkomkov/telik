package io.github.henry753951.airplayremote.remote.input

import io.github.henry753951.airplayremote.remote.identity.RemoteIdentityStore

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.InputMethod
import android.media.AudioManager
import android.speech.RecognizerIntent
import android.text.InputType
import android.view.inputmethod.EditorInfo
import androidx.annotation.RequiresApi
import io.github.henry753951.airplayremote.remote.protocol.RemoteNative
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

open class RemoteAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "AtvAccessibility"
        var instance: RemoteAccessibilityService? = null
            private set

        val isServiceRunning: Boolean
            get() = instance != null
    }

    private var screenWidth = 1920
    private var screenHeight = 1080
    private var pointerX = 960f
    private var pointerY = 540f

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        dev.tvhomekit.HomeKitService.start(this)
        menuBindingButton = RemoteIdentityStore.getMenuButtonBinding(this)
        Log.i(TAG, "RemoteAccessibilityService connected, menuBindingButton=$menuBindingButton")

        val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (wm != null) {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            pointerX = screenWidth / 2f
            pointerY = screenHeight / 2f
            Log.i(TAG, "Screen resolution: ${screenWidth}x${screenHeight}")
        }

    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            event.packageName?.let { dev.tvhomekit.HomeKitService.tv?.onForegroundPackage(it.toString()) }
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "RemoteAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "RemoteAccessibilityService destroyed")
    }

    @Volatile
    var menuBindingButton: String = "none"

    fun sendKey(name: String, isMappedToMenu: Boolean = false): Boolean {
        val lowerName = name.lowercase().trim()
        val mappedMenu = isMappedToMenu || (menuBindingButton.isNotBlank() &&
                menuBindingButton != "none" &&
                menuBindingButton.equals(lowerName, ignoreCase = true))

        if (mappedMenu) {
            Log.d(TAG, "Button $name redirected to KEYCODE_MENU")
            return execInputKeyEvent("KEYCODE_MENU") || performGlobalAction(GLOBAL_ACTION_BACK)
        }

        systemKey(lowerName)?.let { return it }

        return when (lowerName) {
            "menu" -> performGlobalAction(GLOBAL_ACTION_BACK)
            "home" -> performGlobalAction(GLOBAL_ACTION_HOME)
            "power" -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            "select" -> {
                val focused = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                    ?: findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
                if (focused != null && focused.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    Log.d(TAG, "Clicked focused node")
                    true
                } else {
                    execInputKeyEvent("KEYCODE_DPAD_CENTER")
                }
            }
            "up" -> navigateDpad(AccessibilityNodeInfo.FOCUS_INPUT, "KEYCODE_DPAD_UP", 0, -200)
            "down" -> navigateDpad(AccessibilityNodeInfo.FOCUS_INPUT, "KEYCODE_DPAD_DOWN", 0, 200)
            "left" -> navigateDpad(AccessibilityNodeInfo.FOCUS_INPUT, "KEYCODE_DPAD_LEFT", -200, 0)
            "right" -> navigateDpad(AccessibilityNodeInfo.FOCUS_INPUT, "KEYCODE_DPAD_RIGHT", 200, 0)
            "play_pause" -> execInputKeyEvent("KEYCODE_MEDIA_PLAY_PAUSE")
            "volume_up" -> execInputKeyEvent("KEYCODE_VOLUME_UP")
            "volume_down" -> execInputKeyEvent("KEYCODE_VOLUME_DOWN")
            "mute" -> execInputKeyEvent("KEYCODE_VOLUME_MUTE")
            "siri" -> execInputKeyEvent("KEYCODE_SEARCH")
            else -> execInputKeyEvent("KEYCODE_${lowerName.uppercase()}")
        }
    }

    /** Нажатия от имени системы: работают без adb и доходят до плееров, которые не принимают команды от приложений. */
    private fun systemKey(name: String): Boolean? {
        val audio = getSystemService(AudioManager::class.java)
        fun volume(direction: Int): Boolean {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            return true
        }
        if (Build.VERSION.SDK_INT >= 33) {
            when (name) {
                "up" -> return performGlobalAction(GLOBAL_ACTION_DPAD_UP)
                "down" -> return performGlobalAction(GLOBAL_ACTION_DPAD_DOWN)
                "left" -> return performGlobalAction(GLOBAL_ACTION_DPAD_LEFT)
                "right" -> return performGlobalAction(GLOBAL_ACTION_DPAD_RIGHT)
                "select" -> return performGlobalAction(GLOBAL_ACTION_DPAD_CENTER)
            }
        }
        if (Build.VERSION.SDK_INT >= 31 && name == "play_pause") {
            return performGlobalAction(GLOBAL_ACTION_KEYCODE_HEADSETHOOK)
        }
        return when (name) {
            "volume_up" -> volume(AudioManager.ADJUST_RAISE)
            "volume_down" -> volume(AudioManager.ADJUST_LOWER)
            "mute" -> volume(AudioManager.ADJUST_TOGGLE_MUTE)
            "siri" -> startVoiceSearch()
            else -> null
        }
    }

    private fun startVoiceSearch(): Boolean {
        for (action in listOf(Intent.ACTION_ASSIST, RecognizerIntent.ACTION_WEB_SEARCH)) {
            try {
                startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: RuntimeException) {
                Log.w(TAG, "Voice search via $action failed: $e")
            }
        }
        return false
    }

    /** Системная мини-клавиатура службы: сообщает о поле ввода и вводит текст с клавиатуры iPhone. */
    @RequiresApi(33)
    private inner class RemoteInputMethod : InputMethod(this@RemoteAccessibilityService) {
        override fun onStartInput(attribute: EditorInfo, restarting: Boolean) {
            editorAction = attribute.imeOptions and EditorInfo.IME_MASK_ACTION
            Log.i(TAG, "Text field: inputType=${attribute.inputType} package=${attribute.packageName}")
            RemoteNative.nativeSetTextFocus(attribute.inputType != InputType.TYPE_NULL, "")
        }

        override fun onFinishInput() {
            Log.i(TAG, "Text field closed")
            RemoteNative.nativeSetTextFocus(false, "")
        }
    }

    private var editorAction = EditorInfo.IME_ACTION_NONE

    @RequiresApi(33)
    override fun onCreateInputMethod(): InputMethod = RemoteInputMethod()

    fun insertText(text: String) {
        if (Build.VERSION.SDK_INT < 33) return
        val connection = inputMethod?.currentInputConnection ?: return
        if (text == "\n") {
            connection.performEditorAction(
                if (editorAction == EditorInfo.IME_ACTION_NONE) EditorInfo.IME_ACTION_DONE else editorAction,
            )
        } else {
            connection.commitText(text, 1, null)
        }
    }

    fun deleteBackward(count: Int) {
        if (Build.VERSION.SDK_INT < 33 || count <= 0) return
        inputMethod?.currentInputConnection?.deleteSurroundingText(count, 0)
    }

    fun replaceText(text: String) {
        if (Build.VERSION.SDK_INT < 33) return
        val connection = inputMethod?.currentInputConnection ?: return
        connection.performContextMenuAction(android.R.id.selectAll)
        connection.commitText(text, 1, null)
    }

    fun handleButton(name: String) {
        sendKey(name)
    }

    private fun navigateDpad(focusType: Int, fallbackKeycode: String, swipeDx: Int, swipeDy: Int): Boolean {
        val focused = findFocus(focusType)
        val direction = when (fallbackKeycode) {
            "KEYCODE_DPAD_UP" -> android.view.View.FOCUS_UP
            "KEYCODE_DPAD_DOWN" -> android.view.View.FOCUS_DOWN
            "KEYCODE_DPAD_LEFT" -> android.view.View.FOCUS_LEFT
            "KEYCODE_DPAD_RIGHT" -> android.view.View.FOCUS_RIGHT
            else -> 0
        }

        if (focused != null && direction != 0) {
            val next = focused.focusSearch(direction)
            if (next != null && next.performAction(AccessibilityNodeInfo.ACTION_FOCUS)) {
                return true
            }
        }

        // Fallback: try executing input keyevent if available
        if (execInputKeyEvent(fallbackKeycode)) {
            return true
        }

        // Alternatively perform swipe gesture
        val cx = screenWidth / 2f
        val cy = screenHeight / 2f
        swipe(cx, cy, cx + swipeDx, cy + swipeDy, 100)
        return true
    }

    private fun execInputKeyEvent(keycode: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("input", "keyevent", keycode))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    fun handleTouch(dx: Double, dy: Double, phase: Int) {
        if (phase == 2) { // Moved
            val sensitivity = 1.5f
            pointerX = (pointerX + dx.toFloat() * sensitivity).coerceIn(0f, screenWidth.toFloat())
            pointerY = (pointerY + dy.toFloat() * sensitivity).coerceIn(0f, screenHeight.toFloat())
        }
    }

    fun handleMouseClick() {
        tap(pointerX, pointerY)
    }

    private fun tap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    private fun swipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }
}
