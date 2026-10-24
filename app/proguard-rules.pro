# R8 для release выключен (minifyEnabled=false). Правила на случай включения:
-keep class com.github.junrar.** { *; }
-keep class org.apache.commons.compress.** { *; }
-keep class net.lingala.zip4j.** { *; }
-dontwarn org.tukaani.xz.**
-dontwarn org.apache.commons.compress.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.slf4j.**
