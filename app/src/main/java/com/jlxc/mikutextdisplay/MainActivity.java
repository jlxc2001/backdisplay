package com.jlxc.mikutextdisplay;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUi();

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "MikuTextDisplayNode:ScreenLock");
            try { wakeLock.acquire(); } catch (Throwable ignored) {}
        }

        textView = new TextDisplayView(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        root.addView(textView, lp);
        setContentView(root);

        SharedPreferences sp = getSharedPreferences(CommandReceiver.PREFS, Context.MODE_PRIVATE);
        String text = sp.getString(CommandReceiver.KEY_TEXT, "READY");
        textView.setText(text == null ? "" : text);

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
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Throwable ignored) {}
        super.onDestroy();
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
            sendUdpReply("PONG miku_text_node udp=" + UDP_PORT + " http=" + HTTP_PORT, address, port);
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
                    body = "PONG miku_text_node";
                } else if (uri.startsWith("/status")) {
                    contentType = "application/json; charset=utf-8";
                    String current = textView == null ? "" : textView.getTextValue();
                    body = "{\"type\":\"miku_text_node\",\"status\":1,\"udp_port\":" + UDP_PORT
                            + ",\"http_port\":" + HTTP_PORT
                            + ",\"ip\":\"" + escapeJson(getLocalIp()) + "\""
                            + ",\"text\":\"" + escapeJson(current) + "\"}"
                            ;
                } else {
                    contentType = "text/html; charset=utf-8";
                    body = "<html><body><h3>MikuTextDisplayNode</h3>"
                            + "<p>UDP: " + UDP_PORT + "</p>"
                            + "<p>HTTP: " + HTTP_PORT + "</p>"
                            + "<p>Use /show?text=hello , /clear , /status , /ping</p>"
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

    public static class TextDisplayView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        private String text = "READY";
        private long marqueeStart = SystemClock.uptimeMillis();
        private float lastTextWidth = 0f;
        private boolean scrolling = false;

        public TextDisplayView(Context context) {
            super(context);
            setBackgroundColor(Color.BLACK);
            paint.setColor(Color.WHITE);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setFakeBoldText(true);
        }

        public synchronized void setText(String value) {
            text = value == null ? "" : value;
            marqueeStart = SystemClock.uptimeMillis();
            invalidate();
        }

        public synchronized String getTextValue() {
            return text;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            final int w = getWidth();
            final int h = getHeight();
            if (w <= 0 || h <= 0) return;

            String current;
            synchronized (this) { current = text; }
            if (current == null || current.length() == 0) {
                return;
            }

            int padX = Math.max(8, Math.round(w * 0.015f));
            int padY = Math.max(2, Math.round(h * 0.10f));
            float targetHeight = Math.max(20f, h - padY * 2f);

            paint.setTextSize(targetHeight);
            Paint.FontMetrics fm = paint.getFontMetrics();
            float actualHeight = fm.descent - fm.ascent;
            if (actualHeight > 0) {
                paint.setTextSize(targetHeight * targetHeight / actualHeight);
            }

            fm = paint.getFontMetrics();
            float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
            float textWidth = paint.measureText(current);
            lastTextWidth = textWidth;
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
    }
}
