# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Enable better debugging
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Apache POI specific rules
-dontwarn org.apache.poi.**
-dontwarn org.apache.commons.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.schemas.**
-dontwarn org.etsi.**
-dontwarn org.w3.**
-dontwarn org.xml.**
-dontwarn com.microsoft.schemas.**
-dontwarn com.graphbuilder.**
-dontwarn javax.xml.**
-dontwarn org.slf4j.**

# Keep POI classes that are accessed via reflection
-keep class org.apache.poi.** { *; }
-keep class org.apache.xmlbeans.** { *; }
-keep class org.openxmlformats.schemas.** { *; }
-keep class org.apache.commons.io.** { *; }

# Keep enumerations
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Rules for document content URI access
-keepclassmembers class * extends android.content.ContentProvider {
    public android.database.Cursor query(android.net.Uri, java.lang.String[], java.lang.String, java.lang.String[], java.lang.String);
    public java.lang.String getType(android.net.Uri);
    public android.net.Uri insert(android.net.Uri, android.content.ContentValues);
    public int delete(android.net.Uri, java.lang.String, java.lang.String[]);
    public int update(android.net.Uri, android.content.ContentValues, java.lang.String, java.lang.String[]);
}

# Rules for handling the new Android storage system
-keep class androidx.core.content.FileProvider { *; }
-keep class androidx.documentfile.provider.** { *; }

# Keep our document parser and its related classes
-keep class com.example.ablindexaminer.data.WordDocumentParser { *; }
-keep class com.example.ablindexaminer.data.DocumentParsingResult { *; }

# Keep URI permission handling
-keepclassmembers class android.content.ContentResolver {
    public void takePersistableUriPermission(android.net.Uri, int);
    public void releasePersistableUriPermission(android.net.Uri, int);
}

# Preserve the Uri class to ensure it's not obfuscated
-keep class android.net.Uri { *; }

# Keep specific methods for XML parsing
-keepclassmembers class * {
    @org.apache.xmlbeans.** *;
}