# Zilbio Proguard & R8 Rules

-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

-keep class com.android.zilbio.domain.model.** { *; }
-keepclassmembers class com.android.zilbio.domain.model.** { *; }

-keep class com.android.zilbio.data.rewards.** { *; }
-keepclassmembers class com.android.zilbio.data.rewards.** { *; }

-keep class com.google.gson.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

-dontwarn okhttp3.**
-dontwarn okio.**
