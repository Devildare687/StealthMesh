# Gson reflection targets in the shared bitchat sources (persisted state payloads).
-keep class io.github.devildare687.stealthmesh.favorites.** { *; }
-keep class io.github.devildare687.stealthmesh.services.SeenMessageStore$* { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Kotlin metadata needed by reflection-based serialization.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Tink references JSR-305 annotations not present on Android.
-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
