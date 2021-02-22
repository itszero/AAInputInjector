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

const val DEBUG_DUMP_KEYEVENT = false

class AndroidAutoHook : IXposedHookLoadPackage {
    var callback: Any? = null
    var ctx: Context? = null
    var lastFacetType: AAFacetType = AAFacetType.UNKNOWN_FACET

    @Throws(Throwable::class)
    override fun handleLoadPackage(lpparam: LoadPackageParam) {
        if (XposedHelpers.findClassIfExists(
                "com.google.android.gms.car.senderprotocol.InputEndPoint",
                lpparam.classLoader
            ) == null
        ) {
            return
        }
        Log.i(
            "AAInputInjector",
            "found car class in ${lpparam.packageName} - ${lpparam.processName}"
        )

        // - Controller

        XposedHelpers.findAndHookConstructor(
            "com.google.android.gms.car.senderprotocol.InputEndPoint",
            lpparam.classLoader,
            "com.google.android.gms.car.senderprotocol.InputEndPoint.InputCallback",
            "com.google.android.gms.car.senderprotocol.ProtocolManager.ProtocolErrorHandler",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun afterHookedMethod(param: MethodHookParam) {
                    Log.i("AAInputInjector", "InputEndPoint created, callback = ${param.args[0]}")
                    callback = param.args[0]
                }
            }
        )

        if (DEBUG_DUMP_KEYEVENT) {
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
                        Log.i(
                            "AAInputInjector",
                            "${param.thisObject} - onKeyEvent ${writer.toString()}"
                        )
                    }
                }
            )
        }

        val injectKeyEventBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val keyCode = intent!!.getIntExtra("keyCode", 0)
                val delta = intent.getIntExtra("delta", 0)
                if (callback != null) {
                    if (keyCode == AAKeyCode.KEYCODE_ROTARY_CONTROLLER.keyCode) {
                        Log.i("AAInputInjector", "Inject scroll event, delta = $delta")
                        sendScrollEvent(callback!!, delta)
                    } else {
                        Log.i(
                            "AAInputInjector",
                            "Injecting key: ${
                                AAKeyCode.values().firstOrNull { it.keyCode == keyCode }?.name
                            }($keyCode) delta: $delta"
                        )
                        sendKeyEvent(lpparam.classLoader, callback!!, keyCode)
                    }
                }
            }
        }

        val checkinBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                // check if we're in the active android auto session
                if (callback != null) {
                    sendFacetType(lastFacetType)
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
                    app.registerReceiver(
                        injectKeyEventBroadcastReceiver,
                        IntentFilter(Utils.intent_inject_key)
                    )
                    app.registerReceiver(
                        checkinBroadcastReceiver,
                        IntentFilter(Utils.intent_checkin)
                    )

                    ctx = app.applicationContext
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
                    app.unregisterReceiver(checkinBroadcastReceiver)

                    ctx = null
                }
            }
        )

        // - Media keys

        XposedHelpers.findAndHookMethod(
            "fkb", /* GhFacetTracker */
            lpparam.classLoader,
            "g", /* updateGsaWithNewFacet */
            Intent::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    val facetTrackerClass = param!!.thisObject::class.java
                    val facetTypeObserver = XposedHelpers.getObjectField(param.thisObject, "d")
                    val facetTypeObj = XposedHelpers.callMethod(facetTypeObserver, "h")
                    val facetTypeOrdinal = XposedHelpers.callMethod(facetTypeObj, "a")

                    val facetType =
                        AAFacetType.values().firstOrNull { it.ordinal == facetTypeOrdinal }

                    if (facetType == null) {
                        Log.i(
                            "AAInputInjector",
                            "app changed, facetType = unknown ($facetTypeOrdinal)"
                        )
                    } else {
                        lastFacetType = facetType
                        sendFacetType(facetType)
                    }
                }
            }
        )
    }

    private fun sendKeyEvent(classLoader: ClassLoader, callback: Any, keyCode: Int) {
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

    private fun sendScrollEvent(callback: Any, delta: Int) {
        XposedHelpers.callMethod(callback, "q", AAKeyCode.KEYCODE_ROTARY_CONTROLLER.keyCode, delta)
    }

    private fun sendFacetType(facetType: AAFacetType) {
        Log.i("AAInputInjector", "app changed, facetType = $facetType")
        val intent = Intent(Utils.intent_facet_changed)
        intent.putExtra("facetType", facetType.name)
        ctx?.sendBroadcast(intent)
    }
}
