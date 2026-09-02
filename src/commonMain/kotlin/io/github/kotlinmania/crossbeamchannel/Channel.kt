// port-lint: source channel.rs
package io.github.kotlinmania.crossbeamchannel

import io.github.kotlinmania.crossbeamchannel.counter.Receiver as CounterReceiver
import io.github.kotlinmania.crossbeamchannel.counter.Sender as CounterSender
import io.github.kotlinmania.crossbeamchannel.counter.new as newCounter
import io.github.kotlinmania.crossbeamchannel.flavors.ArrayChannel
import io.github.kotlinmania.crossbeamchannel.flavors.ArrayReceiver
import io.github.kotlinmania.crossbeamchannel.flavors.ArraySender
import io.github.kotlinmania.crossbeamchannel.flavors.AtChannel
import io.github.kotlinmania.crossbeamchannel.flavors.ListChannel
import io.github.kotlinmania.crossbeamchannel.flavors.ListReceiver
import io.github.kotlinmania.crossbeamchannel.flavors.ListSender
import io.github.kotlinmania.crossbeamchannel.flavors.NeverChannel
import io.github.kotlinmania.crossbeamchannel.flavors.TickChannel
import io.github.kotlinmania.crossbeamchannel.flavors.ZeroChannel
import io.github.kotlinmania.crossbeamchannel.flavors.ZeroReceiver
import io.github.kotlinmania.crossbeamchannel.flavors.ZeroSender
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Creates a channel of unbounded capacity.
 */
public fun <T> unbounded(): Pair<Sender<T>, Receiver<T>> {
    val (s, r) = newCounter(ListChannel.new<T>())
    val sender = Sender(SenderFlavor.List(s.chan.sender(), s))
    val receiver = Receiver(ReceiverFlavor.List(r.chan.receiver(), r))
    return Pair(sender, receiver)
}

/**
 * Creates a channel of bounded capacity.
 */
public fun <T> bounded(cap: Int): Pair<Sender<T>, Receiver<T>> {
    return if (cap == 0) {
        val (s, r) = newCounter(ZeroChannel.new<T>())
        val sender = Sender(SenderFlavor.Zero(s.chan.sender(), s))
        val receiver = Receiver(ReceiverFlavor.Zero(r.chan.receiver(), r))
        Pair(sender, receiver)
    } else {
        val (s, r) = newCounter(ArrayChannel.withCapacity<T>(cap))
        val sender = Sender(SenderFlavor.Array(s.chan.sender(), s))
        val receiver = Receiver(ReceiverFlavor.Array(r.chan.receiver(), r))
        Pair(sender, receiver)
    }
}

/**
 * Creates a receiver that delivers a message after a certain duration of time.
 */
public fun after(duration: Duration): Receiver<Instant> {
    val deadline = now() + duration
    return Receiver(ReceiverFlavor.At(AtChannel.newDeadline(deadline)))
}

/**
 * Creates a receiver that delivers a message at a certain instant in time.
 */
public fun at(whenInstant: Instant): Receiver<Instant> {
    return Receiver(ReceiverFlavor.At(AtChannel.newDeadline(whenInstant)))
}

/**
 * Creates a receiver that never delivers messages.
 */
public fun <T> never(): Receiver<T> {
    return Receiver(ReceiverFlavor.Never(NeverChannel.new()))
}

/**
 * Creates a receiver that delivers messages periodically.
 */
public fun tick(duration: Duration): Receiver<Instant> {
    val deliveryTime = now() + duration
    return Receiver(ReceiverFlavor.Tick(TickChannel.new(deliveryTime, duration)))
}

internal sealed class SenderFlavor<T> {
    class Array<T>(val sender: ArraySender<T>, val counter: CounterSender<ArrayChannel<T>>) : SenderFlavor<T>()
    class List<T>(val sender: ListSender<T>, val counter: CounterSender<ListChannel<T>>) : SenderFlavor<T>()
    class Zero<T>(val sender: ZeroSender<T>, val counter: CounterSender<ZeroChannel<T>>) : SenderFlavor<T>()
}

internal sealed class ReceiverFlavor<T> {
    class Array<T>(val receiver: ArrayReceiver<T>, val counter: CounterReceiver<ArrayChannel<T>>) : ReceiverFlavor<T>()
    class List<T>(val receiver: ListReceiver<T>, val counter: CounterReceiver<ListChannel<T>>) : ReceiverFlavor<T>()
    class Zero<T>(val receiver: ZeroReceiver<T>, val counter: CounterReceiver<ZeroChannel<T>>) : ReceiverFlavor<T>()
    class At(val channel: AtChannel) : ReceiverFlavor<Instant>()
    class Never<T>(val channel: NeverChannel<T>) : ReceiverFlavor<T>()
    class Tick(val channel: TickChannel) : ReceiverFlavor<Instant>()
}

