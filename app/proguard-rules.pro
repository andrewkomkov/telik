# Keep JNI callback interface (called from native code)
-keep class io.github.henry753951.airplayremote.airplay.bridge.RaopCallbackHandler { *; }
-keep class * implements io.github.henry753951.airplayremote.airplay.bridge.RaopCallbackHandler { *; }
-keep class io.github.henry753951.airplayremote.airplay.bridge.LogListener { *; }
-keep class * implements io.github.henry753951.airplayremote.airplay.bridge.LogListener { *; }

# Started by app_process through the adb shell
-keep class io.github.henry753951.airplayremote.headphones.HeadphonesTool { public static void main(java.lang.String[]); }

# Keep NativeBridge native methods
-keep class io.github.henry753951.airplayremote.airplay.bridge.NativeBridge { *; }

-keep class io.github.henry753951.airplayremote.remote.protocol.RemoteNative** { *; }
-keep class * implements io.github.henry753951.airplayremote.remote.protocol.RemoteNative$Callback { *; }
-keep class io.github.henry753951.airplayremote.remote.input.PersistentInput { *; }
