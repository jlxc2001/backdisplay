package com.jlxc.mikutextdisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.io.File;

public final class DisplaySettings {
    private DisplaySettings() {}

    public static final String PREFS = CommandReceiver.PREFS;

    public static final String KEY_TEXT_COLOR = "text_color";
    public static final String KEY_BG_COLOR = "bg_color";
    public static final String KEY_SHOW_IP = "show_ip";
    public static final String KEY_KEEP_SCREEN_ON = "keep_screen_on";
    public static final String KEY_BRIGHTNESS = "brightness";
    public static final String KEY_BG_IMAGE_ENABLED = "bg_image_enabled";
    public static final String KEY_BG_IMAGE_PATH = "bg_image_path";
    public static final String KEY_BLINK_ENABLED = "blink_enabled";
    public static final String KEY_BLINK_SPEED = "blink_speed";
    public static final String KEY_BLINK_TYPE = "blink_type";

    public static final int BLINK_SPEED_SLOW = 0;
    public static final int BLINK_SPEED_NORMAL = 1;
    public static final int BLINK_SPEED_FAST = 2;

    public static final int BLINK_TYPE_HARD = 0;
    public static final int BLINK_TYPE_FADE = 1;
    public static final int BLINK_TYPE_DOUBLE = 2;

    public static final int DEFAULT_TEXT_COLOR = Color.WHITE;
    public static final int DEFAULT_BG_COLOR = Color.BLACK;
    public static final boolean DEFAULT_SHOW_IP = true;
    public static final boolean DEFAULT_KEEP_SCREEN_ON = true;
    public static final float DEFAULT_BRIGHTNESS = 1.0f;
    public static final boolean DEFAULT_BLINK_ENABLED = false;
    public static final int DEFAULT_BLINK_SPEED = BLINK_SPEED_NORMAL;
    public static final int DEFAULT_BLINK_TYPE = BLINK_TYPE_HARD;

    public static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getTextColor(Context context) {
        return prefs(context).getInt(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR);
    }

    public static int getBgColor(Context context) {
        return prefs(context).getInt(KEY_BG_COLOR, DEFAULT_BG_COLOR);
    }

    public static boolean isShowIp(Context context) {
        return prefs(context).getBoolean(KEY_SHOW_IP, DEFAULT_SHOW_IP);
    }

    public static boolean isKeepScreenOn(Context context) {
        return prefs(context).getBoolean(KEY_KEEP_SCREEN_ON, DEFAULT_KEEP_SCREEN_ON);
    }

    public static float getBrightness(Context context) {
        float b = prefs(context).getFloat(KEY_BRIGHTNESS, DEFAULT_BRIGHTNESS);
        if (b < 0.05f) b = 0.05f;
        if (b > 1.0f) b = 1.0f;
        return b;
    }

    public static boolean isBgImageEnabled(Context context) {
        return prefs(context).getBoolean(KEY_BG_IMAGE_ENABLED, false);
    }

    public static String getBgImagePath(Context context) {
        String path = prefs(context).getString(KEY_BG_IMAGE_PATH, "");
        return path == null ? "" : path;
    }

    public static File getBgImageFile(Context context) {
        return new File(context.getFilesDir(), "miku_background_image.bin");
    }

    public static boolean isBlinkEnabled(Context context) {
        return prefs(context).getBoolean(KEY_BLINK_ENABLED, DEFAULT_BLINK_ENABLED);
    }

    public static int getBlinkSpeed(Context context) {
        int speed = prefs(context).getInt(KEY_BLINK_SPEED, DEFAULT_BLINK_SPEED);
        if (speed < BLINK_SPEED_SLOW || speed > BLINK_SPEED_FAST) speed = DEFAULT_BLINK_SPEED;
        return speed;
    }

    public static int getBlinkPeriodMs(Context context) {
        switch (getBlinkSpeed(context)) {
            case BLINK_SPEED_SLOW:
                return 1200;
            case BLINK_SPEED_FAST:
                return 360;
            case BLINK_SPEED_NORMAL:
            default:
                return 700;
        }
    }

    public static String getBlinkSpeedName(Context context) {
        switch (getBlinkSpeed(context)) {
            case BLINK_SPEED_SLOW:
                return "慢";
            case BLINK_SPEED_FAST:
                return "快";
            case BLINK_SPEED_NORMAL:
            default:
                return "中";
        }
    }

    public static int getBlinkType(Context context) {
        int type = prefs(context).getInt(KEY_BLINK_TYPE, DEFAULT_BLINK_TYPE);
        if (type < BLINK_TYPE_HARD || type > BLINK_TYPE_DOUBLE) type = DEFAULT_BLINK_TYPE;
        return type;
    }

    public static String getBlinkTypeName(Context context) {
        switch (getBlinkType(context)) {
            case BLINK_TYPE_FADE:
                return "呼吸";
            case BLINK_TYPE_DOUBLE:
                return "双闪";
            case BLINK_TYPE_HARD:
            default:
                return "开关";
        }
    }

    public static String colorToHex(int color) {
        return String.format("#%08X", color);
    }
}
