#pragma once
#include <string>

namespace fnmusic {
// Return stable categories only; receiver-provided text must never reach UI/logs.
inline std::string sessionFailureEvent(const std::string& reason, bool controlConnected, bool sessionSetup) {
    if (reason.find("Timed out") != std::string::npos)
        return controlConnected ? "receiver_response_timeout" : "connection_timeout";
    if (reason.find("No PIN was entered") != std::string::npos)
        return "receiver_response_timeout";
    if (reason.find("closed by the device") != std::string::npos)
        return "receiver_closed_before_ready";
    if (reason.find("Device refused SETUP") != std::string::npos ||
        (sessionSetup && reason.find("(HTTP 400)") != std::string::npos))
        return "receiver_request_rejected";
    return "connection_failed";
}
}
