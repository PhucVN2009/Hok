package com.star.android;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class Initialize {

    // Tên binary daemon trong assets (phải khớp với file trong assets/)
    public String daemonEXE = "Injector";
    public static String socket;
    private static final String TAG = "DAEMON_INIT";

    public Initialize(final Context context) {
        System.loadLibrary("Starcool");

        // 1. Khởi chạy game
        try {
            PackageManager packageManager = context.getPackageManager();
            Intent launchIntent = packageManager.getLaunchIntentForPackage("com.dts.freefiremax");
            if (launchIntent != null) {
                context.startActivity(launchIntent);
            } else {
                Log.e(TAG, "Game package not found!");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // 2. Chạy setup trong background thread để tránh ANR
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String outPath = context.getFilesDir().getPath();
                    String daemonPath = outPath + "/" + daemonEXE;

                    // Dừng daemon cũ để tránh "Text file busy" (ETXTBSY)
                    Runtime.getRuntime().exec("su -c killall -9 " + daemonEXE);
                    Thread.sleep(300);

                    // Copy binary từ assets
                    boolean success = CopyFromAssets(context, outPath, daemonEXE);

                    if (success) {
                        // Cấp quyền thực thi
                        Runtime.getRuntime().exec("su -c chmod 777 " + daemonPath).waitFor();

                        socket = "su -c ./ " + daemonPath;

                        // Khởi chạy daemon
                        Runtime.getRuntime().exec("su -c " + daemonPath);

                        // Quay lại main thread để hiển thị Floater/Menu
                        new Handler(Looper.getMainLooper()).post(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, "Menu Initialized!", Toast.LENGTH_SHORT).show();
                                new Floater(context);
                            }
                        });
                    } else {
                        Log.e(TAG, "Failed to copy daemon from assets");
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error in background init: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * Copy binary từ APK assets vào thư mục data nội bộ.
     */
    public static boolean CopyFromAssets(Context ctx, String outPath, String fileName) {
        File file = new File(outPath);
        if (!file.exists()) {
            file.mkdirs();
        }

        InputStream inputStream = null;
        FileOutputStream fileOutputStream = null;

        try {
            inputStream = ctx.getAssets().open(fileName);
            File outFile = new File(file, fileName);
            fileOutputStream = new FileOutputStream(outFile);

            byte[] buffer = new byte[4096];
            int byteRead;
            while ((byteRead = inputStream.read(buffer)) != -1) {
                fileOutputStream.write(buffer, 0, byteRead);
            }

            fileOutputStream.flush();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Copy Error: " + e.getMessage());
            return false;
        } finally {
            try {
                if (inputStream != null) inputStream.close();
                if (fileOutputStream != null) fileOutputStream.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
