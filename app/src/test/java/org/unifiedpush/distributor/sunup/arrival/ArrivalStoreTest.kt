package org.unifiedpush.distributor.sunup.arrival

import android.content.Context
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArrivalStoreTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before fun clear() {
        context.deleteDatabase("push_arrival.db")
    }

    @Test fun defaultOffAndCommittedWritesAreVisibleToIndependentConnectionsAndReopen() {
        ArrivalStore(context).use { ui ->
            ArrivalStore(context).use { service ->
                assertFalse(service.isEnabled("forkgram"))
                ui.setEnabled("forkgram", true)
                assertTrue(service.isEnabled("forkgram"))
                assertFalse(service.isEnabled("talk"))
                ui.setEnabled("forkgram", false)
                assertFalse(service.isEnabled("forkgram"))
                ui.setEnabled("forkgram", true)
            }
        }
        ArrivalStore(context).use { assertTrue(it.isEnabled("forkgram")) }
    }

    @Test fun dedupUsesTransportIdentityNotPayloadAndSurvivesReopen() {
        ArrivalStore(context).use {
            assertTrue(it.firstSeen("server", "uaid", "channel1", "v1", 100))
            assertFalse(it.firstSeen("server", "uaid", "channel1", "v1", 101))
            assertTrue(it.firstSeen("server", "uaid", "channel1", "v2", 102))
            assertTrue(it.firstSeen("server", "uaid", "channel2", "v1", 103))
            assertTrue(it.firstSeen("server", "new-uaid", "channel1", "v1", 104))
            assertTrue(it.firstSeen("new-server", "uaid", "channel1", "v1", 105))
        }
        ArrivalStore(context).use {
            assertFalse(it.firstSeen("server", "uaid", "channel1", "v1", 106))
            assertTrue(it.firstSeen("server", "uaid", "channel1", "v1", 86_400_200))
        }
    }

    @Test fun dedupIsBoundedAndMissingIdentityNeverSuppresses() {
        ArrivalStore(context).use { db ->
            repeat(513) { assertTrue(db.firstSeen("s", "u", "c", "v$it", it.toLong())) }
            assertTrue(db.firstSeen("s", "u", "c", "v0", 600))
            repeat(2) { assertTrue(db.firstSeen("s", "u", "c", "", 601)) }
        }
    }

    @Test
    fun fullUninstallClearsOptInButUnrelatedPackageRemains() {
        ArrivalStore(context).use {
            it.setEnabled("forkgram", true)
            it.setEnabled("talk", true)
            it.removePackage("forkgram")
            assertFalse(it.isEnabled("forkgram"))
            assertTrue(it.isEnabled("talk"))
        }
    }
}
