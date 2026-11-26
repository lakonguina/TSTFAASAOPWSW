package com.tstfaasaopwsw

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Daily allowed-hours window, including the overnight (wrapping) case. */
class WindowTest {

    @Test fun daytime_window_allows_inside_only() {
        val w = Window(startMin = 8 * 60, endMin = 22 * 60) // 08:00–22:00
        assertTrue(w.allows(8 * 60))        // start inclusive
        assertTrue(w.allows(12 * 60))
        assertFalse(w.allows(22 * 60))      // end exclusive
        assertFalse(w.allows(7 * 60 + 59))
        assertFalse(w.allows(23 * 60))
    }

    @Test fun overnight_window_wraps_past_midnight() {
        val w = Window(startMin = 22 * 60, endMin = 6 * 60) // 22:00–06:00
        assertTrue(w.allows(23 * 60))
        assertTrue(w.allows(2 * 60))
        assertTrue(w.allows(22 * 60))       // start inclusive
        assertFalse(w.allows(6 * 60))       // end exclusive
        assertFalse(w.allows(12 * 60))
    }
}
