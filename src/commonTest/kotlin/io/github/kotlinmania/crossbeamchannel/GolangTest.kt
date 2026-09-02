// port-lint: tests tests/golang.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals

class GolangTest {
    @Test
    fun fifo() {
        val n = 10
        val (s, r) = bounded<Int>(n)
        for (i in 0 until n) {
            s.send(i)
        }
        for (i in 0 until n) {
            assertEquals(Result.success(i), r.tryRecv())
        }
    }

    @Test
    fun select4() {
        val (c, r) = bounded<Int>(1)
        val (_, r1) = bounded<Int>(0)
        c.send(42)

        var received = 0
        select {
            onRecv(r1) { error("unreachable") }
            onRecv(r) { v -> received = v.getOrNull() ?: 0 }
        }
        assertEquals(42, received)
    }
}
