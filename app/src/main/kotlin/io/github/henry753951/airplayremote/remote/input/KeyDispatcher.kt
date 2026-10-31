package io.github.henry753951.airplayremote.remote.input

import io.github.henry753951.airplayremote.remote.identity.RemoteIdentityStore

import android.content.Context
import android.util.Log

enum class InjectionMode(val key: String, val displayName: String) {
    AUTO("auto", "自动自适应 (硬件 -> ADB -> 无障碍)"),
    EVENT("event", "仅硬件事件 (/dev/input)"),
    ADB("adb", "仅本地 ADB (127.0.0.1:5555)"),
    A11Y("a11y", "仅无障碍服务 (Accessibility)");

    companion object {
        fun fromKey(key: String): InjectionMode {
            return values().find { it.key.equals(key, ignoreCase = true) } ?: AUTO
        }
    }
}

object KeyDispatcher {
    private const val TAG = "KeyDispatcher"

    @Volatile
    var currentMode: InjectionMode = InjectionMode.AUTO

    @Volatile
    var menuBindingButton: String = "none"

    @Volatile
    var lastUsedEngine: String = "未执行"
        private set

    fun init(context: Context) {
        val savedModeKey = RemoteIdentityStore.getInjectionMode(context)
        currentMode = InjectionMode.fromKey(savedModeKey)
        menuBindingButton = RemoteIdentityStore.getMenuButtonBinding(context)
        Log.i(TAG, "KeyDispatcher initialized: mode=$currentMode, menuBinding=$menuBindingButton")

        if (currentMode == InjectionMode.ADB) {
            LocalAdbDriver.init(context)
        }
    }

    fun setMode(context: Context, mode: InjectionMode) {
        currentMode = mode
        RemoteIdentityStore.setInjectionMode(context, mode.key)
        Log.i(TAG, "InjectionMode updated to: $mode")
        if (mode == InjectionMode.ADB) {
            LocalAdbDriver.init(context)
        }
    }

    fun dispatchButton(name: String): String {
        val lowerName = name.lowercase().trim()
        val isMappedToMenu = menuBindingButton.isNotBlank() &&
                menuBindingButton != "none" &&
                menuBindingButton.equals(lowerName, ignoreCase = true)

        val mode = currentMode
        val executedEngine = InputRouter.route(
            mode,
            hardware = {
                (mode != InjectionMode.AUTO || HardwareInputController.isHardwareAvailable) &&
                    HardwareInputController.sendKey(lowerName, isMappedToMenu)
            },
            adb = { LocalAdbDriver.sendKey(lowerName, isMappedToMenu) },
            accessibility = { RemoteAccessibilityService.instance?.sendKey(lowerName, isMappedToMenu) == true },
        )

        lastUsedEngine = executedEngine
        val display = if (isMappedToMenu) "$name -> MENU" else name
        Log.i(TAG, "dispatchButton: $display via [$executedEngine]")
        return executedEngine
    }
}
