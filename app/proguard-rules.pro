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

    # --- GSON Rules for PT Timer Data Classes ---

    # Keep the AppState class and its members
    -keep class com.billweberx.pt_timer.data.AppState { *; }

    # Keep the TimerSetup class and its members
    -keep class com.billweberx.pt_timer.data.TimerSetup { *; }

    # Keep the SetupConfig class and its members
    -keep class com.billweberx.pt_timer.data.SetupConfig { *; }

    # Keep the SpinnerOption class and its members
    -keep class com.billweberx.pt_timer.data.SpinnerOption { *; }

    # Keep the default, no-argument constructor for GSON's reflection
    -keepclassmembers class * {
        public <init>();
    }
