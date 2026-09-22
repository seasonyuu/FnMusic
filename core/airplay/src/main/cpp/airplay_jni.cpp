#include "airplay_probe.h"
#include <jni.h>
namespace {
std::string utf(JNIEnv* env,jstring text) {
    if (!text) return {};
    const char* p=env->GetStringUTFChars(text,nullptr);
    if (!p) return {};
    std::string s(p); env->ReleaseStringUTFChars(text,p); return s;
}
}
extern "C" JNIEXPORT jstring JNICALL
Java_com_seasonyuu_fnmusic_core_airplay_NativeAirPlayProbe_run(
    JNIEnv* env,jobject,jstring host,jint port,jint seconds,jstring deviceId,jstring creds,jobject callbacks) {
    auto cls=env->GetObjectClass(callbacks);
    auto onEvent=env->GetMethodID(cls,"onEvent","(Ljava/lang/String;)V");
    auto pollPin=env->GetMethodID(cls,"pollPin","()Ljava/lang/String;");
    auto cancelled=env->GetMethodID(cls,"isCancelled","()Z");
    auto save=env->GetMethodID(cls,"saveCredentials","(Ljava/lang/String;)V");
    if (env->ExceptionCheck()) return nullptr;
    auto stringCall=[&](jmethodID method,const std::string& text) {
        if (env->ExceptionCheck()) return;
        auto value=env->NewStringUTF(text.c_str());
        if (!value) return;
        env->CallVoidMethod(callbacks,method,value); env->DeleteLocalRef(value);
    };
    fnmusic::ProbeCallbacks cb;
    cb.event=[&](const std::string& s) { stringCall(onEvent,s); };
    cb.saveCredentials=[&](const std::string& s) { stringCall(save,s); };
    cb.cancelled=[&] { return env->ExceptionCheck() || env->CallBooleanMethod(callbacks,cancelled); };
    cb.pollPin=[&] {
        if (env->ExceptionCheck()) return std::string();
        auto value=static_cast<jstring>(env->CallObjectMethod(callbacks,pollPin));
        if (env->ExceptionCheck()) return std::string();
        auto s=utf(env,value); if (value) env->DeleteLocalRef(value); return s;
    };
    const auto h=utf(env,host), id=utf(env,deviceId), credentials=utf(env,creds);
    if (env->ExceptionCheck()) return nullptr;
    try {
        auto r=fnmusic::runProbe(h,port,seconds,id,credentials,cb);
        if (env->ExceptionCheck()) return nullptr;
        return env->NewStringUTF(r.json().c_str());
    } catch (...) {
        if (env->ExceptionCheck()) return nullptr;
        fnmusic::ProbeResult r; r.reason="native_exception";
        return env->NewStringUTF(r.json().c_str());
    }
}
