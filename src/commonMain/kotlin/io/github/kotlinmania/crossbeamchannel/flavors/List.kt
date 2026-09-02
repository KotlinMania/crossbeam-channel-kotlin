// port-lint: source flavors/list.rs
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
import io.github.kotlinmania.crossbeamchannel.fetchAdd
import io.github.kotlinmania.crossbeamchannel.fetchOr
import io.github.kotlinmania.crossbeamchannel.now
import kotlin.concurrent.atomics.AtomicLong
import kotlin.concurrent.atomics.AtomicReference
import kotlin.time.Instant

private const val WRITE: Long = 1L
private const val READ: Long = 2L
private const val LAP: Long = 32L
private const val BLOCK_CAP: Int = 31
private const val SHIFT: Int = 1
private const val MARK_BIT: Long = 1L

internal class ListSlot<T> {
    var msg: T? = null
    val state = AtomicLong(0L)

    fun waitWrite() {
        val backoff = Backoff()
        while ((state.load() and WRITE) == 0L) {
            backoff.snooze()
        }
    }
}

internal class ListBlock<T> {
    val next = AtomicReference<ListBlock<T>?>(null)
    val slots = Array(BLOCK_CAP) { ListSlot<T>() }

    fun waitNext(): ListBlock<T> {
        val backoff = Backoff()
        while (true) {
            val n = next.load()
            if (n != null) {
                return n
            }
            backoff.snooze()
        }
    }
}

private class Position<T> {
    val block = AtomicReference<ListBlock<T>?>(null)
    val index = AtomicLong(0L)
}

/**
 * Unbounded channel implemented as a linked list of blocks.
 */
internal class ListChannel<T> {
    private val head = Position<T>()
    private val tail = Position<T>()
    internal val receivers = SyncWaker()

    fun receiver(): ListReceiver<T> = ListReceiver(this)

    fun sender(): ListSender<T> = ListSender(this)

