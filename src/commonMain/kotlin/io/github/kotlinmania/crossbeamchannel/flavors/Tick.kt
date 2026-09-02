// port-lint: source flavors/tick.rs
package io.github.kotlinmania.crossbeamchannel.flavors

import io.github.kotlinmania.crossbeamchannel.Backoff
import io.github.kotlinmania.crossbeamchannel.Context
import io.github.kotlinmania.crossbeamchannel.Operation
import io.github.kotlinmania.crossbeamchannel.RecvTimeoutError
import io.github.kotlinmania.crossbeamchannel.SelectHandle
import io.github.kotlinmania.crossbeamchannel.Token
import io.github.kotlinmania.crossbeamchannel.TryRecvError
import io.github.kotlinmania.crossbeamchannel.now
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Channel that delivers messages periodically.
 */
internal class TickChannel(
    deliveryTime: Instant,
    private val duration: Duration
) : SelectHandle {

    private val deliveryTimeRef = AtomicReference(deliveryTime)

    fun tryRecv(): kotlin.Result<Instant> {
        while (true) {
            val curr = now()
            val dt = deliveryTimeRef.load()

            if (curr < dt) {
                return kotlin.Result.failure(TryRecvError.Empty)
            }

            val next = curr + duration
            if (deliveryTimeRef.compareAndSet(expectedValue = dt, newValue = next)) {
                return kotlin.Result.success(dt)
            }
        }
    }

    fun recv(deadline: Instant?): kotlin.Result<Instant> {
        val backoff = Backoff()
        while (true) {
            val dt = deliveryTimeRef.load()
            val curr = now()

            if (deadline != null) {
                if (deadline < dt) {
                    if (curr < deadline) {
                        // wait until deadline
                    }
                    return kotlin.Result.failure(RecvTimeoutError.Timeout)
                }
            }

            val next = maxOf(dt, curr) + duration
            if (deliveryTimeRef.compareAndSet(expectedValue = dt, newValue = next)) {
                while (now() < dt) {
                    backoff.snooze()
                }
                return kotlin.Result.success(dt)
            }
            backoff.snooze()
        }
    }

    fun read(token: Token): kotlin.Result<Instant> {
        val tick = token.tick
        return if (tick != null) {
            kotlin.Result.success(tick)
        } else {
            kotlin.Result.failure(IllegalStateException("no message in token"))
        }
    }

    fun isEmpty(): Boolean {
        return now() < deliveryTimeRef.load()
    }

    fun isFull(): Boolean = !isEmpty()

    fun len(): Int = if (isEmpty()) 0 else 1

    fun capacity(): Int? = 1

    override fun trySelect(token: Token): Boolean {
        val res = tryRecv()
        return if (res.isSuccess) {
            token.tick = res.getOrNull()
            true
        } else {
            false
        }
    }

    override fun deadline(): Instant? = deliveryTimeRef.load()

    override fun register(oper: Operation, cx: Context): Boolean = isReady()

    override fun unregister(oper: Operation) {}

    override fun accept(token: Token, cx: Context): Boolean = trySelect(token)

    override fun isReady(): Boolean = !isEmpty()

    override fun watch(oper: Operation, cx: Context): Boolean = isReady()

    override fun unwatch(oper: Operation) {}

    companion object {
        fun new(deliveryTime: Instant, dur: Duration): TickChannel = TickChannel(deliveryTime, dur)
    }
}
