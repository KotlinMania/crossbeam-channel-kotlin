// port-lint: tests tests/iter.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IterTest {
    @Test
    fun recvIter() {
        val (s, r) = unbounded<Int>()
        s.send(3)
        s.send(1)
        s.send(2)
        s.release()

        val iter = r.iter()
        assertTrue(iter.hasNext())
        assertEquals(3, iter.next())
        assertTrue(iter.hasNext())
        assertEquals(1, iter.next())
        assertTrue(iter.hasNext())
        assertEquals(2, iter.next())
        assertFalse(iter.hasNext())
    }

    @Test
    fun recvTryIter() {
        val (s, r) = unbounded<Int>()
        s.send(1)
        s.send(2)

        val tryIter = r.tryIter()
        assertTrue(tryIter.hasNext())
        assertEquals(1, tryIter.next())
        assertTrue(tryIter.hasNext())
        assertEquals(2, tryIter.next())
        assertFalse(tryIter.hasNext())
    }
}
