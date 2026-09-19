# Add project-specific ProGuard rules here.
# Suppress warnings about missing optional APIs (used by reflection in some
# AndroidX libraries we depend on).
-keep class com.glmwebshell.**.model.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