    fun startSend(token: Token): Boolean {
        val backoff = Backoff()
        var tailIndex = tail.index.load()
        var block = tail.block.load()
        var nextBlock: ListBlock<T>? = null

        while (true) {
            if ((tailIndex and MARK_BIT) != 0L) {
                token.listBlock = null
                return true
            }

            val offset = ((tailIndex ushr SHIFT) % LAP).toInt()

            if (offset == BLOCK_CAP) {
                backoff.snooze()
                tailIndex = tail.index.load()
                block = tail.block.load()
                continue
            }

            if (offset + 1 == BLOCK_CAP && nextBlock == null) {
                nextBlock = ListBlock()
            }

            if (block == null) {
                val newBlock = ListBlock<T>()
                if (tail.block.compareAndSet(expectedValue = null, newValue = newBlock)) {
                    head.block.store(newBlock)
                    block = newBlock
                } else {
                    nextBlock = newBlock
                    tailIndex = tail.index.load()
                    block = tail.block.load()
                    continue
                }
            }

            val newTail = tailIndex + (1L shl SHIFT)
            if (tail.index.compareAndSet(expectedValue = tailIndex, newValue = newTail)) {
                if (offset + 1 == BLOCK_CAP) {
                    val next = nextBlock ?: ListBlock()
                    tail.block.store(next)
                    tail.index.fetchAdd(1L shl SHIFT)
                    block.next.store(next)
                }

                token.listBlock = block
                token.listOffset = offset
                return true
            } else {
                tailIndex = tail.index.load()
                block = tail.block.load()
                backoff.spin()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun write(token: Token, msg: T): SendOutcome<T> {
        val blockObj = token.listBlock
            ?: return SendOutcome.Err(SendError(msg))

        val block = blockObj as ListBlock<T>
        val slot = block.slots[token.listOffset]
        slot.msg = msg
        slot.state.fetchOr(WRITE)

        receivers.notifyObservers()
        return SendOutcome.Ok
    }

    fun startRecv(token: Token): Boolean {
        val backoff = Backoff()
        var headIndex = head.index.load()
        var block = head.block.load()

        while (true) {
            val offset = ((headIndex ushr SHIFT) % LAP).toInt()

            if (offset == BLOCK_CAP) {
                backoff.snooze()
                headIndex = head.index.load()
                block = head.block.load()
                continue
            }

            val tailIndex = tail.index.load()
            if (headIndex ushr SHIFT == (tailIndex and MARK_BIT.inv()) ushr SHIFT) {
                if ((tailIndex and MARK_BIT) != 0L) {
                    token.listBlock = null
                    return true
                }
                return false
            }

            if (block == null) {
                backoff.snooze()
                headIndex = head.index.load()
                block = head.block.load()
                continue
            }

            val newHead = headIndex + (1L shl SHIFT)
            if (head.index.compareAndSet(expectedValue = headIndex, newValue = newHead)) {
                if (offset + 1 == BLOCK_CAP) {
                    val next = block.waitNext()
                    head.block.store(next)
                    head.index.fetchAdd(1L shl SHIFT)
                }

                token.listBlock = block
                token.listOffset = offset
                return true
            } else {
                headIndex = head.index.load()
                block = head.block.load()
                backoff.spin()
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun read(token: Token): kotlin.Result<T> {
        val blockObj = token.listBlock
            ?: return kotlin.Result.failure(RecvError())

        val block = blockObj as ListBlock<T>
        val slot = block.slots[token.listOffset]
        slot.waitWrite()
        val msg = slot.msg
        slot.msg = null
        slot.state.fetchOr(READ)

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

    fun send(msg: T, deadline: Instant?): SendTimeoutOutcome<T> {
        val token = Token()
        return if (startSend(token)) {
            when (val outcome = write(token, msg)) {
                is SendOutcome.Ok -> SendTimeoutOutcome.Ok
                is SendOutcome.Err -> SendTimeoutOutcome.Err(SendTimeoutError.Disconnected(outcome.error.inner))
            }
        } else {
            SendTimeoutOutcome.Err(SendTimeoutError.Disconnected(msg))
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

    fun isDisconnected(): Boolean = (tail.index.load() and MARK_BIT) != 0L

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
        var tailIndex = tail.index.load()

        while (true) {
            if ((tailIndex and MARK_BIT) != 0L) {
                return false
            }

            val newTail = tailIndex or MARK_BIT
            if (tail.index.compareAndSet(expectedValue = tailIndex, newValue = newTail)) {
                receivers.disconnect()
                return true
            } else {
                tailIndex = tail.index.load()
                backoff.spin()
            }
        }
    }

    fun isEmpty(): Boolean {
        val headIndex = head.index.load()
        val tailIndex = tail.index.load()
        return (headIndex ushr SHIFT) == ((tailIndex and MARK_BIT.inv()) ushr SHIFT)
    }

    fun isFull(): Boolean = false

    fun len(): Int {
        while (true) {
            var tailVal = tail.index.load()
            var headVal = head.index.load()

            if (tail.index.load() == tailVal) {
                tailVal = tailVal and ((1L shl SHIFT) - 1L).inv()
                headVal = headVal and ((1L shl SHIFT) - 1L).inv()

                if ((tailVal ushr SHIFT) and (LAP - 1L) == LAP - 1L) {
                    tailVal += (1L shl SHIFT)
                }
                if ((headVal ushr SHIFT) and (LAP - 1L) == LAP - 1L) {
                    headVal += (1L shl SHIFT)
                }

                val lap = (headVal ushr SHIFT) / LAP
                tailVal -= (lap * LAP) shl SHIFT
                headVal -= (lap * LAP) shl SHIFT

                tailVal = tailVal ushr SHIFT
                headVal = headVal ushr SHIFT

                val diff = tailVal - headVal - (tailVal / LAP)
                return diff.toInt().coerceAtLeast(0)
            }
        }
    }

    fun capacity(): Int? = null

    companion object {
        fun <T> new(): ListChannel<T> = ListChannel()
    }
}

internal class ListSender<T>(val channel: ListChannel<T>) : SelectHandle {
    override fun trySelect(token: Token): Boolean = channel.startSend(token)
    override fun deadline(): Instant? = null
    override fun register(oper: Operation, cx: Context): Boolean = isReady()
    override fun unregister(oper: Operation) {}
    override fun accept(token: Token, cx: Context): Boolean = channel.startSend(token)
    override fun isReady(): Boolean = true
    override fun watch(oper: Operation, cx: Context): Boolean = isReady()
    override fun unwatch(oper: Operation) {}
}

internal class ListReceiver<T>(val channel: ListChannel<T>) : SelectHandle {
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
