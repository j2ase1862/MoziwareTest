# Add project specific ProGuard rules here.
# kotlinx.serialization: 직렬화 대상 DTO 의 @Serializable 메타데이터 보존
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class io.vasim.glass.** {
    *** Companion;
}
-keepclasseswithmembers class io.vasim.glass.** {
    kotlinx.serialization.KSerializer serializer(...);
}
