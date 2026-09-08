# Keep native methods and their classes
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep project models and data classes
-keep class io.github.immaghzbad.aetherst.model.** { *; }
-keep class io.github.immaghzbad.aetherst.data.** { *; }

# Absolutely keep the JNI bridge class and all its members
-keep class io.github.immaghzbad.aetherst.core.HevTun2SocksNative {
    <methods>;
    <fields>;
}

# General networking and serialization stability
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn okhttp3.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

-keep class io.github.immaghzbad.aetherst.** { *; }

-keep public class * extends android.app.Application {
    <init>();
    void onCreate();
}

-keep public class * extends android.app.Activity {
    <init>();
    void onCreate(android.os.Bundle);
}
