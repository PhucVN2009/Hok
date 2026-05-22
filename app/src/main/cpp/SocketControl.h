#ifndef SOCKET_CONTROL_H
#define SOCKET_CONTROL_H

#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include <string>
#include <pthread.h>
#include <cstring>
#include "Logger.h"

extern bool hackMap;

static bool main_thread_flag = true;

inline void* socket_server_thread(void* arg) {
    int server_fd, new_socket;
    struct sockaddr_un address;

    server_fd = socket(AF_UNIX, SOCK_STREAM, 0);
    if (server_fd == -1) return nullptr;

    memset(&address, 0, sizeof(address));
    address.sun_family = AF_UNIX;
    const char* socket_name = "StarcoolHOK_socket";
    address.sun_path[0] = '\0';
    strcpy(address.sun_path + 1, socket_name);
    socklen_t len = offsetof(struct sockaddr_un, sun_path) + 1 + strlen(socket_name);

    if (::bind(server_fd, (struct sockaddr*)&address, len) < 0) {
        close(server_fd);
        return nullptr;
    }
    if (listen(server_fd, 5) < 0) {
        close(server_fd);
        return nullptr;
    }

    char buf[128] = {0};
    while (main_thread_flag) {
        socklen_t al = sizeof(address);
        new_socket = accept(server_fd, (struct sockaddr*)&address, &al);
        if (new_socket < 0) continue;

        int n = read(new_socket, buf, 127);
        if (n > 0) {
            buf[n] = '\0';
            std::string msg(buf);
            while (!msg.empty() && (msg.back() == '\n' || msg.back() == '\r'))
                msg.pop_back();
            if      (msg == "MAP_HACK:1") hackMap = true;
            else if (msg == "MAP_HACK:0") hackMap = false;
        }
        close(new_socket);
        memset(buf, 0, sizeof(buf));
    }
    close(server_fd);
    return nullptr;
}

#endif // SOCKET_CONTROL_H
