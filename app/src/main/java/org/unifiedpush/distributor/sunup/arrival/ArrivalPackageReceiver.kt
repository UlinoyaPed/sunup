package org.unifiedpush.distributor.sunup.arrival

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** A fresh installation must opt in again; package replacement does not send FULLY_REMOVED. */
class ArrivalPackageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_FULLY_REMOVED) return
        val packageName = intent.data?.schemeSpecificPart ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ArrivalStore(context).use { it.removePackage(packageName) }
                PushArrivalNotifier.cancel(context, packageName)
            } catch (e: Exception) {
                Log.w("ArrivalPackageReceiver", "Could not clear arrival setting", e)
            } finally {
                pending.finish()
            }
        }
    }
}
