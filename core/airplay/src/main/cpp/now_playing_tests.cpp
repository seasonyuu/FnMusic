#define main upstreamCoreTestsMain
#include "test/core_tests.cpp"
#undef main

int main(int argc, char** argv) {
    if(argc==2 && std::string(argv[1])=="--unicode-fixture") {
        using namespace airplay::bplist;
        auto data=encode(Value::object({{"title",Value::str("歌曲 🎵 Café")},{"timestamp",Value::date(100.5)}}));
        std::fwrite(data.data(),1,data.size(),stdout); return 0;
    }
    g_test = "FnMusic now-playing encrypted transport";
    Rig r;
    std::vector<std::string> commands;
    RaopEvents events;
    events.launched = [&](bool ok, const std::string&) { r.launchedOk=ok; };
    events.pinRequired = [&](const std::string& name) { r.pinDevice=name; };
    events.remoteCommand = [&](const std::string& cmd) { commands.push_back(cmd); };
    r.sender = std::make_unique<RaopSender>(r.io, events);
    r.sender->setClock([&] { return r.now; });
    r.sender->setStrictReceiverAuth(true);
    runAp2Handshake(r, FakeReceiver::Mode::Ap2Pin, RaopDeviceInfo::Auth::HapPin);
    if(!r.launchedOk || !*r.launchedOk) return 1;
    r.sender->setNowPlaying("歌曲 🎵", "歌手", "Album", "jpeg-fixture", "image/jpeg");
    r.sender->setPlaybackInfo(12345,180000,true);
    r.exchange();
    bool info=false,state=false,supported=false,client=false,progress=false;
    for(const auto& req : r.rx.requests) {
        if(req.body.starts_with("progress: ")) progress=true;
        if(req.method!="POST" || req.uri!="/command") continue;
        auto root=airplay::bplist::decode(bytesOf(req.body));
        CHECK(root.has_value(),"authenticated peer decodes published plist");
        if(!root) continue;
        const auto type=root->find("type")->asStr();
        const auto* params=root->find("params");
        if(type=="updateMRNowPlayingInfo") {
            auto* n=params->find("params"); info=true;
            CHECK(n->find("kMRMediaRemoteNowPlayingInfoTitle")->asStr()=="歌曲 🎵","Unicode title preserved");
            CHECK(n->find("kMRMediaRemoteNowPlayingInfoArtworkData")->data==bytesOf("jpeg-fixture"),"actual cover bytes transmitted");
            CHECK(n->find("kMRMediaRemoteNowPlayingInfoTimestamp")->type==airplay::bplist::Value::Type::Date,"timestamp is CFDate");
            CHECK(n->find("kMRMediaRemoteNowPlayingInfoElapsedTime")->r==12.345,"position in seconds");
        }
        if(type=="updateMRPlaybackState") {state=true;CHECK(params->find("mrPlaybackState")->asInt()==1,"playing state");}
        if(type=="updateMRSupportedCommands") supported=true;
        if(type=="updateMRNowPlayingClient") client=true;
    }
    CHECK(info&&state&&supported&&client&&progress,"all metadata channels present");
    const auto previousRequests = r.rx.requests.size();
    r.sender->setNowPlaying("Next", "", "", "", "image/jpeg");
    r.sender->setPlaybackInfo(60000,180000,false);
    r.exchange();
    bool paused=false,cleared=false;
    for(size_t i=previousRequests;i<r.rx.requests.size();++i) {
        const auto& req=r.rx.requests[i];
        if(req.uri!="/command") continue;
        auto root=airplay::bplist::decode(bytesOf(req.body));
        if(!root) continue;
        const auto type=root->find("type")->asStr();const auto* params=root->find("params");
        if(type=="updateMRPlaybackState") paused=params->find("mrPlaybackState")->asInt()==2;
        if(type=="updateMRNowPlayingInfo") {
            const auto* info=params->find("params");
            cleared=!info->find("kMRMediaRemoteNowPlayingInfoArtworkData");
            CHECK(info->find("kMRMediaRemoteNowPlayingInfoElapsedTime")->r==60.0,"seek publishes new position");
            CHECK(info->find("kMRMediaRemoteNowPlayingInfoPlaybackRate")->r==0.0,"paused clock does not advance");
        }
    }
    CHECK(paused&&cleared,"pause state and cover removal transmitted on track change");
    r.sender->onTcpConnected(RaopTcp::Event,{r.localIp,50001},{r.peerIp,7001});
    auto push=[&](const std::string& type,const std::string& command,bool corrupt=false) {
        using namespace airplay::bplist;
        auto body=encode(Value::object({{"type",Value::str(type)},{"value",Value::str(command)}}));
        std::string request="POST /command RTSP/1.0\r\nCSeq: 42\r\nContent-Length: "+std::to_string(body.size())+"\r\n\r\n"+strOf(body);
        auto frame=r.rx.pushEvent(request);
        if(corrupt) frame.back()^=1;
        r.sender->onTcpData(RaopTcp::Event,spanOf(std::string_view(frame).substr(0,7)));
        r.sender->onTcpData(RaopTcp::Event,spanOf(std::string_view(frame).substr(7)));
    };
    for(const auto* cmd : {"play","paus","plps","nitm","pitm"}) push("sendMediaRemoteCommand",cmd);
    CHECK(commands.size()==5,"all five authenticated commands dispatched exactly once");
    push("updateInfo","play");push("sendMediaRemoteCommand","unsupported");
    CHECK(commands.size()==5,"unrelated and unsupported commands ignored");
    push("sendMediaRemoteCommand","play",true);
    CHECK(commands.size()==5,"tampered ciphertext cannot control player");
    r.sender->stop();push("sendMediaRemoteCommand","play");
    CHECK(commands.size()==5,"closed session cannot control player");
    return g_failures ? 1 : 0;
}
