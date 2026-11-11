# Keep Room entities
-keep class com.gallery.app.data.database.** { *; }

# Media3
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-keep class coil.** { *; }

# Keep data classes
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
