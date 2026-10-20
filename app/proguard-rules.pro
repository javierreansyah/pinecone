
# MuPDF's JNI resolves these Java types, fields, and callbacks by their original names.
# The upstream Fitz AAR does not ship consumer keep rules.
-keep class com.artifex.mupdf.fitz.** { *; }
