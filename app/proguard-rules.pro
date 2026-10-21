# SQLCipher
-keep class net.zetetic.database.** { *; }
-keep class net.zetetic.database.sqlcipher.** { *; }
-dontwarn net.zetetic.database.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.hossammo.cash.**$$serializer { *; }
-keepclassmembers class com.hossammo.cash.** {
    *** Companion;
}
-keepclasseswithmembers class com.hossammo.cash.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# fastexcel / OkHttp optional deps
-dontwarn org.apache.commons.compress.**
-dontwarn com.github.rzymek.opczip.**
-dontwarn javax.xml.stream.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
-keep class org.dhatim.fastexcel.** { *; }