/**
 * An iterator over messages on a receiver.
 */
public class Iter<T> internal constructor(
    private val receiver: Receiver<T>
) : Iterator<T> {
    private var nextMsg: T? = null
    private var done = false

    override fun hasNext(): Boolean {
        if (done) return false
        if (nextMsg != null) return true
        val res = receiver.recv()
        return if (res.isSuccess) {
            nextMsg = res.getOrNull()
            true
        } else {
            done = true
            false
        }
    }

    override fun next(): T {
        if (!hasNext()) throw NoSuchElementException()
        val msg = nextMsg ?: throw NoSuchElementException()
        nextMsg = null
        return msg
    }
}

/**
 * A non-blocking iterator over messages on a receiver.
 */
public class TryIter<T> internal constructor(
    private val receiver: Receiver<T>
) : Iterator<T> {
    private var nextMsg: T? = null
    private var done = false

    override fun hasNext(): Boolean {
        if (done) return false
        if (nextMsg != null) return true
        val res = receiver.tryRecv()
        return if (res.isSuccess) {
            nextMsg = res.getOrNull()
            true
        } else {
            done = true
            false
        }
    }

    override fun next(): T {
        if (!hasNext()) throw NoSuchElementException()
        val msg = nextMsg ?: throw NoSuchElementException()
        nextMsg = null
        return msg
    }
}

/**
 * An owning iterator over messages on a receiver.
 */
public class IntoIter<T> internal constructor(
    private val receiver: Receiver<T>
) : Iterator<T> {
    private val iter = Iter(receiver)
    override fun hasNext(): Boolean = iter.hasNext()
    override fun next(): T = iter.next()
}

/**
 * The sending side of a channel.
 */
