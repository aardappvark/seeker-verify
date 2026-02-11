# Seeker Verify - ProGuard Rules

# Keep kotlinx.serialization models used for JSON-RPC
-keepclassmembers class com.midmightbit.sgt.** {
    <fields>;
    <init>(...);
}

# Keep serializer companion objects
-keepclassmembers class com.midmightbit.sgt.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
