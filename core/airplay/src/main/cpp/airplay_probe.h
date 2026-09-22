#pragma once
#include <atomic>
#include <functional>
#include <string>

namespace fnmusic {
// Probe only. No production playback surface is exposed before the real-device gate.
struct ProbeCallbacks {
    bool exerciseFlush = false;
    std::function<void(const std::string&)> event;
    std::function<std::string()> pollPin;
    std::function<bool()> cancelled;
    std::function<void(const std::string&)> saveCredentials;
};
struct ProbeResult {
    std::string status = "failed";
    std::string reason = "not_started";
    std::string stage = "input_validation";
    long streamingMs = 0;
    long audioPacketsSubmitted = 0;
    long timingRepliesSubmitted = 0;
    long eventRepliesSubmitted = 0;
    long nonzeroFramesProduced = 0;
    bool authenticated = false;
    bool eventChannelOpened = false;
    bool eventChannelFailed = false;
    int flushesAcknowledged = 0;
    bool hasTransportEvidence(int seconds) const;
    std::string json() const;
};
ProbeResult runProbe(const std::string& host, int port, int seconds,
                     const std::string& deviceId, const std::string& credentials,
                     const ProbeCallbacks& callbacks);
}
