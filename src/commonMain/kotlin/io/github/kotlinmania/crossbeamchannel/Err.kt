// port-lint: source src/err.rs
package io.github.kotlinmania.crossbeamchannel

/**
 * An error returned from the [Sender.send] method.
 *
 * The message could not be sent because the channel is disconnected.
 *
 * The error contains the message so it can be recovered.
 */
class SendError<T>(val inner: T) {
    /** Human-readable description of the failure. */
    val message: String get() = "sending on a disconnected channel"

    override fun toString(): String = "SendError(..)"

    override fun equals(other: Any?): Boolean =
        this === other || (other is SendError<*> && inner == other.inner)

    override fun hashCode(): Int = inner?.hashCode() ?: 0

    /**
     * Unwraps the message.
     *
     * # Examples
     *
     * ```
     * val (s, r) = unbounded<String>()
     * r.close()
     *
     * val result = s.send("foo")
     * if (result is SendOutcome.Err) {
     *     check(result.error.intoInner() == "foo")
     * }
     * ```
     */
    fun intoInner(): T = inner
}

/**
 * An error returned from the [Sender.trySend] method.
 *
 * The error contains the message being sent so it can be recovered.
 */
sealed class TrySendError<T> {
    /** The message that could not be sent. */
    abstract val inner: T

    /** Human-readable description of the failure. */
    val message: String
        get() = when (this) {
            is Full<*> -> "sending on a full channel"
            is Disconnected<*> -> "sending on a disconnected channel"
        }

    /**
     * The message could not be sent because the channel is full.
     *
     * If this is a zero-capacity channel, then the error indicates that there was no receiver
     * available to receive the message at the time.
     */
    class Full<T>(override val inner: T) : TrySendError<T>() {
        override fun toString(): String = "Full(..)"
    }

    /** The message could not be sent because the channel is disconnected. */
    class Disconnected<T>(override val inner: T) : TrySendError<T>() {
        override fun toString(): String = "Disconnected(..)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TrySendError<*>) return false
        if (this::class != other::class) return false
        return inner == other.inner
    }

    override fun hashCode(): Int = (this::class.hashCode() * 31) + (inner?.hashCode() ?: 0)

    /**
     * Unwraps the message.
     *
     * # Examples
     *
     * ```
     * val (s, r) = bounded<String>(0)
     *
     * val result = s.trySend("foo")
     * if (result is TrySendOutcome.Err) {
     *     check(result.error.intoInner() == "foo")
     * }
     * ```
     */
    fun intoInner(): T = inner

    /** Returns `true` if the send operation failed because the channel is full. */
    fun isFull(): Boolean = this is Full<*>

    /** Returns `true` if the send operation failed because the channel is disconnected. */
    fun isDisconnected(): Boolean = this is Disconnected<*>
}

/** Convert a [SendError] into the equivalent [TrySendError.Disconnected]. */
fun <T> SendError<T>.toTrySendError(): TrySendError<T> = TrySendError.Disconnected(inner)

/**
 * An error returned from the [Sender.sendTimeout] method.
 *
 * The error contains the message being sent so it can be recovered.
 */
sealed class SendTimeoutError<T> {
    /** The message that could not be sent. */
    abstract val inner: T

    /** Human-readable description of the failure. */
    val message: String
        get() = when (this) {
            is Timeout<*> -> "timed out waiting on send operation"
            is Disconnected<*> -> "sending on a disconnected channel"
        }

    /**
     * The message could not be sent because the channel is full and the operation timed out.
     *
     * If this is a zero-capacity channel, then the error indicates that there was no receiver
     * available to receive the message and the operation timed out.
     */
    class Timeout<T>(override val inner: T) : SendTimeoutError<T>()

    /** The message could not be sent because the channel is disconnected. */
    class Disconnected<T>(override val inner: T) : SendTimeoutError<T>()

    override fun toString(): String = "SendTimeoutError(..)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SendTimeoutError<*>) return false
        if (this::class != other::class) return false
        return inner == other.inner
    }

    override fun hashCode(): Int = (this::class.hashCode() * 31) + (inner?.hashCode() ?: 0)

    /**
     * Unwraps the message.
     *
     * # Examples
     *
     * ```
     * import kotlin.time.Duration.Companion.seconds
     *
     * val (s, r) = unbounded<String>()
     *
     * val result = s.sendTimeout("foo", 1.seconds)
     * if (result is SendTimeoutOutcome.Err) {
     *     check(result.error.intoInner() == "foo")
     * }
     * ```
     */
    fun intoInner(): T = inner

    /** Returns `true` if the send operation timed out. */
    fun isTimeout(): Boolean = this is Timeout<*>

    /** Returns `true` if the send operation failed because the channel is disconnected. */
    fun isDisconnected(): Boolean = this is Disconnected<*>
}

/** Convert a [SendError] into the equivalent [SendTimeoutError.Disconnected]. */
fun <T> SendError<T>.toSendTimeoutError(): SendTimeoutError<T> = SendTimeoutError.Disconnected(inner)

