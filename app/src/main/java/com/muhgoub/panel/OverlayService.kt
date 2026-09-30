package com.muhgoub.panel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "panel_overlay"
        const val NOTIF_ID = 1
    }

    private lateinit var wm: WindowManager
    private lateinit var params: WindowManager.LayoutParams
    private var rootView: View? = null

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "hide_capture") applySecureFlag()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (rootView == null) showOverlay()
        return START_STICKY
    }

    override fun onDestroy() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        rootView?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
        rootView = null
        super.onDestroy()
    }

    // ---------- notification ----------

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "MUHGOUB", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT
        )
        return builder
            .setContentTitle("MUHGOUB")
            .setContentText("لوحة التحكم تعمل")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    // ---------- overlay window ----------

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun mm(v: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_MM, v, resources.displayMetrics
    ).toInt()

    private fun showOverlay() {
        val themed = ContextThemeWrapper(this, R.style.Theme_Panel)
        val inflater = LayoutInflater.from(themed)
        val view = inflater.inflate(R.layout.overlay_menu, null)

        val w = mm(50f) // 5 cm
        val h = mm(70f) // 7 cm
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        params = WindowManager.LayoutParams(
            w, h, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - w) / 2
            y = dp(120)
        }

        if (Prefs.getBool(this, "hide_capture")) {
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_SECURE
        }

        view.findViewById<View>(R.id.btn_close).setOnClickListener { stopSelf() }
        setupDrag(view.findViewById(R.id.header))

        val content = view.findViewById<LinearLayout>(R.id.content)
        buildChecks(themed, inflater, content)
        addSpace(themed, content, 14)
        buildGroup(themed, content, "grp1", listOf("محجوب", "تامر", "Off"))
        addSpace(themed, content, 10)
        buildGroup(themed, content, "grp2", listOf("احمد", "كريم", "عمو", "Off"))
        addSpace(themed, content, 10)

        wm.addView(view, params)
        rootView = view
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    private fun applySecureFlag() {
        val hide = Prefs.getBool(this, "hide_capture")
        params.flags = if (hide) {
            params.flags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            params.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        }
        rootView?.let { wm.updateViewLayout(it, params) }
    }

    private fun setupDrag(header: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = e.rawX
                    touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (e.rawX - touchX).toInt()
                    params.y = startY + (e.rawY - touchY).toInt()
                    rootView?.let { wm.updateViewLayout(it, params) }
                    true
                }
                else -> false
            }
        }
    }

    // ---------- content ----------

    private val checkLabels = listOf(
        "حبل 1", "حبل٢",
        "كين٣", "عمو٤",
        "جين٥", "كين٦",
        "تفعيل رقم ٧", "تفعيل رقم ٨",
        "تفعيل رقم ٩", "تفعيل رقم ١٠",
        "تفعيل رقم ١١", "تفعيل رقم ١٢"
    )

    private fun buildChecks(ctx: Context, inflater: LayoutInflater, container: LinearLayout) {
        for (r in 0 until 6) {
            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            for (c in 0..1) {
                val idx = r * 2 + c
                val item = inflater.inflate(R.layout.item_check, row, false)
                item.layoutParams = LinearLayout.LayoutParams(0, dp(46), 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
                bindCheck(item, checkLabels[idx], "chk_$idx")
                row.addView(item)
            }
            container.addView(row)
        }
    }

    private fun bindCheck(item: View, label: String, key: String) {
        val text = item.findViewById<TextView>(R.id.item_text)
        val box = item.findViewById<CheckBox>(R.id.item_box)
        text.text = label

        fun render() {
            val on = Prefs.getBool(this, key)
            box.isChecked = on
            item.setBackgroundResource(if (on) R.drawable.bg_item_on else R.drawable.bg_item)
        }
        render()
        item.setOnClickListener {
            val newState = !Prefs.getBool(this, key)
            Prefs.setBool(this, key, newState)
            render()
            toast(newState)
        }
    }

    private fun buildGroup(ctx: Context, container: LinearLayout, key: String, labels: List<String>) {
        val offIndex = labels.lastIndex
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val pills = labels.mapIndexed { i, label ->
            TextView(ctx).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 16f
                maxLines = 1
                isClickable = true
                isFocusable = true
                layoutParams = LinearLayout.LayoutParams(0, dp(52), 1f).apply {
                    setMargins(dp(4), dp(4), dp(4), dp(4))
                }
            }
        }

        fun render() {
            val sel = Prefs.getInt(this, key, offIndex)
            pills.forEachIndexed { i, pill ->
                val active = i == sel && i != offIndex
                pill.setBackgroundResource(if (active) R.drawable.bg_pill_on else R.drawable.bg_pill)
                pill.setTextColor(if (i == offIndex && !active) 0xFFBDBDBD.toInt() else 0xFFFFFFFF.toInt())
            }
        }

        pills.forEachIndexed { i, pill ->
            pill.setOnClickListener {
                val old = Prefs.getInt(this, key, offIndex)
                val newSel = if (i == old) offIndex else i
                Prefs.setInt(this, key, newSel)
                render()
                toast(newSel != offIndex)
            }
            row.addView(pill)
        }
        render()
        container.addView(row)
    }

    private fun addSpace(ctx: Context, container: LinearLayout, heightDp: Int) {
        container.addView(View(ctx), LinearLayout.LayoutParams(1, dp(heightDp)))
    }

    private fun toast(on: Boolean) {
        Toast.makeText(
            this,
            getString(if (on) R.string.toast_on else R.string.toast_off),
            Toast.LENGTH_SHORT
        ).show()
    }
}
