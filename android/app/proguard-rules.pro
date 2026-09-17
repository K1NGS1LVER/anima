# Anima release shrinker rules.
# Entry points are resolved by the Android framework by name, never from Kotlin
# call sites, so R8 must not rename or strip them.
-keep class io.agents.anima.AnimaAccessibilityService { *; }
-keep class io.agents.anima.FloatingOverlayService { *; }
-keep class io.agents.anima.TaskerReceiver { *; }
-keep class io.agents.anima.MainActivity { *; }

# Skill rows are (de)serialized by field name against skills.json produced by the
# Python runtime; renaming these breaks cross-runtime skill portability.
-keepclassmembers class io.agents.anima.engine.** { <fields>; }

-dontwarn org.json.**
