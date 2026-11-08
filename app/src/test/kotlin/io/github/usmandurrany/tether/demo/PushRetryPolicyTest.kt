package io.github.usmandurrany.tether.demo

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import io.github.usmandurrany.tether.android.PushWakeRetryPolicy

class PushRetryPolicyTest {
    @Test fun limitsFailuresAndSystemRestarts() {
        assertTrue(PushWakeRetryPolicy.mayStart(0, 1000, 1000))
        assertFalse(PushWakeRetryPolicy.mayStart(1, 1000, 1000))
        assertFalse(PushWakeRetryPolicy.mayStart(2, 1000, 1000))
        assertFalse(PushWakeRetryPolicy.mayStart(3, 1000, 1000))
    }

    @Test fun rejectsStaleOrInvalidWork() {
        assertTrue(PushWakeRetryPolicy.mayStart(0, 1000, 1000 + PushWakeRetryPolicy.MAX_AGE_MILLIS - 1))
        assertFalse(PushWakeRetryPolicy.mayStart(0, 1000, 1000 + PushWakeRetryPolicy.MAX_AGE_MILLIS))
        assertFalse(PushWakeRetryPolicy.mayStart(0, 0, 1000))
        assertFalse(PushWakeRetryPolicy.mayStart(0, 2000, 1000))
    }
}
