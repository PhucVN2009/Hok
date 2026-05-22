package com.star.android.utils;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketClient {
    private static final String SOCKET_NAME = "StarcoolHOK_socket";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static void send(final String msg) {
        executor.execute(() -> {
            try (LocalSocket s = new LocalSocket()) {
                s.connect(new LocalSocketAddress(SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));
                OutputStream out = s.getOutputStream();
                out.write(msg.getBytes());
                out.flush();
            } catch (Exception ignored) {}
        });
    }

    public static void setMapHack(boolean on) { send("MAP_HACK:" + (on ? "1" : "0")); }
}