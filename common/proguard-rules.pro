-dontobfuscate
-dontoptimize
-allowaccessmodification
-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod,*Annotation*

-keep class com.liskovsoft.** { *; }
-keep class com.newtube.** { *; }
-keep class io.yash.meetube.** { *; }

-keep class androidx.media3.** { *; }
-keep interface androidx.media3.** { *; }
-dontwarn androidx.media3.**

-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
    <methods>;
}
-dontwarn com.google.protobuf.**

-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public class * extends com.bumptech.glide.module.AppGlideModule
-keep public class * extends com.bumptech.glide.module.LibraryGlideModule
-keep class com.bumptech.glide.GeneratedAppGlideModuleImpl { *; }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-dontwarn com.bumptech.glide.**

-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class * extends android.view.View { <init>(...); }
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
-keepclassmembers class * implements android.os.Parcelable { public static final ** CREATOR; }

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn kotlin.**
-dontwarn kotlinx.**
