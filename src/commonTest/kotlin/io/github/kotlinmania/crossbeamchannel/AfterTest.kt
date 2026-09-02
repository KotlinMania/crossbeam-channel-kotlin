// port-lint: tests tests/after.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class AfterTest {
    @Test
    fun capacity() {
        for (i in 0 until 10) {
            val r = after(i.milliseconds)
            assertEquals(1, r.capacity())
        }
    }

    @Test
    fun lenEmptyFull() {
        val r = after(0.milliseconds)
        assertEquals(1, r.len())
        assertFalse(r.isEmpty())
        assertTrue(r.isFull())

        val res = r.tryRecv()
        assertTrue(res.isSuccess)

        assertEquals(0, r.len())
        assertTrue(r.isEmpty())
        assertFalse(r.isFull())
    }

    @Test
    fun tryRecvZero() {
        val r = after(0.milliseconds)
        val res = r.tryRecv()
        assertTrue(res.isSuccess)
        assertTrue(r.tryRecv().exceptionOrNull() is TryRecvError.Empty)
    }
}
