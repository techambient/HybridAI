# Keep Compose annotations
-keep class androidx.compose.** { *; }
-keep class kotlin.Metadata { *; }

# Remove logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

# Keep Activities
-keep public class * extends androidx.activity.ComponentActivity

# Keep Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}