public class Sender<T> internal constructor(
    internal val flavor: SenderFlavor<T>
) : SelectHandle {

    /**
     * Attempts to send a message into the channel without blocking.
     */
    public fun trySend(msg: T): TrySendOutcome<T> {
        return when (flavor) {
            is SenderFlavor.Array -> flavor.sender.channel.trySend(msg)
            is SenderFlavor.List -> flavor.sender.channel.trySend(msg)
            is SenderFlavor.Zero -> flavor.sender.channel.trySend(msg)
        }
    }

    /**
     * Sends a message into the channel, blocking if the channel is full.
     */
    public fun send(msg: T): SendOutcome<T> {
        return when (flavor) {
            is SenderFlavor.Array -> when (val res = flavor.sender.channel.send(msg, null)) {
                is SendTimeoutOutcome.Ok -> SendOutcome.Ok
                is SendTimeoutOutcome.Err -> SendOutcome.Err(SendError(res.error.inner))
            }
            is SenderFlavor.List -> when (val res = flavor.sender.channel.send(msg, null)) {
                is SendTimeoutOutcome.Ok -> SendOutcome.Ok
                is SendTimeoutOutcome.Err -> SendOutcome.Err(SendError(res.error.inner))
            }
            is SenderFlavor.Zero -> when (val res = flavor.sender.channel.send(msg, null)) {
                is SendTimeoutOutcome.Ok -> SendOutcome.Ok
                is SendTimeoutOutcome.Err -> SendOutcome.Err(SendError(res.error.inner))
            }
        }
    }

    /**
     * Sends a message into the channel, blocking at most until `timeout`.
     */
    public fun sendTimeout(msg: T, timeout: Duration): SendTimeoutOutcome<T> {
        val deadline = now() + timeout
        return sendDeadline(msg, deadline)
    }

    /**
     * Sends a message into the channel, blocking at most until `deadline`.
     */
    public fun sendDeadline(msg: T, deadline: Instant): SendTimeoutOutcome<T> {
        return when (flavor) {
            is SenderFlavor.Array -> flavor.sender.channel.send(msg, deadline)
            is SenderFlavor.List -> flavor.sender.channel.send(msg, deadline)
            is SenderFlavor.Zero -> flavor.sender.channel.send(msg, deadline)
        }
    }

    /**
     * Returns `true` if the channel is empty.
     */
    public fun isEmpty(): Boolean = when (flavor) {
        is SenderFlavor.Array -> flavor.sender.channel.isEmpty()
        is SenderFlavor.List -> flavor.sender.channel.isEmpty()
        is SenderFlavor.Zero -> flavor.sender.channel.isEmpty()
    }

    /**
     * Returns `true` if the channel is full.
     */
    public fun isFull(): Boolean = when (flavor) {
        is SenderFlavor.Array -> flavor.sender.channel.isFull()
        is SenderFlavor.List -> flavor.sender.channel.isFull()
        is SenderFlavor.Zero -> flavor.sender.channel.isFull()
    }

    /**
     * Returns the number of messages in the channel.
     */
    public fun len(): Int = when (flavor) {
        is SenderFlavor.Array -> flavor.sender.channel.len()
        is SenderFlavor.List -> flavor.sender.channel.len()
        is SenderFlavor.Zero -> flavor.sender.channel.len()
    }

    /**
     * Returns the channel capacity, or `null` if unbounded.
     */
    public fun capacity(): Int? = when (flavor) {
        is SenderFlavor.Array -> flavor.sender.channel.capacity()
        is SenderFlavor.List -> flavor.sender.channel.capacity()
        is SenderFlavor.Zero -> flavor.sender.channel.capacity()
    }

    /**
     * Returns `true` if this sender and `other` send into the same channel.
     */
    public fun sameChannel(other: Sender<T>): Boolean {
        return when (val f = flavor) {
            is SenderFlavor.Array -> other.flavor is SenderFlavor.Array && f.sender.channel === other.flavor.sender.channel
            is SenderFlavor.List -> other.flavor is SenderFlavor.List && f.sender.channel === other.flavor.sender.channel
            is SenderFlavor.Zero -> other.flavor is SenderFlavor.Zero && f.sender.channel === other.flavor.sender.channel
        }
    }

    /**
     * Writes a message into the channel using a selection token.
     */
    internal fun write(token: Token, msg: T): SendOutcome<T> {
        return when (flavor) {
            is SenderFlavor.Array -> flavor.sender.channel.write(token, msg)
            is SenderFlavor.List -> flavor.sender.channel.write(token, msg)
            is SenderFlavor.Zero -> flavor.sender.channel.write(token, msg)
        }
    }

    /**
     * Clones this sender.
     */
    public fun clone(): Sender<T> {
        return when (flavor) {
            is SenderFlavor.Array -> Sender(SenderFlavor.Array(flavor.sender, flavor.counter.acquire()))
            is SenderFlavor.List -> Sender(SenderFlavor.List(flavor.sender, flavor.counter.acquire()))
            is SenderFlavor.Zero -> Sender(SenderFlavor.Zero(flavor.sender, flavor.counter.acquire()))
        }
    }

    /**
     * Releases this sender endpoint.
     */
    public fun release() {
        when (flavor) {
            is SenderFlavor.Array -> flavor.counter.release { it.disconnect() }
            is SenderFlavor.List -> flavor.counter.release { it.disconnect() }
            is SenderFlavor.Zero -> flavor.counter.release { it.disconnect() }
        }
    }

    private val selectHandle: SelectHandle
        get() = when (flavor) {
            is SenderFlavor.Array -> flavor.sender
            is SenderFlavor.List -> flavor.sender
            is SenderFlavor.Zero -> flavor.sender
        }

    override fun trySelect(token: Token): Boolean = selectHandle.trySelect(token)
    override fun deadline(): Instant? = selectHandle.deadline()
    override fun register(oper: Operation, cx: Context): Boolean = selectHandle.register(oper, cx)
    override fun unregister(oper: Operation) = selectHandle.unregister(oper)
    override fun accept(token: Token, cx: Context): Boolean = selectHandle.accept(token, cx)
    override fun isReady(): Boolean = selectHandle.isReady()
    override fun watch(oper: Operation, cx: Context): Boolean = selectHandle.watch(oper, cx)
    override fun unwatch(oper: Operation) = selectHandle.unwatch(oper)
}

/**
 * The receiving side of a channel.
 */
