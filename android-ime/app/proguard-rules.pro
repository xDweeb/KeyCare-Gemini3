# KeyCare IME ProGuard Rules
# ===========================

# Keep the IME service class
-keep class com.keycare.ime.KeyCareIME { *; }
-keep class com.keycare.ime.** { *; }

# Keep API client classes
-keep class com.keycare.ime.api.** { *; }

# Keep all activities
-keep class * extends android.app.Activity

# Keep InputMethodService
-keep class * extends android.inputmethodservice.InputMethodService

# Keep JSON parsing (org.json)
-keep class org.json.** { *; }
-dontwarn org.json.**

# Keep Android components
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver

# Preserve annotations
-keepattributes *Annotation*

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# Don't warn about missing classes
-dontwarn javax.**
-dontwarn java.awt.**
-dontwarn org.slf4j.**
