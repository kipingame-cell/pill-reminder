package com.kipigame.pills;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Восстановление напоминаний после перезагрузки телефона. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Scheduler.rescheduleAll(ctx);
        }
    }
}
