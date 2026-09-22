#include "airplay_probe.h"
#include <atomic>
#include <csignal>
#include <fstream>
#include <iostream>
#include <iterator>
#include <sys/stat.h>
#include <unistd.h>
#include <fcntl.h>
namespace {
volatile std::sig_atomic_t cancelled=0;
void interrupt(int) { cancelled=1; }
std::string read(const std::string& path) {
    std::ifstream f(path);
    return {std::istreambuf_iterator<char>(f),std::istreambuf_iterator<char>()};
}
}
int main(int argc,char** argv) {
    if (argc!=7 && argc!=8) {
        std::cerr<<"usage: fnmusic_airplay_probe IP PORT SECONDS DEVICE_ID PIN_FILE CREDS_FILE\n";
        return 2;
    }
    std::signal(SIGINT,interrupt); std::signal(SIGTERM,interrupt);
    fnmusic::ProbeCallbacks cb;
    cb.exerciseFlush=argc==8 && std::string(argv[7])=="controls";
    cb.event=[](const std::string& e) { std::cout<<"EVENT "<<e<<std::endl; };
    cb.cancelled=[] { return cancelled!=0; };
    cb.pollPin=[&] {
        std::ifstream f(argv[5]); std::string pin;
        if (f>>pin) { ::unlink(argv[5]); return pin; }
        return std::string();
    };
    cb.saveCredentials=[&](const std::string& text) {
        int fd=::open(argv[6],O_WRONLY|O_CREAT|O_TRUNC,0600);
        if (fd<0) throw std::runtime_error("credential storage failed");
        ::fchmod(fd,0600);
        const auto n=::write(fd,text.data(),text.size()); ::close(fd);
        if (n!=static_cast<ssize_t>(text.size())) throw std::runtime_error("credential storage failed");
    };
    try {
        auto r=fnmusic::runProbe(argv[1],std::stoi(argv[2]),std::stoi(argv[3]),argv[4],read(argv[6]),cb);
        std::cout<<"RESULT "<<r.json()<<std::endl;
        return r.status=="passed" ? 0:(r.status=="blocked" ? 3:1);
    } catch (...) { std::cerr<<"invalid arguments or local I/O failure\n"; return 2; }
}
