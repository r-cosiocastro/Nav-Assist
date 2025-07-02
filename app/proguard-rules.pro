-keepclassmembers class com.dasc.auxiliovisionis.data.remote.model.** { *; }
-keep class com.dasc.auxiliovisionis.data.remote.model.** { *; }

-keepattributes Signature
-keepattributes *Annotation*
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep public class com.google.gson.reflect.TypeToken
-keep public class * extends com.google.gson.reflect.TypeToken

-keepnames class kotlinx.serialization.SerialName
        -keepnames class kotlinx.serialization.Serializable
        -keepnames class kotlinx.serialization.internal.** { *; }
        -keepclassmembers class * {
            @kotlinx.serialization.SerialName <fields>;
            @kotlinx.serialization.Serializable <fields>;
            @kotlinx.serialization.Transient <fields>;
            kotlinx.serialization.KSerializer serializer(...);
        }
        -keepclasseswithmembers class * {
            public static final kotlinx.serialization.KSerializer Companion;
        }