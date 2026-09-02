// port-lint: source utils.rs
@file:OptIn(kotlin.time.ExperimentalTime::class)

package io.github.kotlinmania.crossbeamchannel

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.AtomicLong
import kotlin.time.Clock
import kotlin.time.Instant

private val RNG_STATE = AtomicInt(1_406_868_647)

internal fun now(): Instant = Clock.System.now()

internal fun AtomicLong.fetchOr(mask: Long): Long {
    while (true) {
        val curr = load()
        if (compareAndSet(expectedValue = curr, newValue = curr or mask)) {
            return curr
        }
    }
}

internal fun AtomicLong.fetchAdd(delta: Long): Long {
    while (true) {
        val curr = load()
        if (compareAndSet(expectedValue = curr, newValue = curr + delta)) {
            return curr
        }
    }
}

/**
 * Randomly shuffles a list in place using a 32-bit Xorshift PRNG and Daniel Lemire's fast modulo reduction.
 */
internal fun <T> shuffle(v: MutableList<T>) {
    val len = v.size
    if (len <= 1) {
        return
    }

    for (i in 1 until len) {
        var x = RNG_STATE.load()
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        RNG_STATE.store(x)

        val ux = x.toLong() and 0xFFFFFFFFL
        val n = (i + 1).toLong()
        val j = (((ux * n) ushr 32) and 0xFFFFFFFFL).toInt()

        val tmp = v[i]
        v[i] = v[j]
        v[j] = tmp
    }
}

/**
 * Randomly shuffles an array in place using a 32-bit Xorshift PRNG and Daniel Lemire's fast modulo reduction.
 */
internal fun <T> shuffle(v: Array<T>) {
    val len = v.size
    if (len <= 1) {
        return
    }

    for (i in 1 until len) {
        var x = RNG_STATE.load()
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        RNG_STATE.store(x)

        val ux = x.toLong() and 0xFFFFFFFFL
        val n = (i + 1).toLong()
        val j = (((ux * n) ushr 32) and 0xFFFFFFFFL).toInt()

        val tmp = v[i]
        v[i] = v[j]
        v[j] = tmp
    }
}

/**
 * Sleeps until the deadline, or forever if the deadline is null.
 */
internal fun sleepUntil(deadline: Instant?) {
    val backoff = Backoff()
    while (true) {
        val curr = now()
        if (deadline != null) {
            if (curr >= deadline) {
                break
            }
        }
        backoff.snooze()
    }
}

/**
 * Backoff helper for lock-free spinning and snoozing.
 */
internal class Backoff {
    private var step = 0

    fun reset() {
        step = 0
    }

    fun spin(): Boolean {
        if (step <= 6) {
            step++
            var dummy = 0
            val iters = 1 shl step
            for (i in 0 until iters) {
                dummy += i
            }
            return true
        }
        return false
    }

    fun snooze(): Boolean {
        if (step <= 6) {
            spin()
            return true
        }
        step++
        var dummy = 0
        for (i in 0 until 64) {
            dummy += i
        }
        return true
    }

    fun isCompleted(): Boolean = step > 6
}
