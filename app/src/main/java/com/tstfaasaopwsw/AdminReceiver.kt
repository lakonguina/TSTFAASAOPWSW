package com.tstfaasaopwsw

import android.app.admin.DeviceAdminReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Admin component. Designated via:
 *   adb shell dpm set-device-owner com.tstfaasaopwsw/.AdminReceiver
 */
class AdminReceiver : DeviceAdminReceiver() {

    override fun onEnabled(context: Context, intent: Intent) {
        Log.i(TAG, PolicyManager.reconcile(context).text)
    }

    companion object {
        private const val TAG = "ForceList"
        fun componentName(context: Context): ComponentName =
            ComponentName(context.applicationContext, AdminReceiver::class.java)
    }
}
