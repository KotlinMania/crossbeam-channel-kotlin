// port-lint: source src/counter.rs
package io.github.kotlinmania.crossbeamchannel.counter

import kotlin.concurrent.atomics.AtomicLong

/** Reference counter internals. */
internal class Counter<C>(
    /** The number of senders associated with the channel. */
    val senders: AtomicLong,
    /** The number of receivers associated with the channel. */
    val receivers: AtomicLong,
    /** The internal channel. */
    val chan: C,
) {
    constructor(chan: C) : this(AtomicLong(1L), AtomicLong(1L), chan)
}

/** Wraps a channel into the reference counter. */
internal fun <C> new(chan: C): Pair<Sender<C>, Receiver<C>> {
    val counter = Counter(chan)
    val s = Sender(counter)
    val r = Receiver(counter)
    return s to r
}

/** The sending side. */
internal class Sender<C> internal constructor(
    private val counter: Counter<C>,
) {
    /** The underlying channel. */
    val chan: C get() = counter.chan

    /** Acquires another sender reference. */
    fun acquire(): Sender<C> {
        val count = counter.senders.fetchAndAdd(1)

        // Cloning senders and calling forget on the clones could potentially overflow the
        // counter. It's very difficult to recover sensibly from such degenerate scenarios so we
        // just abort when the count becomes very large.
        if (count > Long.MAX_VALUE / 2) {
            abortOnOverflow()
        }

        return Sender(counter)
    }

    /**
     * Releases the sender reference.
     *
     * [disconnect] will be called if this is the last sender reference.
     */
    fun release(disconnect: (C) -> Boolean) {
        if (counter.senders.fetchAndAdd(-1) == 1L) {
            disconnect(counter.chan)
        }
    }

    override fun equals(other: Any?): Boolean =
        other is Sender<*> && counter === other.counter

    override fun hashCode(): Int = counter.hashCode()
}

/** The receiving side. */
internal class Receiver<C> internal constructor(
    private val counter: Counter<C>,
) {
    /** The underlying channel. */
    val chan: C get() = counter.chan

    /** Acquires another receiver reference. */
    fun acquire(): Receiver<C> {
        val count = counter.receivers.fetchAndAdd(1)

        // Cloning receivers and calling forget on the clones could potentially overflow the
        // counter. It's very difficult to recover sensibly from such degenerate scenarios so we
        // just abort when the count becomes very large.
        if (count > Long.MAX_VALUE / 2) {
            abortOnOverflow()
        }

        return Receiver(counter)
    }

    /**
     * Releases the receiver reference.
     *
     * [disconnect] will be called if this is the last receiver reference.
     */
    fun release(disconnect: (C) -> Boolean) {
        if (counter.receivers.fetchAndAdd(-1) == 1L) {
            disconnect(counter.chan)
        }
    }

    override fun equals(other: Any?): Boolean =
        other is Receiver<*> && counter === other.counter

    override fun hashCode(): Int = counter.hashCode()
}

/**
 * The upstream Rust port calls std::process::abort() if a reference counter
 * grows past isize::MAX, the assumption being that a runaway counter is an
 * unrecoverable invariant violation. Kotlin has no abort intrinsic; throwing
 * an IllegalStateException is the closest correct signal — the JVM and
 * Kotlin/Native runtimes treat an uncaught error from a non-coroutine
 * context as a hard crash.
 */
private fun abortOnOverflow(): Nothing =
    throw IllegalStateException("crossbeam-channel: reference counter overflowed")
