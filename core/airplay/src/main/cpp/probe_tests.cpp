#include "airplay_probe.h"
#include "session_error.h"
#include <iostream>
#include <cstdlib>
void require(bool ok,const char* message) {
    if (!ok) { std::cerr<<message<<'\n'; std::exit(1); }
}
int main() {
    using fnmusic::sessionFailureEvent;
    require(sessionFailureEvent("Timed out waiting for the device", false, false)=="connection_timeout", "TCP timeout is not receiver approval");
    require(sessionFailureEvent("Timed out waiting for the device", true, true)=="receiver_response_timeout", "connected receiver wait is separate");
    require(sessionFailureEvent("Pairing with Receiver failed (HTTP 400)", true, true)=="receiver_request_rejected", "SETUP refusal is not a sender timeout");
    require(sessionFailureEvent("Pairing with Receiver failed (HTTP 400)", true, false)=="connection_failed", "earlier authentication failure must not be attributed to SETUP approval");
    require(sessionFailureEvent("Connection closed by the device", true, true)=="receiver_closed_before_ready", "receiver FIN is distinguished");
    fnmusic::ProbeResult r;
    r.authenticated=true; r.streamingMs=90000; r.audioPacketsSubmitted=11275;
    r.timingRepliesSubmitted=35; r.eventChannelOpened=true; r.nonzeroFramesProduced=2600000;
    require(r.hasTransportEvidence(90),"An idle but healthy Mac event channel is allowed");
    r.eventChannelFailed=true;
    require(!r.hasTransportEvidence(90),"A closed or corrupt event channel must fail");
    r.eventChannelFailed=false; r.eventChannelOpened=false;
    require(!r.hasTransportEvidence(90),"Unopened event channel cannot pass");
    r.eventChannelOpened=true; r.timingRepliesSubmitted=0;
    require(!r.hasTransportEvidence(90),"One-way UDP submission cannot pass");
    r.timingRepliesSubmitted=35; r.authenticated=false;
    require(!r.hasTransportEvidence(90),"Unauthenticated receiver cannot pass");
    r.authenticated=true; r.audioPacketsSubmitted=500;
    require(!r.hasTransportEvidence(90),"A short packet burst cannot prove sustained playback");
    r.audioPacketsSubmitted=11275; r.streamingMs=89999;
    require(!r.hasTransportEvidence(90),"Pairing time must not count as streaming time");
    r.streamingMs=90000; r.nonzeroFramesProduced=0;
    require(!r.hasTransportEvidence(90),"Generated silence cannot pass the test-tone check");
    const auto invalid=fnmusic::runProbe("invalid",7000,90,"","",{});
    require(invalid.reason=="invalid_arguments", "Reject invalid endpoints without network I/O");
    std::cout<<"Probe evidence checks passed\n";
}
