package com.jlxc.mikutextdisplay;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class CommandReceiver extends BroadcastReceiver {
    public static final String ACTION_SHOW = "com.jlxc.mikutextdisplay.SHOW";
    public static final String ACTION_CLEAR = "com.jlxc.mikutextdisplay.CLEAR";
    public static final String ACTION_INTERNAL_UPDATE = "com.jlxc.mikutextdisplay.INTERNAL_UPDATE";
    public static final String EXTRA_TEXT = "text";
    public static final String PREFS = "miku_text_node";
    public static final String KEY_TEXT = "current_text";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();
        String text;
        if (ACTION_CLEAR.equals(action)) {
            text = "";
        } else if (ACTION_SHOW.equals(action)) {
            text = intent.getStringExtra(EXTRA_TEXT);
            if (text == null) text = "";
        } else {
            return;
        }

        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit().putString(KEY_TEXT, text).apply();

        Intent update = new Intent(ACTION_INTERNAL_UPDATE);
        update.setPackage(context.getPackageName());
        update.putExtra(EXTRA_TEXT, text);
        context.sendBroadcast(update);
    }
}
