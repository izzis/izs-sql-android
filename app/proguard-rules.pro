# Add project specific ProGuard rules here.
-keep class org.mariadb.jdbc.** { *; }
-keep class com.jcraft.jsch.** { *; }
-keep class javax.crypto.** { *; }
-keep class javax.net.ssl.** { *; }
-dontwarn org.mariadb.jdbc.**
-dontwarn com.jcraft.jsch.**
