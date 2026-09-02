// port-lint: source flavors/zero.rs
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
import io.github.kotlinmania.crossbeamchannel.SpinLock
import io.github.kotlinmania.crossbeamchannel.Token
import io.github.kotlinmania.crossbeamchannel.TryRecvError
import io.github.kotlinmania.crossbeamchannel.TrySendError
import io.github.kotlinmania.crossbeamchannel.TrySendOutcome
import io.github.kotlinmania.crossbeamchannel.Waker
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.time.Instant

/**
 * A slot for passing one message from a sender to a receiver.
 */
internal class Packet<T>(
    var msg: T? = null,
    val onStack: Boolean = true
) {
    val ready = AtomicBoolean(false)

    fun waitReady() {
        val backoff = Backoff()
        while (!ready.load()) {
            backoff.snooze()
        }
    }
}

internal class ZeroInner {
    val senders = Waker()
    val receivers = Waker()
    var isDisconnected = false
}

/**
 * Zero-capacity channel (rendezvous channel).
 */
internal class ZeroChannel<T> {
    internal val lock = SpinLock()
    internal val inner = ZeroInner()

    fun receiver(): ZeroReceiver<T> = ZeroReceiver(this)

    fun sender(): ZeroSender<T> = ZeroSender(this)

    fun startSend(token: Token): Boolean {
        return lock.withLock {
            val operation = inner.receivers.trySelect()
            if (operation != null) {
                token.zeroPacket = operation.packet
                true
            } else if (inner.isDisconnected) {
                token.zeroPacket = null
                true
            } else {
                false
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun write(token: Token, msg: T): SendOutcome<T> {
        val packetObj = token.zeroPacket
            ?: return SendOutcome.Err(SendError(msg))

        val packet = packetObj as Packet<T>
        packet.msg = msg
        packet.ready.store(true)
        return SendOutcome.Ok
    }

    fun startRecv(token: Token): Boolean {
        return lock.withLock {
            val operation = inner.senders.trySelect()
            if (operation != null) {
                token.zeroPacket = operation.packet
                true
            } else if (inner.isDisconnected) {
                token.zeroPacket = null
                true
            } else {
                false
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun read(token: Token): kotlin.Result<T> {
        val packetObj = token.zeroPacket
            ?: return kotlin.Result.failure(RecvError())

        val packet = packetObj as Packet<T>
        return if (packet.onStack) {
            val msg = packet.msg
            packet.msg = null
            packet.ready.store(true)
            if (msg != null) {
                kotlin.Result.success(msg)
            } else {
                kotlin.Result.failure(RecvError())
            }
        } else {
            packet.waitReady()
            val msg = packet.msg
            packet.msg = null
            if (msg != null) {
                kotlin.Result.success(msg)
            } else {
                kotlin.Result.failure(RecvError())
            }
        }
    }

    fun trySend(msg: T): TrySendOutcome<T> {
        val token = Token()
        val res = lock.withLock {
            val op = inner.receivers.trySelect()
            if (op != null) {
                token.zeroPacket = op.packet
                null
            } else if (inner.isDisconnected) {
                TrySendOutcome.Err(TrySendError.Disconnected(msg))
            } else {
                TrySendOutcome.Err(TrySendError.Full(msg))
            }
        }

        if (res != null) return res

        return when (val outcome = write(token, msg)) {
            is SendOutcome.Ok -> TrySendOutcome.Ok
            is SendOutcome.Err -> TrySendOutcome.Err(TrySendError.Disconnected(outcome.error.inner))
        }
    }

    fun send(msg: T, deadline: Instant?): SendTimeoutOutcome<T> {
        val token = Token()
        var immediateDisconn = false
        var immediateReady = false

        lock.withLock {
            val op = inner.receivers.trySelect()
            if (op != null) {
                token.zeroPacket = op.packet
                immediateReady = true
            } else if (inner.isDisconnected) {
                immediateDisconn = true
            }
        }

        if (immediateReady) {
            return when (val outcome = write(token, msg)) {
                is SendOutcome.Ok -> SendTimeoutOutcome.Ok
                is SendOutcome.Err -> SendTimeoutOutcome.Err(SendTimeoutError.Disconnected(outcome.error.inner))
            }
        }
        if (immediateDisconn) {
            return SendTimeoutOutcome.Err(SendTimeoutError.Disconnected(msg))
        }

        return Context.with { cx ->
            val oper = Operation.hook()
            val packet = Packet(msg = msg, onStack = true)

            lock.withLock {
                inner.senders.registerWithPacket(oper, packet, cx)
                inner.receivers.notifyObservers()
            }

            val sel = cx.waitUntil(deadline)

            when (sel) {
                is Selected.Waiting -> throw IllegalStateException("unreachable")
                is Selected.Aborted -> {
                    lock.withLock {
                        inner.senders.unregister(oper)
                    }
                    val prev = cx.trySelectOrPrevious(Selected.Aborted)
                    if (prev != null) {
                        packet.waitReady()
                        SendTimeoutOutcome.Ok
                    } else {
                        SendTimeoutOutcome.Err(SendTimeoutError.Timeout(msg))
                    }
                }
                is Selected.Disconnected -> {
                    lock.withLock {
                        inner.senders.unregister(oper)
                    }
                    SendTimeoutOutcome.Err(SendTimeoutError.Disconnected(msg))
                }
                is Selected.Ready -> {
                    packet.waitReady()
                    SendTimeoutOutcome.Ok
                }
            }
        }
    }

    fun tryRecv(): kotlin.Result<T> {
        val token = Token()
        var immediateEmpty = false
        var immediateDisconn = false
        var immediateReady = false

        lock.withLock {
            val op = inner.senders.trySelect()
            if (op != null) {
                token.zeroPacket = op.packet
                immediateReady = true
            } else if (inner.isDisconnected) {
                immediateDisconn = true
            } else {
                immediateEmpty = true
            }
        }

        if (immediateReady) {
            return read(token).fold(
                onSuccess = { kotlin.Result.success(it) },
                onFailure = { kotlin.Result.failure(TryRecvError.Disconnected) }
            )
        }
        if (immediateDisconn) {
            return kotlin.Result.failure(TryRecvError.Disconnected)
        }
        return kotlin.Result.failure(TryRecvError.Empty)
    }

    fun recv(deadline: Instant?): kotlin.Result<T> {
        val token = Token()
        var immediateDisconn = false
        var immediateReady = false

        lock.withLock {
            val op = inner.senders.trySelect()
            if (op != null) {
                token.zeroPacket = op.packet
                immediateReady = true
            } else if (inner.isDisconnected) {
                immediateDisconn = true
            }
        }

        if (immediateReady) {
            return read(token).fold(
                onSuccess = { kotlin.Result.success(it) },
                onFailure = { kotlin.Result.failure(RecvTimeoutError.Disconnected) }
            )
        }
        if (immediateDisconn) {
            return kotlin.Result.failure(RecvTimeoutError.Disconnected)
        }

        return Context.with { cx ->
            val oper = Operation.hook()
            val packet = Packet<T>(msg = null, onStack = false)

            lock.withLock {
                inner.receivers.registerWithPacket(oper, packet, cx)
                inner.senders.notifyObservers()
            }

            val sel = cx.waitUntil(deadline)

            when (sel) {
                is Selected.Waiting -> throw IllegalStateException("unreachable")
                is Selected.Aborted -> {
                    lock.withLock {
                        inner.receivers.unregister(oper)
                    }
                    val prev = cx.trySelectOrPrevious(Selected.Aborted)
                    if (prev != null) {
                        token.zeroPacket = packet
                        read(token).fold(
                            onSuccess = { kotlin.Result.success(it) },
                            onFailure = { kotlin.Result.failure(RecvTimeoutError.Disconnected) }
                        )
                    } else {
                        kotlin.Result.failure(RecvTimeoutError.Timeout)
                    }
                }
                is Selected.Disconnected -> {
                    lock.withLock {
                        inner.receivers.unregister(oper)
                    }
                    kotlin.Result.failure(RecvTimeoutError.Disconnected)
                }
                is Selected.Ready -> {
                    val p = cx.waitPacket()
                    token.zeroPacket = p ?: packet
                    read(token).fold(
                        onSuccess = { kotlin.Result.success(it) },
                        onFailure = { kotlin.Result.failure(RecvTimeoutError.Disconnected) }
                    )
                }
            }
        }
    }

    fun disconnect(): Boolean {
        return lock.withLock {
            if (inner.isDisconnected) {
                false
            } else {
                inner.isDisconnected = true
                inner.senders.disconnect()
                inner.receivers.disconnect()
                true
            }
        }
    }

    fun isEmpty(): Boolean {
        return lock.withLock {
            !inner.senders.canSelect()
        }
    }

    fun isFull(): Boolean {
        return lock.withLock {
            !inner.receivers.canSelect()
        }
    }

    fun len(): Int = 0

    fun capacity(): Int? = 0

    companion object {
        fun <T> new(): ZeroChannel<T> = ZeroChannel()
    }
}

internal class ZeroSender<T>(val channel: ZeroChannel<T>) : SelectHandle {
    override fun trySelect(token: Token): Boolean = channel.startSend(token)
    override fun deadline(): Instant? = null
    override fun register(oper: Operation, cx: Context): Boolean {
        val packet = Packet<T>(msg = null, onStack = false)
        return channel.lock.withLock {
            channel.inner.senders.registerWithPacket(oper, packet, cx)
            channel.inner.receivers.notifyObservers()
            channel.inner.receivers.canSelect() || channel.inner.isDisconnected
        }
    }
    override fun unregister(oper: Operation) {
        channel.lock.withLock {
            channel.inner.senders.unregister(oper)
        }
    }
    override fun accept(token: Token, cx: Context): Boolean {
        token.zeroPacket = cx.waitPacket()
        return true
    }
    override fun isReady(): Boolean = channel.lock.withLock {
        channel.inner.receivers.canSelect() || channel.inner.isDisconnected
    }
    override fun watch(oper: Operation, cx: Context): Boolean = channel.lock.withLock {
        channel.inner.senders.watch(oper, cx)
        channel.inner.receivers.canSelect() || channel.inner.isDisconnected
    }
    override fun unwatch(oper: Operation) {
        channel.lock.withLock {
            channel.inner.senders.unwatch(oper)
        }
    }
}

internal class ZeroReceiver<T>(val channel: ZeroChannel<T>) : SelectHandle {
    override fun trySelect(token: Token): Boolean = channel.startRecv(token)
    override fun deadline(): Instant? = null
    override fun register(oper: Operation, cx: Context): Boolean {
        val packet = Packet<T>(msg = null, onStack = false)
        return channel.lock.withLock {
            channel.inner.receivers.registerWithPacket(oper, packet, cx)
            channel.inner.senders.notifyObservers()
            channel.inner.senders.canSelect() || channel.inner.isDisconnected
        }
    }
    override fun unregister(oper: Operation) {
        channel.lock.withLock {
            channel.inner.receivers.unregister(oper)
        }
    }
    override fun accept(token: Token, cx: Context): Boolean {
        token.zeroPacket = cx.waitPacket()
        return true
    }
    override fun isReady(): Boolean = channel.lock.withLock {
        channel.inner.senders.canSelect() || channel.inner.isDisconnected
    }
    override fun watch(oper: Operation, cx: Context): Boolean = channel.lock.withLock {
        channel.inner.receivers.watch(oper, cx)
        channel.inner.senders.canSelect() || channel.inner.isDisconnected
    }
    override fun unwatch(oper: Operation) {
        channel.lock.withLock {
            channel.inner.receivers.unwatch(oper)
        }
    }
}
