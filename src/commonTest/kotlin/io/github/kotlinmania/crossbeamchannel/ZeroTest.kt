// port-lint: tests tests/zero.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZeroTest {
    @Test
    fun smoke() {
        val (s, r) = bounded<Int>(0)
        assertEquals(TrySendOutcome.Err(TrySendError.Full(7)), s.trySend(7))
        assertTrue(r.tryRecv().exceptionOrNull() is TryRecvError.Empty)
    }

    @Test
    fun capacity() {
        val (s, r) = bounded<Unit>(0)
        assertEquals(0, s.capacity())
        assertEquals(0, r.capacity())
    }

    @Test
    fun lenEmptyFull() {
        val (s, r) = bounded<Int>(0)

        assertEquals(0, s.len())
        assertTrue(s.isEmpty())
        assertTrue(s.isFull())
        assertEquals(0, r.len())
        assertTrue(r.isEmpty())
        assertTrue(r.isFull())
    }

    // Unported: Rust Drop trait with custom destructor (drops) cannot be reproduced in Kotlin garbage collection.
}
