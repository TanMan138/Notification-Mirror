# R8 rules for the release build.
#
# The failure mode here is silent: a stripped serializer or a renamed Ktor
# field does not break the build, it breaks mirroring at runtime, on a device,
# after shipping. Anything removed from this file needs a real device test.

# Readable crash reports from a minified build.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization -------------------------------------------------
# Serializers are generated as nested $$serializer classes and looked up
# through the companion, neither of which R8 can see being used.
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations, AnnotationDefault

-keepclassmembers class com.tanmay.notificationmirror.** {
    *** Companion;
}
-keepclasseswithmembers class com.tanmay.notificationmirror.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.tanmay.notificationmirror.**$$serializer { *; }

# The library's own reflective lookups.
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**

# --- Ktor / OkHttp ---------------------------------------------------------
# Ktor updates these through atomic field updaters, which resolve by name.
-keepclassmembers class io.ktor.** { volatile <fields>; }
-keepclassmembers class io.ktor.client.engine.okhttp.** { volatile <fields>; }
-dontwarn io.ktor.**

# Optional TLS providers OkHttp probes for and this app never bundles.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Ktor logs through SLF4J; no binding ships here.
-dontwarn org.slf4j.**

# --- Coroutines ------------------------------------------------------------
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# --- Supabase --------------------------------------------------------------
# Wire models inside the SDK are serialized the same way ours are.
-keepclassmembers class io.github.jan.supabase.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.jan.supabase.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class io.github.jan.supabase.**$$serializer { *; }
-dontwarn io.github.jan.supabase.**
