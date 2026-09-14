-keep class dadb.** { *; }
-dontwarn dadb.**
-keep class com.google.zxing.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
