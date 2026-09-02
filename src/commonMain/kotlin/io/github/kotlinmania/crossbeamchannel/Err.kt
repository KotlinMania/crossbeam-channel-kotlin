// port-lint: source err.rs
package io.github.kotlinmania.crossbeamchannel

/**
 * An error returned from the [Sender.send] method.
 *
 * The message could not be sent because the channel is disconnected.
 *
 * The error contains the message so it can be recovered.
 */
public class SendError<T>(
    public val inner: T,
) {
    /** Human-readable description of the failure. */
    public val message: String get() = "sending on a disconnected channel"

    override fun toString(): String = "SendError(..)"

    override fun equals(other: Any?): Boolean =
        this === other || (other is SendError<*> && inner == other.inner)

    override fun hashCode(): Int = inner?.hashCode() ?: 0

    /**
     * Unwraps the message.
     */
    public fun intoInner(): T = inner
}

/**
 * Result of [Sender.send].
 */
public sealed interface SendOutcome<out T> {
    /** Successfully sent. */
    public data object Ok : SendOutcome<Nothing>

    /** Failed to send because the channel was disconnected. */
    public data class Err<T>(
        public val error: SendError<T>,
    ) : SendOutcome<T>
}

/**
 * An error returned from the [Sender.trySend] method.
 *
 * The error contains the message being sent so it can be recovered.
 */
public sealed class TrySendError<T> {
    /** The message that could not be sent. */
    public abstract val inner: T

    /** Human-readable description of the failure. */
    public val message: String
        get() =
            when (this) {
                is Full<*> -> "sending on a full channel"
                is Disconnected<*> -> "sending on a disconnected channel"
            }

    /**
     * The message could not be sent because the channel is full.
     *
     * If this is a zero-capacity channel, then the error indicates that there was no receiver
     * available to receive the message at the time.
     */
    public class Full<T>(
        override val inner: T,
    ) : TrySendError<T>() {
        override fun toString(): String = "Full(..)"
    }

    /** The message could not be sent because the channel is disconnected. */
    public class Disconnected<T>(
        override val inner: T,
    ) : TrySendError<T>() {
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
     */
    public fun intoInner(): T = inner

    /** Returns `true` if the send operation failed because the channel is full. */
    public fun isFull(): Boolean = this is Full<*>

    /** Returns `true` if the send operation failed because the channel is disconnected. */
    public fun isDisconnected(): Boolean = this is Disconnected<*>
}

/**
 * Result of [Sender.trySend].
 */
public sealed interface TrySendOutcome<out T> {
    /** Successfully sent. */
    public data object Ok : TrySendOutcome<Nothing>

    /** Failed to send because the channel was full or disconnected. */
    public data class Err<T>(
        public val error: TrySendError<T>,
    ) : TrySendOutcome<T>
}

/** Convert a [SendError] into the equivalent [TrySendError.Disconnected]. */
public fun <T> SendError<T>.toTrySendError(): TrySendError<T> = TrySendError.Disconnected(inner)

/**
 * An error returned from the [Sender.sendTimeout] method.
 *
 * The error contains the message being sent so it can be recovered.
 */
public sealed class SendTimeoutError<T> {
    /** The message that could not be sent. */
    public abstract val inner: T

    /** Human-readable description of the failure. */
    public val message: String
        get() =
            when (this) {
                is Timeout<*> -> "timed out waiting on send operation"
                is Disconnected<*> -> "sending on a disconnected channel"
            }

    /**
     * The message could not be sent because the channel is full and the operation timed out.
     *
     * If this is a zero-capacity channel, then the error indicates that there was no receiver
     * available to receive the message and the operation timed out.
     */
    public class Timeout<T>(
        override val inner: T,
    ) : SendTimeoutError<T>()

    /** The message could not be sent because the channel is disconnected. */
    public class Disconnected<T>(
        override val inner: T,
    ) : SendTimeoutError<T>()

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
     */
    public fun intoInner(): T = inner

    /** Returns `true` if the send operation timed out. */
    public fun isTimeout(): Boolean = this is Timeout<*>

