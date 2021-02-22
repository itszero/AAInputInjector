package tw.zero.aainputinjector

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Binder
import android.os.IBinder
import android.view.*
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams

class OverlayService : Service() {
    private var overlayView: View? = null
    private var currentAppFacet: AAFacetType = AAFacetType.UNKNOWN_FACET
    private var isEyeRideInstalled = false
    private var isEyeRideConnected = false

    inner class LocalBinder : Binder() {
        // Return this instance of LocalService so clients can call public methods
        fun getService(): OverlayService = this@OverlayService
    }

    private val binder = LocalBinder()
    override fun onBind(intent: Intent?): IBinder? {
        return binder
    }

    val isOverlayActive
        get() = overlayView != null

    private val facetUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val facetTypeStr = intent!!.getStringExtra("facetType")
            val facetType = AAFacetType.valueOf(facetTypeStr!!)
            currentAppFacet = facetType
            updateView(null)
        }
    }

    private val captainRiderReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            isEyeRideConnected = intent!!.getBooleanExtra("isConnected", false)
            updateView(null)
        }
    }

    override fun onCreate() {
        super.onCreate()
        registerReceiver(facetUpdateReceiver, IntentFilter(Utils.intent_facet_changed))
        registerReceiver(captainRiderReceiver, IntentFilter(Utils.intent_eyeride_report))
    }

    override fun onDestroy() {
        super.onDestroy()

        unregisterReceiver(facetUpdateReceiver)
        unregisterReceiver(captainRiderReceiver)

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    fun startOverlay() {
        isEyeRideInstalled = packageManager.getInstalledPackages(0).firstOrNull {
            it.packageName == "com.eyelights.intercom"
        } != null

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView = createView()

        val params = LayoutParams(
            LayoutParams.MATCH_PARENT,
            LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.NO_GRAVITY
        params.x = 0
        params.y = 0
        windowManager.addView(overlayView, params)

        sendBroadcast(Intent(Utils.intent_checkin))
    }

    fun stopOverlay() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    private fun createView(): View {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val interceptorLayout = object : FrameLayout(this) {
            var startY: Float = 0f
            var initialY: Float = 0f
            override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                if (!super.dispatchTouchEvent(ev)) {
                    when (ev!!.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startY = ev.rawY
                            initialY =
                                (overlayView!!.layoutParams as WindowManager.LayoutParams).y.toFloat()
                        }
                        MotionEvent.ACTION_MOVE -> {
                            overlayView!!.updateLayoutParams<WindowManager.LayoutParams> {
                                y = (initialY + ev.rawY - startY).toInt()
                            }

                            windowManager.updateViewLayout(
                                overlayView!!,
                                overlayView!!.layoutParams
                            )
                        }
                    }
                }
                return true
            }
        }

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.controller, interceptorLayout)

        view.findViewById<ImageButton>(R.id.btn_prev).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_ROTARY_CONTROLLER, -1)
        }

        view.findViewById<ImageButton>(R.id.btn_enter).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_ENTER, 0)
        }

        view.findViewById<ImageButton>(R.id.btn_next).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_ROTARY_CONTROLLER, 1)
        }

        view.findViewById<ImageButton>(R.id.btn_home).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_HOME, 0)
        }

        view.findViewById<ImageButton>(R.id.btn_up).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_DPAD_UP, 0)
        }

        view.findViewById<ImageButton>(R.id.btn_down).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_DPAD_DOWN, 0)
        }

        view.findViewById<ImageButton>(R.id.btn_vol_up).setOnClickListener {
            sendBroadcast(Intent(Utils.intent_eyeride_vol_up))
        }

        view.findViewById<ImageButton>(R.id.btn_vol_down).setOnClickListener {
            sendBroadcast(Intent(Utils.intent_eyeride_vol_down))
        }

        updateView(view)

        return view
    }

    private fun updateView(view: View?) {
        val viewToUse = (view ?: overlayView) ?: return

        viewToUse.findViewById<ImageButton>(R.id.btn_alt_app).setOnClickListener {
            if (currentAppFacet !== AAFacetType.NAVIGATION) {
                Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_NAVIGATION, 0)
            } else {
                Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_MEDIA, 0)
            }
        }

        viewToUse.findViewById<ImageButton>(R.id.btn_alt_app).setImageResource(
            if (currentAppFacet == AAFacetType.NAVIGATION) R.drawable.ic_music else R.drawable.ic_nav
        )

        viewToUse.findViewById<LinearLayout>(R.id.layout_eyeride_volume).visibility =
            if (isEyeRideInstalled) LinearLayout.VISIBLE else LinearLayout.GONE

        viewToUse.findViewById<ImageButton>(R.id.btn_vol_up).isEnabled = isEyeRideConnected
        viewToUse.findViewById<ImageButton>(R.id.btn_vol_up)
            .backgroundTintList =
            ColorStateList.valueOf(if (isEyeRideConnected) Color.rgb(0xD5, 0, 0) else Color.BLACK)
        viewToUse.findViewById<ImageButton>(R.id.btn_vol_down).isEnabled = isEyeRideConnected
        viewToUse.findViewById<ImageButton>(R.id.btn_vol_down)
            .backgroundTintList =
            ColorStateList.valueOf(if (isEyeRideConnected) Color.rgb(0xD5, 0, 0) else Color.BLACK)
    }
}
