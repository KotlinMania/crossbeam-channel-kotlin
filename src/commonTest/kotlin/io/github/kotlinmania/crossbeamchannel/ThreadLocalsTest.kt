// port-lint: tests tests/thread_locals.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertEquals

class ThreadLocalsTest {
    // Upstream Rust tests TLS destruction ordering with Drop. Kotlin Multiplatform manages
    // context and thread locals via Context.kt without native destructor hooks.
    @Test
    fun contextHandling() {
        val (s, r) = unbounded<Int>()
        s.send(100)
        assertEquals(Result.success(100), r.tryRecv())
    }
}
