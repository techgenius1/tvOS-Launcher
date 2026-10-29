package com.techgenius.tvoslauncher.present;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * After boot: starts the admin server if Presentation is enabled, and opens the presentation
 * full-screen when "Start on boot" is on (port of BootReceiver.kt). The launcher's Home screen
 * also checks this on first start, since a HOME app may come up before this broadcast.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        Presenter.ensureRunning(c);
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)) Presenter.openOnBootOnce(c);
    }
}
