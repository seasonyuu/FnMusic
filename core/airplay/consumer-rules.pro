# airplay_session_jni.cpp looks up these methods by their original names and
# signatures on the callback object. R8 cannot see those JNI calls.
-keep interface com.seasonyuu.fnmusic.core.airplay.NativeAirPlaySession$Callbacks { *; }
-keep class * implements com.seasonyuu.fnmusic.core.airplay.NativeAirPlaySession$Callbacks { *; }
