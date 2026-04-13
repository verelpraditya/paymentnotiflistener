# Add project specific ProGuard rules here.

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Keep Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.notiflistener.app.model.** { *; }
-keep class com.google.gson.** { *; }

# Keep Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Keep notification service
-keep class com.notiflistener.app.service.NotifListenerService { *; }
