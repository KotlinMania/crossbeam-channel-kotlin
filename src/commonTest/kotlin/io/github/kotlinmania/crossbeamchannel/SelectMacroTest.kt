// port-lint: tests tests/select_macro.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SelectMacroTest {
    @Test
    fun smoke1() {
        val (s1, r1) = unbounded<Int>()
        val (s2, r2) = unbounded<Int>()

        s1.send(1)

        var received1 = 0
        select {
            onRecv(r1) { v ->
                received1 = v.getOrNull() ?: 0
            }
            onRecv(r2) {
                error("unreachable")
            }
        }
        assertEquals(1, received1)

        s2.send(2)

        var received2 = 0
        select {
            onRecv(r1) {
                error("unreachable")
            }
            onRecv(r2) { v ->
                received2 = v.getOrNull() ?: 0
            }
        }
        assertEquals(2, received2)
    }

    @Test
    fun smoke2() {
        val (_, r1) = unbounded<Int>()
        val (_, r2) = unbounded<Int>()
        val (_, r3) = unbounded<Int>()
        val (_, r4) = unbounded<Int>()
        val (s5, r5) = unbounded<Int>()

        s5.send(5)

        var received5 = 0
        select {
            onRecv(r1) { error("unreachable") }
            onRecv(r2) { error("unreachable") }
            onRecv(r3) { error("unreachable") }
            onRecv(r4) { error("unreachable") }
            onRecv(r5) { v -> received5 = v.getOrNull() ?: 0 }
        }
        assertEquals(5, received5)
    }

    @Test
    fun onSendAndRecv() {
        val (s, r) = unbounded<Int>()

        var sendSuccess = false
        select {
            onSend(s, 42) { outcome ->
                sendSuccess = outcome is SendOutcome.Ok
            }
        }
        assertTrue(sendSuccess)

        var receivedVal = 0
        select {
            onRecv(r) { v ->
                receivedVal = v.getOrNull() ?: 0
            }
        }
        assertEquals(42, receivedVal)
    }
}
