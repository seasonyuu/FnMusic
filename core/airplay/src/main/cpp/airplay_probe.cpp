#include "airplay_probe.h"
#include "raop_loop.h"
#include "raop_sender.h"
#include "ring_buffer.h"
#include <arpa/inet.h>
#include <array>
#include <cmath>
#include <sstream>

namespace fnmusic {
using namespace fxchain;
namespace {
// Only count submissions, never claim a UDP send proves delivery or audible output.
class ObservedIo final : public RaopIo {
public:
    RaopLoop loop;
    ProbeResult& result;
    explicit ObservedIo(ProbeResult& r) : result(r) {}
    void tcpConnect(RaopTcp c, const std::string& h, uint16_t p) override { loop.tcpConnect(c,h,p); }
    void tcpSend(RaopTcp c, std::span<const uint8_t> b) override {
        if (c == RaopTcp::Event) ++result.eventRepliesSubmitted;
        loop.tcpSend(c,b);
    }
    void tcpClose(RaopTcp c, bool flush) override { loop.tcpClose(c,flush); }
    uint16_t udpBind(RaopUdp c) override { return loop.udpBind(c); }
    void udpSend(RaopUdp c, const RaopEndpoint& to, std::span<const uint8_t> b) override {
        if (c == RaopUdp::Audio) ++result.audioPacketsSubmitted;
        if (c == RaopUdp::Timing) ++result.timingRepliesSubmitted;
        loop.udpSend(c,to,b);
    }
    void udpClose(RaopUdp c) override { loop.udpClose(c); }
};
std::string category(const std::string& error) {
    if (error.find("proof") != std::string::npos || error.find("signature") != std::string::npos ||
        error.find("authentication failed") != std::string::npos) return "receiver_authentication_failed";
    if (error.find("PIN") != std::string::npos) return "pairing_rejected";
    if (error.find("403") != std::string::npos || error.find("access") != std::string::npos) return "receiver_access_denied";
    if (error.find("Timed out") != std::string::npos) return "handshake_timeout";
    return "protocol_or_network_error";
}
}
bool ProbeResult::hasTransportEvidence(int seconds) const {
    return seconds > 0 && authenticated && streamingMs >= seconds*1000L &&
        eventChannelOpened && !eventChannelFailed && timingRepliesSubmitted > 0 &&
        nonzeroFramesProduced > 0 && audioPacketsSubmitted >= seconds*44100L/352*9/10;
}
std::string ProbeResult::json() const {
    std::ostringstream s;
    // All strings below are fixed internal enums, never receiver-supplied text.
    s << "{\"status\":\"" << status << "\",\"reason\":\"" << reason
      << "\",\"stage\":\"" << stage << "\",\"strict_auth\":true,\"authenticated\":" << (authenticated ? "true":"false")
      << ",\"event_channel_opened\":" << (eventChannelOpened ? "true":"false")
      << ",\"event_channel_failed\":" << (eventChannelFailed ? "true":"false")
      << ",\"streaming_ms\":" << streamingMs
      << ",\"audio_packets_submitted\":" << audioPacketsSubmitted
      << ",\"timing_replies_submitted\":" << timingRepliesSubmitted
      << ",\"event_replies_submitted\":" << eventRepliesSubmitted
      << ",\"nonzero_frames_produced\":" << nonzeroFramesProduced
      << ",\"flushes_acknowledged\":" << flushesAcknowledged
      << ",\"audible_output\":\"pending_manual_confirmation\"}";
    return s.str();
}
ProbeResult runProbe(const std::string& host, int port, int seconds,
                     const std::string& deviceId, const std::string& credentials,
                     const ProbeCallbacks& cb) {
    ProbeResult result;
    in_addr addr{};
    if (inet_pton(AF_INET,host.c_str(),&addr) != 1 || port < 1 || port > 65535 || seconds < 1 || seconds > 600) {
        result.reason = "invalid_arguments";
        return result;
    }
    ObservedIo io(result);
    // Half a second at most; no six-second startup reservoir from the upstream demo.
    RingBuffer<int16_t> ring(32768);
    using Clock = std::chrono::steady_clock;
    const auto start = Clock::now();
    auto streamingStart = start;
    auto pinStart = start;
    auto flushStart = start;
    bool flushIssued = false;
    bool streaming = false, done = false, awaitingPin = false;
    auto emit = [&](const std::string& e) { if (cb.event) cb.event(e); };
    RaopEvents events;
    events.launched = [&](bool ok,const std::string& error) {
        if (ok) {
            streaming = true;
            streamingStart = Clock::now();
            result.authenticated = true;
            emit("streaming");
        } else { result.reason = category(error); done = true; emit(result.reason); }
    };
    events.closed = [&] {
        if (!done) { result.reason = "receiver_closed"; done = true; emit(result.reason); }
    };
    events.pinRequired = [&](const std::string&) {
        awaitingPin = true;
        pinStart = Clock::now();
        emit("pin_required");
    };
    events.credentialsObtained = [&](const std::string&,const std::string& c) {
        if (cb.saveCredentials) cb.saveCredentials(c);
    };
    // Raw upstream logs contain endpoint/metadata; expose only stable event categories.
    RaopLogSink log = [&](RaopLogLevel, const std::string& line) {
        if (line.find("FnMusic FLUSH acknowledged") != std::string::npos) {
            ++result.flushesAcknowledged;
            emit("flush_acknowledged");
        }
        std::string stage;
        if (line.find("RTSP connected") != std::string::npos) stage="pairing";
        else if (line.find("refused transient pairing (470)") != std::string::npos) stage="pin_start";
        else if (line.find("/pair-pin-start OK") != std::string::npos) stage="pin_setup";
        else if (line.find("pair-verify complete") != std::string::npos) stage="authenticated";
        else if (line.find("GET /info ok") != std::string::npos) stage="session_setup";
        else if (line.find("event channel open (") != std::string::npos) stage="event_channel";
        else if (line.find("RECORD reply") != std::string::npos) stage="stream_setup";
        else if (line.find("stream SETUP ok") != std::string::npos) stage="streaming";
        if (stage=="event_channel") result.eventChannelOpened=true;
        if (line.find("event channel error:") != std::string::npos ||
            line.find("event channel closed") != std::string::npos ||
            line.find("event-channel decrypt failed") != std::string::npos) {
            result.eventChannelFailed=true;
            result.reason="event_channel_failed";
            done=true;
            emit(result.reason);
        }
        if (!stage.empty()) { result.stage=stage; emit(stage); }
    };
    RaopSender sender(io,std::move(events),log);
    sender.setStrictReceiverAuth(true);
    sender.setReceiverResponseTimeout(std::chrono::seconds(120));
    sender.setInputFormat(44100);
    sender.attachRing(&ring);
    RaopIdentity identity;
    identity.name = "FnMusic verification";
    // Stable, locally administered sender identity; not the receiver's device ID.
    identity.deviceId = "02:46:4E:4D:55:53";
    sender.setIdentity(identity);
    sender.setNowPlaying("FnMusic test tone", "FnMusic", "AirPlay verification");
    sender.setAuth(credentials.empty() ? RaopDeviceInfo::Auth::HapTransient : RaopDeviceInfo::Auth::HapPin,
                   true,deviceId,credentials,"");
    auto stop = [&] { sender.stop(); emit("stopped"); };
    try {
        result.stage="connecting";
        sender.start(host,static_cast<uint16_t>(port),"FnMusic verification");
        // start() resets volume; low-amplitude fixture supplies the attenuation.
        sender.setVolume(100);
        uint64_t frame = 0;
        while (!done) {
            if (cb.cancelled && cb.cancelled()) { result.status="blocked"; result.reason="cancelled"; break; }
            if (awaitingPin) {
                const auto pin = cb.pollPin ? cb.pollPin() : std::string();
                if (!pin.empty()) {
                    if (pin.size()!=4 || pin.find_first_not_of("0123456789") != std::string::npos) {
                        emit("invalid_pin");
                    } else {
                        awaitingPin=false;
                        sender.submitPin(pin);
                    }
                }
                if (Clock::now()-pinStart > std::chrono::seconds(120)) {
                    result.status="blocked"; result.reason="pin_required"; break;
                }
            }
            if (!streaming && Clock::now()-start > std::chrono::seconds(180)) {
                result.status="blocked"; result.reason="handshake_timeout"; break;
            }
            if (streaming) {
                result.streamingMs=std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now()-streamingStart).count();
                if (cb.exerciseFlush && !flushIssued && result.streamingMs >= 10000) {
                    flushIssued = true;
                    flushStart = Clock::now();
                    if (!sender.flushAudio()) { result.reason="flush_not_started"; break; }
                    emit("flush_pause");
                }
                if (sender.flushPending() && Clock::now()-flushStart > std::chrono::seconds(5)) {
                    result.reason="flush_timeout"; break;
                }
                if (result.streamingMs >= seconds*1000L) {
                    // macOS can keep the event channel idle. It must be connected and healthy;
                    // scripted event request/reply coverage comes from the fake receiver tests.
                    result.status=result.hasTransportEvidence(seconds) &&
                        (!cb.exerciseFlush || result.flushesAcknowledged == 1) ? "passed":"failed";
                    result.reason=result.status=="passed" ? "transport_checks_passed":"insufficient_transport_evidence";
                    break;
                }
                // Identical deterministic PCM in macOS and Android. One second of 440 Hz,
                // one second of 660 Hz, then a second of silence. Gentle amplitude.
                const bool paused = cb.exerciseFlush && flushIssued && result.streamingMs < 16000;
                while (!paused && !sender.flushPending() && ring.availableWrite() >= 704) {
                    std::array<int16_t,704> samples{};
                    for (size_t i=0;i<352;++i,++frame) {
                        const int segment=int(frame/44100)%3;
                        const double hz=segment==0 ? 440.0:660.0;
                        const int16_t v=segment==2 ? 0:static_cast<int16_t>(1800*std::sin(2*3.141592653589793*hz*double(frame)/44100));
                        samples[2*i]=samples[2*i+1]=v;
                        if (v!=0) ++result.nonzeroFramesProduced;
                    }
                    ring.tryPush(samples);
                }
            }
            io.loop.pump(sender,std::chrono::milliseconds(10));
        }
        stop();
    } catch (...) {
        result.status="failed"; result.reason="native_exception";
        stop();
    }
    return result;
}
}
