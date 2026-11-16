package com.eman.fleet

import android.annotation.SuppressLint
import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class TrackingService : Service(), LocationListener {
    private lateinit var lm: LocationManager
    private val handler = Handler(Looper.getMainLooper())
    private var best: Location? = null
    private var wake: PowerManager.WakeLock? = null
    private val loop = object : Runnable {
        override fun run() { sendNow(); handler.postDelayed(this, intervalMs()) }
    }

    private fun prefs() = getSharedPreferences("fleet", MODE_PRIVATE)
    private fun intervalMs() = prefs().getInt("interval", 10).coerceAtLeast(1) * 60_000L

    override fun onBind(i: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("track", "Tracking", NotificationManager.IMPORTANCE_LOW))
        val n = notif("Tracking active")
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION) else startForeground(1, n)
        wake = (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "fleet:track").apply { acquire() }
        lm = getSystemService(LOCATION_SERVICE) as LocationManager
        try {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(p, 60_000L, 0f, this, Looper.getMainLooper())
                    lm.getLastKnownLocation(p)?.let { consider(it) }
                }
            }
        } catch (e: SecurityException) { status("Location permission missing"); stopSelf(); return START_NOT_STICKY }
        prefs().edit().putBoolean("running", true).apply()
        handler.removeCallbacks(loop); handler.post(loop)
        return START_STICKY
    }

    private fun notif(text: String): Notification =
        Notification.Builder(this, "track").setContentTitle("Fleet Tracker").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation).setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)).build()

    private fun consider(l: Location) {
        val b = best
        if (b == null || l.time > b.time + 30_000 || l.accuracy < b.accuracy) best = l
    }
    override fun onLocationChanged(l: Location) { consider(l) }

    private fun status(s: String) {
        prefs().edit().putString("status", java.text.SimpleDateFormat("HH:mm:ss").format(java.util.Date()) + "  " + s).apply()
        getSystemService(NotificationManager::class.java).notify(1, notif(s))
    }

    private fun sendNow() {
        val l = best
        if (l == null) { status("Waiting for GPS fix..."); return }
        val p = prefs()
        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.let { it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / it.getIntExtra(BatteryManager.EXTRA_SCALE, 100) } ?: -1
        val body = JSONObject().put("code", p.getString("code", "")).put("id", p.getString("id", ""))
            .put("type", p.getString("type", "Crane")).put("driver", p.getString("driver", ""))
            .put("lat", l.latitude).put("lng", l.longitude).put("acc", l.accuracy.toDouble())
            .put("speed", l.speed.toDouble()).put("battery", level).toString()
        Thread {
            try {
                val c = URL(p.getString("server", "")!!.trimEnd('/') + "/api/location").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 15000; c.readTimeout = 15000
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toByteArray()) }
                val code = c.responseCode
                status(if (code == 200) "Sent %.5f, %.5f".format(l.latitude, l.longitude) else "Server rejected ($code) - check code/ID")
            } catch (e: Exception) { status("No network - will retry") }
        }.start()
    }

    override fun onDestroy() {
        handler.removeCallbacks(loop)
        try { lm.removeUpdates(this) } catch (e: Exception) {}
        wake?.let { if (it.isHeld) it.release() }
        prefs().edit().putBoolean("running", false).apply()
        super.onDestroy()
    }
}
