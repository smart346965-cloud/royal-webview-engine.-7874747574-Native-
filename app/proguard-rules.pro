# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================================
# AndroidX WorkManager / Startup — R8 Runtime Protection
# ============================================================

-keep class androidx.work.** { *; }
-keep class androidx.startup.** { *; }

-keep class androidx.work.impl.WorkDatabase { *; }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

-keep class androidx.work.impl.WorkManagerInitializer { *; }

-keepclassmembers class * extends androidx.work.ListenableWorker {
    <init>(...);
}

-keepclassmembers class * extends androidx.work.Worker {
    <init>(...);
}

-keepnames class androidx.work.**
-keepnames class androidx.startup.**
