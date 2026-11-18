# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, RuntimeVisibleAnnotations
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.flux.reader.**$$serializer { *; }
-keepclassmembers class com.flux.reader.** { *** Companion; }
-keepclasseswithmembers class com.flux.reader.** { kotlinx.serialization.KSerializer serializer(...); }
# Ktor / OkHttp / slf4j / junrar
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-dontwarn java.lang.management.**
-dontwarn org.apache.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-keep class com.github.junrar.** { *; }
