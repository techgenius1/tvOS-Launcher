package com.techgenius.tvoslauncher;

import android.content.Context;
import android.media.AudioManager;
import android.view.KeyEvent;

/**
 * Navigation sounds using Android TV's own system sounds (the same clicks Google's launcher and
 * Settings use): a per-direction focus sound when moving and the key click on select and Back,
 * played at 60% of the normal sound-effect level. Settings -> Home -> Navigation sounds.
 */
final class Clicks {
    static final float VOLUME = 0.6f;
    private static AudioManager audio;

    private Clicks() {
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences("sounds", Context.MODE_PRIVATE).getBoolean("nav_sounds", false);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("sounds", Context.MODE_PRIVATE).edit().putBoolean("nav_sounds", on).apply();
    }

    static void init(Context c) {
        if (audio == null) audio = c.getApplicationContext().getSystemService(AudioManager.class);
    }

    private static void play(Context c, int effect) {
        init(c);
        if (audio != null && enabled(c)) audio.playSoundEffect(effect, VOLUME);
    }

    /** Focus moved: Android's focus sound for that direction. */
    static void move(Context c, int keyCode) {
        int fx;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_UP: fx = AudioManager.FX_FOCUS_NAVIGATION_UP; break;
            case KeyEvent.KEYCODE_DPAD_DOWN: fx = AudioManager.FX_FOCUS_NAVIGATION_DOWN; break;
            case KeyEvent.KEYCODE_DPAD_LEFT: fx = AudioManager.FX_FOCUS_NAVIGATION_LEFT; break;
            default: fx = AudioManager.FX_FOCUS_NAVIGATION_RIGHT; break;
        }
        play(c, fx);
    }

    /** Something was chosen. */
    static void select(Context c) {
        play(c, AudioManager.FX_KEY_CLICK);
    }

    /** Back. */
    static void back(Context c) {
        play(c, AudioManager.FX_KEY_CLICK);
    }
}
