# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/google/home/cartland/android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Begin Enable minification

#-ignorewarnings
-dontobfuscate

-keepattributes SourceFile,LineNumberTable
-keepattributes Signature
-keepattributes *Annotation*
#-keepattributes InnerClasses

#-keep public class com.liskovsoft.youtubeapi.** { *; }
#-keep class okhttp3.**{*;}
#-keep interface okhttp3.** { *; }

-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn com.jayway.jsonpath.**
-dontwarn retrofit2.**
-dontwarn org.slf4j.**

# End Enable minification

# ---- MeeTube release shrinking: keep everything reached by reflection ----
-dontobfuscate
-dontoptimize
-allowaccessmodification

# App, vendored modules and the media service models (retrofit / jsonpath / prefs plumbing read these by reflection)
-keep class com.newtube.** { *; }
-keep class com.liskovsoft.** { *; }
-keep class io.yash.meetube.** { *; }

# media3: LiveDashManifestParser and Helpers.setField reach into its internals
-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

# sabr-media3: protobuf-javalite generated messages are reflected over
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}
-dontwarn com.google.protobuf.**

# Glide (generated module, webp decoder)
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}
-keep class com.zjupure.webpdecoder.** { *; }

# Networking, native engines, ADB
-keep class org.chromium.net.** { *; }
-keep class com.eclipsesource.v8.** { *; }
-keep class org.conscrypt.** { *; }
-keep class dadb.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn kotlin.**
-dontwarn kotlinx.**

# RxJava
-dontwarn io.reactivex.rxjava3.**
-keep class io.reactivex.rxjava3.** { *; }

# WorkManager workers are created by class name
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# Views inflated from XML by name, custom attributes
-keep class * extends android.view.View { <init>(...); }
-keepclassmembers class * extends android.app.Activity { public void *(android.view.View); }

# Enums and Parcelables used by name
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
-keepclassmembers class * implements android.os.Parcelable { public static final ** CREATOR; }
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ---- Glass engine: AGSL RuntimeShader, RenderNode, RenderEffect and the BatterySaver reflection ----
-keep class com.newtube.mobile.ui.common.Glass* { *; }
-keep class com.newtube.mobile.ui.common.SheetGlass { *; }
-keep class com.newtube.mobile.ui.common.SheetGlass$* { *; }
-keep class com.newtube.mobile.ui.common.NavLens { *; }
-keep class com.newtube.mobile.ui.common.FocusTimer { *; }
-keep class com.newtube.mobile.ui.common.BatterySaver { *; }
-keepclassmembers class android.animation.ValueAnimator {
    public static void setDurationScale(float);
}
-dontwarn android.graphics.RuntimeShader
-dontwarn android.graphics.RenderEffect
-dontwarn android.graphics.RenderNode

# ---- Glide: generated module, library modules, webp decoder ----
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl { *; }
-dontwarn com.bumptech.glide.**

# ---- media3 / ExoPlayer: reflected internals, services, optional deps ----
-keepclassmembers class androidx.media3.** { *; }
-keep class * extends androidx.media3.session.MediaSessionService { *; }
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.jspecify.annotations.**
-dontwarn com.google.android.gms.**

# ---- sabr-media3: protobuf-javalite messages, builders, enums and the ump parser ----
-keep class com.newtube.sabr.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite$Builder { *; }
-keepclassmembers class * extends com.google.protobuf.Internal$EnumLite { *; }
-keepclassmembers class * implements com.google.protobuf.Internal$EnumLite {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    public static ** forNumber(int);
}
