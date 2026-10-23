package org.unifiedpush.distributor.sunup.arrival

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.util.Base64
import okhttp3.Request
import okhttp3.WebSocket
import okio.ByteString
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.unifiedpush.distributor.sunup.DatabaseFactory
import org.unifiedpush.distributor.sunup.api.ServerConnection

/** Local simulated WebSocket frames, not an Autopush end-to-end test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PushDeliveryTest {
    @Test fun forwardingAndAckPreserveBytesWithFeatureOffOnOrPermissionDenied() {
        val context: Context = RuntimeEnvironment.getApplication()
        val db = DatabaseFactory.getDb(context).writableDatabase
        db.delete("apps", null, null)
        db.execSQL(
            "INSERT INTO apps (connectorToken, packageName, appToken, disabled, msgCount) VALUES (?, ?, ?, 0, 0)",
            arrayOf("token", "test.forkgram", "channel")
        )
        val manager = context.getSystemService(NotificationManager::class.java)
        val application = shadowOf(context as Application)
        val ws = RecordingSocket()
        val connection = ServerConnection(context) {}
        val payload = byteArrayOf(0, 1, -1, 127, -128)
        val encoded = Base64.encodeToString(payload, Base64.URL_SAFE or Base64.NO_WRAP)
        listOf(false to true, true to true, true to false).forEachIndexed { index, (enabled, allowed) ->
            ArrivalStore(context).use { it.setEnabled("test.forkgram", enabled) }
            shadowOf(manager).setNotificationsEnabled(allowed)
            connection.onMessage(ws, """{"messageType":"notification","channelID":"channel","version":"$index","data":"$encoded"}""")
            val delivery = application.broadcastIntents.last { it.action == "org.unifiedpush.android.connector.MESSAGE" }
            assertEquals("test.forkgram", delivery.`package`)
            assertArrayEquals(payload, delivery.getByteArrayExtra("bytesMessage"))
            assertTrue(ws.sent.last().contains("ack"))
            assertTrue(ws.sent.last().contains("\"version\":\"$index\""))
        }
        assertEquals(3, DatabaseFactory.getDb(context).getAppFromChanId("channel")!!.msgCount)
        // A transport replay still follows the library's original forwarding and ACK behavior.
        connection.onMessage(ws, """{"messageType":"notification","channelID":"channel","version":"2","data":"$encoded"}""")
        assertEquals(4, DatabaseFactory.getDb(context).getAppFromChanId("channel")!!.msgCount)
        connection.onMessage(ws, "{}")
        assertEquals(4, DatabaseFactory.getDb(context).getAppFromChanId("channel")!!.msgCount)
        // Empty pushes are valid wake-up signals too.
        connection.onMessage(ws, """{"messageType":"notification","channelID":"channel","version":"empty"}""")
        assertArrayEquals(
            byteArrayOf(),
            application.broadcastIntents.last {
                it.action == "org.unifiedpush.android.connector.MESSAGE"
            }.getByteArrayExtra("bytesMessage")
        )
    }

    private class RecordingSocket : WebSocket {
        val sent = mutableListOf<String>()
        override fun request(): Request = Request.Builder().url("https://example.invalid").build()
        override fun queueSize(): Long = 0
        override fun send(text: String): Boolean {
            sent += text
            return true
        }
        override fun send(bytes: ByteString): Boolean = error("Unexpected binary send")
        override fun close(code: Int, reason: String?): Boolean = true
        override fun cancel() = Unit
    }
}
