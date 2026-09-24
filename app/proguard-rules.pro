# Shizuku instantiates the daemon by class name, via reflection, in a separate process.
# Nothing in the app calls its constructor directly, so R8 would otherwise strip it.
-keep class com.mwilky.hilight.plus.core.HiLightDaemonService { *; }

# The AIDL interface is the binder contract between the app and the daemon. Keep it intact
# so both sides agree on method ordering and the interface descriptor.
-keep class com.mwilky.hilight.plus.core.IHiLightService { *; }
-keep class com.mwilky.hilight.plus.core.IHiLightService$* { *; }
-keep class com.mwilky.hilight.plus.core.ILogSink { *; }
-keep class com.mwilky.hilight.plus.core.ILogSink$* { *; }

# Rule JSON stores enum names (PatternMode.valueOf etc.); keep enum names readable so saved
# settings survive across releases with different obfuscation maps.
-keepnames enum com.mwilky.hilight.plus.**

# Keep line numbers in stack traces from Play Console crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
