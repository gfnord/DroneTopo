# DJI MSDK V5 native libraries must not be obfuscated. Mirror the V5 sample proguard rules.
-dontwarn com.dji.**
-dontwarn dji.**
-dontwarn com.cySdkyc.**

-keep class com.dji.** { *; }
-keep class dji.** { *; }
-keep class com.cySdkyc.** { *; }

# Keep KeyManager / KeyTools / FlightControllerKey reflective access
-keep class dji.sdk.keyvalue.** { *; }
-keep class dji.v5.** { *; }

# OkHttp (already shipped with consumer rules but reinforce critical ones)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**

# Keep our BuildConfig + data models
-keep class com.infinitii.m4td.gps.data.** { *; }
-keepclassmembers class com.infinitii.m4td.gps.** {
    public *;
}
