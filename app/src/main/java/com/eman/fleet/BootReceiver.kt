package com.eman.fleet

import android.content.*

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (c.getSharedPreferences("fleet", Context.MODE_PRIVATE).getBoolean("running", false)) {
            try { c.startForegroundService(Intent(c, TrackingService::class.java)) } catch (e: Exception) {}
        }
    }
}
