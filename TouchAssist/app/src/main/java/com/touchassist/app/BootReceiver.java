package com.touchassist.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/**
 * Starts the floating service on device boot if the user has enabled auto-start.
 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SharedPreferences prefs = context.getSharedPreferences("touch_assist_prefs",
                    Context.MODE_PRIVATE);
            boolean autoStart = prefs.getBoolean("auto_start", true);
            if (autoStart) {
                Intent serviceIntent = new Intent(context, FloatingService.class);
                context.startForegroundService(serviceIntent);
            }
        }
    }
}
