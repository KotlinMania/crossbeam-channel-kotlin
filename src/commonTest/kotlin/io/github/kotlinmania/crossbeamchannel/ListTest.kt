// port-lint: tests tests/list.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class ListTest {
    @Test
    fun smoke() {
        val (s, r) = unbounded<Int>()
        assertEquals(TrySendOutcome.Ok, s.trySend(7))
        assertEquals(Result.success(7), r.tryRecv())

        assertEquals(SendOutcome.Ok, s.send(8))
        assertEquals(Result.success(8), r.recv())

        assertTrue(r.tryRecv().exceptionOrNull() is TryRecvError.Empty)
        assertTrue(r.recvTimeout(10.milliseconds).exceptionOrNull() is RecvTimeoutError.Timeout)
    }

    @Test
    fun capacity() {
        val (s, r) = unbounded<Unit>()
        assertNull(s.capacity())
        assertNull(r.capacity())
    }

    @Test
    fun lenEmptyFull() {
        val (s, r) = unbounded<Unit>()

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

        assertEquals(Result.success(Unit), r.recv())

        assertEquals(0, s.len())
        assertTrue(s.isEmpty())
        assertFalse(s.isFull())
        assertEquals(0, r.len())
        assertTrue(r.isEmpty())
        assertFalse(r.isFull())
    }

    @Test
    fun trySend() {
        val count = 1000
        val (s, r) = unbounded<Int>()
        for (i in 0 until count) {
            assertEquals(TrySendOutcome.Ok, s.trySend(i))
        }

        r.release()
        assertEquals(TrySendOutcome.Err(TrySendError.Disconnected(777)), s.trySend(777))
    }

    @Test
    fun send() {
        val count = 1000
        val (s, r) = unbounded<Int>()
        for (i in 0 until count) {
            assertEquals(SendOutcome.Ok, s.send(i))
        }

        r.release()
        assertEquals(SendOutcome.Err(SendError(777)), s.send(777))
    }

    @Test
    fun sendTimeout() {
        val count = 1000
        val (s, r) = unbounded<Int>()
        for (i in 0 until count) {
            assertEquals(SendTimeoutOutcome.Ok, s.sendTimeout(i, i.milliseconds))
        }

        r.release()
        assertEquals(
            SendTimeoutOutcome.Err(SendTimeoutError.Disconnected(777)),
            s.sendTimeout(777, 0.milliseconds)
        )
    }

    @Test
    fun sendAfterDisconnect() {
        val (s, r) = unbounded<Int>()

        assertEquals(SendOutcome.Ok, s.send(1))
        assertEquals(SendOutcome.Ok, s.send(2))
        assertEquals(SendOutcome.Ok, s.send(3))

        r.release()

        assertEquals(SendOutcome.Err(SendError(4)), s.send(4))
        assertEquals(TrySendOutcome.Err(TrySendError.Disconnected(5)), s.trySend(5))
        assertEquals(
            SendTimeoutOutcome.Err(SendTimeoutError.Disconnected(6)),
            s.sendTimeout(6, 0.milliseconds)
        )
    }

    @Test
    fun recvAfterDisconnect() {
        val (s, r) = unbounded<Int>()

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
        val (s, r) = unbounded<Int>()

        assertEquals(0, s.len())
        assertEquals(0, r.len())

        for (i in 0 until 50) {
            assertEquals(SendOutcome.Ok, s.send(i))
            assertEquals(i + 1, s.len())
        }

        for (i in 0 until 50) {
            assertEquals(Result.success(i), r.recv())
            assertEquals(50 - i - 1, r.len())
        }

        assertEquals(0, s.len())
        assertEquals(0, r.len())
    }

    @Test
    fun fairness() {
        val count = 1000
        val (s1, r1) = unbounded<Unit>()
        val (s2, r2) = unbounded<Unit>()

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
        val (s, r) = unbounded<Unit>()

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

    // Unported: Rust Drop trait with custom destructor (drops) cannot be reproduced in Kotlin garbage collection.
}
