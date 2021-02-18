package tw.zero.aainputinjector

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import java.io.StringWriter

class Hook : IXposedHookLoadPackage {
    var callback: Any? = null

    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (XposedHelpers.findClassIfExists("com.google.android.gms.car.senderprotocol.InputEndPoint", lpparam.classLoader) == null) {
            return
        }
        Log.i("CAR.SERVICE", "found car class in ${lpparam.packageName} - ${lpparam.processName}")

        XposedHelpers.findAndHookConstructor(
            "com.google.android.gms.car.senderprotocol.InputEndPoint",
            lpparam.classLoader,
            "com.google.android.gms.car.senderprotocol.InputEndPoint.InputCallback",
            "com.google.android.gms.car.senderprotocol.ProtocolManager.ProtocolErrorHandler",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    Log.i("CAR.SERVICE", "InputEndPoint created, callback = ${param.args[0]}")
                    callback = param.args[0]
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            "cad",
            lpparam.classLoader,
            "a",
            "qlu",
            Int::class.java, // longpress = 0
            Int::class.java, // repeatcount = 0
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    val writer = StringWriter()
                    val qlu = param.args[0]
                    writer.append("qlu: ")
                    val fields = qlu::class.java.declaredFields
                    for (field in fields) {
                        field.isAccessible = true
                        writer.append("${field.name} = ${field.get(qlu)}, ")
                    }
                    Log.i("CAR.SERVICE", "${param.thisObject} - onKeyEvent ${writer.toString()}")
                }
            }
        )

        val injectKeyEventBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val keyCode = intent!!.getIntExtra("keyCode", 0)
                val delta = intent!!.getIntExtra("delta", 0)
                if (callback != null) {
                    if (keyCode == AAKeyCode.KEYCODE_ROTARY_CONTROLLER.keyCode) {
                        Log.i("CAR.SERVICE", "Inject scroll event, delta = $delta")
                        sendScrollEvent(callback!!, delta)
                    } else {
                        Log.i(
                            "CAR.SERVICE",
                            "Injecting key: ${
                                AAKeyCode.values().firstOrNull { it.keyCode == keyCode }?.name
                            }($keyCode) delta: $delta"
                        )
                        sendKeyEvent(lpparam.classLoader, callback!!, keyCode)
                    }
                }
            }
        }

        XposedHelpers.findAndHookMethod(
            "android.app.Application",
            lpparam.classLoader,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    val app = param!!.thisObject as Application
                    Log.i("CAR.SERVICE", "register broadcast receiver")
                    app.registerReceiver(injectKeyEventBroadcastReceiver, IntentFilter(Utils.intent_inject_key))
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            "android.app.Application",
            lpparam.classLoader,
            "onTerminate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    val app = param!!.thisObject as Application
                    app.unregisterReceiver(injectKeyEventBroadcastReceiver)
                }
            }
        )
    }

    fun sendKeyEvent(classLoader: ClassLoader, callback: Any, keyCode: Int) {
        val keyEventDown = XposedHelpers.newInstance(
            XposedHelpers.findClass(
                "qlu",
                classLoader
            )
        )
        XposedHelpers.setIntField(keyEventDown, "a", 15)
        XposedHelpers.setIntField(keyEventDown, "b", keyCode)
        XposedHelpers.setBooleanField(keyEventDown, "c", true) // isDown
        XposedHelpers.setIntField(keyEventDown, "d", 0)
        XposedHelpers.setBooleanField(keyEventDown, "e", false)
        XposedHelpers.callMethod(callback, "n", keyEventDown)

        val keyEventUp = XposedHelpers.newInstance(
            XposedHelpers.findClass(
                "qlu",
                classLoader
            )
        )
        XposedHelpers.setIntField(keyEventUp, "a", 15)
        XposedHelpers.setIntField(keyEventUp, "b", keyCode)
        XposedHelpers.setBooleanField(keyEventUp, "c", false) // isDown
        XposedHelpers.setIntField(keyEventUp, "d", 0)
        XposedHelpers.setBooleanField(keyEventUp, "e", false)
        XposedHelpers.callMethod(callback, "n", keyEventUp)
    }

    fun sendScrollEvent(callback: Any, delta: Int) {
        XposedHelpers.callMethod(callback, "q", AAKeyCode.KEYCODE_ROTARY_CONTROLLER.keyCode, delta)
    }
}
