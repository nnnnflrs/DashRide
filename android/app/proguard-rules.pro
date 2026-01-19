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

# Capacitor plugins - keep all plugin classes and methods
-keep class com.getcapacitor.** { *; }
-keep class com.motorcycle.dashride.ph.** { *; }

# Google Play Services and Google Maps
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# Keep native methods (required for JNI calls)
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep WebView JavaScript interface
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# AndroidX and Support Library
-keep class androidx.** { *; }
-dontwarn androidx.**
