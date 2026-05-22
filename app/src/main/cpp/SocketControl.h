#ifndef SOCKET_CONTROL_H
#define SOCKET_CONTROL_H

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <string>
#include <pthread.h>
#include <cstring>
#include "Logger.h"

extern bool  hackMap;
extern bool  hackCamXa;
extern float camXaValue;
extern bool  hackSkin;
extern bool  hackESP;

static bool main_thread_flag = true;

inline void* socket_server_thread(void*) {
    int server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd < 0) return nullptr;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    const char* name = "StarcoolPRO_socket";
    addr.sun_path[0] = '\0';
    strcpy(addr.sun_path + 1, name);
    socklen_t len = offsetof(struct sockaddr_un, sun_path) + 1 + strlen(name);

    if (::bind(server_fd, (sockaddr*)&addr, len) < 0) { close(server_fd); return nullptr; }
    if (listen(server_fd, 5) < 0)                     { close(server_fd); return nullptr; }

    char buf[256] = {0};
    while (main_thread_flag) {
        socklen_t al = sizeof(addr);
        int sock = accept(server_fd, (sockaddr*)&addr, &al);
        if (sock < 0) continue;
        int n = read(sock, buf, 255);
        if (n > 0) {
            buf[n] = '\0';
            std::string msg(buf);
            while (!msg.empty() && (msg.back()=='\n'||msg.back()=='\r'||msg.back()==' ')) msg.pop_back();

            if      (msg=="MAP_HACK:1")    hackMap   = true;
            else if (msg=="MAP_HACK:0")    hackMap   = false;
            else if (msg=="SKIN_ENABLE:1") hackSkin  = true;
            else if (msg=="SKIN_ENABLE:0") hackSkin  = false;
            else if (msg=="ESP_ENABLE:1")  hackESP   = true;
            else if (msg=="ESP_ENABLE:0")  hackESP   = false;
            else if (msg.find("CAMXA_VAL:")==0) {
                camXaValue = std::stof(msg.substr(10));
                hackCamXa  = camXaValue > 0.0f;
            }
        }
        close(sock);
        memset(buf, 0, sizeof(buf));
    }
    close(server_fd);
    return nullptr;
}

#endif
