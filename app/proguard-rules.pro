# Weaver release R8 configuration.
#
# Release builds run R8 in full mode (AGP 9 default) with the optimizing
# defaults from proguard-android-optimize.txt. These project rules cover the
# two things R8 cannot see on its own: the reflective WebView JS bridge and
# the kotlinx.serialization sealed hierarchy used by the native<->web bridge.

# ── Crash deobfuscation ──────────────────────────────────────────────────
# Keep line numbers so release stack traces symbolicate against the
# mapping.txt R8 emits at app/build/outputs/mapping/release/mapping.txt.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations are needed at runtime by kotlinx.serialization (@SerialName etc).
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,InnerClasses,EnclosingMethod,Signature

# ── WebView JS bridge ────────────────────────────────────────────────────
# JsBridgeInterface.post() is invoked by the WebView via addJavascriptInterface.
# R8 sees no Kotlin/Java call site, so every @JavascriptInterface method must
# be kept or the entire web->native bridge silently dies in release builds.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ── kotlinx.serialization ────────────────────────────────────────────────
# The runtime ships consumer rules, but the bridge leans on a sealed
# polymorphic hierarchy (Outbound / Inbound, classDiscriminator = "type")
# plus @Serializable Nav3 NavKey routes. Keep generated serializers and the
# Companion handles for everything under our package so polymorphic
# encode/decode keeps working after shrinking.
-if @kotlinx.serialization.Serializable class com.weaver.app.**
-keep, includedescriptorclasses class com.weaver.app.**$$serializer { *; }

-keepclassmembers @kotlinx.serialization.Serializable class com.weaver.app.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Concrete subclasses of the sealed bridge messages are resolved by SerialName
# at runtime — keep them and their members intact.
-keep class com.weaver.app.bridge.Outbound { *; }
-keep class com.weaver.app.bridge.Outbound$* { *; }
-keep class com.weaver.app.bridge.Inbound { *; }
-keep class com.weaver.app.bridge.Inbound$* { *; }

# @Serializable Nav3 destinations — serialized by Nav3 for saved-state.
-keep class com.weaver.app.ui.Login { *; }
-keep class com.weaver.app.ui.Home { *; }
-keep class com.weaver.app.ui.ProjectRoute { *; }
-keep class com.weaver.app.ui.Overview { *; }
-keep class com.weaver.app.ui.Focused { *; }
-keep class com.weaver.app.ui.MultiSelect { *; }

# ── Components referenced only from the manifest ─────────────────────────
# AGP keeps manifest-declared classes automatically (WeaverApp, MainActivity,
# AssetContentProvider) — no rules needed. Listed here for the next reader.

# ── Release linkage note ─────────────────────────────────────────────────
# Release builds link :dari-noop (a no-op); the inspectable :dari module is
# debug-only, so there is nothing Dari-related to keep here.
