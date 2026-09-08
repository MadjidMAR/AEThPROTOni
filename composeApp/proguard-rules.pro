-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
-keepattributes RuntimeVisible*Annotation*,AnnotationDefault
-dontoptimize
-dontnote **
-keep class androidx.compose.** { *; }
-keep class org.jetbrains.compose.** { *; }
-keep class org.jetbrains.skiko.** { *; }
-keep class org.jetbrains.skia.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.navigation.** { *; }
-keep class androidx.savedstate.** { *; }
-keepclassmembers,allowobfuscation class * {
    @androidx.compose.runtime.Composable *;
    @androidx.compose.runtime.ReadOnlyComposable *;
}
-keep class androidx.compose.runtime.** { *; }
-keep class io.github.immaghzbad.aetherst.MainKt { *; }
-keep class io.github.immaghzbad.aetherst.** { *; }
-keep class io.github.immaghzbad.aetherst.platform.** { *; }
-keep class io.github.immaghzbad.aetherst.shared.desktop.** { *; }
-keep class io.github.immaghzbad.aetherst.shared.core.** { *; }
-keep class io.github.immaghzbad.aetherst.shared.data.** { *; }
-keep class io.github.immaghzbad.aetherst.shared.model.** { *; }
-keep class io.github.immaghzbad.aetherst.shared.ui.** { *; }
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers,allowobfuscation class * {
    @kotlinx.serialization.Serializable *;
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class kotlinx.serialization.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keep class kotlin.** { *; }
-keep class kotlin.reflect.** { *; }
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
-keep interface okio.** { *; }
-keep class org.jetbrains.compose.resources.** { *; }
-dontwarn okio.**
-dontwarn okhttp3.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn dalvik.system.**
-dontwarn com.jetbrains.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
