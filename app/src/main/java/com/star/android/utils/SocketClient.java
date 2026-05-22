package com.star.android.utils;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketClient {
    private static final String SOCKET_NAME = "StarcoolPRO_socket";
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

    public static void setMapHack(boolean on)   { send("MAP_HACK:"    + (on?"1":"0")); }
    public static void setSkinUnlock(boolean on) { send("SKIN_ENABLE:" + (on?"1":"0")); }
    public static void setESP(boolean on)        { send("ESP_ENABLE:"  + (on?"1":"0")); }
    public static void setCamXa(float v)         { send("CAMXA_VAL:"  + v); }
}
