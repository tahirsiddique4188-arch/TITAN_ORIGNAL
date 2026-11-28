# ==============================================================================
# TITAN IDE v3.0 — R8 FULL-MODE MINIFICATION, SHRINKING & OBFUSCATION SHIELD
# ==============================================================================
-allowaccessmodification
-overloadaggressively
-repackageclasses 'o'
-optimizationpasses 5

# Strip original file names & debug attributes so decompilers cannot read structure
-renamesourcefileattribute T
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Strip all debug/verbose/info Log calls in Release APK
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Keep ONLY Android Manifest Entry Points (Activities, Services, Providers)
-keep public class com.example.MainActivity { *; }
-keep public class com.example.ui.AssistantActivity { *; }
-keep public class com.example.services.TitanAccessibilityService { *; }
-keep public class com.example.services.TitanNotificationListenerService { *; }
-keep public class com.example.services.TitanFloatingBubbleService { *; }
-keep public class com.example.voice.TitanVoiceInteractionService { *; }
-keep public class com.example.voice.TitanVoiceInteractionSessionService { *; }
-keep public class com.example.voice.TitanWakeWordService { *; }
-keep public class com.example.telecom.TitanCallScreeningService { *; }
-keep public class com.titan.ide.voice.TitanCallForegroundService { *; }
-keep public class androidx.core.content.FileProvider { *; }

# Keep Room Database & Entities (required by KSP generated impl)
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep class com.example.data.local.db.** { *; }
-dontwarn androidx.room.paging.**

# Keep Jetpack Compose, OkHttp, Coil, Media3, Coroutines safe under R8
-keepclassmembers class * extends androidx.compose.ui.Modifier { *; }
-dontwarn androidx.compose.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil.**
-dontwarn org.jsoup.**
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.media3.**

# Keep Sherpa-ONNX Offline Whisper JNI bindings safe under R8
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

-keep public class com.example.telecom.TitanCallOverlayService { *; }
-keep public class com.example.telecom.TitanIncomingCallReceiver { *; }