public class Receiver<T> internal constructor(
    internal val flavor: ReceiverFlavor<T>
) : SelectHandle, Iterable<T> {

    /**
     * Attempts to receive a message from the channel without blocking.
     */
    @Suppress("UNCHECKED_CAST")
    public fun tryRecv(): kotlin.Result<T> {
        return when (flavor) {
            is ReceiverFlavor.Array -> flavor.receiver.channel.tryRecv()
            is ReceiverFlavor.List -> flavor.receiver.channel.tryRecv()
            is ReceiverFlavor.Zero -> flavor.receiver.channel.tryRecv()
            is ReceiverFlavor.At -> flavor.channel.tryRecv() as kotlin.Result<T>
            is ReceiverFlavor.Never -> flavor.channel.tryRecv()
            is ReceiverFlavor.Tick -> flavor.channel.tryRecv() as kotlin.Result<T>
        }
    }

    /**
     * Receives a message from the channel, blocking if the channel is empty.
     */
    @Suppress("UNCHECKED_CAST")
    public fun recv(): kotlin.Result<T> {
        val res = when (flavor) {
            is ReceiverFlavor.Array -> flavor.receiver.channel.recv(null)
            is ReceiverFlavor.List -> flavor.receiver.channel.recv(null)
            is ReceiverFlavor.Zero -> flavor.receiver.channel.recv(null)
            is ReceiverFlavor.At -> flavor.channel.recv(null) as kotlin.Result<T>
            is ReceiverFlavor.Never -> flavor.channel.recv(null)
            is ReceiverFlavor.Tick -> flavor.channel.recv(null) as kotlin.Result<T>
        }
        return res.fold(
            onSuccess = { kotlin.Result.success(it) },
            onFailure = { kotlin.Result.failure(RecvError()) }
        )
    }

    /**
     * Receives a message from the channel, blocking at most until `timeout`.
     */
    public fun recvTimeout(timeout: Duration): kotlin.Result<T> {
        val deadline = now() + timeout
        return recvDeadline(deadline)
    }

    /**
     * Receives a message from the channel, blocking at most until `deadline`.
     */
    @Suppress("UNCHECKED_CAST")
    public fun recvDeadline(deadline: Instant): kotlin.Result<T> {
        return when (flavor) {
            is ReceiverFlavor.Array -> flavor.receiver.channel.recv(deadline)
            is ReceiverFlavor.List -> flavor.receiver.channel.recv(deadline)
            is ReceiverFlavor.Zero -> flavor.receiver.channel.recv(deadline)
            is ReceiverFlavor.At -> flavor.channel.recv(deadline) as kotlin.Result<T>
            is ReceiverFlavor.Never -> flavor.channel.recv(deadline)
            is ReceiverFlavor.Tick -> flavor.channel.recv(deadline) as kotlin.Result<T>
        }
    }

    /**
     * Returns `true` if the channel is empty.
     */
    public fun isEmpty(): Boolean = when (flavor) {
        is ReceiverFlavor.Array -> flavor.receiver.channel.isEmpty()
        is ReceiverFlavor.List -> flavor.receiver.channel.isEmpty()
        is ReceiverFlavor.Zero -> flavor.receiver.channel.isEmpty()
        is ReceiverFlavor.At -> flavor.channel.isEmpty()
        is ReceiverFlavor.Never -> flavor.channel.isEmpty()
        is ReceiverFlavor.Tick -> flavor.channel.isEmpty()
    }

    /**
     * Returns `true` if the channel is full.
     */
    public fun isFull(): Boolean = when (flavor) {
        is ReceiverFlavor.Array -> flavor.receiver.channel.isFull()
        is ReceiverFlavor.List -> flavor.receiver.channel.isFull()
        is ReceiverFlavor.Zero -> flavor.receiver.channel.isFull()
        is ReceiverFlavor.At -> flavor.channel.isFull()
        is ReceiverFlavor.Never -> flavor.channel.isFull()
        is ReceiverFlavor.Tick -> flavor.channel.isFull()
    }

    /**
     * Returns the number of messages in the channel.
     */
    public fun len(): Int = when (flavor) {
        is ReceiverFlavor.Array -> flavor.receiver.channel.len()
        is ReceiverFlavor.List -> flavor.receiver.channel.len()
        is ReceiverFlavor.Zero -> flavor.receiver.channel.len()
        is ReceiverFlavor.At -> flavor.channel.len()
        is ReceiverFlavor.Never -> flavor.channel.len()
        is ReceiverFlavor.Tick -> flavor.channel.len()
    }

    /**
     * Returns the channel capacity, or `null` if unbounded.
     */
    public fun capacity(): Int? = when (flavor) {
        is ReceiverFlavor.Array -> flavor.receiver.channel.capacity()
        is ReceiverFlavor.List -> flavor.receiver.channel.capacity()
        is ReceiverFlavor.Zero -> flavor.receiver.channel.capacity()
        is ReceiverFlavor.At -> flavor.channel.capacity()
        is ReceiverFlavor.Never -> flavor.channel.capacity()
        is ReceiverFlavor.Tick -> flavor.channel.capacity()
    }

    /**
     * Returns `true` if this receiver and `other` receive from the same channel.
     */
    public fun sameChannel(other: Receiver<T>): Boolean {
        return when (val f = flavor) {
            is ReceiverFlavor.Array -> other.flavor is ReceiverFlavor.Array && f.receiver.channel === other.flavor.receiver.channel
            is ReceiverFlavor.List -> other.flavor is ReceiverFlavor.List && f.receiver.channel === other.flavor.receiver.channel
            is ReceiverFlavor.Zero -> other.flavor is ReceiverFlavor.Zero && f.receiver.channel === other.flavor.receiver.channel
            is ReceiverFlavor.At -> other.flavor is ReceiverFlavor.At && f.channel === other.flavor.channel
            is ReceiverFlavor.Never -> other.flavor is ReceiverFlavor.Never
            is ReceiverFlavor.Tick -> other.flavor is ReceiverFlavor.Tick && f.channel === other.flavor.channel
        }
    }

    /**
     * Reads a message from the channel using a selection token.
     */
    @Suppress("UNCHECKED_CAST")
    internal fun read(token: Token): kotlin.Result<T> {
        return when (flavor) {
            is ReceiverFlavor.Array -> flavor.receiver.channel.read(token)
            is ReceiverFlavor.List -> flavor.receiver.channel.read(token)
            is ReceiverFlavor.Zero -> flavor.receiver.channel.read(token)
            is ReceiverFlavor.At -> flavor.channel.read(token) as kotlin.Result<T>
            is ReceiverFlavor.Never -> flavor.channel.read(token)
            is ReceiverFlavor.Tick -> flavor.channel.read(token) as kotlin.Result<T>
        }
    }

    /**
     * Returns an iterator over messages in the channel.
     */
    public fun iter(): Iter<T> = Iter(this)

    /**
     * Returns a non-blocking iterator over messages in the channel.
     */
    public fun tryIter(): TryIter<T> = TryIter(this)

    /**
     * Returns an owning iterator over messages in the channel.
     */
    public fun intoIter(): IntoIter<T> = IntoIter(this)

    override fun iterator(): Iterator<T> = iter()

    /**
     * Clones this receiver.
     */
    @Suppress("UNCHECKED_CAST")
    public fun clone(): Receiver<T> {
        return when (flavor) {
            is ReceiverFlavor.Array -> Receiver(ReceiverFlavor.Array(flavor.receiver, flavor.counter.acquire()))
            is ReceiverFlavor.List -> Receiver(ReceiverFlavor.List(flavor.receiver, flavor.counter.acquire()))
            is ReceiverFlavor.Zero -> Receiver(ReceiverFlavor.Zero(flavor.receiver, flavor.counter.acquire()))
            is ReceiverFlavor.At -> Receiver(ReceiverFlavor.At(flavor.channel) as ReceiverFlavor<T>)
            is ReceiverFlavor.Never -> Receiver(ReceiverFlavor.Never(flavor.channel))
            is ReceiverFlavor.Tick -> Receiver(ReceiverFlavor.Tick(flavor.channel) as ReceiverFlavor<T>)
        }
    }

    /**
     * Releases this receiver endpoint.
     */
    public fun release() {
        when (flavor) {
            is ReceiverFlavor.Array -> flavor.counter.release { it.disconnect() }
            is ReceiverFlavor.List -> flavor.counter.release { it.disconnect() }
            is ReceiverFlavor.Zero -> flavor.counter.release { it.disconnect() }
            is ReceiverFlavor.At -> {}
            is ReceiverFlavor.Never -> {}
            is ReceiverFlavor.Tick -> {}
        }
    }

    private val selectHandle: SelectHandle
        get() = when (flavor) {
            is ReceiverFlavor.Array -> flavor.receiver
            is ReceiverFlavor.List -> flavor.receiver
            is ReceiverFlavor.Zero -> flavor.receiver
            is ReceiverFlavor.At -> flavor.channel
            is ReceiverFlavor.Never -> flavor.channel
            is ReceiverFlavor.Tick -> flavor.channel
        }

    override fun trySelect(token: Token): Boolean = selectHandle.trySelect(token)
    override fun deadline(): Instant? = selectHandle.deadline()
    override fun register(oper: Operation, cx: Context): Boolean = selectHandle.register(oper, cx)
    override fun unregister(oper: Operation) = selectHandle.unregister(oper)
    override fun accept(token: Token, cx: Context): Boolean = selectHandle.accept(token, cx)
    override fun isReady(): Boolean = selectHandle.isReady()
    override fun watch(oper: Operation, cx: Context): Boolean = selectHandle.watch(oper, cx)
    override fun unwatch(oper: Operation) = selectHandle.unwatch(oper)
}
