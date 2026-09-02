// port-lint: source flavors/array.rs
package io.github.kotlinmania.crossbeamchannel.flavors

import io.github.kotlinmania.crossbeamchannel.Backoff
import io.github.kotlinmania.crossbeamchannel.Context
import io.github.kotlinmania.crossbeamchannel.Operation
import io.github.kotlinmania.crossbeamchannel.RecvError
import io.github.kotlinmania.crossbeamchannel.RecvTimeoutError
import io.github.kotlinmania.crossbeamchannel.SelectHandle
import io.github.kotlinmania.crossbeamchannel.Selected
import io.github.kotlinmania.crossbeamchannel.SendError
import io.github.kotlinmania.crossbeamchannel.SendOutcome
import io.github.kotlinmania.crossbeamchannel.SendTimeoutError
import io.github.kotlinmania.crossbeamchannel.SendTimeoutOutcome
import io.github.kotlinmania.crossbeamchannel.SyncWaker
import io.github.kotlinmania.crossbeamchannel.Token
import io.github.kotlinmania.crossbeamchannel.TryRecvError
import io.github.kotlinmania.crossbeamchannel.TrySendError
import io.github.kotlinmania.crossbeamchannel.TrySendOutcome
import io.github.kotlinmania.crossbeamchannel.now
import kotlin.concurrent.atomics.AtomicLong
import kotlin.time.Instant

internal class ArraySlot<T>(
    initialStamp: Long
) {
    val stamp = AtomicLong(initialStamp)
    var msg: T? = null
}

/**
 * Bounded channel based on a preallocated array.
 */
