// port-lint: tests tests/ready.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadyTest {
    @Test
    fun smoke1() {
        val (s1, r1) = unbounded<Int>()
        val (s2, r2) = unbounded<Int>()

        s1.send(1)

        val sel1 = Select()
        sel1.recv(r1)
        sel1.recv(r2)
        assertEquals(0, sel1.ready())
        assertEquals(Result.success(1), r1.tryRecv())

        s2.send(2)

        val sel2 = Select()
        sel2.recv(r1)
        sel2.recv(r2)
        assertEquals(1, sel2.ready())
        assertEquals(Result.success(2), r2.tryRecv())
    }

    @Test
    fun smoke2() {
        val (s1, r1) = unbounded<Int>()
        val (s2, r2) = unbounded<Int>()
        val (s3, r3) = unbounded<Int>()
        val (s4, r4) = unbounded<Int>()
        val (s5, r5) = unbounded<Int>()

        s5.send(5)

        val sel = Select()
        sel.recv(r1)
        sel.recv(r2)
        sel.recv(r3)
        sel.recv(r4)
        val oper5 = sel.recv(r5)
        assertEquals(4, sel.ready())
        assertEquals(Result.success(5), r5.tryRecv())
    }

    @Test
    fun defaultWhenDisconnected() {
        val (s1, r1) = unbounded<Int>()
        s1.release()

        val sel1 = Select()
        sel1.recv(r1)
        val op1 = sel1.tryReady()
        assertTrue(op1.isSuccess)
        assertEquals(0, op1.getOrNull())
        assertTrue(r1.tryRecv().exceptionOrNull() is TryRecvError.Disconnected)

        val (s2, r2) = bounded<Int>(0)
        r2.release()

        val sel2 = Select()
        sel2.send(s2)
        val op2 = sel2.tryReady()
        assertTrue(op2.isSuccess)
        assertEquals(0, op2.getOrNull())
        assertEquals(TrySendOutcome.Err(TrySendError.Disconnected(0)), s2.trySend(0))
    }
}
