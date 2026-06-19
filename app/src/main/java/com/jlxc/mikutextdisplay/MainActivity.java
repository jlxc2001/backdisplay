package com.jlxc.mikutextdisplay;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.Locale;

public class MainActivity extends Activity {
    public static final int UDP_PORT = 47230;
    public static final int HTTP_PORT = 47231;

    private TextDisplayView textView;
    private volatile boolean running = true;
    private DatagramSocket udpSocket;
    private ServerSocket httpServerSocket;
    private PowerManager.WakeLock wakeLock;

    private final BroadcastReceiver internalReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && CommandReceiver.ACTION_INTERNAL_UPDATE.equals(intent.getAction())) {
                String text = intent.getStringExtra(CommandReceiver.EXTRA_TEXT);
                if (text == null) text = "";
                setDisplayText(text, false);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        applyScreenSettings();
        hideSystemUi();

        textView = new TextDisplayView(this);
        textView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openSettings();
                return true;
            }
        });

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                openSettings();
                return true;
            }
        });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        root.addView(textView, lp);
        setContentView(root);

        SharedPreferences sp = getSharedPreferences(CommandReceiver.PREFS, Context.MODE_PRIVATE);
        String text = sp.getString(CommandReceiver.KEY_TEXT, "READY");
        textView.setText(text == null ? "" : text);
        textView.applySettings();

        registerReceiver(internalReceiver, new IntentFilter(CommandReceiver.ACTION_INTERNAL_UPDATE));
        startUdpServer();
        startHttpServer();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        hideSystemUi();
        if (intent != null && intent.hasExtra(CommandReceiver.EXTRA_TEXT)) {
            setDisplayText(intent.getStringExtra(CommandReceiver.EXTRA_TEXT), true);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyScreenSettings();
        if (textView != null) textView.applySettings();
        hideSystemUi();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        hideSystemUi();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        // Dedicated display node: ignore Back to avoid accidentally exiting the receiver.
        hideSystemUi();
    }

    @Override
    protected void onDestroy() {
        running = false;
        try { unregisterReceiver(internalReceiver); } catch (Throwable ignored) {}
        try { if (udpSocket != null) udpSocket.close(); } catch (Throwable ignored) {}
        try { if (httpServerSocket != null) httpServerSocket.close(); } catch (Throwable ignored) {}
        releaseWakeLock();
        super.onDestroy();
    }

    private void openSettings() {
        Intent intent = new Intent(this, SettingsActivity.class);
        startActivity(intent);
    }

    private void applyScreenSettings() {
        if (DisplaySettings.isKeepScreenOn(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            acquireWakeLock();
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            releaseWakeLock();
        }
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = DisplaySettings.getBrightness(this);
        getWindow().setAttributes(lp);
    }

    private void acquireWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) return;
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "MikuTextDisplayNode:ScreenLock");
                wakeLock.acquire();
            }
        } catch (Throwable ignored) {}
    }

    private void releaseWakeLock() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {}
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void setDisplayText(final String rawText, final boolean save) {
        final String text = sanitize(rawText);
        if (save) {
            getSharedPreferences(CommandReceiver.PREFS, Context.MODE_PRIVATE)
                    .edit().putString(CommandReceiver.KEY_TEXT, text).apply();
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textView.setText(text);
            }
        });
    }

    private String sanitize(String text) {
        if (text == null) return "";
        String s = text.replace('\n', ' ').replace('\r', ' ').trim();
        if (s.length() > 500) s = s.substring(0, 500);
        return s;
    }

    private void startUdpServer() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] buf = new byte[2048];
                try {
                    udpSocket = new DatagramSocket(UDP_PORT);
                    while (running) {
                        DatagramPacket packet = new DatagramPacket(buf, buf.length);
                        udpSocket.receive(packet);
                        String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), Charset.forName("UTF-8"));
                        handleUdpMessage(msg, packet.getAddress(), packet.getPort());
                    }
                } catch (IOException ignored) {
                }
            }
        }, "MikuUdpServer");
        t.setDaemon(true);
        t.start();
    }

    private void handleUdpMessage(String msg, InetAddress address, int port) {
        String s = msg == null ? "" : msg.trim();
        if (s.startsWith("\uFEFF")) s = s.substring(1);
        String upper = s.toUpperCase(Locale.US);
        if (upper.equals("PING")) {
            sendUdpReply("PONG miku_text_node udp=" + UDP_PORT + " http=" + HTTP_PORT + " ip=" + getLocalIp(), address, port);
            return;
        }
        if (upper.equals("CLEAR") || upper.equals("OFF")) {
            setDisplayText("", true);
            sendUdpReply("OK CLEAR", address, port);
            return;
        }
        if (upper.startsWith("SHOW:")) {
            setDisplayText(s.substring(5), true);
        } else if (upper.startsWith("TEXT:")) {
            setDisplayText(s.substring(5), true);
        } else {
            setDisplayText(s, true);
        }
        sendUdpReply("OK", address, port);
    }

    private void sendUdpReply(String text, InetAddress address, int port) {
        try {
            if (udpSocket == null || address == null || port <= 0) return;
            byte[] data = text.getBytes(Charset.forName("UTF-8"));
            DatagramPacket reply = new DatagramPacket(data, data.length, address, port);
            udpSocket.send(reply);
        } catch (Throwable ignored) {}
    }

    private void startHttpServer() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    httpServerSocket = new ServerSocket(HTTP_PORT);
                    httpServerSocket.setSoTimeout(1000);
                    while (running) {
                        try {
                            Socket socket = httpServerSocket.accept();
                            handleHttpClient(socket);
                        } catch (SocketTimeoutException ignored) {
                        }
                    }
                } catch (IOException ignored) {
                }
            }
        }, "MikuHttpServer");
        t.setDaemon(true);
        t.start();
    }

    private void handleHttpClient(Socket socket) {
        try {
            socket.setSoTimeout(2000);
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            String firstLine = br.readLine();
            String body = "OK";
            String contentType = "text/plain; charset=utf-8";
            int code = 200;

            if (firstLine == null || firstLine.length() == 0) {
                code = 400;
                body = "Bad Request";
            } else {
                String[] parts = firstLine.split(" ");
                String uri = parts.length >= 2 ? parts[1] : "/";
                if (uri.startsWith("/show") || uri.startsWith("/text")) {
                    String text = getQueryParam(uri, "text");
                    if (text == null) text = "";
                    setDisplayText(text, true);
                    body = "OK SHOW";
                } else if (uri.startsWith("/clear") || uri.startsWith("/off")) {
                    setDisplayText("", true);
                    body = "OK CLEAR";
                } else if (uri.startsWith("/ping")) {
                    body = "PONG miku_text_node ip=" + getLocalIp();
                } else if (uri.startsWith("/status")) {
                    contentType = "application/json; charset=utf-8";
                    String current = textView == null ? "" : textView.getTextValue();
                    body = "{\"type\":\"miku_text_node\",\"status\":1,\"udp_port\":" + UDP_PORT
                            + ",\"http_port\":" + HTTP_PORT
                            + ",\"ip\":\"" + escapeJson(getLocalIp()) + "\""
                            + ",\"text\":\"" + escapeJson(current) + "\""
                            + ",\"text_color\":\"" + escapeJson(DisplaySettings.colorToHex(DisplaySettings.getTextColor(this))) + "\""
                            + ",\"background_color\":\"" + escapeJson(DisplaySettings.colorToHex(DisplaySettings.getBgColor(this))) + "\""
                            + ",\"background_image\":" + (DisplaySettings.isBgImageEnabled(this) ? "true" : "false")
                            + ",\"show_ip\":" + (DisplaySettings.isShowIp(this) ? "true" : "false")
                            + ",\"keep_screen_on\":" + (DisplaySettings.isKeepScreenOn(this) ? "true" : "false")
                            + ",\"brightness\":" + String.format(Locale.US, "%.2f", DisplaySettings.getBrightness(this))
                            + "}";
                } else {
                    contentType = "text/html; charset=utf-8";
                    body = "<html><body><h3>MikuTextDisplayNode</h3>"
                            + "<p>IP: " + escapeHtml(getLocalIp()) + "</p>"
                            + "<p>UDP: " + UDP_PORT + "</p>"
                            + "<p>HTTP: " + HTTP_PORT + "</p>"
                            + "<p>Use /show?text=hello , /clear , /status , /ping</p>"
                            + "<p>Long press the strip screen to open local settings.</p>"
                            + "</body></html>";
                }
            }

            writeHttp(socket.getOutputStream(), code, contentType, body);
        } catch (Throwable ignored) {
        } finally {
            try { socket.close(); } catch (Throwable ignored) {}
        }
    }

    private void writeHttp(OutputStream out, int code, String contentType, String body) throws IOException {
        byte[] data = body.getBytes("UTF-8");
        String status = code == 200 ? "OK" : "ERROR";
        String headers = "HTTP/1.1 " + code + " " + status + "\r\n"
                + "Content-Type: " + contentType + "\r\n"
                + "Access-Control-Allow-Origin: *\r\n"
                + "Connection: close\r\n"
                + "Content-Length: " + data.length + "\r\n\r\n";
        out.write(headers.getBytes("UTF-8"));
        out.write(data);
        out.flush();
    }

    private String getQueryParam(String uri, String key) {
        int q = uri.indexOf('?');
        if (q < 0 || q >= uri.length() - 1) return null;
        String query = uri.substring(q + 1);
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq <= 0) continue;
            String k = decode(pair.substring(0, eq));
            if (key.equals(k)) return decode(pair.substring(eq + 1));
        }
        return null;
    }

    private String decode(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Throwable e) {
            return s;
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"': sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 32) sb.append(String.format(Locale.US, "\\u%04x", (int)c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private String getLocalIp() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return "unknown";
            WifiInfo info = wm.getConnectionInfo();
            if (info == null) return "unknown";
            int ip = info.getIpAddress();
            return String.format(Locale.US, "%d.%d.%d.%d", ip & 0xff, (ip >> 8) & 0xff, (ip >> 16) & 0xff, (ip >> 24) & 0xff);
        } catch (Throwable e) {
            return "unknown";
        }
    }

    public class TextDisplayView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private final Paint ipPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private String text = "READY";
        private long marqueeStart = SystemClock.uptimeMillis();
        private boolean scrolling = false;

        private int textColor = DisplaySettings.DEFAULT_TEXT_COLOR;
        private int bgColor = DisplaySettings.DEFAULT_BG_COLOR;
        private boolean showIp = DisplaySettings.DEFAULT_SHOW_IP;
        private boolean bgImageEnabled = false;
        private String bgImagePath = "";
        private Bitmap bgBitmap;
        private long lastBgDecode = 0;

        public TextDisplayView(Context context) {
            super(context);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setFakeBoldText(true);
            ipPaint.setTextAlign(Paint.Align.RIGHT);
            ipPaint.setFakeBoldText(false);
        }

        public synchronized void setText(String value) {
            text = value == null ? "" : value;
            marqueeStart = SystemClock.uptimeMillis();
            invalidate();
        }

        public synchronized String getTextValue() {
            return text;
        }

        public void applySettings() {
            textColor = DisplaySettings.getTextColor(getContext());
            bgColor = DisplaySettings.getBgColor(getContext());
            showIp = DisplaySettings.isShowIp(getContext());
            boolean enabled = DisplaySettings.isBgImageEnabled(getContext());
            String path = DisplaySettings.getBgImagePath(getContext());
            if (path == null) path = "";
            if (enabled != bgImageEnabled || !path.equals(bgImagePath) || bgBitmap == null) {
                bgImageEnabled = enabled;
                bgImagePath = path;
                decodeBackgroundBitmapIfNeeded(true);
            }
            invalidate();
        }

        private void decodeBackgroundBitmapIfNeeded(boolean force) {
            if (!bgImageEnabled || bgImagePath.length() == 0) {
                if (bgBitmap != null) {
                    try { bgBitmap.recycle(); } catch (Throwable ignored) {}
                    bgBitmap = null;
                }
                return;
            }
            long now = SystemClock.uptimeMillis();
            if (!force && bgBitmap != null && now - lastBgDecode < 30000) return;
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                Bitmap bm = BitmapFactory.decodeFile(bgImagePath, opts);
                if (bm != null) {
                    if (bgBitmap != null && bgBitmap != bm) {
                        try { bgBitmap.recycle(); } catch (Throwable ignored) {}
                    }
                    bgBitmap = bm;
                    lastBgDecode = now;
                }
            } catch (Throwable ignored) {}
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final int w = getWidth();
            final int h = getHeight();
            if (w <= 0 || h <= 0) return;

            drawBackground(canvas, w, h);

            String current;
            synchronized (this) { current = text; }

            boolean hasIp = showIp;
            String ip = hasIp ? "IP " + getLocalIp() : "";
            float ipTextSize = Math.max(10f, h * 0.15f);
            float ipReserveBottom = hasIp ? Math.max(18f, ipTextSize + 5f) : 0f;

            if (current != null && current.length() > 0) {
                drawMainText(canvas, current, w, h, ipReserveBottom);
            }
            if (hasIp) {
                drawIp(canvas, ip, w, h, ipTextSize);
            }
        }

        private void drawBackground(Canvas canvas, int w, int h) {
            canvas.drawColor(bgColor);
            if (!bgImageEnabled) return;
            decodeBackgroundBitmapIfNeeded(false);
            if (bgBitmap == null || bgBitmap.isRecycled()) return;
            int bw = bgBitmap.getWidth();
            int bh = bgBitmap.getHeight();
            if (bw <= 0 || bh <= 0) return;

            float scale = Math.max(w / (float) bw, h / (float) bh);
            float dw = bw * scale;
            float dh = bh * scale;
            float left = (w - dw) / 2f;
            float top = (h - dh) / 2f;
            Rect src = new Rect(0, 0, bw, bh);
            RectF dst = new RectF(left, top, left + dw, top + dh);
            canvas.drawBitmap(bgBitmap, src, dst, null);
        }

        private void drawMainText(Canvas canvas, String current, int w, int h, float ipReserveBottom) {
            int padX = Math.max(8, Math.round(w * 0.015f));
            int padY = Math.max(2, Math.round(h * 0.10f));
            float targetHeight = Math.max(20f, h - padY * 2f - ipReserveBottom);

            paint.setColor(textColor);
            paint.setTextSize(targetHeight);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float actualHeight = fm.descent - fm.ascent;
            if (actualHeight > 0) {
                paint.setTextSize(targetHeight * targetHeight / actualHeight);
            }

            fm = paint.getFontMetrics();
            float textAreaCenterY = (h - ipReserveBottom) / 2f;
            float baseline = textAreaCenterY - (fm.ascent + fm.descent) / 2f;
            float textWidth = paint.measureText(current);
            float availableWidth = w - padX * 2f;
            scrolling = textWidth > availableWidth;

            if (!scrolling) {
                float x = (w - textWidth) / 2f;
                canvas.drawText(current, x, baseline, paint);
            } else {
                float speedPxPerSec = Math.max(90f, w * 0.085f); // About 160px/s on 1920px width.
                float gap = Math.max(80f, w * 0.08f);
                float cycle = textWidth + gap + w;
                float elapsed = (SystemClock.uptimeMillis() - marqueeStart) / 1000f;
                float offset = (elapsed * speedPxPerSec) % cycle;
                float x = w - offset;
                canvas.drawText(current, x, baseline, paint);
                if (x + textWidth + gap < w) {
                    canvas.drawText(current, x + textWidth + gap, baseline, paint);
                }
                postInvalidateDelayed(16);
            }
        }

        private void drawIp(Canvas canvas, String ip, int w, int h, float textSize) {
            ipPaint.setColor(applyAlpha(textColor, 0.72f));
            ipPaint.setTextSize(textSize);
            Paint.FontMetrics fm = ipPaint.getFontMetrics();
            float baseline = h - 4f - fm.descent;
            canvas.drawText(ip, w - 10f, baseline, ipPaint);
        }

        private int applyAlpha(int color, float alpha) {
            int a = Math.round(Color.alpha(color) * alpha);
            return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
        }
    }
}