/**
 * An error returned from the [Receiver.recv] method.
 *
 * A message could not be received because the channel is empty and disconnected.
 */
class RecvError : Throwable() {
    override val message: String get() = "receiving on an empty and disconnected channel"

    override fun equals(other: Any?): Boolean = other is RecvError

    override fun hashCode(): Int = RECV_ERROR_HASH

    override fun toString(): String = "RecvError"

    private companion object {
        const val RECV_ERROR_HASH: Int = 0x52_45_43_56
    }
}

/** An error returned from the [Receiver.tryRecv] method. */
sealed class TryRecvError : Throwable() {
    /**
     * A message could not be received because the channel is empty.
     *
     * If this is a zero-capacity channel, then the error indicates that there was no sender
     * available to send a message at the time.
     */
    object Empty : TryRecvError() {
        override fun toString(): String = "Empty"
    }

    /** The message could not be received because the channel is empty and disconnected. */
    object Disconnected : TryRecvError() {
        override fun toString(): String = "Disconnected"
    }

    override val message: String
        get() = when (this) {
            Empty -> "receiving on an empty channel"
            Disconnected -> "receiving on an empty and disconnected channel"
        }

    /** Returns `true` if the receive operation failed because the channel is empty. */
    fun isEmpty(): Boolean = this is Empty

    /** Returns `true` if the receive operation failed because the channel is disconnected. */
    fun isDisconnected(): Boolean = this is Disconnected
}

/** Convert a [RecvError] into the equivalent [TryRecvError.Disconnected]. */
fun RecvError.toTryRecvError(): TryRecvError = this.let { TryRecvError.Disconnected }

/** An error returned from the [Receiver.recvTimeout] method. */
sealed class RecvTimeoutError : Throwable() {
    /**
     * A message could not be received because the channel is empty and the operation timed out.
     *
     * If this is a zero-capacity channel, then the error indicates that there was no sender
     * available to send a message and the operation timed out.
     */
    object Timeout : RecvTimeoutError() {
        override fun toString(): String = "Timeout"
    }

    /** The message could not be received because the channel is empty and disconnected. */
    object Disconnected : RecvTimeoutError() {
        override fun toString(): String = "Disconnected"
    }

    override val message: String
        get() = when (this) {
            Timeout -> "timed out waiting on receive operation"
            Disconnected -> "channel is empty and disconnected"
        }

    /** Returns `true` if the receive operation timed out. */
    fun isTimeout(): Boolean = this is Timeout

    /** Returns `true` if the receive operation failed because the channel is disconnected. */
    fun isDisconnected(): Boolean = this is Disconnected
}

/** Convert a [RecvError] into the equivalent [RecvTimeoutError.Disconnected]. */
fun RecvError.toRecvTimeoutError(): RecvTimeoutError = this.let { RecvTimeoutError.Disconnected }

/**
 * An error returned from the [Select.trySelect] method.
 *
 * Failed because none of the channel operations were ready.
 */
class TrySelectError : Throwable() {
    override val message: String get() = "all operations in select would block"

    override fun equals(other: Any?): Boolean = other is TrySelectError

    override fun hashCode(): Int = TRY_SELECT_HASH

    override fun toString(): String = "TrySelectError"

    private companion object {
        const val TRY_SELECT_HASH: Int = 0x54_53_45_4C
    }
}

/**
 * An error returned from the [Select.selectTimeout] method.
 *
 * Failed because none of the channel operations became ready before the timeout.
 */
class SelectTimeoutError : Throwable() {
    override val message: String get() = "timed out waiting on select"

    override fun equals(other: Any?): Boolean = other is SelectTimeoutError

    override fun hashCode(): Int = SELECT_TIMEOUT_HASH

    override fun toString(): String = "SelectTimeoutError"

    private companion object {
        const val SELECT_TIMEOUT_HASH: Int = 0x53_54_4F_55
    }
}

/**
 * An error returned from the [Select.tryReady] method.
 *
 * Failed because none of the channel operations were ready.
 */
class TryReadyError : Throwable() {
    override val message: String get() = "all operations in select would block"

    override fun equals(other: Any?): Boolean = other is TryReadyError

    override fun hashCode(): Int = TRY_READY_HASH

    override fun toString(): String = "TryReadyError"

    private companion object {
        const val TRY_READY_HASH: Int = 0x54_52_44_59
    }
}

/**
 * An error returned from the [Select.readyTimeout] method.
 *
 * Failed because none of the channel operations became ready before the timeout.
 */
class ReadyTimeoutError : Throwable() {
    override val message: String get() = "timed out waiting on select"

    override fun equals(other: Any?): Boolean = other is ReadyTimeoutError

    override fun hashCode(): Int = READY_TIMEOUT_HASH

    override fun toString(): String = "ReadyTimeoutError"

    private companion object {
        const val READY_TIMEOUT_HASH: Int = 0x52_44_54_4F
    }
}
