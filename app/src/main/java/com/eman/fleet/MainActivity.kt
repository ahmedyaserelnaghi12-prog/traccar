package com.eman.fleet

import android.Manifest
import android.app.Activity
import android.content.*
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.*

class MainActivity : Activity() {
    private val fields = LinkedHashMap<String, EditText>()
    private lateinit var typeSpin: Spinner
    private lateinit var btn: Button
    private lateinit var statusTv: TextView
    private val h = Handler(Looper.getMainLooper())
    private val types = listOf("Crane", "Trailer", "Truck", "Other")
    private fun p() = getSharedPreferences("fleet", MODE_PRIVATE)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 40) }
        root.addView(TextView(this).apply { text = "Fleet Tracker"; textSize = 26f })
        fun field(key: String, label: String, hint: String, pw: Boolean = false, num: Boolean = false) {
            root.addView(TextView(this).apply { text = label; setPadding(0, 28, 0, 0) })
            val e = EditText(this).apply {
                this.hint = hint; setSingleLine(); setText(p().getString(key, if (key == "interval") "10" else ""))
                if (pw) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                if (num) inputType = InputType.TYPE_CLASS_NUMBER
            }
            fields[key] = e; root.addView(e)
        }
        field("server", "Server address", "https://your-server.com")
        field("code", "Driver code", "Code from your manager", pw = true)
        field("id", "Plate / Unit ID", "e.g. CR-101")
        field("driver", "Driver name", "Your name")
        field("interval", "Send location every (minutes)", "10", num = true)
        root.addView(TextView(this).apply { text = "Unit type"; setPadding(0, 28, 0, 0) })
        typeSpin = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, types)
            setSelection(types.indexOf(p().getString("type", "Crane")).coerceAtLeast(0))
        }
        root.addView(typeSpin)
        btn = Button(this).apply { setOnClickListener { toggle() } }
        root.addView(btn, LinearLayout.LayoutParams(-1, -2).apply { topMargin = 40 })
        statusTv = TextView(this).apply { textSize = 14f; gravity = Gravity.START; setPadding(0, 30, 0, 0) }
        root.addView(statusTv)
        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() { super.onResume(); tick() }
    override fun onPause() { super.onPause(); h.removeCallbacksAndMessages(null) }
    private fun tick() {
        val run = p().getBoolean("running", false)
        btn.text = if (run) "Stop tracking" else "Start tracking"
        statusTv.text = (if (run) "● Tracking\n" else "Stopped\n") + (p().getString("status", "") ?: "")
        h.postDelayed({ tick() }, 3000)
    }

    private fun toggle() {
        if (p().getBoolean("running", false)) {
            startService(Intent(this, TrackingService::class.java).setAction("STOP")); p().edit().putBoolean("running", false).apply(); tick(); return
        }
        val v = fields.mapValues { it.value.text.toString().trim() }
        if (v["server"].isNullOrEmpty() || v["id"].isNullOrEmpty() || v["code"].isNullOrEmpty()) {
            Toast.makeText(this, "Fill server, code and unit ID", Toast.LENGTH_LONG).show(); return
        }
        val e = p().edit()
        v.forEach { (k, s) -> if (k == "interval") e.putInt(k, s.toIntOrNull() ?: 10) else e.putString(k, s) }
        e.putString("type", types[typeSpin.selectedItemPosition]).apply()
        askPermissions()
    }

    private fun askPermissions() {
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) need.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing = need.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 1); return }
        if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Choose \"Allow all the time\" for location", Toast.LENGTH_LONG).show()
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2); return
        }
        launch()
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Location permission is required", Toast.LENGTH_LONG).show(); return
        }
        if (code == 1) askPermissions() else launch()
    }

    private fun launch() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try { startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))) } catch (e: Exception) {}
        }
        startForegroundService(Intent(this, TrackingService::class.java))
        p().edit().putString("status", "Starting...").putBoolean("running", true).apply(); tick()
    }
}
