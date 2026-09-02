// port-lint: tests tests/same_channel.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class SameChannelTest {
    @Test
    fun afterSameChannel() {
        val r = after(50.milliseconds)
        val r2 = r.clone()
        assertTrue(r.sameChannel(r2))

        val r3 = after(50.milliseconds)
        assertFalse(r.sameChannel(r3))
        assertFalse(r2.sameChannel(r3))

        val r4 = after(100.milliseconds)
        assertFalse(r.sameChannel(r4))
        assertFalse(r2.sameChannel(r4))
    }

    @Test
    fun arraySameChannel() {
        val (s, r) = bounded<Int>(1)
        val s2 = s.clone()
        assertTrue(s.sameChannel(s2))

        val r2 = r.clone()
        assertTrue(r.sameChannel(r2))

        val (s3, r3) = bounded<Int>(1)
        assertFalse(s.sameChannel(s3))
        assertFalse(s2.sameChannel(s3))
        assertFalse(r.sameChannel(r3))
        assertFalse(r2.sameChannel(r3))
    }

    @Test
    fun listSameChannel() {
        val (s, r) = unbounded<Int>()
        val s2 = s.clone()
        assertTrue(s.sameChannel(s2))

        val r2 = r.clone()
        assertTrue(r.sameChannel(r2))

        val (s3, r3) = unbounded<Int>()
        assertFalse(s.sameChannel(s3))
        assertFalse(s2.sameChannel(s3))
        assertFalse(r.sameChannel(r3))
        assertFalse(r2.sameChannel(r3))
    }

    @Test
    fun neverSameChannel() {
        val r = never<Int>()
        val r2 = r.clone()
        assertTrue(r.sameChannel(r2))

        val r3 = never<Int>()
        assertTrue(r.sameChannel(r3))
        assertTrue(r2.sameChannel(r3))
    }

    @Test
    fun tickSameChannel() {
        val r = tick(50.milliseconds)
        val r2 = r.clone()
        assertTrue(r.sameChannel(r2))

        val r3 = tick(50.milliseconds)
        assertFalse(r.sameChannel(r3))
        assertFalse(r2.sameChannel(r3))

        val r4 = tick(100.milliseconds)
        assertFalse(r.sameChannel(r4))
        assertFalse(r2.sameChannel(r4))
    }

    @Test
    fun zeroSameChannel() {
        val (s, r) = bounded<Int>(0)
        val s2 = s.clone()
        assertTrue(s.sameChannel(s2))

        val r2 = r.clone()
        assertTrue(r.sameChannel(r2))

        val (s3, r3) = bounded<Int>(0)
        assertFalse(s.sameChannel(s3))
        assertFalse(s2.sameChannel(s3))
        assertFalse(r.sameChannel(r3))
        assertFalse(r2.sameChannel(r3))
    }

    @Test
    fun differentFlavorsSameChannel() {
        val (s1, r1) = bounded<Int>(0)
        val (s2, r2) = unbounded<Int>()

        assertFalse(s1.sameChannel(s2))
        assertFalse(r1.sameChannel(r2))
    }
}
