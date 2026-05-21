#ifndef SOCKET_CONTROL_H
#define SOCKET_CONTROL_H

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <string>
#include <pthread.h>
#include <cstring>
#include <stdio.h>
#include "Logger.h"

// ── Extern flags (defined in native-lib.cpp) ──────────────────────────────
extern bool  hackMap;
extern bool  hackCamXa;
extern float camXaValue;
extern bool  hackEsp;

// ── ESP shared data ───────────────────────────────────────────────────────
#define ESP_MAX_HEROES 10

struct ESPHero {
    float screenX, screenY; // pixel coords (Unity Y already flipped)
    float worldX,  worldZ;  // world coords fallback
    int   hp,      maxHp;
    int   camp;             // 1 or 2
    bool  isEnemy;
    bool  valid;
    char  name[64];
};

extern ESPHero g_espHeroes[ESP_MAX_HEROES];
extern int     g_espHeroCount;
extern int     g_selfCamp;
extern float   g_screenW;
extern float   g_screenH;

static pthread_mutex_t g_espMutex = PTHREAD_MUTEX_INITIALIZER;
static bool main_thread_flag = true;

// ── Serialize ESP → "N|sx,sy,wx,wz,hp,maxhp,camp,enemy,name|..." ─────────
static std::string BuildEspResponse() {
    pthread_mutex_lock(&g_espMutex);
    char buf[4096];
    int  pos = snprintf(buf, sizeof(buf), "%d", g_espHeroCount);
    for (int i = 0; i < g_espHeroCount && pos < (int)sizeof(buf) - 128; i++) {
        const ESPHero& h = g_espHeroes[i];
        if (!h.valid) continue;
        pos += snprintf(buf + pos, sizeof(buf) - pos,
            "|%.1f,%.1f,%.1f,%.1f,%d,%d,%d,%d,%s",
            h.screenX, h.screenY, h.worldX, h.worldZ,
            h.hp, h.maxHp, h.camp, h.isEnemy ? 1 : 0, h.name);
    }
    pthread_mutex_unlock(&g_espMutex);
    return std::string(buf);
}

// ── Socket server thread (same pattern as original) ───────────────────────
inline void* socket_server_thread(void* arg) {
    int server_fd, new_socket;
    struct sockaddr_un address;
    socklen_t addrlen = sizeof(address);

    if ((server_fd = socket(AF_UNIX, SOCK_STREAM, 0)) == -1)
        return nullptr;

    memset(&address, 0, sizeof(struct sockaddr_un));
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

        int valread = read(new_socket, buffer, 1024);
        if (valread > 0) {
            buffer[valread] = '\0';
            std::string msg(buffer);
            msg.erase(msg.find_last_not_of(" \n\r\t") + 1);

            if (msg.find("MAP_HACK:") == 0) {
                hackMap = (msg.substr(9) == "1");
            }
            else if (msg.find("CAMXA_VAL:") == 0) {
                camXaValue = std::stof(msg.substr(10));
                hackCamXa  = (camXaValue > 0.0f);
            }
            // ── ESP: bật/tắt ──────────────────────────────────────────
            else if (msg.find("ESP_ENABLE:") == 0) {
                hackEsp = (msg.substr(11) == "1");
                if (!hackEsp) {
                    pthread_mutex_lock(&g_espMutex);
                    g_espHeroCount = 0;
                    pthread_mutex_unlock(&g_espMutex);
                }
            }
            // ── ESP: Java hỏi data → C++ trả lời trên cùng connection ─
            else if (msg == "GET_ESP") {
                std::string resp = BuildEspResponse() + "\n";
                write(new_socket, resp.c_str(), resp.size());
            }
            // ── Screen size (Java gửi 1 lần lúc khởi động) ────────────
            else if (msg.find("SCREEN:") == 0) {
                std::string dims = msg.substr(7);
                size_t comma = dims.find(',');
                if (comma != std::string::npos) {
                    g_screenW = std::stof(dims.substr(0, comma));
                    g_screenH = std::stof(dims.substr(comma + 1));
                }
            }
        }
        close(new_socket);
        memset(buffer, 0, 1024);
    }
    close(server_fd);
    return nullptr;
}

#endif // SOCKET_CONTROL_H
