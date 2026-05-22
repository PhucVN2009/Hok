package com.star.android.utils;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketClient {
    private static final String SOCKET_NAME = "StarcoolPRO_socket";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ── Gửi message tới native ────────────────────────────────────
    private static void send(final String message) {
        executor.execute(() -> {
            try (LocalSocket socket = new LocalSocket()) {
                socket.connect(new LocalSocketAddress(
                        SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));
                OutputStream out = socket.getOutputStream();
                out.write(message.getBytes());
                out.flush();
            } catch (Exception ignored) {}
        });
    }

    // ── Existing ──────────────────────────────────────────────────
    public static void setMapHack(boolean active) {
        send("MAP_HACK:" + (active ? "1" : "0"));
    }

    public static void setCamXa(float value) {
        send("CAMXA_VAL:" + value);
    }

    // ── Mod Skin ──────────────────────────────────────────────────
    /** Bật/tắt mod skin */
    public static void setSkinEnable(boolean enable) {
        send("SKIN_ENABLE:" + (enable ? "1" : "0"));
    }

    /** Set hero ID cần mod skin (ví dụ: 105 = Toro) */
    public static void setSkinHeroId(int heroId) {
        send("SKIN_HERO:" + heroId);
    }

    /** Set skin ID muốn dùng */
    public static void setSkinId(int skinId) {
        send("SKIN_ID:" + skinId);
    }

    /**
     * Gửi tất cả thông tin skin cùng lúc
     * @param enable  bật/tắt
     * @param heroId  ID hero (vd 105)
     * @param skinId  ID skin (vd 1, 2, 3…)
     */
    public static void setModSkin(boolean enable, int heroId, int skinId) {
        send("SKIN_HERO:" + heroId);
        send("SKIN_ID:"   + skinId);
        send("SKIN_ENABLE:" + (enable ? "1" : "0"));
    }

    // ── ESP ───────────────────────────────────────────────────────
    /** Bật/tắt ESP (map hack / actor visibility) */
    public static void setESP(boolean enable) {
        send("ESP_ENABLE:" + (enable ? "1" : "0"));
    }
}
