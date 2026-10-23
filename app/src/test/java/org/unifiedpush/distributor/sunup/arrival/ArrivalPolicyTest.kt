package org.unifiedpush.distributor.sunup.arrival

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArrivalPolicyTest {
    @Test fun firstPushAlertsAndBurstUpdatesSilently() {
        val first = ArrivalPolicy.next(null, null, 1_000)
        assertEquals(1, first.count)
        assertTrue(first.alert)
        val second = ArrivalPolicy.next(first.count, first.lastAlert, 2_000)
        assertEquals(2, second.count)
        assertFalse(second.alert)
        assertEquals(first.lastAlert, second.lastAlert)
        assertTrue(ArrivalPolicy.next(second.count, second.lastAlert, 31_000).alert)
    }

    @Test fun dismissedNotificationStartsNewCountAndClockRollbackRecovers() {
        assertEquals(1, ArrivalPolicy.next(null, null, 500).count)
        assertTrue(ArrivalPolicy.next(3, 10_000, 500).alert)
        assertEquals(Int.MAX_VALUE, ArrivalPolicy.next(Int.MAX_VALUE, 1, 500).count)
    }
}