internal class ArrayChannel<T>(
    val cap: Int
) {
    private val markBit: Long
    private val oneLap: Long
    private val head = AtomicLong(0L)
    private val tail = AtomicLong(0L)
    private val buffer: Array<ArraySlot<T>>
    internal val senders = SyncWaker()
    internal val receivers = SyncWaker()

    init {
        require(cap > 0) { "capacity must be positive" }

        var mb = 1L
        while (mb < cap + 1) {
            mb = mb shl 1
        }
        markBit = mb
        oneLap = markBit * 2L

        buffer = Array(cap) { i ->
            ArraySlot(i.toLong())
        }
    }

    fun receiver(): ArrayReceiver<T> = ArrayReceiver(this)

    fun sender(): ArraySender<T> = ArraySender(this)

    fun startSend(token: Token): Boolean {
        val backoff = Backoff()
        var tailVal = tail.load()

        while (true) {
            if ((tailVal and markBit) != 0L) {
                token.arraySlot = -1
                token.arrayStamp = 0L
                return true
            }

            val index = (tailVal and (markBit - 1L)).toInt()
            val lap = tailVal and (oneLap - 1L).inv()

            val slot = buffer[index]
            val stamp = slot.stamp.load()

            if (tailVal == stamp) {
                val newTail = if (index + 1 < cap) {
                    tailVal + 1L
                } else {
                    lap + oneLap
                }

                if (tail.compareAndSet(expectedValue = tailVal, newValue = newTail)) {
                    token.arraySlot = index
                    token.arrayStamp = tailVal + 1L
                    return true
                } else {
                    tailVal = tail.load()
                    backoff.spin()
                }
            } else if ((stamp + oneLap) == tailVal + 1L) {
                val headVal = head.load()
                if ((headVal + oneLap) == tailVal) {
                    return false
                }
                backoff.spin()
                tailVal = tail.load()
            } else {
                backoff.snooze()
                tailVal = tail.load()
            }
        }
    }

    fun write(token: Token, msg: T): SendOutcome<T> {
        if (token.arraySlot < 0) {
            return SendOutcome.Err(SendError(msg))
        }

        val slot = buffer[token.arraySlot]
        slot.msg = msg
        slot.stamp.store(token.arrayStamp)

        receivers.notifyObservers()
        return SendOutcome.Ok
    }

    fun startRecv(token: Token): Boolean {
        val backoff = Backoff()
        var headVal = head.load()

        while (true) {
            val index = (headVal and (markBit - 1L)).toInt()
            val lap = headVal and (oneLap - 1L).inv()

            val slot = buffer[index]
            val stamp = slot.stamp.load()

            if (headVal + 1L == stamp) {
                val newHead = if (index + 1 < cap) {
                    headVal + 1L
                } else {
                    lap + oneLap
                }

                if (head.compareAndSet(expectedValue = headVal, newValue = newHead)) {
                    token.arraySlot = index
                    token.arrayStamp = headVal + 1L
                    return true
                } else {
                    headVal = head.load()
                    backoff.spin()
                }
            } else if (stamp == headVal) {
                val tailVal = tail.load()
                if (headVal == (tailVal and markBit.inv())) {
                    if ((tailVal and markBit) != 0L) {
                        token.arraySlot = -1
                        token.arrayStamp = 0L
                        return true
                    }
                    return false
                }
                backoff.spin()
                headVal = head.load()
            } else {
                backoff.snooze()
                headVal = head.load()
            }
        }
    }

    fun read(token: Token): kotlin.Result<T> {
        if (token.arraySlot < 0) {
            return kotlin.Result.failure(RecvError())
        }

        val slot = buffer[token.arraySlot]
        val msg = slot.msg
        slot.msg = null
        slot.stamp.store(token.arrayStamp + oneLap - 1L)

        senders.notifyObservers()
        return if (msg != null) {
            kotlin.Result.success(msg)
        } else {
            kotlin.Result.failure(RecvError())
        }
    }

    fun trySend(msg: T): TrySendOutcome<T> {
        val token = Token()
        return if (startSend(token)) {
            when (val outcome = write(token, msg)) {
                is SendOutcome.Ok -> TrySendOutcome.Ok
                is SendOutcome.Err -> TrySendOutcome.Err(TrySendError.Disconnected(outcome.error.inner))
            }
        } else {
            TrySendOutcome.Err(TrySendError.Full(msg))
        }
    }

    fun isDisconnected(): Boolean = (tail.load() and markBit) != 0L

    fun send(msg: T, deadline: Instant?): SendTimeoutOutcome<T> {
        val token = Token()
        while (true) {
            val backoff = Backoff()
            while (true) {
                if (startSend(token)) {
                    return when (val outcome = write(token, msg)) {
                        is SendOutcome.Ok -> SendTimeoutOutcome.Ok
                        is SendOutcome.Err -> SendTimeoutOutcome.Err(SendTimeoutError.Disconnected(outcome.error.inner))
                    }
                }

                if (backoff.isCompleted()) {
                    break
                } else {
                    backoff.snooze()
                }
            }

            if (deadline != null && now() >= deadline) {
                return SendTimeoutOutcome.Err(SendTimeoutError.Timeout(msg))
            }

            Context.with { cx ->
                val oper = Operation.hook()
                senders.register(oper, cx)

                if (!isFull() || isDisconnected()) {
                    cx.trySelect(Selected.Aborted)
                }

                val sel = cx.waitUntil(deadline)
                when (sel) {
                    is Selected.Waiting -> throw IllegalStateException("unreachable")
                    is Selected.Aborted, is Selected.Disconnected -> {
                        senders.unregister(oper)
                    }
                    is Selected.Ready -> {}
                }
            }
        }
    }

    fun tryRecv(): kotlin.Result<T> {
        val token = Token()
        return if (startRecv(token)) {
            read(token).fold(
                onSuccess = { kotlin.Result.success(it) },
                onFailure = { kotlin.Result.failure(TryRecvError.Disconnected) }
            )
        } else {
            kotlin.Result.failure(TryRecvError.Empty)
        }
    }

    fun recv(deadline: Instant?): kotlin.Result<T> {
        val token = Token()
        while (true) {
            val backoff = Backoff()
            while (true) {
                if (startRecv(token)) {
                    return read(token).fold(
                        onSuccess = { kotlin.Result.success(it) },
                        onFailure = { kotlin.Result.failure(RecvTimeoutError.Disconnected) }
                    )
                }

                if (backoff.isCompleted()) {
                    break
                } else {
                    backoff.snooze()
                }
            }

            if (deadline != null && now() >= deadline) {
                return kotlin.Result.failure(RecvTimeoutError.Timeout)
            }

            Context.with { cx ->
                val oper = Operation.hook()
                receivers.register(oper, cx)

                if (!isEmpty() || isDisconnected()) {
                    cx.trySelect(Selected.Aborted)
                }

                val sel = cx.waitUntil(deadline)
                when (sel) {
                    is Selected.Waiting -> throw IllegalStateException("unreachable")
                    is Selected.Aborted, is Selected.Disconnected -> {
                        receivers.unregister(oper)
                    }
                    is Selected.Ready -> {}
                }
            }
        }
    }

    fun disconnect(): Boolean {
        val backoff = Backoff()
        var tailVal = tail.load()

        while (true) {
            if ((tailVal and markBit) != 0L) {
                return false
            }

            val newTail = tailVal or markBit
            if (tail.compareAndSet(expectedValue = tailVal, newValue = newTail)) {
                senders.disconnect()
                receivers.disconnect()
                return true
            } else {
                tailVal = tail.load()
                backoff.spin()
            }
        }
    }

    fun isEmpty(): Boolean {
        val headVal = head.load()
        val tailVal = tail.load()
        return headVal == (tailVal and markBit.inv())
    }

    fun isFull(): Boolean {
        val headVal = head.load()
        val tailVal = tail.load()
        return headVal + oneLap == (tailVal and markBit.inv())
    }

    fun len(): Int {
        val headVal = head.load()
        val tailVal = tail.load()
        val h = headVal
        val t = tailVal and markBit.inv()

        val hLap = h and (oneLap - 1L).inv()
        val tLap = t and (oneLap - 1L).inv()
        val hIdx = (h and (markBit - 1L)).toInt()
        val tIdx = (t and (markBit - 1L)).toInt()

        return if (hLap == tLap) {
            tIdx - hIdx
        } else {
            cap - hIdx + tIdx
        }
    }

    fun capacity(): Int? = cap

    companion object {
        fun <T> withCapacity(cap: Int): ArrayChannel<T> = ArrayChannel(cap)
    }
}

