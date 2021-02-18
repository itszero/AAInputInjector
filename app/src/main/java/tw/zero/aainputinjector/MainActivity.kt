package tw.zero.aainputinjector

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.IBinder
import android.provider.Settings
import android.widget.Button

class MainActivity : AppCompatActivity() {
    private var isOverlayActive: Boolean = false
    private lateinit var overlayService: OverlayService
    private var overlayServiceConnected: Boolean = false

    private val overlayServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as OverlayService.LocalBinder
            overlayService = binder.getService()
            overlayServiceConnected = true
            isOverlayActive = overlayService.isOverlayActive
            updateOverlayButton()
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            overlayServiceConnected = false
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestOverlayPermissionIfNeeded()

        findViewById<Button>(R.id.btn_start_stop).setOnClickListener {
            if (!overlayServiceConnected) {
                return@setOnClickListener
            }

            isOverlayActive = !isOverlayActive

            updateOverlayButton()
            if (isOverlayActive) {
                overlayService.startOverlay()
            } else {
                overlayService.stopOverlay()
            }
        }

        val serviceIntent = Intent(this, OverlayService::class.java)
        startService(serviceIntent)
        bindService(serviceIntent, overlayServiceConnection, 0)
    }

    override fun onStop() {
        super.onStop()

        unbindService(overlayServiceConnection)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        requestOverlayPermissionIfNeeded()
    }

    private fun requestOverlayPermissionIfNeeded() {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 0)
        }
    }

    private fun updateOverlayButton() {
        val button = findViewById<Button>(R.id.btn_start_stop)
        if (isOverlayActive) {
            button.text = "Stop Overlay"
        } else {
            button.text = "Start Overlay"
        }
    }
}