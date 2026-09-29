# MariaDB Connector/J: driver lookup + reflective field access. Names must NOT
# be obfuscated — DriverManager resolves the driver by class name, and the
# META-INF/services plugins (auth/codec/credential/TLS) load via ServiceLoader
# against these exact names.
-keep class org.mariadb.jdbc.** { *; }
-dontwarn org.mariadb.jdbc.**

# mwiede JSch (com.github.mwiede:jsch) keeps the original com.jcraft.jsch
# package names. Session/channel/crypto setup goes through Class.forName
# with hardcoded strings (e.g. com.jcraft.jsch.jce.Random), so names must
# NOT be obfuscated or release builds crash on connect with
# ClassNotFoundException.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**

# Room + Hilt are compile-time generated (no reflection at runtime) and both
# ship their own consumer rules. These attributes are all R8 needs to keep
# generic signatures and annotation metadata intact for them.
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
