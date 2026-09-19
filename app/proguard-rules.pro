# Add project specific ProGuard rules here.

# Keep serializable models (Room + kotlinx.serialization).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.glmwebshell.**$$serializer { *; }
-keepclassmembers class com.glmwebshell.** {
    *** Companion;
}
-keepclasseswithmembers class com.glmwebshell.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# BouncyCastle (Ed25519 verifier)
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**

# AndroidX WebKit
-keep class androidx.webkit.** { *; }
