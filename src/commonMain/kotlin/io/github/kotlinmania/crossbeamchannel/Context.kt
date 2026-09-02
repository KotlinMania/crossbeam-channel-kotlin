// port-lint: source context.rs
package io.github.kotlinmania.crossbeamchannel

import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Instant

private val NEXT_CONTEXT_ID = AtomicLong(1L)

/**
 * Identifier associated with an operation by a specific thread on a specific channel.
 */
public data class Operation(public val id: Long) {
    public companion object {
        private val NEXT_OPERATION_ID = AtomicLong(10L)

        /**
         * Creates a fresh unique operation identifier.
         */
        public fun hook(): Operation {
            val id = NEXT_OPERATION_ID.fetchAndAdd(1L)
            return Operation(id)
        }
    }
}

/**
 * Current state of a select or a blocking operation.
 */
public sealed class Selected {
    /**
     * Still waiting for an operation.
     */
    public data object Waiting : Selected()

    /**
     * The attempt to block the current thread has been aborted.
     */
    public data object Aborted : Selected()

    /**
     * An operation became ready because a channel is disconnected.
     */
    public data object Disconnected : Selected()

    /**
     * An operation became ready because a message can be sent or received.
     */
    public data class Ready(public val operation: Operation) : Selected()

    public fun toCode(): Long = when (this) {
        is Waiting -> 0L
        is Aborted -> 1L
        is Disconnected -> 2L
        is Ready -> operation.id
    }

    public companion object {
        public fun fromCode(code: Long): Selected = when (code) {
            0L -> Waiting
            1L -> Aborted
            2L -> Disconnected
            else -> Ready(Operation(code))
        }
    }
}

/**
 * Inner representation of `Context`.
 */
private class ContextInner(
    val id: Long,
    val select: AtomicLong = AtomicLong(0L),
    val packet: AtomicReference<Any?> = AtomicReference(null)
) {
    fun reset() {
        select.store(0L)
        packet.store(null)
    }
}

/**
 * Thread/coroutine context used in select and blocking operations.
 */
public class Context private constructor(private val inner: ContextInner) {

    public val id: Long
        get() = inner.id

    /**
     * Resets `select` and `packet`.
     */
    public fun reset() {
        inner.reset()
    }

    /**
     * Attempts to select an operation.
     * On failure, returns error with the previously selected state.
     */
    public fun trySelect(select: Selected): kotlin.Result<Unit> {
        val target = select.toCode()
        val prev = inner.select.compareAndExchange(expectedValue = 0L, newValue = target)
        return if (prev == 0L) {
            kotlin.Result.success(Unit)
        } else {
            kotlin.Result.failure(IllegalStateException(Selected.fromCode(prev).toString()))
        }
    }

    /**
     * Attempts to select an operation returning the previous Selected on failure.
     */
    public fun trySelectOrPrevious(select: Selected): Selected? {
        val target = select.toCode()
        val prev = inner.select.compareAndExchange(expectedValue = 0L, newValue = target)
        return if (prev == 0L) {
            null
        } else {
            Selected.fromCode(prev)
        }
    }

    /**
     * Returns the selected operation.
     */
    public fun selected(): Selected {
        return Selected.fromCode(inner.select.load())
    }

    /**
     * Stores a packet.
     */
    public fun storePacket(packet: Any?) {
        if (packet != null) {
            inner.packet.store(packet)
        }
    }

    /**
     * Waits until a packet is provided and returns it.
     */
    public fun waitPacket(): Any? {
        val backoff = Backoff()
        while (true) {
            val p = inner.packet.load()
            if (p != null) {
                return p
            }
            backoff.snooze()
        }
    }

    /**
     * Waits until an operation is selected and returns it.
     * If the deadline is reached, `Selected.Aborted` will be selected.
     */
    public fun waitUntil(deadline: Instant?): Selected {
        val backoff = Backoff()
        while (true) {
            val sel = selected()
            if (sel != Selected.Waiting) {
                return sel
            }

            if (deadline != null) {
                val curr = now()
                if (curr >= deadline) {
                    val prev = trySelectOrPrevious(Selected.Aborted)
                    return prev ?: Selected.Aborted
                }
            }

            backoff.snooze()
        }
    }

    /**
     * Unparks the thread this context belongs to.
     */
    public fun unpark() {
        // In common atomic model, atomic polling in waitUntil observes the state change immediately.
    }

    /**
     * Returns the id of the thread/context this context belongs to.
     */
    public fun threadId(): Long {
        return inner.id
    }

    public companion object {
        /**
         * Creates a new `Context`.
         */
        public fun new(): Context {
            val id = NEXT_CONTEXT_ID.fetchAndAdd(1L)
            return Context(ContextInner(id))
        }

        /**
         * Creates a new context for the duration of the closure.
         */
        public inline fun <R> with(block: (Context) -> R): R {
            val cx = Context.new()
            return block(cx)
        }
    }
}
