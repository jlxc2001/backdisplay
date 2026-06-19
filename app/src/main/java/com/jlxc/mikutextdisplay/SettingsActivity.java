package com.jlxc.mikutextdisplay;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;

public class SettingsActivity extends Activity {
    private static final int REQ_PICK_IMAGE = 1001;

    private final int[] textColors = new int[] {
            Color.WHITE,
            Color.RED,
            Color.YELLOW,
            Color.GREEN,
            Color.CYAN,
            Color.BLUE,
            Color.MAGENTA,
            0xffff8800
    };

    private final int[] bgColors = new int[] {
            Color.BLACK,
            0xff101010,
            0xff001126,
            0xff260000,
            0xff002611,
            Color.WHITE,
            0xff202020
    };

    private TextView titleView;
    private Button textColorButton;
    private Button bgColorButton;
    private Button showIpButton;
    private Button keepOnButton;
    private Button brightnessButton;
    private Button bgImageButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        hideSystemUi();
        applyBrightness();
        applyKeepScreenOn();
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUi();
        applyBrightness();
        applyKeepScreenOn();
        refreshButtons();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemUi();
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

    private void buildUi() {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setFillViewport(false);
        scroll.setBackgroundColor(0xff111111);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(10, 8, 10, 8);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                HorizontalScrollView.LayoutParams.WRAP_CONTENT,
                HorizontalScrollView.LayoutParams.MATCH_PARENT));

        titleView = makeLabel();
        row.addView(titleView, cellParams(390));

        textColorButton = makeButton("文字色");
        textColorButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleTextColor(); }
        });
        row.addView(textColorButton, cellParams(170));

        bgColorButton = makeButton("背景色");
        bgColorButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cycleBgColor(); }
        });
        row.addView(bgColorButton, cellParams(170));

        bgImageButton = makeButton("选择背景图");
        bgImageButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { pickBgImage(); }
        });
        row.addView(bgImageButton, cellParams(200));

        Button importBgImage = makeButton("导入/sdcard图");
        importBgImage.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { importBgFromSdcard(); }
        });
        row.addView(importBgImage, cellParams(210));

        Button clearBgImage = makeButton("清背景图");
        clearBgImage.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { clearBgImage(); }
        });
        row.addView(clearBgImage, cellParams(170));

        showIpButton = makeButton("显示IP");
        showIpButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleShowIp(); }
        });
        row.addView(showIpButton, cellParams(160));

        keepOnButton = makeButton("屏幕常亮");
        keepOnButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toggleKeepScreenOn(); }
        });
        row.addView(keepOnButton, cellParams(170));

        Button brightnessDown = makeButton("亮度-");
        brightnessDown.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustBrightness(-0.1f); }
        });
        row.addView(brightnessDown, cellParams(130));

        brightnessButton = makeButton("亮度");
        brightnessButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setDefaultBrightness(); }
        });
        row.addView(brightnessButton, cellParams(170));

        Button brightnessUp = makeButton("亮度+");
        brightnessUp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { adjustBrightness(0.1f); }
        });
        row.addView(brightnessUp, cellParams(130));

        Button reset = makeButton("恢复默认");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { resetDefaults(); }
        });
        row.addView(reset, cellParams(170));

        Button back = makeButton("返回显示");
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { finish(); }
        });
        row.addView(back, cellParams(170));

        setContentView(scroll);
        refreshButtons();
    }

    private LinearLayout.LayoutParams cellParams(int width) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.MATCH_PARENT);
        lp.setMargins(4, 0, 4, 0);
        return lp;
    }

    private TextView makeLabel() {
        TextView v = new TextView(this);
        v.setTextColor(Color.WHITE);
        v.setTextSize(14f);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setSingleLine(false);
        return v;
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13f);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setPadding(4, 0, 4, 0);
        return b;
    }

    private void refreshButtons() {
        SharedPreferences sp = DisplaySettings.prefs(this);
        int textColor = DisplaySettings.getTextColor(this);
        int bgColor = DisplaySettings.getBgColor(this);
        boolean showIp = DisplaySettings.isShowIp(this);
        boolean keepOn = DisplaySettings.isKeepScreenOn(this);
        float brightness = DisplaySettings.getBrightness(this);
        boolean hasBgImage = DisplaySettings.isBgImageEnabled(this);

        titleView.setText("Miku文字屏设置\nIP: " + getLocalIp() + "  UDP 47230 / HTTP 47231");

        textColorButton.setText("文字色\n" + DisplaySettings.colorToHex(textColor));
        textColorButton.setTextColor(textColor);

        bgColorButton.setText("背景色\n" + DisplaySettings.colorToHex(bgColor));
        bgColorButton.setTextColor(contrastColor(bgColor));
        try { bgColorButton.setBackgroundColor(bgColor); } catch (Throwable ignored) {}

        bgImageButton.setText(hasBgImage ? "背景图\n已启用" : "选择背景图");
        showIpButton.setText(showIp ? "显示IP\n开" : "显示IP\n关");
        keepOnButton.setText(keepOn ? "屏幕常亮\n开" : "屏幕常亮\n关");
        brightnessButton.setText("亮度\n" + Math.round(brightness * 100f) + "%");
    }

    private int contrastColor(int color) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        int luminance = (r * 299 + g * 587 + b * 114) / 1000;
        return luminance > 150 ? Color.BLACK : Color.WHITE;
    }

    private int indexOfColor(int[] list, int color) {
        for (int i = 0; i < list.length; i++) {
            if (list[i] == color) return i;
        }
        return -1;
    }

    private void cycleTextColor() {
        int current = DisplaySettings.getTextColor(this);
        int index = indexOfColor(textColors, current);
        int next = textColors[(index + 1 + textColors.length) % textColors.length];
        DisplaySettings.prefs(this).edit().putInt(DisplaySettings.KEY_TEXT_COLOR, next).apply();
        refreshButtons();
    }

    private void cycleBgColor() {
        int current = DisplaySettings.getBgColor(this);
        int index = indexOfColor(bgColors, current);
        int next = bgColors[(index + 1 + bgColors.length) % bgColors.length];
        DisplaySettings.prefs(this).edit().putInt(DisplaySettings.KEY_BG_COLOR, next).apply();
        refreshButtons();
    }

    private void toggleShowIp() {
        boolean next = !DisplaySettings.isShowIp(this);
        DisplaySettings.prefs(this).edit().putBoolean(DisplaySettings.KEY_SHOW_IP, next).apply();
        refreshButtons();
    }

    private void toggleKeepScreenOn() {
        boolean next = !DisplaySettings.isKeepScreenOn(this);
        DisplaySettings.prefs(this).edit().putBoolean(DisplaySettings.KEY_KEEP_SCREEN_ON, next).apply();
        applyKeepScreenOn();
        refreshButtons();
    }

    private void adjustBrightness(float delta) {
        float b = DisplaySettings.getBrightness(this) + delta;
        if (b < 0.05f) b = 0.05f;
        if (b > 1.0f) b = 1.0f;
        DisplaySettings.prefs(this).edit().putFloat(DisplaySettings.KEY_BRIGHTNESS, b).apply();
        applyBrightness();
        applyKeepScreenOn();
        refreshButtons();
    }

    private void setDefaultBrightness() {
        DisplaySettings.prefs(this).edit().putFloat(DisplaySettings.KEY_BRIGHTNESS, DisplaySettings.DEFAULT_BRIGHTNESS).apply();
        applyBrightness();
        applyKeepScreenOn();
        refreshButtons();
        Toast.makeText(this, "已恢复默认亮度 100%", Toast.LENGTH_SHORT).show();
    }

    private void resetDefaults() {
        SharedPreferences.Editor e = DisplaySettings.prefs(this).edit();
        e.putInt(DisplaySettings.KEY_TEXT_COLOR, DisplaySettings.DEFAULT_TEXT_COLOR);
        e.putInt(DisplaySettings.KEY_BG_COLOR, DisplaySettings.DEFAULT_BG_COLOR);
        e.putBoolean(DisplaySettings.KEY_SHOW_IP, DisplaySettings.DEFAULT_SHOW_IP);
        e.putBoolean(DisplaySettings.KEY_KEEP_SCREEN_ON, DisplaySettings.DEFAULT_KEEP_SCREEN_ON);
        e.putFloat(DisplaySettings.KEY_BRIGHTNESS, DisplaySettings.DEFAULT_BRIGHTNESS);
        e.putBoolean(DisplaySettings.KEY_BG_IMAGE_ENABLED, false);
        e.putString(DisplaySettings.KEY_BG_IMAGE_PATH, "");
        e.apply();
        File f = DisplaySettings.getBgImageFile(this);
        try { if (f.exists()) f.delete(); } catch (Throwable ignored) {}
        applyBrightness();
        applyKeepScreenOn();
        refreshButtons();
        Toast.makeText(this, "已恢复默认设置", Toast.LENGTH_SHORT).show();
    }

    private void applyBrightness() {
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.screenBrightness = DisplaySettings.getBrightness(this);
        getWindow().setAttributes(lp);
    }

    private void applyKeepScreenOn() {
        if (DisplaySettings.isKeepScreenOn(this)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void pickBgImage() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(Intent.createChooser(intent, "选择背景图片"), REQ_PICK_IMAGE);
        } catch (Throwable e) {
            Toast.makeText(this, "无法打开图片选择器", Toast.LENGTH_SHORT).show();
        }
    }

    private void importBgFromSdcard() {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            File src = new File("/sdcard/miku_text_bg.png");
            if (!src.exists()) src = new File("/sdcard/miku_text_bg.jpg");
            if (!src.exists()) {
                Toast.makeText(this, "未找到 /sdcard/miku_text_bg.png", Toast.LENGTH_SHORT).show();
                return;
            }
            in = new java.io.FileInputStream(src);
            File dst = DisplaySettings.getBgImageFile(this);
            out = new FileOutputStream(dst, false);
            byte[] buffer = new byte[16 * 1024];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                total += read;
                if (total > 8 * 1024 * 1024) break;
            }
            out.flush();
            if (dst.exists() && dst.length() > 0) {
                DisplaySettings.prefs(this).edit()
                        .putBoolean(DisplaySettings.KEY_BG_IMAGE_ENABLED, true)
                        .putString(DisplaySettings.KEY_BG_IMAGE_PATH, dst.getAbsolutePath())
                        .apply();
                Toast.makeText(this, "已导入背景图", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "导入失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Throwable e) {
            Toast.makeText(this, "导入失败", Toast.LENGTH_SHORT).show();
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            try { if (out != null) out.close(); } catch (Throwable ignored) {}
            refreshButtons();
        }
    }

    private void clearBgImage() {
        SharedPreferences.Editor e = DisplaySettings.prefs(this).edit();
        e.putBoolean(DisplaySettings.KEY_BG_IMAGE_ENABLED, false);
        e.putString(DisplaySettings.KEY_BG_IMAGE_PATH, "");
        e.apply();
        File f = DisplaySettings.getBgImageFile(this);
        try { if (f.exists()) f.delete(); } catch (Throwable ignored) {}
        refreshButtons();
        Toast.makeText(this, "已清除背景图", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            boolean ok = copyImageToInternal(data.getData());
            if (ok) {
                String path = DisplaySettings.getBgImageFile(this).getAbsolutePath();
                DisplaySettings.prefs(this).edit()
                        .putBoolean(DisplaySettings.KEY_BG_IMAGE_ENABLED, true)
                        .putString(DisplaySettings.KEY_BG_IMAGE_PATH, path)
                        .apply();
                Toast.makeText(this, "背景图已保存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "背景图保存失败", Toast.LENGTH_SHORT).show();
            }
            refreshButtons();
        }
    }

    private boolean copyImageToInternal(Uri uri) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            in = getContentResolver().openInputStream(uri);
            if (in == null) return false;
            File f = DisplaySettings.getBgImageFile(this);
            out = new FileOutputStream(f, false);
            byte[] buffer = new byte[16 * 1024];
            int read;
            int total = 0;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
                total += read;
                if (total > 8 * 1024 * 1024) break;
            }
            out.flush();
            return f.exists() && f.length() > 0;
        } catch (Throwable e) {
            return false;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) {}
            try { if (out != null) out.close(); } catch (Throwable ignored) {}
        }
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
}
