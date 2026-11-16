package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;

/**
 * Apple tvOS navigation sounds (Navigate.wav, Click.wav, Back.wav) loaded from assets/sounds/
 * into a low-latency SoundPool. Settings -> Home -> Navigation sounds.
 */
final class Clicks {
    static final float VOLUME = 0.85f;
    private static AudioManager audio;
    private static SoundPool pool;
    private static int soundNavigate;
    private static int soundClick;
    private static int soundBack;
    private static volatile boolean soundsLoaded;

    private Clicks() {
    }

    static boolean enabled(Context c) {
        return c != null && c.getSharedPreferences("sounds", Context.MODE_PRIVATE).getBoolean("nav_sounds", false);
    }

    static void setEnabled(Context c, boolean on) {
        if (c == null) return;
        c.getSharedPreferences("sounds", Context.MODE_PRIVATE).edit().putBoolean("nav_sounds", on).apply();
        if (on) init(c);
    }

    static synchronized void init(Context c) {
        if (c == null) return;
        Context app = c.getApplicationContext();
        if (audio == null) {
            audio = app.getSystemService(AudioManager.class);
        }
        if (pool == null) {
            try {
                AudioAttributes attrs = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                pool = new SoundPool.Builder()
                        .setMaxStreams(4)
                        .setAudioAttributes(attrs)
                        .build();
                pool.setOnLoadCompleteListener(new SoundPool.OnLoadCompleteListener() {
                    @Override
                    public void onLoadComplete(SoundPool sp, int sampleId, int status) {
                        if (status == 0) {
                            soundsLoaded = true;
                        }
                    }
                });
                soundNavigate = loadAssetSound(app, "sounds/Navigate.wav");
                soundClick = loadAssetSound(app, "sounds/Click.wav");
                soundBack = loadAssetSound(app, "sounds/Back.wav");
            } catch (Throwable ignored) {
            }
        }
    }

    private static int loadAssetSound(Context app, String path) {
        try {
            AssetFileDescriptor afd = app.getAssets().openFd(path);
            int id = pool.load(afd, 1);
            afd.close();
            return id;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static void playSample(Context c, int sampleId, int fallbackEffect) {
        if (!enabled(c)) return;
        init(c);
        if (pool != null && sampleId != 0 && soundsLoaded) {
            pool.play(sampleId, VOLUME, VOLUME, 1, 0, 1.0f);
        } else if (audio != null) {
            audio.playSoundEffect(fallbackEffect, VOLUME);
        }
    }

    /** Focus moved: Apple TV Navigate.wav sound. */
    static void move(Context c, int keyCode) {
        playSample(c, soundNavigate, AudioManager.FX_FOCUS_NAVIGATION_RIGHT);
    }

    /** Something was chosen: Apple TV Click.wav sound. */
    static void select(Context c) {
        playSample(c, soundClick, AudioManager.FX_KEY_CLICK);
    }

    /** Back: Apple TV Back.wav sound. */
    static void back(Context c) {
        playSample(c, soundBack, AudioManager.FX_KEY_CLICK);
    }
}
