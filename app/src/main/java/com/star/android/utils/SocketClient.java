package com.star.android.utils;

import android.net.LocalSocket;
import android.net.LocalSocketAddress;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SocketClient {
    private static final String SOCKET_NAME = "StarcoolPRO_socket";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    // ── Fire-and-forget (giống gốc) ───────────────────────────────────────
    private static void send(final String message) {
        executor.execute(() -> {
            try (LocalSocket socket = new LocalSocket()) {
                socket.connect(new LocalSocketAddress(
                        SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));
                OutputStream out = socket.getOutputStream();
                out.write(message.getBytes());
                out.flush();
            } catch (Exception ignored) {
            }
        });
    }

    // ── Query: gửi lệnh và đọc 1 dòng response (dùng cho GET_ESP) ────────
    // Gọi từ background thread, KHÔNG gọi trên main thread
    private static String query(String message) {
        try (LocalSocket socket = new LocalSocket()) {
            socket.connect(new LocalSocketAddress(
                    SOCKET_NAME, LocalSocketAddress.Namespace.ABSTRACT));
            socket.setSoTimeout(300);
            OutputStream out = socket.getOutputStream();
            out.write(message.getBytes());
            out.flush();
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()));
            String line = reader.readLine();
            return line != null ? line.trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    // ── Các lệnh gốc (giữ nguyên) ─────────────────────────────────────────
    public static void setMapHack(boolean active) {
        send("MAP_HACK:" + (active ? "1" : "0"));
    }

    public static void setCamXa(float value) {
        send("CAMXA_VAL:" + value);
    }

    // ── ESP commands (thêm mới) ────────────────────────────────────────────
    public static void setEspEnable(boolean active) {
        send("ESP_ENABLE:" + (active ? "1" : "0"));
    }

    // Gọi từ background thread — trả về raw string hoặc ""
    public static String getEspData() {
        return query("GET_ESP");
    }

    public static void sendScreenSize(int w, int h) {
        send("SCREEN:" + w + "," + h);
    }

    // ── Parse response "N|sx,sy,wx,wz,hp,maxhp,camp,enemy,name|..." ───────
    public static class ESPHero {
        public float   screenX, screenY;
        public float   worldX,  worldZ;
        public int     hp, maxHp;
        public int     camp;
        public boolean isEnemy;
        public String  name;

        public float hpPercent() {
            return maxHp > 0 ? Math.min(1f, (float) hp / maxHp) : 0f;
        }
    }

    public static ESPHero[] parseEspData(String raw) {
        if (raw == null || raw.isEmpty()) return new ESPHero[0];
        String[] parts = raw.split("\\|");
        if (parts.length < 1) return new ESPHero[0];
        int count;
        try { count = Integer.parseInt(parts[0].trim()); }
        catch (NumberFormatException e) { return new ESPHero[0]; }
        if (count <= 0) return new ESPHero[0];

        ESPHero[] out = new ESPHero[count];
        int filled = 0;
        for (int i = 1; i < parts.length && filled < count; i++) {
            String[] f = parts[i].split(",", 9);
            if (f.length < 9) continue;
            try {
                ESPHero h  = new ESPHero();
                h.screenX  = Float.parseFloat(f[0].trim());
                h.screenY  = Float.parseFloat(f[1].trim());
                h.worldX   = Float.parseFloat(f[2].trim());
                h.worldZ   = Float.parseFloat(f[3].trim());
                h.hp       = Integer.parseInt(f[4].trim());
                h.maxHp    = Integer.parseInt(f[5].trim());
                h.camp     = Integer.parseInt(f[6].trim());
                h.isEnemy  = f[7].trim().equals("1");
                h.name     = f[8].trim();
                out[filled++] = h;
            } catch (NumberFormatException ignored) {}
        }
        if (filled == count) return out;
        ESPHero[] trimmed = new ESPHero[filled];
        System.arraycopy(out, 0, trimmed, 0, filled);
        return trimmed;
    }
}
