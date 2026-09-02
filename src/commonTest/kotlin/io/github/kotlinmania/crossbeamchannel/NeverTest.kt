// port-lint: tests tests/never.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeverTest {
    @Test
    fun capacity() {
        val r = never<Int>()
        assertEquals(0, r.capacity())
    }

    @Test
    fun lenEmptyFull() {
        val r = never<Int>()
        assertEquals(0, r.len())
        assertTrue(r.isEmpty())
        assertTrue(r.isFull())
    }

    @Test
    fun tryRecv() {
        val r = never<Int>()
        assertTrue(r.tryRecv().exceptionOrNull() is TryRecvError.Empty)
    }
}
