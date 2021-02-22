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
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

const val LOG_TAG = "AAInputInjector.CaptainRider"

class CaptainRiderHook : IXposedHookLoadPackage {
    var ctx: Context? = null
    var isConnectionStateMonitorSetup = false

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam?) {
        if (lpparam!!.packageName != "com.eyelights.intercom") {
            return
        }
        Log.i(LOG_TAG, "hook enabled")

        val eyerideCheckInBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, _intent: Intent?) {
                sendReport(lpparam.classLoader)
            }
        }

        val eyerideVolumeControlBroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val appConfig = XposedHelpers.callMethod(
                    getEyelightDevicesInstance(lpparam.classLoader),
                    "getAppConfig"
                )
                var volLevel = XposedHelpers.callMethod(appConfig, "getVolLevel") as Int

                when (intent!!.action) {
                    Utils.intent_eyeride_vol_down -> {
                        volLevel -= 1
                    }
                    Utils.intent_eyeride_vol_up -> {
                        volLevel += 1
                    }
                }

                // volume is 0 ~ 15
                volLevel = volLevel.coerceAtLeast(0).coerceAtMost(15)
                XposedHelpers.callMethod(appConfig, "setVolLevel", volLevel)
                XposedHelpers.callMethod(
                    getEyelightDevicesInstance(lpparam.classLoader),
                    "storeConfig"
                )
                Log.i(LOG_TAG, "volume is set to $volLevel")
            }
        }

        // - setup eyeride report
        XposedHelpers.findAndHookMethod(
            "android.app.Application",
            lpparam.classLoader,
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    val app = param!!.thisObject as Application
                    app.registerReceiver(
                        eyerideCheckInBroadcastReceiver,
                        IntentFilter(Utils.intent_checkin)
                    )
                    app.registerReceiver(
                        eyerideVolumeControlBroadcastReceiver,
                        IntentFilter().apply {
                            addAction(Utils.intent_eyeride_vol_down)
                            addAction(Utils.intent_eyeride_vol_up)
                        }
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
                    app.unregisterReceiver(eyerideCheckInBroadcastReceiver)
                    app.unregisterReceiver(eyerideVolumeControlBroadcastReceiver)

                    val intent = Intent(Utils.intent_eyeride_report)
                    intent.putExtra("isConnected", false)
                    ctx?.sendBroadcast(intent)

                    ctx = null
                }
            }
        )

        // - setup eyeride connection state monitoring
        XposedHelpers.findAndHookMethod(
            "com.eyelights.intercom.common.EyelightsDevices",
            lpparam.classLoader,
            "getConnectionState",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam?) {
                    if (!isConnectionStateMonitorSetup) {
                        isConnectionStateMonitorSetup = true
                        sendReport(lpparam.classLoader)

                        val connectionStateSubject = param!!.result
                        val observerProxy =
                            InvocationHandler { proxy, method, args ->
                                if (method.name == "onNext") {
                                    sendReport(lpparam.classLoader)
                                }

                                null
                            }
                        val observer = Proxy.newProxyInstance(
                            lpparam.classLoader,
                            arrayOf(
                                XposedHelpers.findClass(
                                    "io.reactivex.Observer",
                                    lpparam.classLoader
                                )
                            ),
                            observerProxy
                        )
                        XposedHelpers.callMethod(connectionStateSubject, "subscribe", observer)
                    }
                }
            }
        )
    }

    private fun getEyelightDevicesInstance(classLoader: ClassLoader): Any {
        val eyelightDevicesClass =
            XposedHelpers.findClass("com.eyelights.intercom.common.EyelightsDevices", classLoader)

        return XposedHelpers.getStaticObjectField(eyelightDevicesClass, "INSTANCE")
    }

    private fun sendReport(classLoader: ClassLoader) {
        val intent = Intent(Utils.intent_eyeride_report)

        // EyelightsDevices.INSTANCE.isEyeRideConnected()
        val isEyeRideConnected = XposedHelpers.callMethod(
            getEyelightDevicesInstance(classLoader),
            "isEyeRideConnected"
        ) as Boolean
        intent.putExtra("isConnected", isEyeRideConnected)

        Log.i(LOG_TAG, "send report, isConnected = $isEyeRideConnected")
        ctx?.sendBroadcast(intent)
    }
}
