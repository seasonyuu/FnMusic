#include <jni.h>
#include "raop_sender.h"
#include "raop_loop.h"
#include "ring_buffer.h"
#include "session_error.h"
#include <array>
#include <algorithm>
#include <chrono>
#include <string>
#include <stdexcept>
using namespace fxchain;
namespace {
std::string string(JNIEnv* e, jstring s) {
    if (!s) return {};
    auto p=e->GetStringUTFChars(s,nullptr); if(!p) return {};
    std::string out(p);e->ReleaseStringUTFChars(s,p);return out;
}
}
extern "C" JNIEXPORT void JNICALL
Java_com_seasonyuu_fnmusic_core_airplay_NativeAirPlaySession_run(
    JNIEnv* e,jobject,jstring host,jint port,jstring id,jstring credentials,jobject cb) {
    auto cls=e->GetObjectClass(cb);
    auto event=e->GetMethodID(cls,"onEvent","(Ljava/lang/String;)V");
    auto command=e->GetMethodID(cls,"pollCommand","()Ljava/lang/String;");
    auto nowPlaying=e->GetMethodID(cls,"pollNowPlaying","()[B");
    auto pcm=e->GetMethodID(cls,"pollPcm","(I)[S");
    auto frames=e->GetMethodID(cls,"onFrames","(I)V");
    auto save=e->GetMethodID(cls,"saveCredentials","(Ljava/lang/String;)V");
    auto cancelled=e->GetMethodID(cls,"isCancelled","()Z");
    if(e->ExceptionCheck()) return;
    auto call=[&](jmethodID m,const std::string& s) {
        if(e->ExceptionCheck()) return;
        auto value=e->NewStringUTF(s.c_str());
        if(value) { e->CallVoidMethod(cb,m,value);e->DeleteLocalRef(value); }
    };
    const auto address=string(e,host), deviceId=string(e,id), creds=string(e,credentials);
    if(e->ExceptionCheck()) return;
    try {
        RaopLoop loop;
        RingBuffer<int16_t> ring(2048);
        bool ready=false, done=false, playing=false, pendingFlush=false;
        bool reportedPcm=false, controlConnected=false, sessionSetup=false;
        std::string requestedEpoch, activeEpoch;
        auto start=std::chrono::steady_clock::now(), flushStart=start;
        auto lastResponse=start;
        RaopEvents events;
        events.launched=[&](bool ok,const std::string& reason) {
            const auto failure=fnmusic::sessionFailureEvent(reason, controlConnected, sessionSetup);
            if(!ok) {
                const char* category = reason.find("proof") != std::string::npos || reason.find("signature") != std::string::npos ? "receiver_authentication_failed" :
                    reason.find("connect") != std::string::npos ? "transport_connect_failed" :
                    reason.find("Timed out") != std::string::npos ? "handshake_timeout" : "protocol_failure";
                if(reason.find("closed by the device") != std::string::npos) {
                    category="receiver_closed_before_ready";
                } else if(reason.find("Device refused") != std::string::npos) category="receiver_refused_request";
                else if(reason.find("reset") != std::string::npos) category="transport_reset";
                else if(reason.find("Pair-verify") != std::string::npos) category="pair_verify_failed";
                call(event,std::string("diagnostic:")+category);
                call(event,"diagnostic:elapsed_ms:"+std::to_string(std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::steady_clock::now()-start).count()));
            }
            ready=ok; lastResponse=std::chrono::steady_clock::now(); if(!ok) done=true; call(event,ok?"connected":failure); };
        events.remoteCommand=[&](const std::string& command) { if(ready) call(event,"remote:"+command); };
        events.closed=[&] { if(!done) call(event,"disconnected"); done=true; };
        events.pinRequired=[&](const std::string&) { call(event,"pairing"); };
        events.credentialsObtained=[&](const std::string&,const std::string& c) { call(save,c); };
        RaopSender sender(loop,events,[&](RaopLogLevel,const std::string& line) {
            if(line.find("FnMusic now-playing response ")!=std::string::npos) {
                const auto code=line.substr(line.find("response ")+9);
                if(code.size()==3 && code.find_first_not_of("0123456789")==std::string::npos)
                    call(event,"diagnostic:now_playing_response:"+code);
            }
            if(line.find("RTSP connected")!=std::string::npos) { controlConnected=true;call(event,"diagnostic:control_connected"); }
            if(line.find("pair-verify complete")!=std::string::npos) call(event,"diagnostic:pair_verified");
            if(line.find("GET /info ok")!=std::string::npos) { sessionSetup=true;call(event,"diagnostic:session_setup"); }
            if(line.find("control rx +")!=std::string::npos) lastResponse=std::chrono::steady_clock::now();
            if(line.find("event channel error:")!=std::string::npos ||
               line.find("event channel closed")!=std::string::npos ||
               line.find("decrypt failed")!=std::string::npos) { done=true;call(event,"connection_failed"); }
        });
        sender.setStrictReceiverAuth(true);
        sender.setReceiverResponseTimeout(std::chrono::seconds(120));
        sender.setInputFormat(44100);
        sender.setAuth(creds.empty()?RaopDeviceInfo::Auth::HapTransient:RaopDeviceInfo::Auth::HapPin,true,deviceId,creds,"");
        RaopIdentity identity;identity.name="FnMusic";identity.deviceId="02:46:4E:4D:55:53";sender.setIdentity(identity);

        sender.start(address,static_cast<uint16_t>(port),"FnMusic");
        // The upstream start() resets pending volume, so set it afterwards.
        sender.setVolume(20);
        while(!done && !e->ExceptionCheck() && !e->CallBooleanMethod(cb,cancelled)) {
            auto value=static_cast<jstring>(e->CallObjectMethod(cb,command));
            if(e->ExceptionCheck()) break;
            auto cmd=string(e,value);if(value)e->DeleteLocalRef(value);
            if(ready) {
                auto snapshot=static_cast<jbyteArray>(e->CallObjectMethod(cb,nowPlaying));
                if(e->ExceptionCheck()) break;
                if(snapshot) {
                    const auto length=e->GetArrayLength(snapshot);
                    if(length > 600000) throw std::runtime_error("snapshot too large");
                    std::string data(length, '\0');
                    e->GetByteArrayRegion(snapshot,0,length,reinterpret_cast<jbyte*>(data.data()));
                    e->DeleteLocalRef(snapshot);
                    size_t offset=0;
                    auto number=[&](size_t n) { if(n > data.size()-offset) throw std::runtime_error("snapshot truncated");
                        uint64_t result=0; while(n--) result=(result<<8)|uint8_t(data[offset++]); return result; };
                    auto field=[&]() { const auto n=number(4); if(n>data.size()-offset) throw std::runtime_error("snapshot field");
                        auto result=data.substr(offset,n);offset+=n;return result; };
                    const auto title=field(),artist=field(),album=field();
                    const auto position=number(8),duration=number(8); const bool active=number(1)!=0;
                    const auto cover=field();
                    sender.setNowPlaying(title,artist,album,cover,"image/jpeg");
                    sender.setPlaybackInfo(position,duration,active);
                    call(event,"diagnostic:now_playing_sent");
                }
            }
            if(cmd=="play") playing=true;
            else if(cmd=="pause") playing=false;
            else if(cmd.starts_with("flush:")) { pendingFlush=true; requestedEpoch=cmd.substr(6); }
            else if(cmd.starts_with("pin:")) sender.submitPin(cmd.substr(4));
            else if(cmd.starts_with("volume:")) sender.setVolume(std::stod(cmd.substr(7)));
            if(pendingFlush && ready && !sender.flushPending()) {
                ring.reset();sender.flushAudio();pendingFlush=false;activeEpoch=requestedEpoch;
                flushStart=std::chrono::steady_clock::now();
            }
            if(sender.flushPending() && std::chrono::steady_clock::now()-flushStart>std::chrono::seconds(5)) {
                call(event,"flush_failed");break;
            }
            if(!ready && std::chrono::steady_clock::now()-start>std::chrono::seconds(180)) {
                call(event,"receiver_response_timeout");break;
            }
            if(ready && std::chrono::steady_clock::now()-lastResponse>std::chrono::seconds(12)) {
                call(event,"connection_timeout");break;
            }
            sender.attachRing(playing && !pendingFlush && !sender.flushPending()?&ring:nullptr);
            if(ready && playing && !pendingFlush && !sender.flushPending() && ring.availableWrite()>=704) {
                auto data=static_cast<jshortArray>(e->CallObjectMethod(cb,pcm,static_cast<jint>(704)));
                if(e->ExceptionCheck())break;
                if(data) {
                    const auto n=e->GetArrayLength(data);
                    if(n>0 && n<=704 && n%2==0) {
                        std::array<int16_t,704> buf{};
                        e->GetShortArrayRegion(data,0,n,buf.data());
                        if(!reportedPcm && std::any_of(buf.begin(),buf.begin()+n,[](auto value){return value!=0;})) {
                            reportedPcm=true;call(event,"audio_nonzero");
                        }
                        ring.tryPush(std::span<const int16_t>(buf.data(),n));
                    }
                    e->DeleteLocalRef(data);
                }
            }
            const auto before=ring.availableRead();
            loop.pump(sender,std::chrono::milliseconds(5));
            const auto after=ring.availableRead();
            if(!activeEpoch.empty() && !sender.flushPending()) {
                call(event,"flushed:"+activeEpoch);activeEpoch.clear();
            }
            if(before>after && !e->ExceptionCheck()) e->CallVoidMethod(cb,frames,static_cast<jint>((before-after)/2));
        }
        done=true;
        sender.stop();
    } catch(...) { call(event,"connection_failed"); }
}
