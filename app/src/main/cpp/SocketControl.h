#ifndef SOCKET_CONTROL_H
#define SOCKET_CONTROL_H

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <string>
#include <pthread.h>
#include <cstring>
#include <cstdlib>
#include "Logger.h"

// ── Extern flags từ native-lib.cpp ───────────────────────────────
extern bool     hackMap;
extern bool     hackCamXa;
extern float    camXaValue;
extern bool     hackSkin;
extern bool     hackESP;
extern uint32_t g_skinHeroId;
extern uint16_t g_skinId;

static bool main_thread_flag = true;

// ── Socket server (Android → Native) ─────────────────────────────
inline void* socket_server_thread(void* arg) {
    int server_fd, new_socket;
    struct sockaddr_un address;
    socklen_t addrlen = sizeof(address);

    if ((server_fd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1)
        return nullptr;

    memset(&address, 0, sizeof(address));
    address.sun_family = AF_UNIX;
    const char* socket_name = "StarcoolPRO_socket";
    address.sun_path[0] = '\0';
    strcpy(address.sun_path + 1, socket_name);
    socklen_t len = offsetof(struct sockaddr_un, sun_path) + 1 + strlen(socket_name);

    if (::bind(server_fd, (struct sockaddr*)&address, len) < 0) {
        close(server_fd); return nullptr;
    }
    if (listen(server_fd, 5) < 0) {
        close(server_fd); return nullptr;
    }

    char buffer[1024] = {0};
    while (main_thread_flag) {
        new_socket = accept(server_fd, (struct sockaddr*)&address, &addrlen);
        if (new_socket < 0) continue;

        int n = read(new_socket, buffer, 1023);
        if (n > 0) {
            buffer[n] = '\0';
            std::string msg(buffer);
            // Trim trailing whitespace
            while (!msg.empty() && (msg.back()==' '||msg.back()=='\n'||msg.back()=='\r'||msg.back()=='\t'))
                msg.pop_back();

            // ── Existing commands ──────────────────────────────
            if (msg.find("MAP_HACK:") == 0) {
                hackMap = (msg.substr(9) == "1");
                LOGI("[SOCK] hackMap=%d", hackMap);
            }
            else if (msg.find("CAMXA_VAL:") == 0) {
                camXaValue = std::stof(msg.substr(10));
                hackCamXa  = (camXaValue > 0.0f);
            }
            // ── Mod Skin commands ──────────────────────────────
            else if (msg.find("SKIN_ENABLE:") == 0) {
                hackSkin = (msg.substr(12) == "1");
                LOGI("[SOCK] hackSkin=%d", hackSkin);
            }
            else if (msg.find("SKIN_HERO:") == 0) {
                g_skinHeroId = (uint32_t)std::stoul(msg.substr(10));
                LOGI("[SOCK] skinHeroId=%u", g_skinHeroId);
            }
            else if (msg.find("SKIN_ID:") == 0) {
                g_skinId = (uint16_t)std::stoul(msg.substr(8));
                LOGI("[SOCK] skinId=%u", g_skinId);
            }
            // ── ESP command ────────────────────────────────────
            else if (msg.find("ESP_ENABLE:") == 0) {
                hackESP = (msg.substr(11) == "1");
                LOGI("[SOCK] hackESP=%d", hackESP);
            }
        }
        close(new_socket);
        memset(buffer, 0, sizeof(buffer));
    }
    close(server_fd);
    return nullptr;
}

#endif // SOCKET_CONTROL_H
