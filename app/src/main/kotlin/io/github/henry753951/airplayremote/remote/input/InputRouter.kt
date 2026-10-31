package io.github.henry753951.airplayremote.remote.input

/** Input strategies are lazy: a successful backend must not replay the key through a fallback. */
internal object InputRouter {
    fun route(mode: InjectionMode, hardware: () -> Boolean, adb: () -> Boolean,
              accessibility: () -> Boolean): String = when (mode) {
        InjectionMode.AUTO -> when {
            // После перезагрузки беспроводная отладка выключается, поэтому adb не может быть основным путём.
            accessibility() -> "无障碍服务 (Accessibility)"
            hardware() -> "硬件事件驱动 (/dev/input)"
            else -> "分发失败 (无可用通道)"
        }
        InjectionMode.EVENT -> if (hardware()) "硬件事件驱动" else "硬件驱动失败 (无权限或设备不存在)"
        InjectionMode.ADB -> if (adb()) "本地 ADB" else "本地 ADB 失败 (端口未连接或已关闭)"
        InjectionMode.A11Y -> if (accessibility()) "无障碍服务" else "无障碍服务失败 (服务未开启)"
    }
}
