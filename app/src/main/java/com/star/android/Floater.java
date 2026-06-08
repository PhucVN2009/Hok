package com.star.android;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.text.Html;
import android.util.Base64;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.InputStream;
import android.os.AsyncTask;
import java.io.IOException;
import android.content.res.AssetManager;
import com.topjohnwu.superuser.ipc.RootService;
import java.util.Timer;

public class Floater {

    // Callbacks
    private static MSGConnection conn;
    private static Messenger remoteMessenger;
    private static final Messenger myMessenger = new Messenger(new Handler(Looper.getMainLooper(), null));

    // Native Functions
    public static native void Functions();
    public static native void ChangesID(int ID, int Value);
    public static native void ftKZXivSr(Context context);
    public static native String getBase64Icon();

    public static boolean serviceTestQueued;
    private Timer timer;
    public static int SCREEN_WIDTH = 0;
    public static int SCREEN_HEIGHT = 0;

    public static int Width() {
        return SCREEN_WIDTH;
    }

    public static int Height() {
        return SCREEN_HEIGHT;
    }

    public IBinder onBind(Intent intent) {
        return null;
    }

    // Variables Menu
    private int buttonClick = 0;
    public static int PrimaryColor = 0xFFA70000;

    private static Context context;
    private static Utils utils;

