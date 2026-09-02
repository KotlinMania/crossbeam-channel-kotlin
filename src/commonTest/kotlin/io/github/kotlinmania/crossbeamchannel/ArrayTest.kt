// port-lint: tests tests/array.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ArrayTest {
    @Test
    fun smoke() {
        val (s, r) = bounded<Int>(1)
        assertEquals(SendOutcome.Ok, s.send(7))
        assertEquals(Result.success(7), r.tryRecv())

        assertEquals(SendOutcome.Ok, s.send(8))
        assertEquals(Result.success(8), r.recv())

        assertTrue(r.tryRecv().exceptionOrNull() is TryRecvError.Empty)
        assertTrue(r.recvTimeout(10.milliseconds).exceptionOrNull() is RecvTimeoutError.Timeout)
    }

    @Test
    fun capacity() {
        for (i in 1 until 10) {
            val (s, r) = bounded<Unit>(i)
            assertEquals(i, s.capacity())
            assertEquals(i, r.capacity())
        }
    }

    @Test
    fun lenEmptyFull() {
        val (s, r) = bounded<Unit>(2)

        assertEquals(0, s.len())
        assertTrue(s.isEmpty())
        assertFalse(s.isFull())
        assertEquals(0, r.len())
        assertTrue(r.isEmpty())
        assertFalse(r.isFull())

        assertEquals(SendOutcome.Ok, s.send(Unit))

        assertEquals(1, s.len())
        assertFalse(s.isEmpty())
        assertFalse(s.isFull())
        assertEquals(1, r.len())
        assertFalse(r.isEmpty())
        assertFalse(r.isFull())

        assertEquals(SendOutcome.Ok, s.send(Unit))

        assertEquals(2, s.len())
        assertFalse(s.isEmpty())
        assertTrue(s.isFull())
        assertEquals(2, r.len())
        assertFalse(r.isEmpty())
        assertTrue(r.isFull())

        assertEquals(Result.success(Unit), r.recv())

        assertEquals(1, s.len())
        assertFalse(s.isEmpty())
        assertFalse(s.isFull())
        assertEquals(1, r.len())
        assertFalse(r.isEmpty())
        assertFalse(r.isFull())
    }

    @Test
    fun sendAfterDisconnect() {
        val (s, r) = bounded<Int>(100)

        assertEquals(SendOutcome.Ok, s.send(1))
        assertEquals(SendOutcome.Ok, s.send(2))
        assertEquals(SendOutcome.Ok, s.send(3))

        r.release()

        assertEquals(SendOutcome.Err(SendError(4)), s.send(4))
        assertEquals(TrySendOutcome.Err(TrySendError.Disconnected(5)), s.trySend(5))
        assertEquals(
            SendTimeoutOutcome.Err(SendTimeoutError.Disconnected(6)),
            s.sendTimeout(6, 10.milliseconds)
        )
    }

    @Test
    fun recvAfterDisconnect() {
        val (s, r) = bounded<Int>(100)

        assertEquals(SendOutcome.Ok, s.send(1))
        assertEquals(SendOutcome.Ok, s.send(2))
        assertEquals(SendOutcome.Ok, s.send(3))

        s.release()

        assertEquals(Result.success(1), r.recv())
        assertEquals(Result.success(2), r.recv())
        assertEquals(Result.success(3), r.recv())
        assertEquals(Result.failure(RecvError()), r.recv())
    }

    @Test
    fun len() {
        val cap = 1000
        val (s, r) = bounded<Int>(cap)

        assertEquals(0, s.len())
        assertEquals(0, r.len())

        for (round in 0 until cap / 10) {
            for (i in 0 until 50) {
                assertEquals(SendOutcome.Ok, s.send(i))
                assertEquals(i + 1, s.len())
            }

            for (i in 0 until 50) {
                assertEquals(Result.success(i), r.recv())
                assertEquals(50 - i - 1, r.len())
            }
        }

        assertEquals(0, s.len())
        assertEquals(0, r.len())

        for (i in 0 until cap) {
            assertEquals(SendOutcome.Ok, s.send(i))
            assertEquals(i + 1, s.len())
        }

        for (i in 0 until cap) {
            assertEquals(Result.success(i), r.recv())
        }

        assertEquals(0, s.len())
        assertEquals(0, r.len())
    }

    @Test
    fun fairness() {
        val count = 1000
        val (s1, r1) = bounded<Unit>(count)
        val (s2, r2) = bounded<Unit>(count)

        for (i in 0 until count) {
            s1.send(Unit)
            s2.send(Unit)
        }

        val hits = IntArray(2)
        for (i in 0 until count) {
            val sel = Select()
            val i1 = sel.recv(r1)
            val i2 = sel.recv(r2)
            val op = sel.select()
            when (op.index()) {
                i1 -> {
                    op.recv(r1)
                    hits[0]++
                }
                i2 -> {
                    op.recv(r2)
                    hits[1]++
                }
            }
        }
        assertTrue(hits.all { it >= count / hits.size / 4 })
    }

    @Test
    fun fairnessDuplicates() {
        val count = 1000
        val (s, r) = bounded<Unit>(count)

        for (i in 0 until count) {
            s.send(Unit)
        }

        val hits = IntArray(5)
        for (i in 0 until count) {
            val sel = Select()
            val indices = (0 until 5).map { sel.recv(r) }
            val op = sel.select()
            val idx = indices.indexOf(op.index())
            if (idx >= 0) {
                op.recv(r)
                hits[idx]++
            }
        }
        assertTrue(hits.all { it >= count / hits.size / 4 })
    }

    // Unported: Rust Drop trait with custom destructor (drops, panic_on_drop) cannot be reproduced in Kotlin garbage collection.
}