internal class ArraySender<T>(val channel: ArrayChannel<T>) : SelectHandle {
    override fun trySelect(token: Token): Boolean = channel.startSend(token)
    override fun deadline(): Instant? = null
    override fun register(oper: Operation, cx: Context): Boolean {
        channel.senders.register(oper, cx)
        return isReady()
    }
    override fun unregister(oper: Operation) {
        channel.senders.unregister(oper)
    }
    override fun accept(token: Token, cx: Context): Boolean = channel.startSend(token)
    override fun isReady(): Boolean = !channel.isFull() || channel.isDisconnected()
    override fun watch(oper: Operation, cx: Context): Boolean {
        channel.senders.watch(oper, cx)
        return isReady()
    }
    override fun unwatch(oper: Operation) {
        channel.senders.unwatch(oper)
    }
}

internal class ArrayReceiver<T>(val channel: ArrayChannel<T>) : SelectHandle {
    override fun trySelect(token: Token): Boolean = channel.startRecv(token)
    override fun deadline(): Instant? = null
    override fun register(oper: Operation, cx: Context): Boolean {
        channel.receivers.register(oper, cx)
        return isReady()
    }
    override fun unregister(oper: Operation) {
        channel.receivers.unregister(oper)
    }
    override fun accept(token: Token, cx: Context): Boolean = channel.startRecv(token)
    override fun isReady(): Boolean = !channel.isEmpty() || channel.isDisconnected()
    override fun watch(oper: Operation, cx: Context): Boolean {
        channel.receivers.watch(oper, cx)
        return isReady()
    }
    override fun unwatch(oper: Operation) {
        channel.receivers.unwatch(oper)
    }
}