    public Bitmap getBitmapFromAssets(Context context, String fileName) {
        AssetManager assetManager = context.getAssets();
        InputStream istr;
        Bitmap bitmap = null;
        try {
            istr = assetManager.open(fileName);
            bitmap = BitmapFactory.decodeStream(istr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private Bitmap base64ToBitmap(String base64Str) {
        try {
            byte[] decodedBytes = Base64.decode(base64Str, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.length);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Window System Part
    private WindowManager windowManager;
    private WindowManager.LayoutParams windowManagerParams;
    private FrameLayout frameLayout;
    private static boolean isRunning = false;

    private class LoadImageTask extends AsyncTask<String, Void, Bitmap> {
        private ImageView imageView;

        public LoadImageTask(ImageView imageView) {
            this.imageView = imageView;
        }

        protected Bitmap doInBackground(String... urls) {
            String url = urls[0];
            Bitmap bitmap = null;
            try {
                InputStream in = new java.net.URL(url).openStream();
                bitmap = BitmapFactory.decodeStream(in);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return bitmap;
        }

        protected void onPostExecute(Bitmap result) {
            if (result != null) {
                imageView.setImageBitmap(result);
            }
        }
    }

    ESPView drawView;
    WindowManager.LayoutParams windowManagerDrawViewParams;
    public static native void PxbftKZXivSr(ESPView espView, Canvas canvas, int width, int height);

    public void DrawCanvas() {
        drawView = new ESPView(context);
        windowManagerDrawViewParams = new WindowManager.LayoutParams(-1, -1, this.getLayoutType(), 56, -3);
        windowManagerDrawViewParams.gravity = Gravity.TOP | Gravity.START;
        windowManagerDrawViewParams.x = 0;
        windowManagerDrawViewParams.y = 0;
        windowManagerDrawViewParams.alpha = 0.8f;
        windowManager.addView(drawView, windowManagerDrawViewParams);
    }

    static LinearLayout container_features;

    private int getLayoutType() {
        if (Build.VERSION.SDK_INT >= 26) return 2038;
        if (Build.VERSION.SDK_INT >= 24) return 2002;
        if (Build.VERSION.SDK_INT >= 23) return 2005;
        return 2003;
    }

    public Floater(Context globContext) {
        context = globContext;
        utils = new Utils(context);
        onCreate();
    }

    public void onCreate() {
        onCreateSystemWindow();
        onCreateTemplate();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public void onCreateTemplate() {
        GradientDrawable gradientDrawable_container = new GradientDrawable();
        gradientDrawable_container.setColor(0xFF111111);
        gradientDrawable_container.setCornerRadius(utils.FixDP(8));

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        final LinearLayout container_menu = new LinearLayout(context);
        container_menu.setLayoutParams(new LinearLayout.LayoutParams(utils.FixDP(210), ViewGroup.LayoutParams.WRAP_CONTENT));
        container_menu.setBackgroundColor(0xFF111111);
        container_menu.setVisibility(View.GONE);
        container_menu.setOrientation(LinearLayout.VERTICAL);
        container_menu.setBackground(gradientDrawable_container);

        String base64Icon = getBase64Icon();

        final ImageBase64 icon_cheat = new ImageBase64(context);
        icon_cheat.setLayoutParams(new LinearLayout.LayoutParams(utils.FixDP(80), utils.FixDP(50)));

        if (base64Icon != null && !base64Icon.isEmpty()) {
            icon_cheat.setImageBase64(base64Icon);
        } else {
            Bitmap defaultIcon = Bitmap.createBitmap(80, 50, Bitmap.Config.ARGB_8888);
            defaultIcon.eraseColor(PrimaryColor);
            icon_cheat.setImageBitmap(defaultIcon);
        }

        icon_cheat.setOnTouchListener(onTouchListener());
        icon_cheat.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                icon_cheat.setVisibility(View.GONE);
                container_menu.setVisibility(View.VISIBLE);
            }
        });

        LinearLayout container_top = new LinearLayout(context);
        container_top.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container_top.setPadding(utils.FixDP(5), utils.FixDP(5), utils.FixDP(5), utils.FixDP(5));
        container_top.setGravity(Gravity.CENTER);
        container_top.setOrientation(LinearLayout.HORIZONTAL);

        ImageBase64 icon_menu = new ImageBase64(context);
        icon_menu.setLayoutParams(new LinearLayout.LayoutParams(utils.FixDP(80), utils.FixDP(50)));

        if (base64Icon != null && !base64Icon.isEmpty()) {
            icon_menu.setImageBase64(base64Icon);
        } else {
            Bitmap defaultMenuIcon = Bitmap.createBitmap(80, 50, Bitmap.Config.ARGB_8888);
            defaultMenuIcon.eraseColor(PrimaryColor);
            icon_menu.setImageBitmap(defaultMenuIcon);
        }

        final LinearLayout container_center = new LinearLayout(context);
        container_center.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, utils.FixDP(200)));
        container_center.setGravity(Gravity.CENTER);

        final ScrollView scrollView_center = new ScrollView(context);
        scrollView_center.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        container_features = new LinearLayout(context);
        container_features.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container_features.setPadding(utils.FixDP(5), 0, utils.FixDP(5), 0);
        container_features.setOrientation(LinearLayout.VERTICAL);

        final ProgressBar progressBar = new ProgressBar(context);
        progressBar.setLayoutParams(new LinearLayout.LayoutParams(utils.FixDP(40), utils.FixDP(40)));
        progressBar.getIndeterminateDrawable().setColorFilter(PrimaryColor, PorterDuff.Mode.SRC_IN);

        LinearLayout container_bottom = new LinearLayout(context);
        container_bottom.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container_bottom.setPadding(utils.FixDP(3), utils.FixDP(8), utils.FixDP(3), utils.FixDP(3));
        container_bottom.setOrientation(LinearLayout.VERTICAL);
        container_bottom.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);

        GradientDrawable gradientDrawable_inject_close = new GradientDrawable();
        gradientDrawable_inject_close.setColor(PrimaryColor);
        gradientDrawable_inject_close.setCornerRadius(utils.FixDP(8));
        RippleDrawable rippleDrawable = new RippleDrawable(ColorStateList.valueOf(0xFF111111), gradientDrawable_inject_close, null);

        final Button inject_close = new Button(context);
        inject_close.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, utils.FixDP(33)));
        inject_close.setPadding(0, 0, 0, 0);
        inject_close.setText("CLOSE");
        inject_close.setTextSize(10);
        inject_close.setTextColor(0xFFFFFFFF);
        inject_close.setBackground(rippleDrawable);
        inject_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                icon_cheat.setVisibility(View.VISIBLE);
                container_menu.setVisibility(View.GONE);
            }
        });

        // Xây dựng cây layout
        frameLayout.addView(container);
        container.addView(icon_cheat);
        container.addView(container_menu);

        container_menu.addView(container_top);
        container_top.addView(icon_menu);

        container_menu.addView(container_center);
        container_center.addView(scrollView_center);
        scrollView_center.addView(container_features);

        container_menu.addView(container_bottom);
        container_bottom.addView(inject_close);

        Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Functions();
                ftKZXivSr(context);
            }
        }, 5000);
    }

    @SuppressLint("ClickableViewAccessibility")
    public void onCreateSystemWindow() {
        int LAYOUT_FLAG;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            LAYOUT_FLAG = WindowManager.LayoutParams.TYPE_PHONE;
        }

        frameLayout = new FrameLayout(context);
        frameLayout.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        frameLayout.setOnTouchListener(onTouchListener());
        frameLayout.setAlpha(0.8f);

        windowManagerParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                LAYOUT_FLAG,
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL |
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                        WindowManager.LayoutParams.FLAG_SPLIT_TOUCH,
                PixelFormat.TRANSLUCENT);
        windowManagerParams.gravity = Gravity.TOP | Gravity.LEFT;
        windowManagerParams.x = 15;
        windowManagerParams.y = 15;

        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DrawCanvas();
        windowManager.addView(frameLayout, windowManagerParams);
    }

    private View.OnTouchListener onTouchListener() {
        return new View.OnTouchListener() {
            private int x;
            private int y;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        x = (int) event.getRawX();
                        y = (int) event.getRawY();
                        frameLayout.setAlpha(0.8f);
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int nowX = (int) event.getRawX();
                        int nowY = (int) event.getRawY();
                        int movedX = nowX - x;
                        int movedY = nowY - y;
                        x = nowX;
                        y = nowY;
                        windowManagerParams.x = windowManagerParams.x + movedX;
                        windowManagerParams.y = windowManagerParams.y + movedY;
                        windowManager.updateViewLayout(frameLayout, windowManagerParams);
                        break;
                    case MotionEvent.ACTION_UP:
                        frameLayout.setAlpha(0.9f);
                        break;
                    default:
                        break;
                }
                return false;
            }
        };
    }

    public static void addCategory(String name) {
        GradientDrawable gradientDrawable = new GradientDrawable();
        gradientDrawable.setColor(PrimaryColor);
        gradientDrawable.setCornerRadius(utils.FixDP(4));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, utils.FixDP(25)));
        linearLayout.setBackground(gradientDrawable);
        linearLayout.setGravity(Gravity.CENTER);

        TextView textView = new TextView(context);
        textView.setText(name);
        textView.setTextSize(11);
        textView.setTextColor(0xFFFFFFFF);

        container_features.addView(linearLayout);
        linearLayout.addView(textView);
    }

    public static void addSwitch(final String name, final int ID) {
        if (remoteMessenger == null) {
            serviceTestQueued = true;
            Intent intent = new Intent(context, RootServices.class);
            MSGConnection mSGConnection = new MSGConnection();
            conn = mSGConnection;
            RootService.bind(intent, (ServiceConnection) mSGConnection);
        }

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        linearLayout.setPadding(0, utils.FixDP(2), 0, utils.FixDP(2));
        linearLayout.setOrientation(LinearLayout.HORIZONTAL);
        linearLayout.setGravity(Gravity.CENTER);

        TextView textView = new TextView(context);
        textView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setText(name);
        textView.setTextColor(0xFFFFFFFF);
        textView.setTextSize(11);

        final SwitchStyle switchStyle = new SwitchStyle(context);
        switchStyle.setLayoutParams(new LinearLayout.LayoutParams(65, 35));
        switchStyle.setOnCheckedChangeListener(new SwitchStyle.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(SwitchStyle view, boolean isChecked) {
                ChangesID(ID, 0);
            }
        });

        linearLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchStyle.setChecked(!switchStyle.isChecked());
            }
        });

        linearLayout.addView(textView);
        linearLayout.addView(switchStyle);
        container_features.addView(linearLayout);
    }

    public static void addSeekBar(final String name, int value, int max, final String type, final int ID) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        linearLayout.setPadding(0, utils.FixDP(2), 0, utils.FixDP(2));
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        final TextView textView = new TextView(context);
        textView.setText(name.concat(": ") + value + type);
        textView.setTextSize(11);
        textView.setTextColor(0xFFFFFFFF);
        if (type.equals("Color")) {
            if (value == 0) textView.setText(Html.fromHtml(name + ": <font color='#ffffff'>Branco</font>"));
            else if (value == 1) textView.setText(Html.fromHtml(name + ": <font color='#00FF00'>Verde</font>"));
            else if (value == 2) textView.setText(Html.fromHtml(name + ": <font color='#0000FF'>Azul</font>"));
            else if (value == 3) textView.setText(Html.fromHtml(name + ": <font color='#FF0000'>Vermelho</font>"));
            else if (value == 4) textView.setText(Html.fromHtml(name + ": <font color='#000000'>Preto</font>"));
            else if (value == 5) textView.setText(Html.fromHtml(name + ": <font color='#FFFF00'>Amarelo</font>"));
            else if (value == 6) textView.setText(Html.fromHtml(name + ": <font color='#00FFFF'>Ciano</font>"));
            else if (value == 7) textView.setText(Html.fromHtml(name + ": <font color='#FF00FF'>Magenta</font>"));
            else if (value == 8) textView.setText(Html.fromHtml(name + ": <font color='#808080'>Cinza</font>"));
            else if (value == 9) textView.setText(Html.fromHtml(name + ": <font color='#A020F0'>Roxo</font>"));
        } else if (type.equals("BoxType")) {
            if (value == 0) textView.setText(name.concat(": Stroke"));
            else if (value == 1) textView.setText(name.concat(": Filled"));
            else if (value == 2) textView.setText(name.concat(": Rounded"));
        } else if (type.equals("LineType")) {
            if (value == 0) textView.setText(name.concat(": Top"));
            else if (value == 1) textView.setText(name.concat(": Center"));
            else if (value == 2) textView.setText(name.concat(": Bottom"));
        }

        SeekBar seekBar = new SeekBar(context);
        seekBar.getThumb().setColorFilter(PrimaryColor, PorterDuff.Mode.SRC_IN);
        seekBar.getProgressDrawable().setColorFilter(PrimaryColor, PorterDuff.Mode.SRC_IN);
        seekBar.setProgress(value);
        seekBar.setMax(max);
        if (type.equals("Color")) seekBar.setMax(9);
        else if (type.equals("BoxType")) seekBar.setMax(2);
        else if (type.equals("LineType")) seekBar.setMax(2);

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                if (type.equals("Color")) {
                    if (i == 0) textView.setText(Html.fromHtml(name + ": <font color='#ffffff'>Branco</font>"));
                    else if (i == 1) textView.setText(Html.fromHtml(name + ": <font color='#00FF00'>Verde</font>"));
                    else if (i == 2) textView.setText(Html.fromHtml(name + ": <font color='#0000FF'>Azul</font>"));
                    else if (i == 3) textView.setText(Html.fromHtml(name + ": <font color='#FF0000'>Vermelho</font>"));
                    else if (i == 4) textView.setText(Html.fromHtml(name + ": <font color='#000000'>Preto</font>"));
                    else if (i == 5) textView.setText(Html.fromHtml(name + ": <font color='#FFFF00'>Amarelo</font>"));
                    else if (i == 6) textView.setText(Html.fromHtml(name + ": <font color='#00FFFF'>Ciano</font>"));
                    else if (i == 7) textView.setText(Html.fromHtml(name + ": <font color='#FF00FF'>Magenta</font>"));
                    else if (i == 8) textView.setText(Html.fromHtml(name + ": <font color='#808080'>Cinza</font>"));
                    else if (i == 9) textView.setText(Html.fromHtml(name + ": <font color='#A020F0'>Roxo</font>"));
                } else if (type.equals("BoxType")) {
                    if (i == 0) textView.setText(name.concat(": Stroke"));
                    else if (i == 1) textView.setText(name.concat(": Filled"));
                    else if (i == 2) textView.setText(name.concat(": Corner"));
                } else if (type.equals("LineType")) {
                    if (i == 0) textView.setText(name.concat(": Top"));
                    else if (i == 1) textView.setText(name.concat(": Center"));
                    else if (i == 2) textView.setText(name.concat(": Bottom"));
                } else {
                    textView.setText(name.concat(": ") + i + type);
                }
                ChangesID(ID, i);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        container_features.addView(linearLayout);
        linearLayout.addView(textView);
        linearLayout.addView(seekBar);
    }

    public static void Funcoes(String pkg, String lib, String offset, String hex) {
        Message message = Message.obtain(null, RootServices.MSG_GETINFO);
        message.getData().putString("pkg", pkg);
        message.getData().putString("fileSo", lib);
        message.getData().putInt("offset", Integer.decode(offset).intValue());
        message.getData().putString("hexNumber", hex);
        message.replyTo = myMessenger;
        try {
            remoteMessenger.send(message);
        } catch (RemoteException e) {
            // ignore
        }
    }

    public static class MSGConnection implements ServiceConnection {
        public void onServiceConnected(ComponentName name, IBinder service) {
            setRemoteMessenger(new Messenger(service));
            if (serviceTestQueued) {
                serviceTestQueued = false;
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            setRemoteMessenger((Messenger) null);
        }
    }

    public static final void setRemoteMessenger(Messenger messenger) {
        remoteMessenger = messenger;
    }
}
