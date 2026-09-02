// port-lint: tests tests/tick.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class TickTest {
    @Test
    fun capacity() {
        for (i in 0 until 10) {
            val r = tick(i.milliseconds)
            assertEquals(1, r.capacity())
        }
    }

    @Test
    fun lenEmptyFull() {
        val r = tick(50.milliseconds)
        assertEquals(0, r.len())
        assertTrue(r.isEmpty())
        assertFalse(r.isFull())

        sleepUntil(now() + 100.milliseconds)

        assertEquals(1, r.len())
        assertFalse(r.isEmpty())
        assertTrue(r.isFull())

        val res = r.tryRecv()
        assertTrue(res.isSuccess)

        assertEquals(0, r.len())
        assertTrue(r.isEmpty())
        assertFalse(r.isFull())
    }
}
