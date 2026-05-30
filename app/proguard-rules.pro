# Keep the accessibility service entry point (declared in manifest).
-keep class com.langsense.app.service.LangSenseAccessibilityService { *; }

# Keep activities referenced from the manifest.
-keep class com.langsense.app.ui.** { *; }
