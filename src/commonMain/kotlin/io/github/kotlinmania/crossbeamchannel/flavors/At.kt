// port-lint: source flavors/at.rs
package io.github.kotlinmania.crossbeamchannel.flavors

import io.github.kotlinmania.crossbeamchannel.Backoff
import io.github.kotlinmania.crossbeamchannel.Context
import io.github.kotlinmania.crossbeamchannel.Operation
import io.github.kotlinmania.crossbeamchannel.RecvTimeoutError
import io.github.kotlinmania.crossbeamchannel.SelectHandle
import io.github.kotlinmania.crossbeamchannel.Token
import io.github.kotlinmania.crossbeamchannel.TryRecvError
import io.github.kotlinmania.crossbeamchannel.now
import io.github.kotlinmania.crossbeamchannel.sleepUntil
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Instant

/**
 * Channel that delivers a message at a certain moment in time.
 */
internal class AtChannel(
    private val deliveryTime: Instant
) : SelectHandle {

    private val received = AtomicBoolean(false)

    fun tryRecv(): kotlin.Result<Instant> {
        if (received.load()) {
            return kotlin.Result.failure(TryRecvError.Empty)
        }

        if (now() < deliveryTime) {
            return kotlin.Result.failure(TryRecvError.Empty)
        }

        return if (received.compareAndSet(expectedValue = false, newValue = true)) {
            kotlin.Result.success(deliveryTime)
        } else {
            kotlin.Result.failure(TryRecvError.Empty)
        }
    }

    fun recv(deadline: Instant?): kotlin.Result<Instant> {
        if (received.load()) {
            sleepUntil(deadline)
            return kotlin.Result.failure(RecvTimeoutError.Timeout)
        }

        val backoff = Backoff()
        while (true) {
            val curr = now()
            if (curr >= deliveryTime) {
                break
            }
            if (deadline != null && curr >= deadline) {
                return kotlin.Result.failure(RecvTimeoutError.Timeout)
            }
            backoff.snooze()
        }

        return if (received.compareAndSet(expectedValue = false, newValue = true)) {
            kotlin.Result.success(deliveryTime)
        } else {
            sleepUntil(null)
            throw IllegalStateException("unreachable")
        }
    }

    fun read(token: Token): kotlin.Result<Instant> {
        val at = token.at
        return if (at != null) {
            kotlin.Result.success(at)
        } else {
            kotlin.Result.failure(IllegalStateException("no message in token"))
        }
    }

    fun isEmpty(): Boolean {
        if (received.load()) return true
        if (now() < deliveryTime) return true
        return received.load()
    }

    fun isFull(): Boolean = !isEmpty()

    fun len(): Int = if (isEmpty()) 0 else 1

    fun capacity(): Int? = 1

    override fun trySelect(token: Token): Boolean {
        val res = tryRecv()
        return if (res.isSuccess) {
            token.at = res.getOrNull()
            true
        } else {
            false
        }
    }

    override fun deadline(): Instant? {
        return if (received.load()) null else deliveryTime
    }

    override fun register(oper: Operation, cx: Context): Boolean = isReady()

    override fun unregister(oper: Operation) {}

    override fun accept(token: Token, cx: Context): Boolean = trySelect(token)

    override fun isReady(): Boolean = !isEmpty()

    override fun watch(oper: Operation, cx: Context): Boolean = isReady()

    override fun unwatch(oper: Operation) {}

    companion object {
        fun newDeadline(whenInstant: Instant): AtChannel = AtChannel(whenInstant)
    }
}
