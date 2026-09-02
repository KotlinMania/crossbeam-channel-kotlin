// port-lint: tests tests/mpsc.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MpscTest {
    @Test
    fun smoke() {
        val (tx, rx) = unbounded<Int>()
        tx.send(1)
        assertEquals(Result.success(1), rx.tryRecv())
    }

    @Test
    fun smokeShared() {
        val (tx, rx) = unbounded<Int>()
        tx.send(1)
        assertEquals(Result.success(1), rx.tryRecv())
        val tx2 = tx.clone()
        tx2.send(2)
        assertEquals(Result.success(2), rx.tryRecv())
    }

    @Test
    fun smokePortGone() {
        val (tx, rx) = unbounded<Int>()
        rx.release()
        val res = tx.send(1)
        assertTrue(res is SendOutcome.Err)
    }

    @Test
    fun smokeChanGone() {
        val (tx, rx) = unbounded<Int>()
        tx.release()
        assertTrue(rx.tryRecv().exceptionOrNull() is TryRecvError.Disconnected)
    }
}
