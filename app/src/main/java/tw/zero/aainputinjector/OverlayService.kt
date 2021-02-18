package tw.zero.aainputinjector

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Binder
import android.os.IBinder
import android.view.*
import android.view.WindowManager.LayoutParams
import android.widget.Button
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams

class OverlayService : Service() {
    private var overlayView: View? = null

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

    override fun onDestroy() {
        super.onDestroy()

        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    fun startOverlay() {
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
    }

    fun stopOverlay() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }

    private fun createView() : View {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val interceptorLayout = object : FrameLayout(this) {
            var startY: Float = 0f
            var initialY: Float = 0f
            override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
                if (!super.dispatchTouchEvent(ev)) {
                    when (ev!!.action) {
                        MotionEvent.ACTION_DOWN -> {
                            startY = ev.rawY
                            initialY = (overlayView!!.layoutParams as WindowManager.LayoutParams).y.toFloat()
                        }
                        MotionEvent.ACTION_MOVE -> {
                            overlayView!!.updateLayoutParams<WindowManager.LayoutParams> {
                                y = (initialY + ev.rawY - startY).toInt()
                            }

                            windowManager.updateViewLayout(overlayView!!, overlayView!!.layoutParams)
                        }
                    }
                }
                return true
            }
        }

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.controller, interceptorLayout)

        view.findViewById<Button>(R.id.btn_prev).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_ROTARY_CONTROLLER, -1)
        }

        view.findViewById<Button>(R.id.btn_enter).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_ENTER, 0)
        }

        view.findViewById<Button>(R.id.btn_next).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_ROTARY_CONTROLLER, 1)
        }

        view.findViewById<Button>(R.id.btn_home).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_HOME, 0)
        }

        view.findViewById<Button>(R.id.btn_map).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_NAVIGATION, 0)
        }

        view.findViewById<Button>(R.id.btn_up).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_DPAD_UP, 0)
        }

        view.findViewById<Button>(R.id.btn_down).setOnClickListener {
            Utils.sendKeyEvent(this, AAKeyCode.KEYCODE_DPAD_DOWN, 0)
        }

        return view
    }
}