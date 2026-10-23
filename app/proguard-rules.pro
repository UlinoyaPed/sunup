# keep classes used for Json deserializing
-keep class org.unifiedpush.distributor.sunup.api.data.** { *; }
-keepnames class org.unifiedpush.** { *; }

# preserve line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile