# ==============================================================================
# PLAIN-ENGLISH GUIDE
# Extra rules for R8, the tool that shrinks and speeds up the "release" version.
# Most libraries bring their own rules; these only silence warnings about optional
# annotation libraries that Guava mentions but doesn't need at runtime.
# ==============================================================================
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.j2objc.annotations.**
-dontwarn org.checkerframework.**
-dontwarn afu.org.checkerframework.**
-dontwarn javax.annotation.**
-dontwarn javax.lang.model.element.Modifier
-dontwarn sun.misc.Unsafe
-dontwarn java.lang.reflect.AnnotatedType

# Keep line numbers so crash reports point to the right line (no effect on privacy).
-keepattributes SourceFile,LineNumberTable
