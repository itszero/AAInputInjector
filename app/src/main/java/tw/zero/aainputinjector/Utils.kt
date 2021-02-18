package tw.zero.aainputinjector

import android.content.Context
import android.content.Intent

object Utils {
    const val intent_inject_key = "tw.zero.AAInputInjector.ACTION_INJECT_KEY"

    fun sendKeyEvent(ctx: Context, keyCode: AAKeyCode, delta: Int) {
        val intent = Intent().apply {
            action = Utils.intent_inject_key
            putExtra("keyCode", keyCode.keyCode)
            putExtra("delta", delta)
        }
        ctx.sendBroadcast(intent)
    }
}