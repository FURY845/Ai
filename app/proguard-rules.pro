# ProGuard / R8 Rules for Android Production Builds

# Preserve line numbers and source files for readable stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# Room Database Rules
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class * extends androidx.room.RoomOpenHelper

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Keep Data Models & Entities in com.example
-keep class com.example.data.local.** { *; }
-keep class com.example.engine.search.** { *; }
-keep class com.example.engine.quiz.** { *; }
-keep class com.example.engine.arabic.** { *; }

# OkHttp & Moshi (if used)
-dontwarn okhttp3.**
-dontwarn okio.**
