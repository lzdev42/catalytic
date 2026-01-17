# proguard-rules.pro

# Ignore warnings to allow build to proceed despite missing Android/Optional classes
-ignorewarnings

# Disable optimization to prevent build hangs and VerifyErrors with complex KMP/Coroutine bytecode
-dontoptimize
-dontshrink

# Keep kotlinx and androidx classes to prevent VerifyError / optimize issues
-keep class kotlinx.** { *; }
-keep class androidx.** { *; }

# Keep annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes Exceptions, InnerClasses

# Keep members
-keepclassmembers class kotlinx.** { public <methods>; protected <methods>; }
-keepclassmembers class androidx.** { public <methods>; protected <methods>; }

# Suppress warnings for missing Android/Platform classes (Standard in Desktop builds using Multiplatform libs)
-dontwarn android.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn org.graalvm.**
-dontwarn com.oracle.svm.**
-dontwarn com.squareup.wire.**
-dontwarn okhttp3.internal.platform.**
-dontwarn java.lang.invoke.StringConcatFactory
