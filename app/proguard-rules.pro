# Media3 and OkHttp ship their own consumer rules; these cover our own reflection surface.

# Room entities are constructed reflectively by generated code.
-keep class app.opentv.data.model.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}

# OkHttp pulls in optional Conscrypt/BouncyCastle providers it does not need on Android.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