    /** Returns `true` if the send operation failed because the channel is disconnected. */
    public fun isDisconnected(): Boolean = this is Disconnected<*>
}

/**
 * Result of [Sender.sendTimeout] and [Sender.sendDeadline].
 */
public sealed interface SendTimeoutOutcome<out T> {
    /** Successfully sent. */
    public data object Ok : SendTimeoutOutcome<Nothing>

    /** Failed to send because of timeout or disconnected channel. */
    public data class Err<T>(
        public val error: SendTimeoutError<T>,
    ) : SendTimeoutOutcome<T>
}

/** Convert a [SendError] into the equivalent [SendTimeoutError.Disconnected]. */
public fun <T> SendError<T>.toSendTimeoutError(): SendTimeoutError<T> = SendTimeoutError.Disconnected(inner)

/**
 * An error returned from the [Receiver.recv] method.
 *
 * A message could not be received because the channel is empty and disconnected.
 */
public class RecvError : Throwable() {
    override val message: String get() = "receiving on an empty and disconnected channel"

    override fun equals(other: Any?): Boolean = other is RecvError

    override fun hashCode(): Int = RECV_ERROR_HASH

    override fun toString(): String = "RecvError"

    private companion object {
        const val RECV_ERROR_HASH: Int = 0x52_45_43_56
    }
}

/** An error returned from the [Receiver.tryRecv] method. */
public sealed class TryRecvError : Throwable() {
    /**
     * A message could not be received because the channel is empty.
     *
     * If this is a zero-capacity channel, then the error indicates that there was no sender
     * available to send a message at the time.
     */
    public object Empty : TryRecvError() {
        override fun toString(): String = "Empty"
    }

    /** The message could not be received because the channel is empty and disconnected. */
    public object Disconnected : TryRecvError() {
        override fun toString(): String = "Disconnected"
    }

    override val message: String
        get() =
            when (this) {
                Empty -> "receiving on an empty channel"
                Disconnected -> "receiving on an empty and disconnected channel"
            }

    /** Returns `true` if the receive operation failed because the channel is empty. */
    public fun isEmpty(): Boolean = this is Empty

    /** Returns `true` if the receive operation failed because the channel is disconnected. */
    public fun isDisconnected(): Boolean = this is Disconnected
}

/** Convert a [RecvError] into the equivalent [TryRecvError.Disconnected]. */
public fun RecvError.toTryRecvError(): TryRecvError = this.let { TryRecvError.Disconnected }

/** An error returned from the [Receiver.recvTimeout] method. */
public sealed class RecvTimeoutError : Throwable() {
    /**
     * A message could not be received because the channel is empty and the operation timed out.
     *
     * If this is a zero-capacity channel, then the error indicates that there was no sender
     * available to send a message and the operation timed out.
     */
    public object Timeout : RecvTimeoutError() {
        override fun toString(): String = "Timeout"
    }

    /** The message could not be received because the channel is empty and disconnected. */
    public object Disconnected : RecvTimeoutError() {
        override fun toString(): String = "Disconnected"
    }

    override val message: String
        get() =
            when (this) {
                Timeout -> "timed out waiting on receive operation"
                Disconnected -> "channel is empty and disconnected"
            }

    /** Returns `true` if the receive operation timed out. */
    public fun isTimeout(): Boolean = this is Timeout

    /** Returns `true` if the receive operation failed because the channel is disconnected. */
    public fun isDisconnected(): Boolean = this is Disconnected
}

/** Convert a [RecvError] into the equivalent [RecvTimeoutError.Disconnected]. */
public fun RecvError.toRecvTimeoutError(): RecvTimeoutError = this.let { RecvTimeoutError.Disconnected }

/**
 * An error returned from the [Select.trySelect] method.
 *
 * Failed because none of the channel operations were ready.
 */
public class TrySelectError : Throwable() {
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
public class SelectTimeoutError : Throwable() {
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
public class TryReadyError : Throwable() {
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
public class ReadyTimeoutError : Throwable() {
    override val message: String get() = "timed out waiting on select"

    override fun equals(other: Any?): Boolean = other is ReadyTimeoutError

    override fun hashCode(): Int = READY_TIMEOUT_HASH

    override fun toString(): String = "ReadyTimeoutError"

    private companion object {
        const val READY_TIMEOUT_HASH: Int = 0x52_44_54_4F
    }
}
