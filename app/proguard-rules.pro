-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class dev.smto.driveassistant.**$$serializer { *; }
-keepclassmembers class dev.smto.driveassistant.** {
    *** Companion;
}
-keepclasseswithmembers class dev.smto.driveassistant.** {
    kotlinx.serialization.KSerializer serializer(...);
}
