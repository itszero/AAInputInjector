package tw.zero.aainputinjector

import android.content.Context
import android.content.Intent

object Utils {
    const val intent_inject_key = "tw.zero.AAInputInjector.ACTION_INJECT_KEY"
    const val intent_facet_changed = "tw.zero.AAInputInjector.FACET_CHANGED"
    const val intent_checkin = "tw.zero.AAInputInjector.CHECK_IN"
    const val intent_eyeride_report = "tw.zero.AAInputInjector.EYERIDE_REPORT"
    const val intent_eyeride_vol_up = "tw.zero.AAInputInjector.EYERIDE_VOL_UP"
    const val intent_eyeride_vol_down = "tw.zero.AAInputInjector.EYERIDE_VOL_DOWN"

    fun sendKeyEvent(ctx: Context, keyCode: AAKeyCode, delta: Int) {
        val intent = Intent().apply {
            action = Utils.intent_inject_key
            putExtra("keyCode", keyCode.keyCode)
            putExtra("delta", delta)
        }
        ctx.sendBroadcast(intent)
    }
}