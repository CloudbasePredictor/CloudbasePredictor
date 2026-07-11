# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Keep kotlinx-serialization models used by Ktor content negotiation
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

-keep,includedescriptorclasses class com.cloudbasepredictor.data.remote.**$$serializer { *; }
-keepclassmembers class com.cloudbasepredictor.data.remote.** {
    *** Companion;
}
-keepclasseswithmembers class com.cloudbasepredictor.data.remote.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Room entities
-keep class com.cloudbasepredictor.data.local.** { *; }

# Keep domain models
-keep class com.cloudbasepredictor.model.** { *; }

# Strip release logging calls from accidental direct Log or Timber usage.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
    public static int println(...);
}

-assumenosideeffects class timber.log.Timber {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}

-assumenosideeffects class timber.log.Timber$Tree {
    public *** v(...);
    public *** d(...);
    public *** i(...);
    public *** w(...);
    public *** e(...);
    public *** wtf(...);
}

# Preserve line number information for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
