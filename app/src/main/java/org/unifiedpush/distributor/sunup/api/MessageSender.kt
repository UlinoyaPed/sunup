package org.unifiedpush.distributor.sunup.api

import android.content.Context
import android.util.Log
import java.util.Calendar
import okhttp3.WebSocket
import org.unifiedpush.distributor.sunup.api.data.ClientMessage
import org.unifiedpush.distributor.sunup.services.RestartWorker
import org.unifiedpush.distributor.sunup.utils.TAG

object MessageSender {
    private var websocket: WebSocket? = null
    private var lastPing: Calendar? = null

    fun newWs(ws: WebSocket) {
        synchronized(this) {
            websocket = ws
            lastPing = null
        }
    }

    fun send(context: Context, message: ClientMessage) {
        synchronized(this) {
            websocket?.let {
                // Log.d(TAG, "Sending: ${message.serialize()}")
                Log.d(TAG, "Sending: ${message::class.java.simpleName}")
                message.send(it)
            } ?: run {
                Log.d(TAG, "Msg not sent, will be during restart")
                RestartWorker.run(context, delay = 0)
            }
        }
    }

    /**
     * Send a ping to the server if we haven't done it in the last 60 seconds
     *
     * The server silently close our connection if we send too many pings
     */
    fun ping(context: Context) {
        val now = Calendar.getInstance()
        val sendPing = lastPing?.let { lastPing ->
            val timeoutPassed = Calendar.getInstance()
            timeoutPassed.time = lastPing.time
            timeoutPassed.add(Calendar.SECOND, 60)
            now.after(timeoutPassed)
        } ?: true
        if (sendPing) {
            send(context, ClientMessage.Ping)
            ServerConnection.waitingPong.set(true)
            lastPing = now
        }
    }
}
