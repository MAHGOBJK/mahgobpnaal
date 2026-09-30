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
        
        // 🟢 المقاسات المركزية للأيقونة العائمة لسهولة التحكم فيها
        const val BUBBLE_SIZE_DP = 54
    }

    private lateinit var wm: WindowManager
    private lateinit var menuParams: WindowManager.LayoutParams
    private lateinit var bubbleParams: WindowManager.LayoutParams
    
    private var menuView: View? = null
    private var bubbleView: View? = null
    private var isMenuShowing = false

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
        if (bubbleView == null && menuView == null) {
            initOverlayWindows()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Prefs.sp(this).unregisterOnSharedPreferenceChangeListener(prefListener)
        removeWindow(menuView)
        removeWindow(bubbleView)
        menuView = null
        bubbleView = null
        super.onDestroy()
    }

    private fun removeWindow(v: View?) {
        v?.let {
            try {
                wm.removeView(it)
            } catch (e: Exception) {
            }
        }
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

    private fun initOverlayWindows() {
        val themed = ContextThemeWrapper(this, R.style.Theme_Panel)
        val inflater = LayoutInflater.from(themed)

        // 1. إعداد نوع النافذة المتوافق مع إصدار الأندرويد القديم المستقر
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 🟢 2. بناء وتجهيز الأيقونة العائمة الشفافة (Bubble View)
        bubbleView = inflater.inflate(R.layout.overlay_bubble, null) // تم الربط مع ملف overlay_bubble النظيف
        val bSize = dp(BUBBLE_SIZE_DP)
        bubbleParams = WindowManager.LayoutParams(
            bSize, bSize, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 10
            y = dp(200)
        }

        // 🟢 3. بناء وتجهيز اللوحة الكبيرة (Menu View)
        menuView = inflater.inflate(R.layout.overlay_menu, null)
        val w = mm(50f) // 5 cm
        val h = mm(70f) // 7 cm
        menuParams = WindowManager.LayoutParams(
            w, h, windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (resources.displayMetrics.widthPixels - w) / 2
            y = dp(120)
        }

        // تفعيل الحماية ضد لقطات الشاشة إذا كانت مفعلة
        applySecureFlagInternal()

        // 🟢 ضبط نقرات الأيقونة العائمة لفتح اللوحة الكبيرة
        bubbleView?.setOnClickListener {
            showMenuLayout()
        }
        setupBubbleDrag(bubbleView!!)

        // ضبط زر الإغلاق جوه اللوحة الكبيرة لإخفائها وإرجاع الأيقونة الشفافة
        menuView?.findViewById<View>(R.id.btn_close)?.setOnClickListener { 
            hideMenuLayout()
        }
        setupMenuDrag(menuView!!.findViewById(R.id.header))

        // بناء المحتوى الداخلي والأزرار التابعة للمشروع الأصلي
        val content = menuView!!.findViewById<LinearLayout>(R.id.content)
        buildChecks(themed, inflater, content)
        addSpace(themed, content, 14)
        buildGroup(themed, content, "grp1", listOf("محجوب", "تامر", "Off"))
        addSpace(themed, content, 10)
        buildGroup(themed, content, "grp2", listOf("احمد", "كريم", "عمو", "Off"))
        addSpace(themed, content, 10)

        // تشغيل الأيقونة العائمة الشفافة كديفولت أول ما البرنامج يفتح
        wm.addView(bubbleView, bubbleParams)
        isMenuShowing = false
        Prefs.sp(this).registerOnSharedPreferenceChangeListener(prefListener)
    }

    private fun showMenuLayout() {
        if (!isMenuShowing && menuView != null) {
            if (bubbleView?.windowToken != null) {
                wm.removeView(bubbleView)
            }
            wm.addView(menuView, menuParams)
            isMenuShowing = true
        }
    }

    private fun hideMenuLayout() {
        if (isMenuShowing && menuView != null) {
            if (menuView?.windowToken != null) {
                wm.removeView(menuView)
            }
            wm.addView(bubbleView, bubbleParams)
            isMenuShowing = false
        }
    }

    private fun applySecureFlag() {
        applySecureFlagInternal()
        if (isMenuShowing) {
            menuView?.let { wm.updateViewLayout(it, menuParams) }
        } else {
            bubbleView?.let { wm.updateViewLayout(it, bubbleParams) }
        }
    }

    private fun applySecureFlagInternal() {
        val hide = Prefs.getBool(this, "hide_capture")
        if (hide) {
            menuParams.flags = menuParams.flags or WindowManager.LayoutParams.FLAG_SECURE
            bubbleParams.flags = bubbleParams.flags or WindowManager.LayoutParams.FLAG_SECURE
        } else {
            menuParams.flags = menuParams.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
            bubbleParams.flags = bubbleParams.flags and WindowManager.LayoutParams.FLAG_SECURE.inv()
        }
    }

    // 🟢 ميزة السحب والتحريك الفولاذية للأيقونة العائمة الشفافة
    private fun setupBubbleDrag(view: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        var isClick = false
        
        view.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = bubbleParams.x
                    startY = bubbleParams.y
                    touchX = e.rawX
                    touchY = e.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - touchX).toInt()
                    val dy = (e.rawY - touchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isClick = false
                    }
                    bubbleParams.x = startX + dx
                    bubbleParams.y = startY + dy
                    wm.updateViewLayout(view, bubbleParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun setupMenuDrag(header: View) {
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        header.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = menuParams.x
                    startY = menuParams.y
                    touchX = e.rawX
                    touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    menuParams.x = startX + (e.rawX - touchX).toInt()
                    menuParams.y = startY + (e.rawY - touchY).toInt()
                    menuView?.let { wm.updateViewLayout(it, menuParams) }
                    true
                }
