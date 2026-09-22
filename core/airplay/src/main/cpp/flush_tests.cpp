// Reuse the pinned upstream's protocol peer (Apache-2.0) without editing its source.
#define main upstreamCoreTestsMain
#include "test/core_tests.cpp"
#undef main

int main() {
    g_test = "FnMusic bounded receiver approval wait";
    {
        Rig delayed;
        delayed.sender->setStrictReceiverAuth(true);
        delayed.sender->setReceiverResponseTimeout(std::chrono::seconds(120));
        delayed.rx.mode = FakeReceiver::Mode::Ap2Transient;
        delayed.sender->setAuth(RaopDeviceInfo::Auth::HapTransient, true, "atv-1", "", "");
        delayed.sender->start(delayed.peerIp, 7000, "Receiver");
        delayed.connectControl();
        delayed.advance(30000);
        CHECK(delayed.sender->active() && !delayed.launchedOk, "receiver response may wait beyond old ten-second deadline");
        delayed.exchange();
        CHECK(delayed.launchedOk && *delayed.launchedOk, "delayed strict-auth handshake still succeeds");
        delayed.sender->stop();
        delayed.sender->start(delayed.peerIp, 7000, "Receiver");
        delayed.advance(10100);
        CHECK(!delayed.sender->active(), "new TCP connect keeps original ten-second deadline");
    }
    {
        Rig expired;
        expired.sender->setReceiverResponseTimeout(std::chrono::seconds(120));
        expired.sender->setAuth(RaopDeviceInfo::Auth::None, false, "receiver", "", "");
        expired.sender->start(expired.peerIp, 7000, "Receiver");
        expired.connectControl();
        expired.advance(119000);
        CHECK(expired.sender->active(), "response wait remains active before its limit");
        expired.advance(1100);
        CHECK(!expired.sender->active() && expired.closedCount == 1, "missing approval or response eventually closes the session");
        CHECK(!expired.io.anyUdpOpen() && !expired.sender->nextDeadline(), "expiry releases sockets and timers");
    }
    {
        Rig cancelled;
        cancelled.sender->setReceiverResponseTimeout(std::chrono::seconds(120));
        cancelled.sender->start(cancelled.peerIp, 7000, "Receiver");
        cancelled.connectControl();
        cancelled.advance(30000);
        cancelled.sender->stop();
        CHECK(!cancelled.sender->active() && !cancelled.io.anyUdpOpen(), "user cancellation releases an outstanding approval wait");
    }
    g_test = "FnMusic confirmed FLUSH discards old audio";
    Rig r;
    r.sender->setStrictReceiverAuth(true);
    runAp2Handshake(r, FakeReceiver::Mode::Ap2Transient, RaopDeviceInfo::Auth::HapTransient);
    if (!r.launchedOk || !*r.launchedOk) return 1;
    r.io.takeUdp();
    r.fillRingRamp(352 * 4, 11);
    r.advance(8);
    r.io.takeUdp();
    CHECK(r.sender->flushAudio(), "flush accepted during streaming");
    CHECK(r.sender->flushPending(), "flush waits for authenticated response");
    CHECK(!r.sender->flushAudio(), "cannot overlap flush requests");
    CHECK(r.ring.availableRead() == 0, "old queued samples discarded");
    r.exchange();
    CHECK(!r.sender->flushPending(), "encrypted FLUSH response acknowledged");
    const auto* flush = r.rx.last("FLUSH");
    CHECK(flush && !flush->h("rtp-info").empty(), "FLUSH carries RTP cutoff");
    r.advance(8);
    for (const auto& dg : r.io.takeUdp()) {
        if (dg.s != RaopUdp::Audio) continue;
        const auto pcm = r.rx.decodeAudio(dg.data);
        CHECK(pcm && std::all_of(pcm->begin(), pcm->end(), [](auto sample) { return sample == 0; }),
              "pause sends silence, never previous epoch PCM");
    }
    r.fillRingRamp(352, 123);
    r.advance(8);
    for (const auto& dg : r.io.takeUdp()) {
        if (dg.s != RaopUdp::Audio) continue;
        const auto pcm = r.rx.decodeAudio(dg.data);
        CHECK(pcm && *pcm == rampExpect(704, 123), "new epoch audio decrypts to exact new samples");
    }
    r.sender->stop();
    CHECK(!r.sender->flushAudio(), "idle session rejects flush");
    {
        Rig rejected;
        rejected.sender->setStrictReceiverAuth(true);
        runAp2Handshake(rejected, FakeReceiver::Mode::Ap2Transient, RaopDeviceInfo::Auth::HapTransient);
        CHECK(rejected.sender->flushAudio(), "second receiver starts flush");
        bool bad=false;
        auto ciphertext=rejected.io.takeTcp(RaopTcp::Control);
        auto plaintext=decryptFrames(rejected.rx.ctrlIn, rejected.rx.ctrlInCtr, ciphertext, bad);
        const auto requests=parseRequests(plaintext);
        CHECK(!bad && requests.size()==1, "FLUSH is authenticated and decryptable");
        if(requests.size()==1) {
            const auto refusal=std::string("RTSP/1.0 500 Rejected\r\nCSeq: ")+requests[0].h("cseq")+"\r\nContent-Length: 0\r\n\r\n";
            const auto encrypted=encryptFrames(rejected.rx.ctrlOut,rejected.rx.ctrlOutCtr,refusal);
            rejected.sender->onTcpData(RaopTcp::Control,spanOf(encrypted));
            CHECK(!rejected.sender->active() && rejected.closedCount==1, "rejected FLUSH closes session rather than leaking old PCM");
        }
    }
    std::printf("FnMusic FLUSH: %d/%d checks passed\n", g_checks - g_failures, g_checks);
    return g_failures ? 1 : 0;
}
